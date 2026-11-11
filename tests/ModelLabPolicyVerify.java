import java.net.*;
import java.nio.file.*;
import javax.tools.ToolProvider;
import java.util.*;

/** Host-only negative tests for safe import URLs and model visible answers. */
class ModelLabPolicyVerify {
 static int checks;
 static void yes(boolean cond,String label){checks++;if(!cond)throw new AssertionError(label);}
 public static void main(String[] args)throws Exception{
  Path tmp=Files.createTempDirectory("model-lab-contract");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",tmp.toString(),
    "model-lab/src/main/java/org/localpilot/android/ModelLabNetworkPolicy.java",
    "model-lab/src/main/java/org/localpilot/android/ModelLabAnswer.java")!=0)
    throw new AssertionError("ModelLab policy compilation failed");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{tmp.toUri().toURL()})){
   Class<?> net=loader.loadClass("org.localpilot.android.ModelLabNetworkPolicy");
   var allowed=net.getMethod("allowed",String.class);
   String[] yesUrls={
     "https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_0.gguf",
     "https://cas-bridge.xethub.hf.co/path/model",
     "https://cdn-lfs.huggingface.co/path",
     "https://us.aws.cdn.hf.co/path"};
   for(String url:yesUrls)
    yes((boolean)allowed.invoke(null,url),"expected HTTPS HF host: "+url);
   String[] bad={
     "http://huggingface.co/file.gguf",
     "https://huggingface.co.evil.example/file.gguf",
     "https://127.0.0.1/x.gguf",
     "https://10.0.0.1/x.gguf",
     "https://localhost/x.gguf",
     "file:///data/local/model.gguf",
     "https://huggingface.co:8443/path.gguf",
     "https://user:token@huggingface.co/file.gguf",
     "https://huggingface.co/file.gguf#fragment",
     "https://evil.example/file.gguf",
     "https://us.aws.cdn.hf.co.evil.example/file.gguf",
     "https://huggingface.co@localhost/file.gguf",
     null};
   for(String url:bad)yes(!(boolean)allowed.invoke(null,url),"network host should be rejected: "+url);
   Class<?> a=loader.loadClass("org.localpilot.android.ModelLabAnswer");
   var visible=a.getMethod("visible",String.class);
   yes("Maintain space.".equals(visible.invoke(null,
     "<think>private text</think>Maintain space.")),"strip Qwen reasoning");
   yes(((String)visible.invoke(null,"<think>unfinished")).contains("обрезан"),
       "no incomplete reasoning");
   yes(((String)visible.invoke(null,"<|im_start|>")).contains("служебные"),
       "reject prompt control tokens");
   yes("Use the CPU to verify each game transition.".equals(
     visible.invoke(null,"Use the CPU to verify each game transition.")),
       "normal text unchanged");
  }
  System.out.println("MODELLAB_POLICY_PASS checks="+checks);
 }
}
