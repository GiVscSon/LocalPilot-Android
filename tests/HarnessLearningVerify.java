import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.localpilot.shared.HarnessCoordinator;
import org.localpilot.shared.harness.*;

/** Actual core integration; not a synthetic learning performance benchmark. */
public class HarnessLearningVerify {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static final class Nes implements EnvironmentAdapter,AutoCloseable {
  final Process process;
  final BufferedReader input;
  final PrintWriter output;
  final HarnessCoordinator owner;
  String episode;
  Nes(HarnessCoordinator owner,String core,String rom)throws Exception{
   this.owner=owner;
   process=new ProcessBuilder("node","tests/harness_real_nes_rpc.cjs",core,rom).redirectError(ProcessBuilder.Redirect.INHERIT).start();
   input=process.inputReader();output=new PrintWriter(process.getOutputStream(),true);
  }
  Observation request(String command)throws Exception{
   output.println(command);String line=input.readLine();
   if(line==null||line.startsWith("ERROR"))throw new IOException("NES: "+line);
   String[] p=line.split("\t");
   return new Observation(episode,p[0],p[1],Integer.parseInt(p[2]),Integer.parseInt(p[3]),Integer.parseInt(p[4]),
    Integer.parseInt(p[5]),Integer.parseInt(p[6]),Integer.parseInt(p[7]),HarnessCoordinator.Mode.valueOf(p[10]),Boolean.parseBoolean(p[8]),p[9]);
  }
  public void reset(String id,int seed,Reply<Observation> reply){
   episode=id;
   try{var value=request("reset "+seed);owner.reset(value.epoch(),HarnessCoordinator.Mode.STEP_MODE);reply.complete(value,null);}
   catch(Exception e){reply.complete(null,e.toString());}
  }
  public void observe(Reply<Observation> reply){try{reply.complete(request("observe"),null);}catch(Exception e){reply.complete(null,e.toString());}}
  public void mode(HarnessCoordinator.Mode mode,Reply<Observation> reply){
   try{
    var value=request("realtime");
    if(!owner.changeMode(value.epoch(),mode))throw new IOException("Mode not changed");
    reply.complete(value,null);
   }catch(Exception e){reply.complete(null,e.toString());}
  }
  public void step(HarnessCoordinator.ActionPermit permit,Reply<Observation> reply){
   check(permit!=null&&permit.stopRevision==owner.stopRevision(),"Every real CPU step has coordinator permit");
   try{reply.complete(request(permit.mode==HarnessCoordinator.Mode.STEP_MODE?"step "+permit.button:
     "live "+permit.button+","+permit.beforeFrame+","+permit.epoch),null);}catch(Exception e){reply.complete(null,e.toString());}
  }
  public void stop(){}
  public void close(){process.destroy();}
 }
 static final class Host implements HarnessRuntime.Host {
  final ArrayDeque<Runnable> queue=new ArrayDeque<>();String status="";
  public long now(){return System.nanoTime()/1_000_000;}
  public void post(Runnable task){queue.add(task);}
  public void status(String text){status=text;}
  void drain(){int n=0;while(!queue.isEmpty()){if(++n>10000)throw new AssertionError("Unbounded runtime");queue.remove().run();}}
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=2)throw new IllegalArgumentException("CORE_JS ROM_NES");
  Path root=Files.createTempDirectory("harness-real-nes-");
  HarnessCoordinator owner=new HarnessCoordinator();
  ExperienceStore replay=new ExperienceStore(root.resolve("replay"));
  PolicyRepository repository=new PolicyRepository(root.resolve("policy"));
  Policy initial=repository.active();
  Host host=new Host();
  try(Nes nes=new Nes(owner,args[0],args[1])){
   HarnessRuntime runtime=new HarnessRuntime(owner,nes,replay,repository,host);owner.bindRuntime(runtime);
   runtime.collect();host.drain();
   check(!runtime.running(),"Bounded collection terminates");
   List<ExperienceStore.Transition> transitions=replay.readAll();
   check(transitions.size()>=32,"Real core transitions persisted: "+host.status);
   check(transitions.stream().allMatch(ExperienceStore.Transition::eligible),"All task rewards independently visual/RAM verified");
   var first=transitions.get(0);
   var unknown=new ExperienceStore.Transition(first.pair(),first.action(),null,"UNKNOWN",false,false,
     ExperienceStore.Provenance.REAL_JSNES,initial.sha256());
   check(!unknown.eligible(),"UNKNOWN never ordinary reward RL");
   var fixtureStore=new ExperienceStore(root.resolve("synthetic-negative-fixtures"));
   fixtureStore.append(new ExperienceStore.Transition(first.pair(),first.action(),null,"UNKNOWN",false,false,
     ExperienceStore.Provenance.SYNTHETIC,initial.sha256()));
   check(fixtureStore.readAll().get(0).reward()==null,"Unknown reward survives durable roundtrip without becoming zero");
   var synthetic=new ExperienceStore.Transition(first.pair(),first.action(),first.reward(),first.rewardSource(),
     first.terminated(),first.truncated(),ExperienceStore.Provenance.SYNTHETIC,initial.sha256());
   check(!synthetic.eligible(),"Synthetic fixtures excluded from real learning");
   String before=repository.active().sha256();
   runtime.trainAndEvaluate();host.drain();
   check(!runtime.running(),"Evaluation completes or stops fail closed");
   check(!repository.active().sha256().equals(before),"Real independent holdout promotion: "+host.status);
   String promotedSha=repository.active().sha256();
   Path exported=Path.of("build/harness-learning");Files.createDirectories(exported);
   for(String extension:List.of(".policy",".manifest",".evaluation"))
    Files.copy(root.resolve("policy").resolve(promotedSha+extension),exported.resolve("real-core-candidate"+extension),StandardCopyOption.REPLACE_EXISTING);
   Files.writeString(exported.resolve("real-core-report.json"),"{\"scope\":\"REAL_JSNES original homebrew target task; not Android/device/GGUF/GPU\",\"baselineSha\":\""+
     before+"\",\"candidateSha\":\""+promotedSha+"\",\"trainingTransitions\":"+transitions.size()+"}");
   check(replay.readAll().stream().anyMatch(t->t.before().episode().startsWith("eval-")),"Actual holdout transitions recorded");
   System.out.println(host.status);
   runtime.playRealtime();host.drain();
   check(host.status.equals("Policy episode complete; result recorded"),"REALTIME policy controls continuously running core: "+host.status);
   check(replay.readAll().stream().anyMatch(t->t.before().mode()==HarnessCoordinator.Mode.REALTIME_MODE),"Realtime provenance retained");
   var frozen=nes.request("reset 42");
   var live=nes.request("realtime");var later=nes.request("wait");
   check(later.frame()>live.frame(),"Real-time emulator advances while no policy runs");
   boolean stale=false;try{nes.request("live LEFT,"+live.frame()+","+live.epoch());}catch(IOException expected){stale=true;}
   check(stale,"Stale realtime command discarded by real JS bridge");
   runtime.rollback();check(repository.active().sha256().equals(before),"Rollback restores exact predecessor");
   // Rejection must keep the deployed predecessor, even with a changed candidate.
   Set<Integer> holdout=new HashSet<>();for(int i=10001;i<=10020;i++)holdout.add(i);
   var candidate=new LearningCoordinator().train(initial,transitions,holdout);
   check(candidate.policy().value(first.before(),first.action())!=initial.value(first.before(),first.action()),
     "Actual learned estimate changes on a fixed real observation");
   var a=new ArrayList<EvaluationGate.Episode>();var b=new ArrayList<EvaluationGate.Episode>();
   for(int i=0;i<20;i++){
    a.add(new EvaluationGate.Episode("base-"+i,10001+i,initial.sha256(),2,true,0,true));
    b.add(new EvaluationGate.Episode("next-"+i,10001+i,candidate.policy().sha256(),1,true,0,true));
   }
   PolicyRepository fixtureRepository=new PolicyRepository(root.resolve("synthetic-gate-fixtures"));
   check(!fixtureRepository.apply(candidate,a,b).promote(),"Regression rejected");
   check(repository.active().sha256().equals(before),"Regression preserves previous active artifact");
   // These outcomes are isolated synthetic gate fixtures, never appended to replay.
   b.set(0,new EvaluationGate.Episode("next-0",candidate.seeds().iterator().next(),candidate.policy().sha256(),9,true,0,true));
   check(!EvaluationGate.compare(candidate,a,b).promote(),"Train/holdout overlap rejected");
   b.set(0,new EvaluationGate.Episode("next-0",10001,candidate.policy().sha256(),9,true,0,false));
   check(!EvaluationGate.compare(candidate,a,b).promote(),"Unverified evaluation rejected");
   Path artifact=root.resolve("synthetic-gate-fixtures").resolve(candidate.policy().sha256()+".policy");
   Files.write(artifact,new byte[]{0,1});
   boolean corrupt=false;try{fixtureRepository.apply(candidate,a,b);}catch(IOException expected){corrupt=true;}
   check(corrupt,"Corrupt policy artifact fails closed");
   runtime.collect();int countBeforeStop=replay.readAll().size();
   runtime.stop("USER_STOP");var stoppedFrame=nes.request("observe");host.drain();
   check(nes.request("observe").frame()==stoppedFrame.frame(),"STOP fences already queued policy work");
   check(replay.readAll().size()==countBeforeStop,"STOP does not manufacture a transition");
   try(var files=Files.list(root.resolve("replay"))){
    check(files.anyMatch(p->p.toString().endsWith(".episode-end")),"STOP saves a truncated episode without invented next state/reward");
   }
  }
  System.out.println("HARNESS_REAL_NES_LEARNING_PASS checks="+checks+" evidence="+root);
 }
}
