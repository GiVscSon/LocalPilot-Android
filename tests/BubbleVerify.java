import javax.tools.ToolProvider;
import java.net.*;import java.nio.file.*;import java.util.*;
class BubbleVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("localpilot-bubbles-");List<String> command=new ArrayList<>(List.of("--release","17","-d",out.toString()));
  for(String name:List.of("BubbleBoard","BubblePolicy","BubbleSession","BubbleVision"))command.add("app/src/main/java/org/localpilot/android/"+name+".java");
  command.add("tests/BubbleTest.java");
  command.add("tests/BubbleBounceTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,command.toArray(String[]::new))!=0)throw new AssertionError("bubble compilation");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.BubbleTest").getMethod("main",String[].class).invoke(null,(Object)args);
   loader.loadClass("org.localpilot.android.BubbleBounceTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
  }
 }
}
