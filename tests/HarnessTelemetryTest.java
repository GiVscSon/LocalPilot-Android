import java.util.ArrayList;
import java.util.List;
import org.localpilot.shared.HarnessTelemetry;

/** Pure JVM contract: event data is real evidence, STOP and audit gate are strict. */
public final class HarnessTelemetryTest {
 static void ok(boolean v,String why){if(!v)throw new AssertionError(why);}
 static final class Recorder implements HarnessTelemetry.Sink {
  final List<HarnessTelemetry.Event> events=new ArrayList<>();
  volatile boolean writable=true;
  @Override public void accept(HarnessTelemetry.Event event){events.add(event);}
  @Override public boolean healthy(){return writable;}
 }
 public static void main(String[] args){
  Recorder sink=new Recorder();
  HarnessTelemetry.install(sink);
  ok(HarnessTelemetry.healthy(),"empty healthy sink");
  String ep=HarnessTelemetry.begin("NES homebrew","sha256:model1");
  ok(ep!=null&&!ep.isBlank(),"unique episode ID");
  ok(HarnessTelemetry.active(),"one active real session");
  ok(!HarnessTelemetry.stopRequested(),"clean start");
  try{HarnessTelemetry.begin("NES homebrew","another");throw new AssertionError("overlap allowed");}
  catch(IllegalStateException expected){}
  HarnessTelemetry.emit("FRAME","CAPTURED","frame 25",4);
  HarnessTelemetry.emit("MODEL","MODEL_USED","ACTION=A",251);
  ok(sink.events.size()==3,"start and 2 real traces");
  ok(sink.events.get(2).latencyMs==251,"latency not silently changed");
  ok(sink.events.get(2).episodeId.equals(ep),"model inference wrongly attributed");
  ok(sink.events.get(2).modelId.equals("sha256:model1"),"model identity lost");
  HarnessTelemetry.requestStop();
  ok(HarnessTelemetry.stopRequested(),"STOP did not revoke upcoming action");
  HarnessTelemetry.end("test stop");
  ok(!HarnessTelemetry.active(),"session still owned");
  ok(!HarnessTelemetry.stopRequested(),"STOP remains stuck after close");
  int size=sink.events.size();
  HarnessTelemetry.emit("MODEL","MODEL_USED","unattributed",8);
  ok(sink.events.size()==size,"model claim without episode");
  sink.writable=false;
  try{HarnessTelemetry.begin("NES homebrew","model2");throw new AssertionError("unhealthy audit allowed");}
  catch(IllegalStateException expected){}
  sink.writable=true;
  String another=HarnessTelemetry.begin("NES homebrew","model2");
  ok(!another.equals(ep),"old episode reused");
  HarnessTelemetry.end("done");
  HarnessTelemetry.install(null);
  System.out.println("HARNESS_TELEMETRY_PASS checks=13");
 }
}
