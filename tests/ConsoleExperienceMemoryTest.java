package org.localpilot.shared;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public final class ConsoleExperienceMemoryTest {
 private static void require(boolean ok,String why){
  if(!ok)throw new AssertionError(why);
 }
 private static void expectFailure(Runnable task){
  try{task.run();}catch(IllegalArgumentException expected){return;}
  throw new AssertionError("Unverified episode was accepted");
 }
 public static void main(String[] args)throws Exception{
  Path temp=Files.createTempDirectory("console-memory-test");
  String sha="a".repeat(64),other="b".repeat(64);
  NesTrial changed=NesTrial.create("HOMEBREW","RIGHT",20,50,44,55);
  NesTrial same=NesTrial.create("HOMEBREW","A",50,81,55,55);
  ConsoleExperienceMemory.append(temp,"import:alpha",sha,changed);
  ConsoleExperienceMemory.append(temp,"import:alpha",sha,same);
  ConsoleExperienceMemory.append(temp,"import:beta",other,changed);
  require(ConsoleExperienceMemory.read(temp).size()==3,"three checked episodes");
  String context=ConsoleExperienceMemory.context(temp,"import:alpha",sha);
  require(context.contains("RIGHT:PIXELS_DIFFER"),"real prior move reused");
  require(context.contains("A:NO_VISUAL_CHANGE"),"uncertain prior move reused");
  require(!context.contains("reward=1"),"no synthetic score");
  require(!ConsoleExperienceMemory.context(temp,"import:alpha",other)
    .contains("RIGHT:PIXELS_DIFFER"),"SHA isolates each model");
  String summary=ConsoleExperienceMemory.summary(temp);
  require(summary.contains("ходов 3")&&summary.contains("веса GGUF не обучались"),
    "honest shared episode accounting");
  expectFailure(()->{
   try{ConsoleExperienceMemory.append(temp,"import:alpha",sha,
     NesTrial.create("USER_ROM","RIGHT",20,50,44,55));}
   catch(IOException io){throw new AssertionError(io);}
  });
  Path journal=temp.resolve(ConsoleExperienceMemory.FILE);
  String text=Files.readString(journal,StandardCharsets.UTF_8);
  Files.writeString(journal,text.replace("PIXELS_DIFFER","NO_VISUAL_CHANGE"),
    StandardCharsets.UTF_8);
  boolean blocked=false;
  try{ConsoleExperienceMemory.context(temp,"import:alpha",sha);}
  catch(IOException corrupted){blocked=true;}
  require(blocked,"checksum failure must prevent untrusted context");
  Files.delete(journal);
  ConsoleExperienceMemory.append(temp,"import:alpha",sha,changed);
  require(ConsoleExperienceMemory.read(temp).size()==1,"recovery after explicit file reset");
  NesTrial proof=NesTrial.create("HOMEBREW","RIGHT",90,106,44,55);
  ConsoleExperienceMemory.appendGoal(temp,"3004","import:alpha",sha,
    proof,128,112,144,112);
  var tasks=ConsoleExperienceMemory.readGoals(temp);
  require(tasks.size()==1&&tasks.get(0).reward()==16&&!tasks.get(0).reached(),
    "actual engine-state reward is re-derived from verified task coordinates");
  require(ConsoleExperienceMemory.goalSummary(temp).contains("1 проверенных CPU-переходов"),
    "shared memory reports task provenance, not generic NES reward");
  boolean rejected=false;
  try{ConsoleExperienceMemory.appendGoal(temp,"3004","import:alpha",sha,
    proof,128,112,90,112);}catch(IllegalArgumentException invalid){rejected=true;}
  require(rejected,"unsupported controller transition must not create positive reward");
  Path goal=temp.resolve(ConsoleExperienceMemory.GOAL_FILE);
  String original=Files.readString(goal,StandardCharsets.UTF_8);
  Files.writeString(goal,original.replace("|16|0|","|99|0|"),StandardCharsets.UTF_8);
  rejected=false;
  try{ConsoleExperienceMemory.readGoals(temp);}catch(IOException corrupted){rejected=true;}
  require(rejected,"tampered engine-state reward must fail closed");
  Files.writeString(goal,original,StandardCharsets.UTF_8);
  NesTrial shortProof=NesTrial.create("HOMEBREW","RIGHT",110,114,55,66);
  ConsoleExperienceMemory.appendGoal(temp,"3005","import:alpha",sha,shortProof,189,112,192,112);
  var mixed=ConsoleExperienceMemory.readGoals(temp);
  require(mixed.size()==2&&mixed.get(1).reached()&&mixed.get(1).reward()==23,
    "Short-step V2 task records coexist with preserved long-step data");
  String oldBody=String.join("|","CONSOLE_HOME_BREW_GOAL_V1","JSNES_6502_ZERO_PAGE_01_02",
    "3004","import:alpha",sha,"RIGHT","90","106","44","55","PIXELS_DIFFER",
    "128","112","144","112","16","0");
  String oldHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
    .digest(oldBody.getBytes(StandardCharsets.UTF_8)));
  Files.writeString(goal,oldBody+"|"+oldHash+"\n",StandardCharsets.UTF_8);
  require(ConsoleExperienceMemory.readGoals(temp).get(0).reward()==16,"Legacy V1 task journal remains readable");
  System.out.println("CONSOLE_SHARED_MEMORY_PASS");
 }
}
