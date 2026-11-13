package org.localpilot.shared;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Actual CPU actor-critic: 64 -> tanh(64) -> tanh(64) -> {masked actions,V}.
 * Java17/Android30+, no JNI or cloud, never issues a game command itself.
 * The native GGUF ModelLab continues to use its existing separate backend.
 *
 * Format LPS2 is explicitly versioned, DataOutputStream big-endian float32.
 * It is NOT silently compatible with an undocumented older weights.bin format.
 */
public final class StudentPolicyNet {
 public static final int INPUT=64,HIDDEN=64,MIN_ACTIONS=2,MAX_ACTIONS=16;
 private static final int MAGIC=0x4c505332; // LPS2
 public final int actionCount;
 final float[] w1=new float[INPUT*HIDDEN],b1=new float[HIDDEN];
 final float[] w2=new float[HIDDEN*HIDDEN],b2=new float[HIDDEN];
 final float[] wa,ba,wv=new float[HIDDEN];
 float bv;

 public record Decision(int action,float oldLogProb,float value,String policySha,
                        float[] probabilities) {
  public Decision {probabilities=probabilities.clone();}
  @Override public float[] probabilities(){return probabilities.clone();}
 }
 public record Forward(float[] h1,float[] h2,float[] probs,float value) {
  public Forward {
   h1=h1.clone();h2=h2.clone();probs=probs.clone();
  }
  @Override public float[] h1(){return h1.clone();}
  @Override public float[] h2(){return h2.clone();}
  @Override public float[] probs(){return probs.clone();}
 }
 public StudentPolicyNet(int actions,long seed){
  if(actions<MIN_ACTIONS||actions>MAX_ACTIONS)throw new IllegalArgumentException("actionCount");
  actionCount=actions;
  wa=new float[HIDDEN*actions];ba=new float[actions];
  Random random=new Random(seed);
  init(w1,INPUT,HIDDEN,random);init(w2,HIDDEN,HIDDEN,random);
  init(wa,HIDDEN,actions,random);
  init(wv,HIDDEN,1,random);
 }
 private StudentPolicyNet(StudentPolicyNet model){
  actionCount=model.actionCount;
  wa=new float[model.wa.length];ba=new float[model.ba.length];
  System.arraycopy(model.w1,0,w1,0,w1.length);
  System.arraycopy(model.b1,0,b1,0,b1.length);
  System.arraycopy(model.w2,0,w2,0,w2.length);
  System.arraycopy(model.b2,0,b2,0,b2.length);
  System.arraycopy(model.wa,0,wa,0,wa.length);
  System.arraycopy(model.ba,0,ba,0,ba.length);
  System.arraycopy(model.wv,0,wv,0,wv.length);bv=model.bv;
 }
 public StudentPolicyNet candidateCopy(){return new StudentPolicyNet(this);}
 private static void init(float[] weights,int fanIn,int fanOut,Random random){
  float scale=(float)Math.sqrt(2.0/(fanIn+fanOut));
  for(int i=0;i<weights.length;i++)weights[i]=(float)(random.nextGaussian()*scale);
 }
 public Forward forward(float[] observation,int mask){
  if(observation==null||observation.length!=INPUT)throw new IllegalArgumentException("obs64 required");
  for(float v:observation)if(!Float.isFinite(v))throw new IllegalArgumentException("nonfinite obs64");
  final int allowed=(1<<actionCount)-1;
  if((mask&allowed)==0||(mask&~allowed)!=0)throw new IllegalArgumentException("invalid legalMask");
  float[] a=new float[HIDDEN],b=new float[HIDDEN],p=new float[actionCount];
  for(int o=0;o<HIDDEN;o++){
   float sum=b1[o],row=o*INPUT;
   for(int i=0;i<INPUT;i++)sum+=w1[row+i]*observation[i];
   if(!Float.isFinite(sum))throw new IllegalStateException("nonfinite layer1");
   a[o]=(float)Math.tanh(sum);
  }
  for(int o=0;o<HIDDEN;o++){
   float sum=b2[o],row=o*HIDDEN;
   for(int i=0;i<HIDDEN;i++)sum+=w2[row+i]*a[i];
   if(!Float.isFinite(sum))throw new IllegalStateException("nonfinite layer2");
   b[o]=(float)Math.tanh(sum);
  }
  float[] logits=new float[actionCount];
  float max=Float.NEGATIVE_INFINITY;
  for(int action=0;action<actionCount;action++){
   if((mask&(1<<action))==0)continue;
   float sum=ba[action],row=action*HIDDEN;
   for(int j=0;j<HIDDEN;j++)sum+=wa[row+j]*b[j];
   if(!Float.isFinite(sum))throw new IllegalStateException("nonfinite actor");
   logits[action]=sum;
   max=Math.max(max,sum);
  }
  double total=0;
  for(int action=0;action<actionCount;action++){
   if((mask&(1<<action))==0)continue; // invalid actions have EXACT zero probability
   p[action]=(float)Math.exp((double)logits[action]-max);
   total+=p[action];
  }
  if(!Double.isFinite(total)||total<=0)throw new IllegalStateException("empty softmax");
  for(int action=0;action<actionCount;action++)p[action]=(float)(p[action]/total);
  float value=bv;
  for(int j=0;j<HIDDEN;j++)value+=wv[j]*b[j];
  if(!Float.isFinite(value))throw new IllegalStateException("nonfinite critic");
  return new Forward(a,b,p,value);
 }
 public Decision choose(float[] obs64,int legalMask,Random random){
  if(random==null)throw new IllegalArgumentException("random");
  Forward fp=forward(obs64,legalMask);
  float[] p=fp.probs();
  double r=random.nextDouble();
  int selected=-1,lastLegal=-1;
  for(int i=0;i<actionCount;i++){
   if((legalMask&(1<<i))==0)continue;
   lastLegal=i;
   r-=p[i];
   if(r<0){selected=i;break;}
  }
  if(selected<0)selected=lastLegal; // float rounding only
  if(selected<0||p[selected]<=0)throw new IllegalStateException("zero-probability action");
  return new Decision(selected,(float)Math.log(p[selected]),fp.value(),sha256(),p);
 }
 public int argmax(float[] obs64,int legalMask){
  float[] p=forward(obs64,legalMask).probs();
  int best=-1;
  for(int i=0;i<actionCount;i++)
   if((legalMask&(1<<i))!=0&&(best<0||p[i]>p[best]))best=i;
  return best;
 }
 public String sha256(){
  try{
   byte[] digest=MessageDigest.getInstance("SHA-256").digest(toBytes());
   StringBuilder s=new StringBuilder(64);
   for(byte b:digest)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
   return s.toString();
  }catch(NoSuchAlgorithmException|IOException e){throw new IllegalStateException("weights hashing",e);}
 }
 private static void emit(DataOutputStream out,float[] data)throws IOException{
  for(float v:data){if(!Float.isFinite(v))throw new IOException("Nonfinite weight");out.writeFloat(v);}
 }
 public byte[] toBytes()throws IOException{
  ByteArrayOutputStream buffer=new ByteArrayOutputStream(40000);
  try(DataOutputStream out=new DataOutputStream(buffer)){
   out.writeInt(MAGIC);out.writeInt(INPUT);out.writeInt(HIDDEN);
   out.writeInt(HIDDEN);out.writeInt(actionCount);
   emit(out,w1);emit(out,b1);emit(out,w2);emit(out,b2);
   emit(out,wa);emit(out,ba);emit(out,wv);emit(out,new float[]{bv});
  }
  return buffer.toByteArray();
 }
 private static void read(DataInputStream in,float[] target)throws IOException{
  for(int i=0;i<target.length;i++){
   float value=in.readFloat();
   if(!Float.isFinite(value))throw new IOException("Nonfinite weight");
   target[i]=value;
  }
 }
 public static StudentPolicyNet fromBytes(byte[] data)throws IOException{
  if(data==null||data.length<20||data.length>100000)throw new IOException("Invalid weights size");
  try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(data))){
   int magic=in.readInt(),input=in.readInt(),h1=in.readInt(),h2=in.readInt(),actions=in.readInt();
   if(magic!=MAGIC||input!=INPUT||h1!=HIDDEN||h2!=HIDDEN||
      actions<MIN_ACTIONS||actions>MAX_ACTIONS)
    throw new IOException("LPS2 shape/magic mismatch");
   StudentPolicyNet net=new StudentPolicyNet(actions,0L);
   int expected=20+4*(net.w1.length+net.b1.length+net.w2.length+net.b2.length+
                       net.wa.length+net.ba.length+net.wv.length+1);
   if(data.length!=expected)throw new IOException("LPS2 payload length mismatch");
   read(in,net.w1);read(in,net.b1);read(in,net.w2);read(in,net.b2);
   read(in,net.wa);read(in,net.ba);read(in,net.wv);
   net.bv=in.readFloat();
   if(!Float.isFinite(net.bv)||in.read()!=-1)throw new IOException("Bad critic/trailing bytes");
   return net;
  }
 }
 public static StudentPolicyNet load(Path weights)throws IOException{
  if(!Files.isRegularFile(weights)||Files.isSymbolicLink(weights))
   throw new IOException("Unsafe policy path");
  return fromBytes(Files.readAllBytes(weights));
 }
 public void saveAtomically(Path weights)throws IOException{
  if(weights==null||weights.getParent()==null)throw new IOException("Missing policy directory");
  Path dir=weights.getParent();
  Files.createDirectories(dir);
  if(Files.isSymbolicLink(dir)||Files.isSymbolicLink(weights))
   throw new IOException("Unsafe student policy path");
  byte[] bytes=toBytes();
  Path tmp=Files.createTempFile(dir,"student-policy-",".pending");
  try{
   try(FileOutputStream output=new FileOutputStream(tmp.toFile())){
    output.write(bytes);output.getFD().sync();
   }
   if(!sha256().equals(load(tmp).sha256()))throw new IOException("Failed weight roundtrip");
   Files.move(tmp,weights,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  }finally{Files.deleteIfExists(tmp);}
 }
}
