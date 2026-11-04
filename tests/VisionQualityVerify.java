import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
class VisionQualityVerify {
 public static void main(String[] args)throws Exception{
  var out=Files.createTempDirectory("localpilot-vision-quality-");var c=new java.util.ArrayList<String>();c.add("-d");c.add(out.toString());
  for(String s:new String[]{"Board2048","PixelBoardReader","SceneRecovery","ArcadeProfile","ArcadeObservation","ArcadePolicy","ArcadeVision"})c.add("app/src/main/java/org/localpilot/android/"+s+".java");
  c.add("tests/ArcadeVisionTemporalTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,c.toArray(String[]::new))!=0)throw new IllegalStateException("compile failed");
  try(var l=new URLClassLoader(new URL[]{out.toUri().toURL()})){l.loadClass("org.localpilot.android.ArcadeVisionTemporalTest").getMethod("main",String[].class).invoke(null,(Object)args);}
 }
}
