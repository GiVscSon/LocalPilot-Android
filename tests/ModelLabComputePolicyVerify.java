import java.net.*;
import java.nio.file.*;
import javax.tools.ToolProvider;
class ModelLabComputePolicyVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("modellab-compute-policy-");
  if(ToolProvider.getSystemJavaCompiler()==null||
     ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
       "--release","17","-d",out.toString(),
       "model-lab/src/main/java/org/localpilot/android/ModelLabComputePolicy.java",
       "tests/ModelLabComputePolicyTest.java")!=0)
   throw new AssertionError("ModelLab compute policy compile");
  try(URLClassLoader l=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   l.loadClass("org.localpilot.android.ModelLabComputePolicyTest")
     .getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
