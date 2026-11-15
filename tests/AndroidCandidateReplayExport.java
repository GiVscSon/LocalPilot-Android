package org.localpilot.shared;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** CI adapter: decode the exact durable on-device rollout for the EXISTING
 * JSNES holdout evaluator. No retraining, fabricated transitions or promotion. */
public final class AndroidCandidateReplayExport {
 public static void main(String[] args)throws Exception{
  if(args.length!=3)throw new IllegalArgumentException("REPLAY ACTIVE_WEIGHTS OUTPUT_TSV");
  var rows=VerifiedPpoReplayStore.read(Path.of(args[0]));
  var active=StudentPolicyNet.load(Path.of(args[1]));StringBuilder tsv=new StringBuilder();
  for(var row:rows){
   float[] before=row.state64(),after=row.nextState64();
   int x=Math.round(before[0]*255),y=Math.round(before[1]*255);
   int nx=Math.round(after[0]*255),ny=Math.round(after[1]*255);
   if(!Arrays.equals(before,HomebrewObs64.encode(x,y))||!Arrays.equals(after,HomebrewObs64.encode(nx,ny))||
      !row.policySha256().equals(active.sha256())||row.legalMask()!=HomebrewObs64.taskMask(x,y,16))
    throw new IllegalArgumentException("Replay observation/mask/active policy mismatch");
   var prediction=active.forward(before,row.legalMask());
   if(Math.abs(Math.log(prediction.probs()[row.actionId()])-row.oldLogProb())>1e-5||
      Math.abs(prediction.value()-row.valueEstimate())>1e-5)
    throw new IllegalArgumentException("Replay was not sampled by pinned active weights");
   tsv.append(row.sequence()).append('\t').append(row.epoch()).append('\t')
    .append(row.romSha256()).append('\t').append(row.policySha256()).append('\t')
    .append(row.frameBefore()).append('\t').append(row.frameAfter()).append('\t')
    .append(row.actionId()).append('\t').append(row.oldLogProb()).append('\t')
    .append(row.valueEstimate()).append('\t').append(x).append('\t').append(y).append('\t')
    .append(nx).append('\t').append(ny).append('\t').append(row.reward()).append('\n');
  }
  Files.writeString(Path.of(args[2]),tsv);
  System.out.println("ANDROID_CANDIDATE_REPLAY_EXPORT_PASS steps="+rows.size());
 }
}
