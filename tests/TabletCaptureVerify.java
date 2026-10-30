import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Run with: java tests/TabletCaptureVerify.java */
class TabletCaptureVerify {
 public static void main(String[] args)throws Exception{
  if(ToolProvider.getSystemJavaCompiler()==null)
   throw new IllegalStateException("Full JDK 17+ required");
  Path out=Files.createTempDirectory("localpilot-tablet-capture-");
  var names=new String[]{
   "CaptureResolution","JumperFrameGeometry",
   "ArcadeProfile","ArcadeObservation","CustomVisionConfig","CustomArcadeVision"
  };
  var cli=new java.util.ArrayList<String>();
  cli.add("--release");cli.add("17");cli.add("-d");cli.add(out.toString());
  for(String name:names)
   cli.add("app/src/main/java/org/localpilot/android/"+name+".java");
  cli.add("tests/TabletCaptureTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,cli.toArray(String[]::new))!=0)
   throw new AssertionError("tablet capture pure-Java compilation failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.TabletCaptureTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
