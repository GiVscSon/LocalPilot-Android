import java.net.*;import java.nio.file.*;import java.util.*;import javax.tools.*;
/** Standalone JVM smoke check for real-device fail-closed behavior. */
class FieldSafetyVerify {
 public static void main(String[] args)throws Exception {
  Path out=Files.createTempDirectory("localpilot-field-safety");
  List<String> cmd=new ArrayList<>(List.of("--release","17","-d",out.toString()));
  for(String name:List.of("JumperState","JumperPolicy","JumperProgressGuard","BubblePanelSafety"))
   cmd.add("app/src/main/java/org/localpilot/android/"+name+".java");
  cmd.add("tests/FieldSafetyTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,cmd.toArray(String[]::new))!=0)
   throw new AssertionError("field safety compile");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.FieldSafetyTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}