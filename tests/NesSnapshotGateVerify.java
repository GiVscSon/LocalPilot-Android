import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import javax.tools.ToolProvider;

/** Races: late callback, old timeout, new ROM, duplicate JNI result. */
class NesSnapshotGateVerify {
 public static void main(String[] args)throws Exception{
  var output=Files.createTempDirectory("nes-snapshot-gate-");
  try{
   int code=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,
    "--release","17","-d",output.toString(),
    "game-lab/src/main/java/org/localpilot/gamelab/NesSnapshotGate.java");
   if(code!=0)throw new AssertionError("Gate did not compile");
   try(var loader=new URLClassLoader(new URL[]{output.toUri().toURL()})){
    Class<?> type=loader.loadClass("org.localpilot.gamelab.NesSnapshotGate");
    Object gate=type.getDeclaredConstructor().newInstance();
    var begin=type.getMethod("begin");
    var current=type.getMethod("current",String.class);
    var finish=type.getMethod("finish",String.class);
    var cancel=type.getMethod("cancel");
    var pending=type.getMethod("pending");
    String first=(String)begin.invoke(gate);
    require((Boolean)pending.invoke(gate),"first request not pending");
    require(!(Boolean)finish.invoke(gate,"0"),"wrong token accepted");
    require(!(Boolean)finish.invoke(gate,"999"),"guessed token accepted");
    require((Boolean)current.invoke(gate,first),"current token missing");
    try{begin.invoke(gate);throw new AssertionError("concurrent request allowed");}
    catch(java.lang.reflect.InvocationTargetException expected){
     require(expected.getCause() instanceof IllegalStateException,"wrong concurrency exception");
    }
    require((Boolean)finish.invoke(gate,first),"current snapshot not delivered");
    require(!(Boolean)finish.invoke(gate,first),"duplicate snapshot accepted");
    String second=(String)begin.invoke(gate);
    require(!second.equals(first),"reused identifier across exports");
    require(!(Boolean)finish.invoke(gate,first),"old WebView response attached to new NES trial");
    require((Boolean)finish.invoke(gate,second),"second snapshot rejected");
    String third=(String)begin.invoke(gate);
    cancel.invoke(gate);
    require(!(Boolean)finish.invoke(gate,third),"callback accepted after pause or ROM reload");
    String fourth=(String)begin.invoke(gate);
    require(!(Boolean)finish.invoke(gate,third),"old timeout cleared new request");
    require((Boolean)finish.invoke(gate,fourth),"new snapshot after cancellation rejected");
    require(!(Boolean)pending.invoke(gate),"pending was never released");
    Object manual=type.getDeclaredConstructor(int.class,int.class).newInstance(1,2);
    Object neural=type.getDeclaredConstructor(int.class,int.class).newInstance(2,2);
    for(int i=0;i<20;i++){
     String a=(String)begin.invoke(manual),b=(String)begin.invoke(neural);
     require(!a.equals(b),"manual and neural snapshots collided");
     require(!(Boolean)current.invoke(manual,b),"neural token accepted by manual export");
     require(!(Boolean)current.invoke(neural,a),"manual token accepted by AI");
     require((Boolean)finish.invoke(manual,a),"manual token did not clear");
     require((Boolean)finish.invoke(neural,b),"AI token did not clear");
    }
   }
   System.out.println("NES_SNAPSHOT_GATE_PASS cases=13");
  }finally{
   try(var walk=Files.walk(output)){
    walk.sorted(java.util.Comparator.reverseOrder()).forEach(path->{
     try{Files.deleteIfExists(path);}catch(java.io.IOException ignored){}
    });
   }
  }
 }
 private static void require(boolean value,String message){
  if(!value)throw new AssertionError(message);
 }
}
