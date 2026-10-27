import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.tools.ToolProvider;

/** java tests/PanelDockingVerify.java: compile and run isolated pure-Java docking tests. */
class PanelDockingVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("pilot-panel-dock-");
  ArrayList<String> sources=new ArrayList<>();
  sources.add("--release");sources.add("17");sources.add("-d");sources.add(out.toString());
  sources.add("app/src/main/java/org/localpilot/android/PanelDocking.java");
  sources.add("tests/PanelDockingTest.java");
  if(ToolProvider.getSystemJavaCompiler()==null)throw new IllegalStateException("JDK required");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,sources.toArray(String[]::new))!=0)
   throw new AssertionError("panel docking compile failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.PanelDockingTest").getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
