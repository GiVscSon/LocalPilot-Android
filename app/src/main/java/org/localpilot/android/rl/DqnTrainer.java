package org.localpilot.android.rl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** CPU Double DQN, Huber loss and Adam. Checkpoints contain real optimizer/replay/RNG state. */
public final class DqnTrainer {
 public static final int CAPACITY=20000,BATCH=32,WARMUP=1000;
 public static final float GAMMA=.99f,LR=.001f;
 public final ArcadeEnv env;
 public final Net online,target;
 public long steps,updates,episodes;
 public float episodeReturn,lastReturn,bestProgress,lastProgress,loss;
 final ArcadeEnv.Rng rng;
 final float[] states=new float[CAPACITY*64],nextStates=new float[CAPACITY*64],rewards=new float[CAPACITY];
 final int[] actions=new int[CAPACITY];final boolean[] terminals=new boolean[CAPACITY];
 int size,position;
 public DqnTrainer(ArcadeEnv.Genre genre,long seed){rng=new ArcadeEnv.Rng(seed);env=new ArcadeEnv(genre);env.reset(rng.next());online=new Net(env.actions().length,rng);target=new Net(env.actions().length,new ArcadeEnv.Rng(2));target.copy(online);}
 public float epsilon(){return 1-.95f*Math.min(1,steps/50000f);}
 public int replaySize(){return size;}
 public void step(){
  if(env.terminated||env.truncated)env.reset(rng.next());
  float[] before=env.observe();int action=rng.unit()<epsilon()?rng.integer(online.outputs):argmax(online.predict(before));ArcadeEnv.Step result=env.step(action);
  remember(before,action,result.reward,result.observation,result.terminated);
  steps++;episodeReturn+=result.reward;bestProgress=Math.max(bestProgress,result.progress);
  if(size>=WARMUP&&steps%4==0)learnBatch();
  if(result.terminated||result.truncated){episodes++;lastReturn=episodeReturn;lastProgress=result.progress;episodeReturn=0;}
 }
 public void remember(float[] s,int action,float reward,float[] next,boolean terminated){
  if(s.length!=64||next.length!=64||action<0||action>=online.outputs||!Float.isFinite(reward))throw new IllegalArgumentException("transition");
  for(int i=0;i<64;i++)if(!Float.isFinite(s[i])||!Float.isFinite(next[i]))throw new IllegalArgumentException("observation");
  System.arraycopy(s,0,states,position*64,64);System.arraycopy(next,0,nextStates,position*64,64);actions[position]=action;rewards[position]=reward;terminals[position]=terminated;
  position=(position+1)%CAPACITY;size=Math.min(CAPACITY,size+1);
 }
 public static float bootstrap(float reward,boolean terminated,float selectedTargetValue){return reward+(terminated?0:GAMMA*selectedTargetValue);}
 public void learnBatch(){
  if(size<BATCH)throw new IllegalStateException("replay warmup");float[] gradient=new float[online.w.length];float total=0;
  for(int b=0;b<BATCH;b++){
   int i=rng.integer(size);float[] s=Arrays.copyOfRange(states,i*64,i*64+64),next=Arrays.copyOfRange(nextStates,i*64,i*64+64);
   int selected=argmax(online.predict(next));float expected=bootstrap(rewards[i],terminals[i],target.predict(next)[selected]);
   total+=online.gradient(s,actions[i],expected,gradient);
  }
  for(int i=0;i<gradient.length;i++)gradient[i]/=BATCH;updates++;online.adam(gradient,updates);loss=total/BATCH;
  if(updates%1000==0)target.copy(online);
 }
 public static int argmax(float[] values){int best=0;for(int i=1;i<values.length;i++)if(values[i]>values[best])best=i;return best;}
 public static final class Net {
  public final int outputs; public final float[] w,m,v;
  final int b1=4096,w2=4160,b2=8256,w3=8320,b3;
  Net(int outputs,ArcadeEnv.Rng rng){this.outputs=outputs;b3=w3+64*outputs;w=new float[b3+outputs];m=new float[w.length];v=new float[w.length];
   for(int i=0;i<4096;i++)w[i]=(rng.unit()*2-1)*(float)Math.sqrt(6.0/64);
   for(int i=w2;i<b2;i++)w[i]=(rng.unit()*2-1)*(float)Math.sqrt(6.0/64);
   for(int i=w3;i<b3;i++)w[i]=(rng.unit()*2-1)*(float)Math.sqrt(6.0/64);
  }
  public float[] predict(float[] x){return forward(x)[2];}
  private float[][] forward(float[] x){
   if(x.length!=64)throw new IllegalArgumentException("network input");float[] a=new float[64],b=new float[64],q=new float[outputs];
   for(int j=0;j<64;j++){float z=w[b1+j];for(int i=0;i<64;i++)z+=x[i]*w[i*64+j];a[j]=Math.max(0,z);}
   for(int j=0;j<64;j++){float z=w[b2+j];for(int i=0;i<64;i++)z+=a[i]*w[w2+i*64+j];b[j]=Math.max(0,z);}
   for(int j=0;j<outputs;j++){float z=w[b3+j];for(int i=0;i<64;i++)z+=b[i]*w[w3+i*outputs+j];q[j]=z;}
   return new float[][]{a,b,q};
  }
  public float gradient(float[] x,int action,float expected,float[] g){
   float[][] f=forward(x);float[] a=f[0],b=f[1];float error=f[2][action]-expected,d=Math.max(-1,Math.min(1,error));
   float[] da=new float[64],db=new float[64];g[b3+action]+=d;
   for(int i=0;i<64;i++){g[w3+i*outputs+action]+=b[i]*d;db[i]=b[i]>0?w[w3+i*outputs+action]*d:0;}
   for(int j=0;j<64;j++){g[b2+j]+=db[j];for(int i=0;i<64;i++){g[w2+i*64+j]+=a[i]*db[j];da[i]+=w[w2+i*64+j]*db[j];}}
   for(int j=0;j<64;j++){if(a[j]<=0)da[j]=0;g[b1+j]+=da[j];for(int i=0;i<64;i++)g[i*64+j]+=x[i]*da[j];}
   return Math.abs(error)<1?.5f*error*error:Math.abs(error)-.5f;
  }
  void adam(float[] g,long iteration){
   double norm=0;for(float x:g)norm+=(double)x*x;float scale=norm>100?(float)(10/Math.sqrt(norm)):1;
   double c1=1-Math.pow(.9,iteration),c2=1-Math.pow(.999,iteration);
   for(int i=0;i<w.length;i++){float d=g[i]*scale;m[i]=.9f*m[i]+.1f*d;v[i]=.999f*v[i]+.001f*d*d;w[i]-=LR*(m[i]/c1)/(Math.sqrt(v[i]/c2)+1e-8);if(!Float.isFinite(w[i]))throw new IllegalStateException("nonfinite network");}
  }
  void copy(Net source){System.arraycopy(source.w,0,w,0,w.length);}
 }
 public String evaluate(int count,long seed,java.util.function.BooleanSupplier cancelled){
  if(count<1||count>1000)throw new IllegalArgumentException("evaluation count");double learned=0,random=0;double[] differences=new double[count];ArcadeEnv.Rng baseline=new ArcadeEnv.Rng(seed);
  for(int episode=0;episode<count;episode++){
   if(cancelled.getAsBoolean())return "Оценка отменена";
   double[] score=new double[2];
   for(int policy=0;policy<2;policy++){ArcadeEnv e=new ArcadeEnv(env.genre);e.reset(seed+episode);
    while(!e.terminated&&!e.truncated){if(cancelled.getAsBoolean())return "Оценка отменена";e.step(policy==0?argmax(online.predict(e.observe())):baseline.integer(e.actions().length));}score[policy]=e.progress;}
   learned+=score[0];random+=score[1];differences[episode]=score[0]-score[1];
  }
  // Paired bootstrap on the same held-out seeds; never changes trainer RNG/weights.
  ArcadeEnv.Rng boot=new ArcadeEnv.Rng(seed^0x12345678);double[] means=new double[1000];
  for(int b=0;b<means.length;b++){for(int i=0;i<count;i++)means[b]+=differences[boot.integer(count)]/count;}Arrays.sort(means);
  return String.format(Locale.ROOT,"Оценка %d эпизодов, seed=%d · шаги=%d, обновления=%d\nМодель: %.2f · случайная: %.2f\nРазница, 95%% bootstrap CI: [%.2f; %.2f]\n%s",count,seed,steps,updates,learned/count,random/count,means[25],means[974],means[25]>0?"Преимущество подтверждено в этом тесте":"Преимущество пока не подтверждено");
 }
 private byte[] stateBytes()throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){
  out.writeInt(0x4c505231);out.writeInt(env.genre.ordinal());out.writeLong(rng.state);out.writeLong(steps);out.writeLong(updates);out.writeLong(episodes);
  for(float f:new float[]{episodeReturn,lastReturn,bestProgress,lastProgress,loss})out.writeFloat(f);env.write(out);
  out.writeInt(online.w.length);for(float[] a:new float[][]{online.w,online.m,online.v,target.w})for(float f:a)out.writeFloat(f);
  out.writeInt(size);out.writeInt(position);for(int i=0;i<size;i++){for(int j=0;j<64;j++)out.writeFloat(states[i*64+j]);for(int j=0;j<64;j++)out.writeFloat(nextStates[i*64+j]);out.writeInt(actions[i]);out.writeFloat(rewards[i]);out.writeBoolean(terminals[i]);}
 }return bytes.toByteArray();}
 public String manifest(){
  StringJoiner a=new StringJoiner(",");for(String v:env.actions())a.add("\""+v+"\"");
  return "{\"format\":\"localpilot-dqn-v1\",\"byte_order\":\"big-endian\",\"genre\":\""+env.genre+"\",\"observation\":\""+ArcadeEnv.OBS_VERSION+"\",\"shape\":[64],\"network\":[64,64,64,"+online.outputs+"],\"actions\":["+a+"],\"gamma\":0.99,\"learning_rate\":0.001,\"batch\":32,\"replay_capacity\":20000,\"physics_hz\":60,\"action_ticks\":4,\"world_size\":[360,640],\"max_episode_steps\":2000,\"global_features\":[\"x/360\",\"y_relative/640\",\"vx/400\",\"vy/650\",\"grounded\",\"scroll_vx/400\",\"scroll_vy/650\",\"speed/400\",\"time_left/90\",\"combo/5\",\"progress/(640+progress)\",\"lane/2\",\"last_action/(actions-1)\",\"jump_ready\",\"bonus_reserved_zero\",\"time_since_landing/3\"],\"object_features\":[\"dx/360\",\"dy/640\",\"width/360\",\"height/640\",\"vx/400\",\"vy/650\",\"type/3\",\"present\"],\"object_selection\":\"six_nearest_distance_then_id\",\"observation_clip\":[-1,1],\"object_padding\":0}";
 }
 private static String sha(byte[] b)throws IOException{try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(b);StringBuilder s=new StringBuilder();for(byte x:digest)s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}catch(NoSuchAlgorithmException e){throw new IOException(e);}}
 public void save(File file)throws IOException{
  File parent=file.getAbsoluteFile().getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("checkpoint directory");
  byte[] state=stateBytes(),manifest=manifest().getBytes(StandardCharsets.UTF_8);File pending=File.createTempFile("checkpoint-",".pending",parent);
  try{try(FileOutputStream raw=new FileOutputStream(pending);ZipOutputStream z=new ZipOutputStream(raw)){
   for(Map.Entry<String,byte[]> entry:Map.of("manifest.json",manifest,"state.bin",state,"SHA256SUMS",(sha(state)+"  state.bin\n"+sha(manifest)+"  manifest.json\n").getBytes(StandardCharsets.UTF_8)).entrySet()){z.putNextEntry(new ZipEntry(entry.getKey()));z.write(entry.getValue());z.closeEntry();}z.finish();z.flush();raw.getFD().sync();}
   if(file.exists()){File backup=new File(file+".bak");File temporary=new File(file+".bak.pending");Files.copy(file.toPath(),temporary.toPath(),StandardCopyOption.REPLACE_EXISTING);Files.move(temporary.toPath(),backup.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
   Files.move(pending.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
  }finally{Files.deleteIfExists(pending.toPath());}
 }
 public static DqnTrainer load(File file,ArcadeEnv.Genre genre)throws IOException{
  if(file.length()>32*1024*1024)throw new IOException("checkpoint size");Map<String,byte[]> entries=new HashMap<>();
  try(ZipInputStream z=new ZipInputStream(new FileInputStream(file))){ZipEntry e;while((e=z.getNextEntry())!=null){if(!Set.of("manifest.json","state.bin","SHA256SUMS").contains(e.getName())||entries.containsKey(e.getName()))throw new IOException("checkpoint entry");ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=z.read(block))!=-1){if(b.size()+n>16*1024*1024)throw new IOException("checkpoint expansion");b.write(block,0,n);}entries.put(e.getName(),b.toByteArray());}}
  if(entries.size()!=3)throw new IOException("checkpoint incomplete");byte[] state=entries.get("state.bin"),metadata=entries.get("manifest.json");String sums=sha(state)+"  state.bin\n"+sha(metadata)+"  manifest.json\n";
  if(!sums.equals(new String(entries.get("SHA256SUMS"),StandardCharsets.UTF_8)))throw new IOException("checkpoint checksum");DqnTrainer t=new DqnTrainer(genre,1);
  if(!t.manifest().equals(new String(metadata,StandardCharsets.UTF_8)))throw new IOException("checkpoint configuration");
  try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(state))){
   if(in.readInt()!=0x4c505231||in.readInt()!=genre.ordinal())throw new IOException("checkpoint version");t.rng.state=in.readLong();t.steps=in.readLong();t.updates=in.readLong();t.episodes=in.readLong();if(t.steps<0||t.updates<0||t.episodes<0)throw new IOException("checkpoint counters");
   t.episodeReturn=finite(in);t.lastReturn=finite(in);t.bestProgress=finite(in);t.lastProgress=finite(in);t.loss=finite(in);t.env.read(in);
   if(in.readInt()!=t.online.w.length)throw new IOException("network shape");for(float[] a:new float[][]{t.online.w,t.online.m,t.online.v,t.target.w})for(int i=0;i<a.length;i++)a[i]=finite(in);
   t.size=in.readInt();t.position=in.readInt();if(t.size<0||t.size>CAPACITY||t.position<0||t.position>=CAPACITY||(t.size<CAPACITY&&t.position!=t.size))throw new IOException("replay bounds");
   for(int i=0;i<t.size;i++){for(int j=0;j<64;j++)t.states[i*64+j]=finite(in);for(int j=0;j<64;j++)t.nextStates[i*64+j]=finite(in);t.actions[i]=in.readInt();t.rewards[i]=finite(in);t.terminals[i]=in.readBoolean();if(t.actions[i]<0||t.actions[i]>=t.online.outputs)throw new IOException("replay action");}
   if(in.read()!=-1)throw new IOException("checkpoint trailing data");
  }return t;
 }
 public static DqnTrainer loadRecovering(File file,ArcadeEnv.Genre genre,long seed)throws IOException{
  File backup=new File(file+".bak");if(!file.exists()&&!backup.exists())return new DqnTrainer(genre,seed);
  IOException failure=null;if(file.exists())try{return load(file,genre);}catch(IOException e){failure=e;}
  if(!backup.exists())throw failure;
  DqnTrainer restored;try{restored=load(backup,genre);}catch(IOException e){if(failure!=null)e.addSuppressed(failure);throw e;}
  if(file.exists())Files.move(file.toPath(),new File(file+".corrupt-"+System.nanoTime()).toPath(),StandardCopyOption.ATOMIC_MOVE);
  restored.save(file);return restored;
 }
 private static float finite(DataInput in)throws IOException{float x=in.readFloat();if(!Float.isFinite(x))throw new IOException("nonfinite checkpoint");return x;}
}
