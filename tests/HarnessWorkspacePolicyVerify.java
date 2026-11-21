import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import javax.tools.ToolProvider;
/** A host-side policy gate, not a device UI or gameplay test. */
class HarnessWorkspacePolicyVerify {
 public static void main(String[] args)throws Exception{
  var out=Files.createTempDirectory("harness-workspace-policy");
  String src="app/src/main/java/org/localpilot/android/";
  int code=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17",
    "-d",out.toString(),src+"HarnessWorkspacePolicy.java",
    "tests/HarnessWorkspacePolicyTest.java");
  if(code!=0)throw new AssertionError("HarnessWorkspacePolicy did not compile");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.HarnessWorkspacePolicyTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
