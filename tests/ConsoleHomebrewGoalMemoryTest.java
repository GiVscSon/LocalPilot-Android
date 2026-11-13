package org.localpilot.shared;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Synthetic file integrity contract; NOT live Android reward provenance. */
public final class ConsoleHomebrewGoalMemoryTest {
 private static int checks;
 private static void check(boolean valid,String message){
  checks++;if(!valid)throw new AssertionError(message);
 }
 private static void mustFail(Io call,String message)throws Exception{
  boolean blocked=false;
  try{call.run();}catch(IOException expected){blocked=true;}
  check(blocked,message);
 }
 private interface Io {void run()throws Exception;}
 private static ConsoleExperienceMemory.HomebrewGoal row(int frame,int x,int delta){
  return new ConsoleExperienceMemory.HomebrewGoal(
    "123","a".repeat(64),"native-nes-gguf","b".repeat(64),"RIGHT",
    frame,frame+16,x,x+delta,delta/16.0f);
 }
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("nes-homebrew-goal-test");
  check(ConsoleExperienceMemory.readHomebrewGoals(dir).isEmpty(),"new store empty");
  var first=row(45,128,16);
  check(!first.ppoEligible(),"goal does not imply on-policy PPO eligibility");
  ConsoleExperienceMemory.appendHomebrewGoal(dir,first);
  Path file=dir.resolve(ConsoleExperienceMemory.HOMEBREW_GOAL_FILE);
  check(Files.isRegularFile(file),"evidence persisted");
  String contents=Files.readString(file,StandardCharsets.UTF_8);
  check(contents.contains("PPO_BLOCKED"),"explicit PPO barrier survives disk");
  check(contents.contains("builtin-homebrew-right-progress-v1"),
    "task identity is not a generic NES score");
  var loaded=ConsoleExperienceMemory.readHomebrewGoals(dir);
  check(loaded.size()==1,"one record after reload");
  check(loaded.get(0).equals(first),"RAM/frames/model identity survive read");
  check(!loaded.get(0).ppoEligible(),"PPO remains blocked after reload");

  var second=row(61,144,16);
  ConsoleExperienceMemory.appendHomebrewGoal(dir,second);
  check(ConsoleExperienceMemory.readHomebrewGoals(dir).size()==2,
    "multiple records preserve hash chain");
  String original=Files.readString(file);
  mustFail(()->ConsoleExperienceMemory.appendHomebrewGoal(dir,
    new ConsoleExperienceMemory.HomebrewGoal(
      "123","a".repeat(64),"native-nes-gguf","b".repeat(64),"RIGHT",
      77,93,160,168,1.0f)),"reject reward inconsistent with CPU RAM delta");
  check(Files.readString(file).equals(original),"failed append leaves original journal intact");
  mustFail(()->ConsoleExperienceMemory.appendHomebrewGoal(dir,
    new ConsoleExperienceMemory.HomebrewGoal(
      "123","a".repeat(64),"native-nes-gguf","b".repeat(64),"LEFT",
      77,93,160,176,1f)),"reject incorrect action direction");
  mustFail(()->ConsoleExperienceMemory.appendHomebrewGoal(dir,
    new ConsoleExperienceMemory.HomebrewGoal(
      "123","a".repeat(64),"native-nes-gguf","b".repeat(64),"RIGHT",
      77,93,254,1,-15.8125f)),"reject zero-page wrap");
  mustFail(()->ConsoleExperienceMemory.appendHomebrewGoal(dir,
    new ConsoleExperienceMemory.HomebrewGoal(
      "123","wrong-hash","native-nes-gguf","b".repeat(64),"RIGHT",
      77,93,160,176,1f)),"ROM SHA required");
  mustFail(()->ConsoleExperienceMemory.appendHomebrewGoal(dir,
    new ConsoleExperienceMemory.HomebrewGoal(
      "123","a".repeat(64),"native-nes-gguf","b".repeat(64),"RIGHT",
      77,92,160,175,.9375f)),"frame duration required");

  String corrupted=original.replace("PPO_BLOCKED","PPO_ALLOWED");
  Files.writeString(file,corrupted);
  mustFail(()->ConsoleExperienceMemory.readHomebrewGoals(dir),"reject modified eligibility flag");
  mustFail(()->ConsoleExperienceMemory.appendHomebrewGoal(dir,second),
    "do not overwrite corrupt evidence");
  Files.writeString(file,original);
  check(ConsoleExperienceMemory.readHomebrewGoals(dir).size()==2,
    "valid prior history restored");

  String broken=original.replace("|0|","|1|");
  Files.writeString(file,broken);
  mustFail(()->ConsoleExperienceMemory.readHomebrewGoals(dir),
    "detect altered SHA links/payload");
  Files.writeString(file,original);

  String foreign=original.replace("a".repeat(64),"c".repeat(64));
  Files.writeString(file,foreign);
  mustFail(()->ConsoleExperienceMemory.readHomebrewGoals(dir),
    "tampered ROM identity invalidates SHA");
  Files.writeString(file,original);
  check(ConsoleExperienceMemory.readHomebrewGoals(dir).size()==2,
    "last valid read");
  System.out.println("NES_RAM_GOAL_MEMORY_PASS checks="+checks+
     " fixture=synthetic legacyJournalUntouched=true ppoEligible=false");
 }
}
