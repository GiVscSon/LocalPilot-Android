import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Standalone JVM regression: java tests/CalibrationVerify.java */
class CalibrationVerify {
 public static void main(String[] args)throws Exception{
  if(ToolProvider.getSystemJavaCompiler()==null)throw new IllegalStateException("Full JDK required");
  Path out=Files.createTempDirectory("localpilot-calibration-");
  ArrayList<String> src=new ArrayList<>();
  src.add("--release");src.add("17");src.add("-d");src.add(out.toString());
  for(String name:new String[]{"Board2048","PixelBoardReader","GameViewport","RegionPickerGeometry","ColorSampleGuard"})
   src.add("app/src/main/java/org/localpilot/android/"+name+".java");
  src.add("tests/CalibrationGuardTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,src.toArray(String[]::new))!=0)
   throw new AssertionError("calibration pure-Java compilation failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.CalibrationGuardTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
