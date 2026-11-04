package org.localpilot.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

/**
 * A small trainable game-outcome network built entirely from the project's
 * existing ArcadeObservation and visual post-action feedback.
 *
 * Input: 24 normalized object/action features.
 * Model: 24 -> 16 tanh -> 1 sigmoid success probability.
 * Targets: 1 ONLY for verified visual game response, 0 ONLY for a fresh
 * observed non-response after a completed action. ACK is never reward.
 *
 * This is a game-specific learned veto, not a replacement for ActionGate,
 * window/ROI ownership, STOP, TTL, UNKNOWN, physical safety and gesture gates.
 */
public final class RealGameNeuralCritic {
 private static final int IN=24,HIDDEN=16,WEIGHTS=IN*HIDDEN+HIDDEN+HIDDEN+1;
 private static final int MIN_POSITIVE=16,MIN_NEGATIVE=12,MAX_BYTES=16000;
 private static final int MAGIC=0x4c504e31; // LPN1
 private static final int VERSION=1;
 private static final double LR=.012;
 private final ArcadeProfile profile;
 private final float[] w=new float[WEIGHTS];
 private long positives,negatives,updates,lastSampleMs=-1;
 private boolean enabled=true;
 public RealGameNeuralCritic(ArcadeProfile p) {
  if(p!=ArcadeProfile.PADDLE&&p!=ArcadeProfile.TRAFFIC&&p!=ArcadeProfile.FLAPPY)
   throw new IllegalArgumentException("Only real arcade profiles have matching action schema");
  profile=p;
  // Deterministic startup is a *new* untrained model, not trained pretrained weights.
  Random rng=new Random(0x4c504e31L+31*p.ordinal());
  for(int j=0;j<IN*HIDDEN;j++)w[j]=(float)(rng.nextGaussian()*.07);
  for(int j=IN*HIDDEN+HIDDEN;j<WEIGHTS-1;j++)w[j]=(float)(rng.nextGaussian()*.07);
 }
 public ArcadeProfile profile(){return profile;}
 public long verifiedSamples(){return positives;}
 public long observedFailures(){return negatives;}
 public long updates(){return updates;}
 public boolean ready(){return enabled&&positives>=MIN_POSITIVE&&negatives>=MIN_NEGATIVE&&updates>=32;}
 public void disable(){enabled=false;}
 private static float clamp(float x){return Math.max(-1f,Math.min(1f,x));}
 /**
  * Exact feature definition saved as version 1 in every checkpoint.
  * Never infer unknown objects or trust predicted gameplay state.
  */
 public static float[] features(ArcadeProfile p,ArcadeObservation s,ArcadePolicy.Decision a){
  if((p!=ArcadeProfile.PADDLE&&p!=ArcadeProfile.TRAFFIC&&p!=ArcadeProfile.FLAPPY)
    ||s==null||a==null||p!=s.profile||!s.usable()
    ||s.confidence<.85f||!s.tracked||s.player==null||s.control==null)
   return null;
  float[] f=new float[IN];
  f[0]=clamp(s.player.x()*2-1);
  f[1]=clamp(s.player.y()*2-1);
  f[2]=clamp(s.control.x()*2-1);
  f[3]=clamp(s.control.y()*2-1);
  f[4]=clamp(s.vx);
  f[5]=clamp(s.vy);
  f[6]=clamp(s.player.width()*4);
  f[7]=clamp(s.player.height()*4);
  f[8]=clamp(s.control.width()*4);
  f[9]=clamp(s.control.height()*4);
  f[10]=clamp((s.gapTop+s.gapBottom)-1);
  f[11]=clamp(s.gapBottom-s.gapTop);
  f[12]=0;f[13]=0;f[14]=0;f[15]=0;
  float best=Float.POSITIVE_INFINITY;
  for(ArcadeObservation.Box b:s.obstacles){
   float dx=b.x()-s.player.x(),dy=b.y()-s.player.y();
   float distance=dx*dx+dy*dy;
   if(distance<best){best=distance;f[12]=clamp(dx);f[13]=clamp(dy);
    f[14]=clamp(b.width()*4);f[15]=clamp(b.height()*4);}
  }
  f[16]=clamp(s.obstacles.size()/12f);
  f[17]=clamp(s.confidence);
  int cls=actionClass(p,a.action);
  if(cls<0)return null;
  f[18+cls]=1;
  f[21+p.ordinal()-1]=1; // PADDLE, TRAFFIC, FLAPPY have ordinal 1,2,3.
  for(float v:f)if(!Float.isFinite(v))return null;
  return f;
 }
 private static int actionClass(ArcadeProfile p,ArcadePolicy.Action a){
  if(a==ArcadePolicy.Action.WAIT)return 0;
  return switch(p){
   case PADDLE->a==ArcadePolicy.Action.MOVE_PADDLE?1:a==ArcadePolicy.Action.LAUNCH?2:-1;
   case TRAFFIC->a==ArcadePolicy.Action.LEFT?1:a==ArcadePolicy.Action.RIGHT?2:-1;
   case FLAPPY->a==ArcadePolicy.Action.FLAP?1:-1;
   default->-1;
  };
 }
 private static double sigmoid(double x){
  if(x>=0){double z=Math.exp(-x);return 1/(1+z);}
  double z=Math.exp(x);return z/(1+z);
 }
 private static double forward(float[] weights,float[] x,double[] hidden){
  for(int j=0;j<HIDDEN;j++){
   double z=weights[IN*HIDDEN+j];
   for(int i=0;i<IN;i++)z+=x[i]*weights[j*IN+i];
   hidden[j]=Math.tanh(z);
  }
  double z=weights[IN*HIDDEN+HIDDEN+HIDDEN];
  for(int j=0;j<HIDDEN;j++)z+=hidden[j]*weights[IN*HIDDEN+HIDDEN+j];
  return sigmoid(z);
 }
 public double predict(ArcadeObservation s,ArcadePolicy.Decision action){
  float[] x=features(profile,s,action);
  if(x==null)return Double.NaN;
  return forward(w,x,new double[HIDDEN]);
 }
 /** A trained critic can only decline a *proposed* gesture. It cannot send one. */
 public ArcadePolicy.Decision filter(ArcadeObservation s,ArcadePolicy.Decision baseline){
  if(baseline==null||!baseline.sendsInput()||!ready())return baseline;
  double confidence=predict(s,baseline);
  if(!Double.isFinite(confidence)||confidence>=.08)return baseline;
  return new ArcadePolicy.Decision(ArcadePolicy.Action.WAIT,0,0,
    "Нейросеть: вероятный неответ игры · наблюдаю без жеста");
 }
 /**
  * Only an independent fresh game frame and the existing per-game response
  * witness can produce a positive example.
  */
 public boolean learnVerified(ArcadeObservation before,ArcadePolicy.Decision action,
                               ArcadeObservation after){
  if(!eligible(before,action,after)||!ArcadePolicy.response(action,before,after))return false;
  return learn(before,action,1);
 }
 /** Called ONLY after ACK + a separate fresh readable frame + visual timeout. */
 public boolean learnNonresponse(ArcadeObservation before,ArcadePolicy.Decision action,
                                  ArcadeObservation after,boolean acknowledged){
  if(!acknowledged||!eligible(before,action,after)
     ||ArcadePolicy.response(action,before,after))return false;
  if(after.timestampMs-before.timestampMs<80)return false;
  return learn(before,action,0);
 }
 private boolean eligible(ArcadeObservation before,ArcadePolicy.Decision action,
                           ArcadeObservation after){
  if(!enabled||before==null||after==null||before.profile!=profile||after.profile!=profile
    ||!before.usable()||!after.usable()||after.confidence<.85f
    ||before.timestampMs<=lastSampleMs||after.timestampMs<=before.timestampMs
    ||after.timestampMs-before.timestampMs>1200)return false;
  return features(profile,before,action)!=null;
 }
 private boolean learn(ArcadeObservation s,ArcadePolicy.Decision action,int label){
  float[] x=features(profile,s,action);if(x==null)return false;
  double[] h=new double[HIDDEN];
  double estimate=forward(w,x,h);
  double error=Math.max(-1,Math.min(1,estimate-label));
  int offset=IN*HIDDEN+HIDDEN;
  // Save old output weights before updating hidden layer.
  double[] hiddenGradient=new double[HIDDEN];
  for(int j=0;j<HIDDEN;j++)
   hiddenGradient[j]=error*w[offset+j]*(1-h[j]*h[j]);
  for(int j=0;j<HIDDEN;j++){
   w[offset+j]-=LR*error*h[j];
   w[IN*HIDDEN+j]-=LR*hiddenGradient[j];
   for(int i=0;i<IN;i++)w[j*IN+i]-=LR*hiddenGradient[j]*x[i];
  }
  w[WEIGHTS-1]-=LR*error;
  for(float z:w)if(!Float.isFinite(z)||Math.abs(z)>8) {
   enabled=false;return false;
  }
  updates++;lastSampleMs=s.timestampMs;
  if(label==1)positives++;else negatives++;
  return true;
 }
 /** Each on-disk model is scoped to the exact game, app and slot by the store. */
 public String encode(){
  try{
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();
   DataOutputStream out=new DataOutputStream(bytes);
   out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(profile.ordinal());
   out.writeLong(positives);out.writeLong(negatives);out.writeLong(updates);
   out.writeLong(lastSampleMs);out.writeBoolean(enabled);
   out.writeInt(w.length);
   for(float z:w)out.writeFloat(z);
   out.flush();
   byte[] payload=bytes.toByteArray();
   MessageDigest sha=MessageDigest.getInstance("SHA-256");
   byte[] digest=sha.digest(payload);
   ByteArrayOutputStream all=new ByteArrayOutputStream();
   all.write(payload);all.write(digest);
   return "NG1:"+Base64.getEncoder().encodeToString(all.toByteArray());
  }catch(IOException|NoSuchAlgorithmException ex){throw new IllegalStateException("model serialization",ex);}
 }
 public static RealGameNeuralCritic decode(String data,ArcadeProfile expected){
  if(data==null||!data.startsWith("NG1:")||expected==null)throw new IllegalArgumentException("critic format");
  try{
   byte[] all=Base64.getDecoder().decode(data.substring(4));
   if(all.length>MAX_BYTES||all.length<80)throw new IllegalArgumentException("critic size");
   int count=all.length-32;
   MessageDigest sha=MessageDigest.getInstance("SHA-256");
   if(!MessageDigest.isEqual(Arrays.copyOfRange(all,count,all.length),
       sha.digest(Arrays.copyOf(all,count))))throw new IllegalArgumentException("critic SHA256");
   DataInputStream in=new DataInputStream(new ByteArrayInputStream(all,0,count));
   if(in.readInt()!=MAGIC||in.readInt()!=VERSION||in.readInt()!=expected.ordinal())
    throw new IllegalArgumentException("critic schema");
   RealGameNeuralCritic m=new RealGameNeuralCritic(expected);
   m.positives=in.readLong();m.negatives=in.readLong();m.updates=in.readLong();
   m.lastSampleMs=in.readLong();m.enabled=in.readBoolean();
   if(m.positives<0||m.negatives<0||m.updates<0
     ||m.positives+m.negatives!=m.updates
     ||m.lastSampleMs< -1||in.readInt()!=WEIGHTS)
    throw new IllegalArgumentException("critic counts or shape");
   for(int i=0;i<WEIGHTS;i++){
    float z=in.readFloat();if(!Float.isFinite(z)||Math.abs(z)>8)
     throw new IllegalArgumentException("critic weights");
    m.w[i]=z;
   }
   if(in.available()!=0)throw new IllegalArgumentException("extra data");
   // Screenshot clocks use Android elapsedRealtime; their epoch restarts after
   // reboot. Never let a persisted old uptime block learning forever.
   m.lastSampleMs=-1;
   return m;
  }catch(IOException|NoSuchAlgorithmException|IllegalArgumentException ex){
   throw new IllegalArgumentException("invalid critic checkpoint",ex);
  }
 }
 public String report(){
  return "neural-critic="+profile+" verified="+positives+
    " observed_nonresponses="+negatives+" updates="+updates+
    " mode="+(ready()?"confidence-gate":"collecting");
 }
}
