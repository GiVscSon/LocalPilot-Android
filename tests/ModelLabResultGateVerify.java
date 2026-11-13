import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import javax.tools.ToolProvider;

/** Prevent stale VLM/text answers from overwriting a newer NES trial. */
class ModelLabResultGateVerify {
 static int checks;
 static void check(boolean ok,String label){
  checks++; if(!ok)throw new AssertionError(label);
 }
 public static void main(String[] args)throws Exception{
  var path=Files.createTempDirectory("modellab-result-gate");
  try{
   int compiled=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",path.toString(),
    "model-lab/src/main/java/org/localpilot/android/ModelLabResultGate.java");
   check(compiled==0,"could not compile result revision gate");
   try(var loader=new URLClassLoader(new URL[]{path.toUri().toURL()})){
    var cls=loader.loadClass("org.localpilot.android.ModelLabResultGate");
    var gate=cls.getDeclaredConstructor().newInstance();
    var nextNes=cls.getMethod("newNes");
    var nextImage=cls.getMethod("newImage");
    var currentNes=cls.getMethod("currentNes",long.class);
    var currentImage=cls.getMethod("currentImage",long.class);
    long initialNes=(Long)nextNes.invoke(gate);
    long initialImg=(Long)nextImage.invoke(gate);
    check((Boolean)currentNes.invoke(gate,initialNes),"NES result current initially");
    check((Boolean)currentImage.invoke(gate,initialImg),"image result current initially");
    long otherNes=(Long)nextNes.invoke(gate);
    check(!(Boolean)currentNes.invoke(gate,initialNes),"stale NES result accepted");
    check((Boolean)currentImage.invoke(gate,initialImg),"NES and image revisions should be independent");
    long otherImg=(Long)nextImage.invoke(gate);
    check(!(Boolean)currentImage.invoke(gate,initialImg),"stale VLM answer accepted");
    check((Boolean)currentImage.invoke(gate,otherImg),"new image not accepted");
    check((Boolean)currentNes.invoke(gate,otherNes),"new NES not accepted");
    long pickedPhoto=(Long)nextImage.invoke(gate);
    check(!(Boolean)currentImage.invoke(gate,otherImg),"new picked photo still has old image revision");
    check((Boolean)currentImage.invoke(gate,pickedPhoto),"picked photo revision invalid");
   }
   System.out.println("MODELLAB_RESULT_GATE_PASS checks="+checks);
  }finally{
   try(var walk=Files.walk(path)){
    walk.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
     try{Files.deleteIfExists(p);}catch(java.io.IOException ignored){}
    });
   }
  }
 }
}
