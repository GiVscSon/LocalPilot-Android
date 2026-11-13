package org.localpilot.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

/** JVM17 neural inference and safety contract, not claims of training. */
public final class StudentPolicyNetTest {
 private static int checks;
 private static void yes(boolean b,String why){
  checks++;if(!b)throw new AssertionError(why);
 }
 private static void fail(Runnable runnable,String message){
  boolean rejected=false;
  try{runnable.run();}catch(IllegalArgumentException|IllegalStateException expected){
   rejected=true;
  }
  yes(rejected,message);
 }
 private static void badBytes(byte[] bytes,String why){
  try{StudentPolicyNet.fromBytes(bytes);}
  catch(IOException expected){yes(true,why);return;}
  throw new AssertionError(why);
 }
 public static void main(String[] args)throws Exception{
  yes(HomebrewObs64.ACTIONS==6,"6 legal NES buttons");
  yes(HomebrewObs64.SCHEMA.equals("obs64-homebrew-x-v1"),"versioned observation");
  yes(HomebrewObs64.id("RIGHT")==1,"stable button identity");
  yes(HomebrewObs64.id("HACK")==-1,"unrecognised button forbidden");
  float[] zero=HomebrewObs64.encode(128,112);
  yes(zero.length==64,"observation has 64 dimensions");
  yes(zero[0]>0.50f&&zero[0]<.51f&&zero[1]>.43f&&zero[1]<.45f,
    "observations carry real RAM bytes");
  yes(zero[5]==0,"unknown features not hallucinated");
  fail(()->HomebrewObs64.encode(256,112),"invalid RAM cannot become observation");
  StudentPolicyNet a=new StudentPolicyNet(HomebrewObs64.ACTIONS,777);
  StudentPolicyNet b=new StudentPolicyNet(HomebrewObs64.ACTIONS,777);
  yes(a.sha256().equals(b.sha256()),"identical seeded weights");
  var forward=a.forward(zero,HomebrewObs64.LEGAL_MASK);
  double sum=0;
  for(float v:forward.probs()){sum+=v;yes(Float.isFinite(v)&&v>=0,"finite action probability");}
  yes(Math.abs(sum-1)<1e-6,"proper normalized softmax");
  yes(Float.isFinite(forward.value()),"finite critic");
  int rightOnly=1<<HomebrewObs64.id("RIGHT");
  var right=a.forward(zero,rightOnly).probs();
  for(int i=0;i<right.length;i++)
   yes(right[i]==(i==1?1.0f:0.0f),"masked invalid button exactly zero");
  for(int i=0;i<100;i++){
   var sampled=a.choose(zero,rightOnly,new Random(i+3));
   yes(sampled.action()==1,"student cannot choose illegal action");
   yes(Math.abs(sampled.oldLogProb())<1e-8,"right-only old log prob zero");
   yes(sampled.policySha().equals(a.sha256()),"decision records exact sampled weights");
   yes(Float.isFinite(sampled.value()),"decision has critic baseline");
  }
  var sample=a.choose(zero,HomebrewObs64.LEGAL_MASK,new Random(9));
  yes((HomebrewObs64.LEGAL_MASK&(1<<sample.action()))!=0,"full legal sample");
  yes(Float.isFinite(sample.oldLogProb())&&sample.oldLogProb()<=0,
    "on-policy action oldLogProb recorded");
  yes(sample.probabilities()[sample.action()]>0,"sampled action has nonzero mass");
  float[] exposed=sample.probabilities();
  exposed[sample.action()]=10;
  yes(sample.probabilities()[sample.action()]<=1,"decision defensive output copy");
  yes(a.argmax(zero,HomebrewObs64.LEGAL_MASK)>=0,"deterministic argmax");
  fail(()->a.forward(zero,0),"reject no legal actions");
  fail(()->a.forward(zero,128),"reject extra legal bits");
  fail(()->a.forward(new float[10],63),"reject wrong obs shape");
  float[] nan=zero.clone();nan[1]=Float.NaN;
  fail(()->a.forward(nan,63),"reject NaN input");
  StudentPolicyNet copy=a.candidateCopy();
  yes(copy.sha256().equals(a.sha256()),"candidate starts byte-identical");
  Path directory=Files.createTempDirectory("student-policy-v1-test");
  Path weights=directory.resolve("student.weights.bin");
  a.saveAtomically(weights);
  yes(Files.size(weights)>30000,"network weights actually serialized");
  yes(StudentPolicyNet.load(weights).sha256().equals(a.sha256()),
      "weights file SHA matches active memory");
  byte[] encoded=Files.readAllBytes(weights);
  yes(encoded[0]=='L'&&encoded[1]=='P'&&encoded[2]=='S'&&encoded[3]=='2',
    "big-endian magic LPS2");
  byte[] corrupt=encoded.clone();corrupt[3]='1';
  badBytes(corrupt,"LPS1 refused: no undocumented binary aliasing");
  byte[] truncate=Arrays.copyOf(encoded,encoded.length-4);
  badBytes(truncate,"truncation rejected");
  byte[] extra=Arrays.copyOf(encoded,encoded.length+1);
  badBytes(extra,"trailing bytes rejected");
  byte[] nonfinite=encoded.clone();
  nonfinite[20]=0x7f;nonfinite[21]=(byte)0xc0;
  nonfinite[22]=0;nonfinite[23]=0;
  badBytes(nonfinite,"malformed nonfinite parameter rejected");
  byte[] bitflip=encoded.clone();
  bitflip[40]^=1;
  StudentPolicyNet modified=StudentPolicyNet.fromBytes(bitflip);
  yes(!modified.sha256().equals(a.sha256()),
     "changed weights must produce changed SHA");
  yes(!Files.exists(directory.resolve("student.weights.bin.pending")),
    "atomic save does not leave pending marker");
  Path oversized=directory.resolve("oversized.weights.bin");
  Files.write(oversized,new byte[100001]);
  boolean oversizedRejected=false;
  try{StudentPolicyNet.load(oversized);}catch(java.io.IOException expected){oversizedRejected=true;}
  yes(oversizedRejected,"oversized checkpoint rejected before file-sized allocation");
  // Student actor is NOT a second input controller: same process singleton.
  var h=HarnessCoordinator.nes();
  h.reset("888",HarnessCoordinator.Mode.REALTIME_MODE);
  yes(h.start(HarnessCoordinator.Actor.STUDENT,"888","RIGHT",45,9,16,100)==null,
     "StudentPolicy may never use realtime unsafe permit");
  yes(h.changeMode("888",HarnessCoordinator.Mode.STEP_MODE),"STEP transition");
  var lease=h.reserveEpisode("888",HarnessCoordinator.PPO_TASK,a.sha256(),1);
  var ticket=h.beginEpisodeObservation(lease,45,9,100);
  yes(h.consume(ticket.nonce,"888",45,9,101),"PPO observation consumed");
  var permit=h.startEpisodeStep(lease,a.sha256(),"RIGHT",45,9,102);
  yes(permit!=null,"one permitted student action in frozen NES");
  yes(h.start(HarnessCoordinator.Actor.STUDENT,"888","RIGHT",45,9,16,100)==null,
     "no competing StudentPolicy action");
  h.stop("TEST_USER_STOP");
  yes(!h.complete(permit,"888",61,10,true,true,103),
     "late ACK after STOP cannot verify student action");
  yes(!h.actionInFlight(),"STOP revoked unique input");
  h.stop("TEST_END");
  System.out.println("STUDENT_RUNTIME_INFERENCE_PASS checks="+checks+
    " parametersBigEndian=true realWeightsSerialized=true trainingNotProven=true");
 }
}
