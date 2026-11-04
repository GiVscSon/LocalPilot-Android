import javax.tools.ToolProvider;
import java.net.*;
import java.nio.file.*;
import java.util.*;
class RlVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("pilot-rl-tests-");List<String> files=new ArrayList<>(List.of("-d",out.toString()));
  try(var paths=Files.walk(Path.of("app/src/main/java/org/localpilot/android/rl"))){paths.filter(p->p.toString().endsWith(".java")).forEach(p->files.add(p.toString()));}
  for(String f:List.of("InputPermit","SafeJournal","RotatingJournal"))files.add("app/src/main/java/org/localpilot/android/"+f+".java");
  files.add("tests/RlTest.java");if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,files.toArray(new String[0]))!=0)throw new AssertionError("compile");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){loader.loadClass("org.localpilot.android.rl.RlTest").getMethod("main",String[].class).invoke(null,(Object)args);}
 }
}
