import java.nio.file.*;
import java.net.*;
import javax.tools.ToolProvider;

/** Lightweight host contract: every model is uniquely named and safe to select. */
class LocalModelCatalogVerify {
 public static void main(String[] args)throws Exception{
  Path tmp=Files.createTempDirectory("local-model-catalog");
  int status=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",tmp.toString(),
    "app/src/main/java/org/localpilot/android/LocalModelCatalog.java");
  if(status!=0)throw new AssertionError("LocalModelCatalog compile");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{tmp.toUri().toURL()})){
   Class<?> cat=loader.loadClass("org.localpilot.android.LocalModelCatalog");
   Class<?> model=loader.loadClass("org.localpilot.android.LocalModelCatalog$Model");
   Object[] vals=model.getEnumConstants();
   if(vals.length!=4)throw new AssertionError("must be four predefined model choices");
   var all=(String[])cat.getMethod("labels",boolean.class).invoke(null,true);
   var basic=(String[])cat.getMethod("labels",boolean.class).invoke(null,false);
   if(all.length!=4||basic.length!=4||all[0].contains("не включена")||
      !basic[1].contains("не включена"))throw new AssertionError("wrong APK compatibility");
   var ids=new java.util.HashSet<String>();
   for(Object entry:vals){
    String id=(String)model.getField("id").get(entry);
    String name=(String)model.getField("filename").get(entry);
    if(!ids.add(id)||!name.endsWith(".gguf")||name.contains(".."))
     throw new AssertionError("unsafe or duplicate model identifier");
   }
   Object vision=vals[3];
   if(!(boolean)model.getMethod("vision").invoke(vision)||
      model.getField("projector").get(vision)==null)
    throw new AssertionError("VL projector pair missing");
   if(cat.getMethod("find",String.class).invoke(null,"bogus")!=vals[0])
    throw new AssertionError("unsafe selected model fallback");
   if(cat.getMethod("defaultModel",boolean.class).invoke(null,true)!=vals[1])
    throw new AssertionError("Qwen should default in full catalogue");
   System.out.println("LOCAL_MODEL_CATALOG_PASS models="+vals.length+" imageProjector=paired");
  }
 }
}
