import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Standalone verification: java tests/Field2048GlassVerify.java */
class Field2048GlassVerify {
 public static void main(String[] args)throws Exception{
  if(ToolProvider.getSystemJavaCompiler()==null)
   throw new IllegalStateException("Full JDK 17 required");
  Path out=Files.createTempDirectory("localpilot-field-2048-");
  var sources=new ArrayList<String>();
  sources.add("--release");sources.add("17");sources.add("-d");sources.add(out.toString());
  for(String name:new String[]{"Board2048","PixelBoardReader","Verified2048","Color2048Reader","Game2048AutoReader","VisionNoveltyMemory"})
   sources.add("app/src/main/java/org/localpilot/android/"+name+".java");
  sources.add("tests/Field2048GlassTest.java");
  sources.add("tests/VisionNoveltyModelTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,sources.toArray(String[]::new))!=0)
   throw new AssertionError("real screenshot colour regression compilation failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.Field2048GlassTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
   loader.loadClass("org.localpilot.android.VisionNoveltyModelTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
