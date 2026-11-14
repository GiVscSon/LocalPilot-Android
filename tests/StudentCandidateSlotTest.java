package org.localpilot.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class StudentCandidateSlotTest {
 public static void main(String[] args)throws Exception {
  Path vault=Files.createTempDirectory("student-slot-test");
  LearningCoordinator.requireCandidateSlot(vault);
  Path dir=vault.resolve("models/candidates");
  Files.createDirectories(dir);
  for(String name:new String[]{"student-homebrew-v1.weights.bin","student-homebrew-v1.manifest.json"}) {
   Path file=dir.resolve(name);
   Files.writeString(file,"keep");
   try { LearningCoordinator.requireCandidateSlot(vault); throw new AssertionError("accepted "+name); }
   catch(IOException expected) {
    if(!expected.getMessage().equals("PPO_PENDING_CANDIDATE_MUST_BE_EVALUATED"))throw expected;
   }
   if(!Files.readString(file).equals("keep"))throw new AssertionError("candidate modified");
   Files.delete(file);
  }
  LearningCoordinator.requireCandidateSlot(vault);
  System.out.println("StudentCandidateSlotTest PASS");
 }
}
