package org.localpilot.shared;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Single local training coordinator (not a second NES agent or JNI backend).
 * Accepts only an on-device StudentPolicy rollout with trusted ROM-RAM reward.
 * It may WRITE CANDIDATE bytes, but has NO method to activate those weights.
 *
 * Promoting requires a separate real-emulator EvaluationGate implementation
 * comparing active/candidate on genuinely disjoint held-out episodes.
 */
public final class LearningCoordinator {
 private LearningCoordinator(){}
 public record Candidate(Path weights,Path manifest,PpoGaeTrainer.Report update) {}
 public static Candidate prepare(StudentPolicyNet active,
       List<VerifiedReplayFilter.Step> verifiedSteps,Path appPrivateVault)throws IOException{
  if(active==null||verifiedSteps==null||appPrivateVault==null)
   throw new IllegalArgumentException("Missing local learning prerequisites");
  if(Thread.currentThread().isInterrupted())throw new IOException("STOP_BEFORE_TRAINING");
  VerifiedReplayFilter.Verdict verdict=VerifiedReplayFilter.inspect(verifiedSteps);
  if(!verdict.eligible())
   throw new IllegalArgumentException("PPO_BLOCKED: "+verdict.reason());
  if(verifiedSteps.get(0).provenance()!=VerifiedReplayFilter.Provenance.RETRO_EMULATED||
     verifiedSteps.stream().anyMatch(s->
       s.rewardSource()!=VerifiedReplayFilter.RewardSource.VERIFIED_ROM_RAM))
   throw new IllegalArgumentException("UNTRUSTED_REWARD_FOR_NES_STUDENT");
  Path directory=appPrivateVault.resolve("models/candidates");
  Path candidate=directory.resolve("student-homebrew-v1.weights.bin");
  PpoGaeTrainer.Report trained=PpoGaeTrainer.updateCandidate(
    active,verifiedSteps,candidate,PpoGaeTrainer.DEFAULT);
  if(Thread.currentThread().isInterrupted())throw new IOException("STOP_AFTER_TRAINING");
  if(!trained.shaAfter().equals(StudentPolicyNet.load(candidate).sha256())||
     trained.shaAfter().equals(trained.shaBefore())||trained.changedParameters()<=0)
   throw new IOException("NO_PROVEN_CANDIDATE_WEIGHT_CHANGE");
  String manifestText="{\n"+
    "  \"schema\":\"localpilot-student-candidate-v1\",\n"+
    "  \"task\":\"builtin-homebrew-right-progress-v1\",\n"+
    "  \"observationSchema\":\""+verifiedSteps.get(0).observationSchema()+"\",\n"+
    "  \"romSha256\":\""+verifiedSteps.get(0).romSha256()+"\",\n"+
    "  \"policyBeforeSha256\":\""+trained.shaBefore()+"\",\n"+
    "  \"policyAfterSha256\":\""+trained.shaAfter()+"\",\n"+
    "  \"verifiedRolloutSteps\":"+trained.eligibleSteps()+",\n"+
    "  \"adamSteps\":"+trained.adamUpdates()+",\n"+
    "  \"changedParameters\":"+trained.changedParameters()+",\n"+
    "  \"evaluation\":\"NOT_RUN\",\n"+
    "  \"promotion\":\"BLOCKED_UNTIL_INDEPENDENT_EVALUATION\"\n"+
    "}\n";
  Path manifest=directory.resolve("student-homebrew-v1.manifest.json");
  byte[] bytes=manifestText.getBytes(StandardCharsets.UTF_8);
  Path tmp=Files.createTempFile(directory,"student-manifest-",".pending");
  try{
   try(FileOutputStream output=new FileOutputStream(tmp.toFile())){
    output.write(bytes);output.getFD().sync();
   }
   if(Thread.currentThread().isInterrupted())throw new IOException("STOP_BEFORE_MANIFEST");
   Files.move(tmp,manifest,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  }finally{Files.deleteIfExists(tmp);}
  return new Candidate(candidate,manifest,trained);
 }
}
