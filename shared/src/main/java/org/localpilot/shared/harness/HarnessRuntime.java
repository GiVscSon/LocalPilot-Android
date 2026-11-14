package org.localpilot.shared.harness;

import org.localpilot.shared.HarnessCoordinator;
import java.util.*;

/** The shared coordinator's bounded task flow. All actions still require its one-use permits.
 * Collection, training, frozen evaluation and deployment are distinct phases.
 */
public final class HarnessRuntime {
 public interface Host {long now();void post(Runnable work);void status(String message);}
 private final HarnessCoordinator owner;
 private final EnvironmentAdapter environment;
 private final ExperienceStore store;
 private final PolicyRepository policies;
 private final Host host;
 private final Random random=new Random(0x4c50484c);
 private final Set<Integer> holdout=new TreeSet<>();
 private final List<EvaluationGate.Episode> baselineResults=new ArrayList<>(),candidateResults=new ArrayList<>();
 private LearningCoordinator.Candidate candidate;
 private Policy active;
 private boolean running;
 private boolean episodeFinished;
 private long generation;
 private long observationAt;
 private int episodeIndex,steps,phase;
 private double totalReturn;
 private String episodeId;
 private EnvironmentAdapter.Observation observation;
 public HarnessRuntime(HarnessCoordinator owner,EnvironmentAdapter environment,ExperienceStore store,
                       PolicyRepository policies,Host host){
  this.owner=owner;this.environment=environment;this.store=store;this.policies=policies;this.host=host;
  for(int i=10001;i<=10020;i++)holdout.add(i);
 }
 public boolean running(){return running;}
 public void stop(String reason){
  boolean wasRunning=running;
  generation++;running=false;owner.stop(reason);owner.models().cancel();
  try{environment.stop();}catch(RuntimeException failure){reason+="; environment stop error: "+failure.getClass().getSimpleName();}
  if(wasRunning&&!episodeFinished&&episodeId!=null){
   try{store.truncate(episodeId,reason);}
   catch(java.io.IOException failure){reason+="; episode-end NOT SAVED: "+failure.getMessage();}
  }
  host.status(reason);
 }
 public void collect(){start(0);}
 public void trainAndEvaluate(){start(1);}
 public void play(){start(3);}
 public void playRealtime(){start(4);}
 public void rollback(){
  if(running)throw new IllegalStateException("STOP before rollback");
  try{Policy old=policies.rollback();host.status("Откат: policy "+old.sha256().substring(0,16));}
  catch(Exception error){host.status("Откат не выполнен: "+error.getMessage());}
 }
 private void start(int next){
  if(running||owner.actionInFlight()||owner.hasPending())throw new IllegalStateException("STOP active owner first");
  try{
   active=policies.active();
   if(next==1){
    candidate=new LearningCoordinator().train(active,store.readAll(),holdout);
    new LearningCoordinator().saveCandidate(policies,candidate);
    baselineResults.clear();candidateResults.clear();
   }
   phase=next;episodeIndex=0;running=true;generation++;beginEpisode();
  }catch(Exception error){stop("STOP: "+error.getMessage());}
 }
 private int seed(){return phase==1||phase==2?10001+episodeIndex:random.nextInt(9000);}
 private Policy currentPolicy(){return phase==2?candidate.policy():active;}
 private void beginEpisode(){
  if(!running)return;
  final long current=generation;
  steps=0;totalReturn=0;
  episodeFinished=false;
  episodeId=(phase==0?"train-":phase>=3?"play-":"eval-")+UUID.randomUUID();
  environment.reset(episodeId,seed(),(value,error)->{
   if(!running||current!=generation)return;
   if(error!=null||value==null){stop("STOP: NES reset "+error);return;}
   if(phase==4){
    environment.mode(HarnessCoordinator.Mode.REALTIME_MODE,(live,failure)->{
     if(!running||current!=generation)return;
     if(failure!=null||live==null){stop("STOP: REALTIME mode unavailable "+failure);return;}
     ready(live);
    });
   }else ready(value);
  });
 }
 private void ready(EnvironmentAdapter.Observation value){
  observation=value;
  observationAt=host.now();
  if(!value.verified()){stop("STOP: task observation UNKNOWN; no policy input authorized");return;}
  host.status((phase==0?"Сбор REAL_JSNES":phase>=3?"Policy играет":"Независимая оценка")+
    " · "+value.mode()+" · цель X="+value.targetX()+" · эпизод "+(episodeIndex+1)+
    " · policy "+currentPolicy().sha256().substring(0,12));
  host.post(this::step);
 }
 private void step(){
  if(!running)return;
  final long current=generation;
  var before=observation;
  Policy policy=currentPolicy();
  int proposed;
  try{proposed=owner.models().policyAction(policy,before);}
  catch(RuntimeException failure){stop("STOP: model unavailable: "+failure.getMessage());return;}
  if(phase==0&&random.nextDouble()<0.6)proposed=random.nextInt(2);
  // Task-specific bounds prevent wrap/clipping from becoming a false reward signal.
  if(before.x()<=32)proposed=1;else if(before.x()>=208)proposed=0;
  final int action=proposed;
  final HarnessCoordinator.ActionPermit permit=owner.agentController().policy(before,action,observationAt,host.now());
  if(permit==null){stop("STOP: action permit rejected");return;}
  environment.step(permit,(after,error)->{
   if(!running||current!=generation)return;
   if(error!=null||after==null||!owner.complete(permit,after.epoch(),after.frame(),after.pixelHash(),true,true)){
    stop("STOP: action/observation unverified "+error);return;
   }
   try{
    steps++;
    var transition=ExperienceStore.assess(before,after,action,steps>=16||!after.verified(),policy.sha256());
    store.append(transition);
    // Always record unknown reward, then stop; it cannot enter the optimizer or evaluation.
    if(transition.reward()==null){stop("STOP: reward UNKNOWN; transition saved, RL excluded");return;}
    totalReturn+=transition.reward();observation=after;observationAt=host.now();
    if(transition.terminated()||transition.truncated())finishEpisode(transition.terminated());
    else host.post(this::step);
   }catch(Exception failure){stop("STOP: replay not saved: "+failure.getMessage());}
  });
 }
 private void finishEpisode(boolean completed){
  episodeFinished=true;
  if(phase==1||phase==2){
   var result=new EvaluationGate.Episode(episodeId,observation.seed(),currentPolicy().sha256(),
     totalReturn,completed,0,true);
   (phase==1?baselineResults:candidateResults).add(result);
  }
  episodeIndex++;
  if(episodeIndex<(phase==0?32:phase>=3?1:20)){host.post(this::beginEpisode);return;}
  if(phase==1){phase=2;episodeIndex=0;host.post(this::beginEpisode);return;}
  if(phase==2){
   try{
    var report=policies.apply(candidate,baselineResults,candidateResults);
    stop((report.promote()?"POLICY PROMOTED: ":"POLICY RETAINED: ")+report.reason()+
      " · median "+report.baselineMedian()+" → "+report.candidateMedian()+
      " · updates="+candidate.updates()+" · candidate="+candidate.policy().sha256().substring(0,16));
   }catch(Exception error){stop("STOP: promotion failed: "+error.getMessage());}
  }else stop(phase==0?"Реальные переходы сохранены. Обучение ещё не запускалось.":"Policy episode complete; result recorded");
 }
}
