package org.localpilot.shared;
import java.nio.file.*;
import java.io.*;
import java.util.*;
/** The SAME production network implementation trains/infers on actual JSNES
 * 6502 observations from a Node emulator. No mock neural policy/gradient.
 */
public final class RealNesStudentAcceptance {
 public static void main(String[] args)throws Exception{
  if(args.length==4&&"PREDICT".equals(args[0])){
   Checkpoint loaded=readWeights(Path.of(args[1]));
   var prediction=propose(loaded,Integer.parseInt(args[2]),Integer.parseInt(args[3]));
   System.out.println(prediction.button()+"|"+prediction.frames()+"|"+prediction.value()+"|"+prediction.margin()+"|"+prediction.admission()+"|"+HomebrewStudentNetwork.SCHEMA);return;
  }
  if(args.length!=3)throw new IllegalArgumentException(
    "Usage: RealNesStudentAcceptance TRAIN verified-steps.tsv weights.bin");
  if(!"TRAIN".equals(args[0])&&!"IMITATE".equals(args[0]))throw new IllegalArgumentException("Unknown objective");
  byte[] datasetBytes=Files.readAllBytes(Path.of(args[1]));
  byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(datasetBytes);
  StringBuilder sourceHash=new StringBuilder();
  for(byte b:digest)sourceHash.append(String.format(Locale.ROOT,"%02x",b&255));
  List<ConsoleExperienceMemory.GoalEpisode> rows=new ArrayList<>();
  for(String line:Files.readAllLines(Path.of(args[1]))){
   String[] p=line.split("\\|",-1);
   if(p.length!=10)throw new IllegalArgumentException("Unverified real NES dataset schema");
   String epoch=p[0],button=p[1];
   int start=Integer.parseInt(p[2]),end=Integer.parseInt(p[3]),
     bx=Integer.parseInt(p[4]),by=Integer.parseInt(p[5]),
     ax=Integer.parseInt(p[6]),ay=Integer.parseInt(p[7]);
   int before=(int)Long.parseUnsignedLong(p[8]),
     after=(int)Long.parseUnsignedLong(p[9]);
   NesTrial proof=NesTrial.create("HOMEBREW",button,start,end,before,after);
   HomebrewGoalReward.Result checked=HomebrewGoalReward.verify(
     button,start,end,bx,by,ax,ay);
   rows.add(new ConsoleExperienceMemory.GoalEpisode(epoch,"import:ci-rollout",
     sourceHash.toString(),button,start,end,before,after,proof.status,bx,by,ax,ay,
     checked.reward(),checked.reached()));
  }
  var learner="IMITATE".equals(args[0])?HomebrewStudentNetwork.trainImitation(rows):HomebrewStudentNetwork.train(rows);
  if(learner.sourceSessions<3||learner.holdoutTransitions<2)
   throw new AssertionError("No independent real NES holdout");
  try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(Path.of(args[2])))){
   out.writeUTF(HomebrewStudentNetwork.SCHEMA);out.writeUTF(learner.objective.name());
   out.writeInt(learner.weights.length);
   for(double weight:learner.weights)out.writeDouble(weight);
  }
  Checkpoint reloaded=readWeights(Path.of(args[2]));
  Checkpoint original=new Checkpoint(learner.weights,learner.objective);
  for(int[] xy:new int[][]{{128,112},{160,112},{192,112}}){
   var trained=propose(original,xy[0],xy[1]);
   var loaded=propose(reloaded,xy[0],xy[1]);
   if(!trained.button().equals(loaded.button())||trained.frames()!=loaded.frames()||
      !trained.admission().equals(loaded.admission())||
      Double.doubleToLongBits(trained.value())!=Double.doubleToLongBits(loaded.value()))
    throw new AssertionError("Trained / exported model predictions differ");
  }
  var first=propose(reloaded,128,112);
  if(!"RIGHT".equals(first.button()))
   throw new AssertionError("Real holdout-trained student did not choose target motion: "+
      first.button());
  System.out.println("NES_STUDENT_REAL_JSNES_PARITY_PASS model=7x12x6 train="+
    learner.trainingTransitions+" holdout="+learner.holdoutTransitions+
    " objective="+learner.objective+" validation_loss="+learner.validationLoss+
    " policy="+first.button());
 }
 private record Checkpoint(double[] weights,HomebrewStudentNetwork.Objective objective){}
 private static HomebrewStudentNetwork.Prediction propose(Checkpoint c,int x,int y){
  return c.objective==HomebrewStudentNetwork.Objective.IMITATION?
   HomebrewStudentNetwork.proposeImitation(c.weights,x,y):HomebrewStudentNetwork.propose(c.weights,x,y);
 }
 private static Checkpoint readWeights(Path file)throws IOException{
  double[] weights=new double[HomebrewStudentNetwork.WEIGHTS];
  HomebrewStudentNetwork.Objective objective;
  try(DataInputStream in=new DataInputStream(Files.newInputStream(file))){
   if(!HomebrewStudentNetwork.SCHEMA.equals(in.readUTF()))throw new IOException("Student schema");
   try{objective=HomebrewStudentNetwork.Objective.valueOf(in.readUTF());}
   catch(IllegalArgumentException bad){throw new IOException("Unknown objective",bad);}
   if(in.readInt()!=weights.length)throw new IOException("Student tensor shape");
   for(int i=0;i<weights.length;i++){
    weights[i]=in.readDouble();
    if(!Double.isFinite(weights[i]))throw new IOException("Nonfinite weight");
   }
   if(in.read()!=-1)throw new IOException("Unexpected weights");
  }
  return new Checkpoint(weights,objective);
 }

}
