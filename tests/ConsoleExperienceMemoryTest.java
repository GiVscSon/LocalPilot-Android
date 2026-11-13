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
  System.out.println("CONSOLE_SHARED_MEMORY_PASS");
 }
}
