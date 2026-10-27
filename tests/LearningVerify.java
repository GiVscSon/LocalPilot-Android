import javax.tools.ToolProvider;
import java.net.*;
import java.nio.file.*;
class LearningVerify {
 public static void main(String[]args)throws Exception{
  Path out=Files.createTempDirectory("localpilot-learning-verify-");var sources=new java.util.ArrayList<String>();sources.add("-d");sources.add(out.toString());
  for(String name:new String[]{"ArcadeLearningStore","VisualGameTarget","ArcadeController","ArcadeFeedback","ArcadeProfile","ArcadePolicy","ArcadeObservation","Board2048","Verified2048","Native2048Learning"})sources.add("app/src/main/java/org/localpilot/android/"+name+".java");
  sources.add("tests/LearningLifecycleTest.java");sources.add("tests/Native2048LearningTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,sources.toArray(String[]::new))!=0)throw new AssertionError("compile failed");
  try(var cl=new URLClassLoader(new URL[]{out.toUri().toURL()})){cl.loadClass("org.localpilot.android.LearningLifecycleTest").getMethod("main",String[].class).invoke(null,(Object)args);cl.loadClass("org.localpilot.android.Native2048LearningTest").getMethod("main",String[].class).invoke(null,(Object)args);}
 }
}
