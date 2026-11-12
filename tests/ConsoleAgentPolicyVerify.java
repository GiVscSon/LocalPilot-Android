import java.net.*;import java.nio.file.*;import javax.tools.ToolProvider;
class ConsoleAgentPolicyVerify {
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("console-agent-contract-");
  String[] sources={
   "shared/src/main/java/org/localpilot/shared/ConsoleAgentBridge.java",
   "shared/src/main/java/org/localpilot/shared/ConsoleAgentPolicy.java",
   "tests/ConsoleAgentPolicyTest.java",
   "shared/src/main/java/org/localpilot/shared/ConsoleObservation.java",
   "tests/ConsoleObservationTest.java"
  };
  String[] command=new String[sources.length+4];
  command[0]="--release";command[1]="17";command[2]="-d";command[3]=dir.toString();
  System.arraycopy(sources,0,command,4,sources.length);
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,command)!=0)
   throw new AssertionError("Console-agent policy must compile cleanly");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{dir.toUri().toURL()})){
   loader.loadClass("org.localpilot.shared.ConsoleAgentPolicyTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
   loader.loadClass("org.localpilot.shared.ConsoleObservationTest")
    .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
