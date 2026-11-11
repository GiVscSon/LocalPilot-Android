import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.HashSet;
import javax.tools.ToolProvider;

/** Host JVM gate: no Android SDK, no model downloads, fixed immutable GGUF sources. */
class ModelLabCatalogVerify {
 public static void main(String[] argv)throws Exception{
  var folder=Files.createTempDirectory("model-catalog");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",folder.toString(),
    "model-lab/src/main/java/org/localpilot/android/ModelLabPresetCatalog.java")!=0)
   throw new AssertionError("GGUF catalogue compilation failed");
  try(var loader=new URLClassLoader(new URL[]{folder.toUri().toURL()})){
   var klass=loader.loadClass("org.localpilot.android.ModelLabPresetCatalog");
   var rows=(java.util.List<?>)klass.getField("ALL").get(null);
   if(rows.size()!=4)throw new AssertionError("expected four model choices");
   HashSet<String> ids=new HashSet<>(),urls=new HashSet<>();
   long total=0;
   for(Object entry:rows){
    var t=entry.getClass();
    String id=(String)t.getField("id").get(entry);
    String source=(String)t.getMethod("modelUrl").invoke(entry);
    String sha=(String)t.getField("sha256").get(entry);
    String revision=(String)t.getField("revision").get(entry);
    long size=(long)t.getField("bytes").get(entry);
    long projectorBytes=(long)t.getField("projectorBytes").get(entry);
    boolean vision=(boolean)t.getMethod("vision").invoke(entry);
    if(!ids.add(id)||!urls.add(source))throw new AssertionError("duplicate pinned model");
    if(!source.startsWith("https://huggingface.co/")||source.contains("/main/")||
       !revision.matches("[0-9a-f]{40}")||!sha.matches("[0-9a-f]{64}")||
       size<128||size>900_000_000L)throw new AssertionError("invalid pinned model "+id);
    if(vision){
     String projector=(String)t.getMethod("projectorUrl").invoke(entry);
     String projectorSha=(String)t.getField("projectorSha256").get(entry);
     if(!projector.startsWith("https://huggingface.co/")||
        !projectorSha.matches("[0-9a-f]{64}")||projectorBytes<128)
      throw new AssertionError("unpaired vision model");
    }else if(projectorBytes!=0)throw new AssertionError("unexpected projector");
    total+=size+projectorBytes;
   }
   if(total!=1_296_368_960L)throw new AssertionError("weight budget changed");
   System.out.println("MODELLAB_ON_DEMAND_CATALOG_PASS models="+rows.size()+" bytes="+total);
  }
 }
}