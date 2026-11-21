import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import javax.tools.ToolProvider;
/** Pure JVM test, no device or gameplay assertion. */
class InstalledGameRouteVerify {
 public static void main(String[] args)throws Exception{
  var path=Files.createTempDirectory("localpilot-installed-game-route");
  String src="app/src/main/java/org/localpilot/android/";
  int code=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17",
    "-d",path.toString(),src+"ArcadeProfile.java",src+"VisualGameTarget.java",
    src+"InstalledGameRoute.java","tests/InstalledGameRouteTest.java");
  if(code!=0)throw new AssertionError("Installed game admission did not compile");
  try(var loader=new URLClassLoader(new URL[]{path.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.InstalledGameRouteTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
