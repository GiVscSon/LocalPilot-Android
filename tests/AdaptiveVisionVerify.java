import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;

/** Explicit host runner; no Android classes or physical-device claims. */
class AdaptiveVisionVerify {
 public static void main(String[] args)throws Exception{
  var out=Files.createTempDirectory("adaptive-vision-");
  int compiled=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17","-d",out.toString(),
      "app/src/main/java/org/localpilot/android/GridPuzzleVision.java",
      "app/src/main/java/org/localpilot/android/AdaptiveGridReader.java",
      "tests/GridPuzzleVisionTest.java","tests/AdaptiveGridReaderTest.java");
  if(compiled!=0)throw new AssertionError("adaptive vision compilation");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   for(String name:new String[]{"GridPuzzleVisionTest","AdaptiveGridReaderTest"})
    loader.loadClass("org.localpilot.android."+name).getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
