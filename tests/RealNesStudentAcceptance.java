package org.localpilot.shared;
import java.nio.file.*;
import java.io.*;
import java.util.*;
/** The SAME production network implementation trains/infers on actual JSNES
 * 6502 observations from a Node emulator. No mock neural policy/gradient.
 */
public final class RealNesStudentAcceptance {
 public static void main(String[] args)throws Exception{
  if(args.length!=3)throw new IllegalArgumentException(
    "Usage: RealNesStudentAcceptance TRAIN verified-steps.tsv weights.bin");
  if(!"TRAIN".equals(args[0]))throw new IllegalArgumentException("Only TRAIN");
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
   rows.add(new ConsoleExperienceMemory.GoalEpisode(epoch,"import:ci-teacher",
     "a".repeat(64),button,start,end,before,after,proof.status,bx,by,ax,ay,
     checked.reward(),checked.reached()));
  }
  var learner=HomebrewStudentNetwork.train(rows);
  if(learner.sourceSessions<3||learner.heldoutTransitions<2)
   throw new AssertionError("No independent real NES holdout");
  try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(Path.of(args[2])))){
   out.writeInt(learner.weights.length);
   for(double weight:learner.weights)out.writeDouble(weight);
  }
  double[] reloaded=new double[HomebrewStudentNetwork.WEIGHTS];
  try(DataInputStream in=new DataInputStream(Files.newInputStream(Path.of(args[2])))){
   if(in.readInt()!=reloaded.length)throw new IOException("Student tensor shape");
   for(int i=0;i<reloaded.length;i++)reloaded[i]=in.readDouble();
   if(in.read()!=-1)throw new IOException("Unexpected weights");
  }
  for(int[] xy:new int[][]{{128,112},{160,112},{192,112}}){
   var trained=HomebrewStudentNetwork.propose(learner.weights,xy[0],xy[1]);
   var loaded=HomebrewStudentNetwork.propose(reloaded,xy[0],xy[1]);
   if(!trained.button().equals(loaded.button())||
      Double.doubleToLongBits(trained.value())!=Double.doubleToLongBits(loaded.value()))
    throw new AssertionError("Trained / exported model predictions differ");
  }
  var first=HomebrewStudentNetwork.propose(reloaded,128,112);
  if(!"RIGHT".equals(first.button()))
   throw new AssertionError("Real holdout-trained student did not choose target motion: "+
      first.button());
  System.out.println("NES_STUDENT_REAL_JSNES_PARITY_PASS model=7x12x6 train="+
    learner.trainingTransitions+" holdout="+learner.holdoutTransitions+
    " offline_td="+learner.heldoutTdMae+
    " policy="+first.button());
 }
}
