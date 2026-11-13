import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import javax.tools.ToolProvider;

/** Verifies actual Java epoch state transitions without requiring Android. */
class NesFrameEpochVerify {
 private static int checks;
 private static void check(boolean condition,String desc){
  checks++;
  if(!condition)throw new AssertionError(desc);
 }
 public static void main(String[] args)throws Exception{
  var output=Files.createTempDirectory("nes-frame-epoch-");
  try{
   int compile=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",output.toString(),
    "game-lab/src/main/java/org/localpilot/gamelab/NesFrameEpoch.java");
   check(compile==0,"NesFrameEpoch.java failed to compile");
   try(var loader=new URLClassLoader(new URL[]{output.toUri().toURL()})){
    var clazz=loader.loadClass("org.localpilot.gamelab.NesFrameEpoch");
    Object gate=clazz.getDeclaredConstructor().newInstance();
    var begin=clazz.getMethod("begin");
    var accept=clazz.getMethod("accept",String.class,int.class);
    var current=clazz.getMethod("current",String.class);
    var token=clazz.getMethod("token");
    var cancel=clazz.getMethod("cancel");
    check("".equals(token.invoke(gate)),"must begin inactive");
    String a=(String)begin.invoke(gate);
    check((boolean)current.invoke(gate,a),"active ROM token");
    check((boolean)accept.invoke(gate,a,15),"first actual frame");
    check(!(boolean)accept.invoke(gate,a,15),"duplicate frame");
    check(!(boolean)accept.invoke(gate,a,14),"out-of-order frame");
    check(!(boolean)accept.invoke(gate,a,-1),"negative frame");
    check(!(boolean)accept.invoke(gate,"999",30),"invalid session accepted");
    Object otherOwner=clazz.getDeclaredConstructor().newInstance();
    String other=(String)begin.invoke(otherOwner);
    check(!a.equals(other),"Activity instances share one unique process epoch namespace");
    String b=(String)begin.invoke(gate);
    check(!a.equals(b),"reused epoch token");
    check(!(boolean)current.invoke(gate,a),"old ROM session still current");
    check(!(boolean)accept.invoke(gate,a,10000),"stale queued frame replay");
    check((boolean)accept.invoke(gate,b,1),"new ROM starts at frame 1");
    check((boolean)accept.invoke(gate,b,15),"monotonic frame");
    cancel.invoke(gate);
    check(!(boolean)accept.invoke(gate,b,16),"frame after destroyed ROM");
    String c=(String)begin.invoke(gate);
    check(!c.equals(a)&&!c.equals(b),"new ROM token after cancel");
    check((boolean)accept.invoke(gate,c,15),"session after cancel");
   }
   System.out.println("NES_FRAME_EPOCH_PASS checks="+checks);
  }finally{
   try(var walk=Files.walk(output)){
    walk.sorted(java.util.Comparator.reverseOrder()).forEach(path->{
     try{Files.deleteIfExists(path);}catch(java.io.IOException ignored){}
    });
   }
  }
 }
}
