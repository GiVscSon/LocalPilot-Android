package org.localpilot.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class StudentCandidateSlotTest {
 public static void main(String[] args)throws Exception {
  Path vault=Files.createTempDirectory("student-slot-test");
  LearningCoordinator.requireCandidateSlot(vault);
  if(LearningCoordinator.candidateSlot(vault)!=LearningCoordinator.CandidateSlot.EMPTY)throw new AssertionError();
  Path dir=vault.resolve("models/candidates");
  Files.createDirectories(dir);
  for(String name:new String[]{"student-homebrew-v1.weights.bin","student-homebrew-v1.manifest.json"}) {
   Path file=dir.resolve(name);
   Files.writeString(file,"keep");
   if(LearningCoordinator.candidateSlot(vault)!=LearningCoordinator.CandidateSlot.INCOMPLETE)throw new AssertionError();
   try { LearningCoordinator.requireCandidateSlot(vault); throw new AssertionError("accepted "+name); }
   catch(IOException expected) {
    if(!expected.getMessage().equals("PPO_PENDING_CANDIDATE_MUST_BE_EVALUATED"))throw expected;
   }
   if(!Files.readString(file).equals("keep"))throw new AssertionError("candidate modified");
   Files.delete(file);
  }
  Files.writeString(dir.resolve("student-homebrew-v1.weights.bin"),"weights");
  Files.writeString(dir.resolve("student-homebrew-v1.manifest.json"),"manifest");
  if(LearningCoordinator.candidateSlot(vault)!=LearningCoordinator.CandidateSlot.COMPLETE_UNVERIFIED)throw new AssertionError();
  try { LearningCoordinator.requireCandidateSlot(vault); throw new AssertionError("accepted complete candidate"); }
  catch(IOException expected) { }
  Files.delete(dir.resolve("student-homebrew-v1.weights.bin"));
  Files.delete(dir.resolve("student-homebrew-v1.manifest.json"));
  LearningCoordinator.requireCandidateSlot(vault);
  System.out.println("StudentCandidateSlotTest PASS");
 }
}
