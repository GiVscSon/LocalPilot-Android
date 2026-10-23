import javax.tools.ToolProvider;import java.net.*;import java.nio.file.*;
class UniversalVerify {
 public static void main(String[]args)throws Exception{Path out=Files.createTempDirectory("pilot-universal-");var sources=new java.util.ArrayList<String>();sources.add("-d");sources.add(out.toString());
  for(String n:new String[]{"CustomVisionConfig","CustomArcadeVision","ArcadeControls","VisualGameTarget","ArcadeLearningStore","ArcadeProfile","ArcadeObservation","ArcadePolicy","ArcadeController","ArcadeFeedback"})sources.add("app/src/main/java/org/localpilot/android/"+n+".java");
  sources.add("tests/UniversalVisionTest.java");if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,sources.toArray(String[]::new))!=0)throw new AssertionError("compile failed");
  try(var cl=new URLClassLoader(new URL[]{out.toUri().toURL()})){cl.loadClass("org.localpilot.android.UniversalVisionTest").getMethod("main",String[].class).invoke(null,(Object)args);}
 }
}
