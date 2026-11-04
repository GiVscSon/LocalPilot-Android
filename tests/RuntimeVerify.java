import javax.tools.ToolProvider;
import java.net.*;
import java.nio.file.*;
class RuntimeVerify {
 public static void main(String[]args)throws Exception{
  Path out=Files.createTempDirectory("pilot-runtime-");var source=new java.util.ArrayList<String>(java.util.List.of("--release","17","-d",out.toString()));
  for(String name:new String[]{"Board2048","PixelBoardReader","GameViewport","RegionPickerGeometry","JumperState","JumperPolicy","JumperFeedback"})source.add("app/src/main/java/org/localpilot/android/"+name+".java");
  source.add("tests/RuntimeFlowTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,source.toArray(String[]::new))!=0)throw new AssertionError("runtime test compile");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){loader.loadClass("org.localpilot.android.RuntimeFlowTest").getMethod("main",String[].class).invoke(null,(Object)args);}
 }
}
