import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.tools.ToolProvider;

/** Real concurrent JVM regression: published GGUF lists never expose partial writes. */
class ModelLabRegistrySnapshotVerify {
 public static void main(String[] args)throws Exception{
  var output=Files.createTempDirectory("modellab-registry-snapshot");
  int code=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
   "--release","17","-d",output.toString(),
   "model-lab/src/main/java/org/localpilot/android/ModelLabRegistrySnapshot.java");
  if(code!=0)throw new AssertionError("Snapshot helper did not compile");
  try(var loader=new URLClassLoader(new URL[]{output.toUri().toURL()})){
   var type=loader.loadClass("org.localpilot.android.ModelLabRegistrySnapshot");
   Object snapshot=type.getDeclaredConstructor().newInstance();
   var publish=type.getMethod("publish",List.class);
   var read=type.getMethod("read");
   var mutable=new ArrayList<>(List.of("A","A","A"));
   publish.invoke(snapshot,mutable);
   mutable.set(0,"CORRUPT");
   var old=(List<?>)read.invoke(snapshot);
   if(!old.equals(List.of("A","A","A")))
    throw new AssertionError("Aliased uncommitted metadata");
   @SuppressWarnings("unchecked")
   var userCopy=(List<String>)read.invoke(snapshot);
   userCopy.set(0,"CORRUPT");
   if(!read.invoke(snapshot).equals(List.of("A","A","A")))
    throw new AssertionError("Read side returned mutable internal list");
   AtomicReference<Throwable> error=new AtomicReference<>();
   Thread writer=new Thread(()->{
    try{
     for(int i=0;i<10000;i++){
      String marker="v"+i;
      publish.invoke(snapshot,List.of(marker,marker,marker));
     }
    }catch(Throwable failure){error.compareAndSet(null,failure);}
   });
   Thread reader=new Thread(()->{
    try{
     for(int i=0;i<10000;i++){
      var current=(List<?>)read.invoke(snapshot);
      if(current.size()!=3||!current.get(0).equals(current.get(1))||
          !current.get(1).equals(current.get(2)))
       throw new AssertionError("Torn/partially published registry");
     }
    }catch(Throwable failure){error.compareAndSet(null,failure);}
   });
   writer.start();reader.start();
   writer.join(10000);reader.join(10000);
   if(writer.isAlive()||reader.isAlive())throw new AssertionError("Registry snapshot thread stalled");
   if(error.get()!=null)throw new AssertionError("Concurrent registry failed",error.get());
   System.out.println("MODELLAB_IMMUTABLE_REGISTRY_CONCURRENT_PASS iterations=10000");
  }
 }
}
