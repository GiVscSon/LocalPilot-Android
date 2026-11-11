import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import javax.tools.ToolProvider;

/** Host JVM checks for the only allowed automatic ROM download. */
class NesRomDownloadVerify {
 public static void main(String[] argv)throws Exception{
  var folder=Files.createTempDirectory("nes-rom-policy");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",folder.toString(),
    "game-lab/src/main/java/org/localpilot/gamelab/VerifiedNesRomDownload.java")!=0)
   throw new AssertionError("ROM downloader compilation failed");
  try(var loader=new URLClassLoader(new URL[]{folder.toUri().toURL()})){
   var klass=loader.loadClass("org.localpilot.gamelab.VerifiedNesRomDownload");
   var allowed=klass.getDeclaredMethod("allowed",String.class,boolean.class);
   allowed.setAccessible(true);
   String source=(String)klass.getField("SOURCE").get(null);
   String sha=(String)klass.getField("SHA256").get(null);
   int bytes=(int)klass.getField("BYTES").get(null);
   if(!sha.matches("[0-9a-f]{64}")||bytes!=40976||
     !(boolean)allowed.invoke(null,source,true))
    throw new AssertionError("pinned release metadata");
   String[] banned={"http://github.com/Riyoway/void-lancer-nes",
     "https://127.0.0.1/rom.nes","https://evil.example/rom.nes",
     "https://github.com/unknown/rom.nes","file:///sdcard/rom.nes",
     "https://user:token@github.com/rom.nes"};
   for(String bad:banned)
    if((boolean)allowed.invoke(null,bad,true))
     throw new AssertionError("untrusted URL "+bad);
   if(!(boolean)allowed.invoke(null,
     "https://release-assets.githubusercontent.com/example?token=x",false))
    throw new AssertionError("expected GitHub release CDN");
   var validate=klass.getMethod("validate",byte[].class);
   byte[] invalid=new byte[bytes];invalid[0]='N';invalid[1]='E';invalid[2]='S';invalid[3]=26;
   boolean rejected=false;
   try{validate.invoke(null,(Object)invalid);}catch(java.lang.reflect.InvocationTargetException ex){
    rejected=ex.getCause() instanceof SecurityException;
   }
   if(!rejected)throw new AssertionError("tampered ROM accepted");
   System.out.println("NES_PINNED_HOMEBREW_POLICY_PASS");
  }
 }
}