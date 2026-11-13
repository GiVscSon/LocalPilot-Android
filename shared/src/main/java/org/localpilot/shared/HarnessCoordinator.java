package org.localpilot.shared;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * The sole permit issuer for one embedded NES session. Models only propose;
 * this class never presses a button or computes a game reward.
 *
 * The Android NES view owns a single process-wide instance and routes manual,
 * scripted and GGUF input through it. A prepared model action cannot be
 * preempted by another actor. Stale proposals always fail closed.
 */
public final class HarnessCoordinator {
 public enum Actor { MANUAL, HEURISTIC, GGUF, STUDENT, PPO_STUDENT }
 public enum Mode { STEP_MODE, REALTIME_MODE }
 /** Immutable read-only dashboard projection. This grants no input authority. */
 public record Snapshot(String epoch,String mode,String owner,boolean waiting,
   boolean actionInFlight,long stopRevision,String lastStopReason) {}

 public static final class Ticket {
  public final String nonce,epoch;
  public final int frame,hash;
  public final long issuedAt,stopRevision;
  private Ticket(String nonce,String epoch,int frame,int hash,long issuedAt,long revision){
   this.nonce=nonce;this.epoch=epoch;this.frame=frame;this.hash=hash;
   this.issuedAt=issuedAt;this.stopRevision=revision;
  }
 }
 public static final class ActionPermit {
  public final Actor actor;
  public final Mode mode;
  public final String epoch,button,nonce;
  public final int beforeFrame,beforeHash,holdFrames;
  public final long stopRevision,issuedAt;
  private ActionPermit(Actor actor,Mode mode,String epoch,String button,
    int beforeFrame,int beforeHash,int holdFrames,long stopRevision,String nonce,long issuedAt){
   this.actor=actor;this.mode=mode;this.epoch=epoch;this.button=button;
   this.beforeFrame=beforeFrame;this.beforeHash=beforeHash;
   this.holdFrames=holdFrames;this.stopRevision=stopRevision;
   this.nonce=nonce;this.issuedAt=issuedAt;
  }
 }
 /** A task/weights-bound reservation spans observation and between-step gaps. */
 public static final String PPO_TASK="builtin-homebrew-right-progress-v1";
 public static final class EpisodeLease {
  public final String epoch,task,policySha;
  private final long revision;
  private int remaining;
  private EpisodeLease(String epoch,String task,String sha,long revision,int steps){
   this.epoch=epoch;this.task=task;policySha=sha;this.revision=revision;remaining=steps;
  }
 }
 private EpisodeLease episode;
 public synchronized EpisodeLease reserveEpisode(String session,String task,String sha,int steps){
  if(stopPending||!matches(session)||episode!=null||waiting!=null||prepared!=null||active!=null||
     !PPO_TASK.equals(task)||sha==null||!sha.matches("[0-9a-f]{64}")||steps<1||steps>16)return null;
  episode=new EpisodeLease(epoch,task,sha,stopRevision,steps);return episode;
 }
 public synchronized boolean ownsEpisode(EpisodeLease lease){
  return lease!=null&&lease==episode&&!stopPending&&matches(lease.epoch)&&lease.revision==stopRevision;
 }
 public synchronized boolean changeEpisodeMode(EpisodeLease lease){
  if(!ownsEpisode(lease)||waiting!=null||prepared!=null||active!=null)return false;
  mode=Mode.STEP_MODE;return true;
 }
 public synchronized Ticket beginEpisodeObservation(EpisodeLease lease,int frame,int hash,long now){
  if(!ownsEpisode(lease)||lease.remaining<=0||mode!=Mode.STEP_MODE)
   throw new IllegalStateException("Expired episode reservation");
  return beginObservation(lease.epoch,frame,hash,now);
 }
 public synchronized ActionPermit startEpisodeStep(EpisodeLease lease,String sha,String button,
     int frame,int hash,long now){
  if(!ownsEpisode(lease)||!lease.policySha.equals(sha)||lease.remaining<=0||mode!=Mode.STEP_MODE)return null;
  ActionPermit result=issue(Actor.PPO_STUDENT,lease.epoch,button,frame,hash,16,now);
  if(result!=null)--lease.remaining;
  return result;
 }
 public synchronized boolean finishEpisode(EpisodeLease lease){
  if(!ownsEpisode(lease)||lease.remaining!=0||waiting!=null||prepared!=null||active!=null)return false;
  episode=null;return true;
 }
 private static final HarnessCoordinator PROCESS_NES=new HarnessCoordinator();
 public static HarnessCoordinator nes(){return PROCESS_NES;}

 private final SecureRandom random=new SecureRandom();
 private String epoch="";
 private Mode mode=Mode.REALTIME_MODE;
 private Ticket waiting,prepared;
 private ActionPermit active;
 private long stopRevision;
 private boolean stopPending;
 private String stopReason="NOT_STARTED";

 public synchronized void reset(String newEpoch,Mode newMode){
  stop("ROM_RESET");
  if(newEpoch==null||!newEpoch.matches("[1-9][0-9]{0,18}")||newMode==null)
   throw new IllegalArgumentException("Invalid emulator reset");
  epoch=newEpoch;mode=newMode;stopPending=false;
 }
 public synchronized Ticket begin(String expectedEpoch,int frame,int hash,long now){
  if(episode!=null)throw new IllegalStateException("Episode owns controller");
  return beginObservation(expectedEpoch,frame,hash,now);
 }
 private Ticket beginObservation(String expectedEpoch,int frame,int hash,long now){
  if(stopPending||!matches(expectedEpoch)||waiting!=null||prepared!=null||active!=null||
     frame<15||hash==0||now<0)
   throw new IllegalStateException("Another NES input owner or unready observation");
  byte[] bytes=new byte[16];random.nextBytes(bytes);
  waiting=new Ticket(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
    epoch,frame,hash,now,stopRevision);
  return waiting;
 }
 /** Correlates a timeout/reply with the exact still-pending observation.
  * An old timer must not cancel a later step of the same reserved episode. */
 public synchronized boolean awaiting(Ticket ticket){
  return ticket!=null&&waiting==ticket&&!stopPending&&matches(ticket.epoch)&&
    ticket.stopRevision==stopRevision;
 }
 public synchronized boolean changeMode(String expectedEpoch,Mode next){
  if(stopPending||episode!=null||!matches(expectedEpoch)||next==null||waiting!=null||
     prepared!=null||active!=null)return false;
  mode=next;return true;
 }
 public synchronized Snapshot snapshot(){
  return new Snapshot(epoch,mode.name(),
    active==null?(episode==null?"NONE":"PPO_STUDENT"):active.actor.name(),
    stopPending||episode!=null||waiting!=null||prepared!=null,active!=null,stopRevision,stopReason);
 }
 public synchronized boolean hasPending(){return stopPending||episode!=null||waiting!=null||prepared!=null;}
 public synchronized boolean actionInFlight(){return active!=null;}
 public synchronized long stopRevision(){return stopRevision;}
 public synchronized String lastStopReason(){return stopReason;}

 /**
  * Every model response consumes its one-use observation. Approval is not
  * input execution: start() must still recheck the same frame and STOP epoch.
  */
 public synchronized boolean consume(String nonce,String expectedEpoch,
                                     int currentFrame,int currentHash,long now){
  Ticket old=waiting;
  if(old==null||!old.nonce.equals(nonce)||!matches(expectedEpoch)||!old.epoch.equals(expectedEpoch))
   return false; // A stale reply never consumes somebody else's observation.
  waiting=null;
  if(old==null||!ConsoleAgentContract.tokenValid(nonce)||
      !old.nonce.equals(nonce)||!matches(expectedEpoch)||
      !old.epoch.equals(expectedEpoch)||old.frame!=currentFrame||
      old.hash!=currentHash||old.stopRevision!=stopRevision||
      now<old.issuedAt||now-old.issuedAt>ConsoleAgentContract.TTL_MS||
      active!=null||prepared!=null){
   stop("STALE_OR_INVALID_PROPOSAL");
   return false;
  }
  prepared=old;
  return true;
 }
 /**
  * A unique action permit is required before WebView dispatch. A GGUF action
  * must consume a matching prepared ticket; manual and baseline inputs are
  * blocked while a model owns the observation.
  */
 public synchronized ActionPermit start(Actor actor,String expectedEpoch,
      String button,int frame,int hash,int holdFrames,long now){
  if(episode!=null||actor==Actor.PPO_STUDENT)return null;
  return issue(actor,expectedEpoch,button,frame,hash,holdFrames,now);
 }
 private ActionPermit issue(Actor actor,String expectedEpoch,String button,int frame,int hash,int holdFrames,long now){
  if(stopPending||actor==null||!matches(expectedEpoch)||active!=null||waiting!=null||
      !(actor==Actor.MANUAL?ConsoleAgentContract.manualPlayable(button):ConsoleAgentContract.playable(button))||frame<15||hash==0||now<0)
   return null;
  if(actor==Actor.GGUF||actor==Actor.STUDENT||actor==Actor.PPO_STUDENT){
   Ticket t=prepared;
   if(t!=null&&(now<t.issuedAt||now-t.issuedAt>ConsoleAgentContract.TTL_MS)){
    prepared=null;return null;
   }
   if(t==null||!t.epoch.equals(expectedEpoch)||t.frame!=frame||
      t.hash!=hash||t.stopRevision!=stopRevision||
      now<t.issuedAt||now-t.issuedAt>ConsoleAgentContract.TTL_MS||
      (actor==Actor.STUDENT?
       mode!=Mode.STEP_MODE||!ConsoleAgentContract.studentFramesValid(holdFrames):
       holdFrames!=ConsoleAgentContract.HOLD_FRAMES))return null;
   prepared=null;
  }else{
   if(prepared!=null)return null;
   if(actor==Actor.MANUAL ? holdFrames!=0 : holdFrames<4||holdFrames>60)
    return null;
  }
  byte[] nonce=new byte[16];random.nextBytes(nonce);
  active=new ActionPermit(actor,mode,epoch,button,frame,hash,holdFrames,stopRevision,
    Base64.getUrlEncoder().withoutPadding().encodeToString(nonce),now);
  return active;
 }
 /**
  * Returns transport/observation validity ONLY. No score, win, training label
  * or positive reward can be inferred from this outcome or changed pixels.
  */
 /** Synchronous WebView lease fence. A core frame already authorized may finish. */
 public synchronized boolean authorizesStep(String session,String nonce,int requestedFrames,long now){
  return !stopPending&&active!=null&&active.mode==Mode.STEP_MODE&&
    (active.actor==Actor.GGUF||active.actor==Actor.STUDENT||active.actor==Actor.PPO_STUDENT)&&matches(session)&&
    active.epoch.equals(session)&&active.nonce.equals(nonce)&&active.holdFrames==requestedFrames&&
    active.stopRevision==stopRevision&&now>=active.issuedAt&&
    now-active.issuedAt<=ConsoleAgentContract.TTL_MS;
 }
 public synchronized long requestStop(String session,String reason){
  if(!matches(session))return -1;
  stop(reason);stopPending=true;return stopRevision;
 }
 public synchronized boolean confirmStop(String session,long revision){
  if(!stopPending||!matches(session)||revision!=stopRevision)return false;
  stopPending=false;return true;
 }
 public synchronized boolean stopAwaitingConfirmation(){return stopPending;}
 public synchronized boolean complete(ActionPermit permit,String expectedEpoch,
      int afterFrame,int afterHash,boolean downAck,boolean upAck,long now){
  // A callback from a previous owner can never revoke a newer owner's lease.
  if(permit==null||permit!=active||!matches(expectedEpoch))return false;
  long delta=(long)afterFrame-permit.beforeFrame;
  boolean exact=permit.mode==Mode.STEP_MODE&&
    (permit.actor==Actor.GGUF||permit.actor==Actor.STUDENT||permit.actor==Actor.PPO_STUDENT);
  if(stopPending||permit.stopRevision!=stopRevision||!downAck||!upAck||
      now<permit.issuedAt||now-permit.issuedAt>ConsoleAgentContract.TTL_MS||
      afterFrame<0||delta<(permit.holdFrames>0?permit.holdFrames:15)||
      exact&&delta!=permit.holdFrames||delta>600||afterHash==0){
   stop("UNVERIFIED_TRANSITION");return false;
  }
  active=null;return true;
 }
 public synchronized boolean stopForEpoch(String session,String reason){
  if(!matches(session))return false;
  stop(reason);return true;
 }
 public synchronized void stop(){stop("STOP");}
 public synchronized void stop(String reason){
  waiting=null;prepared=null;active=null;episode=null;
  if(!stopPending)++stopRevision;
  stopReason=reason==null||reason.isBlank()?"STOP":reason;
 }
 private boolean matches(String expectedEpoch){
  return expectedEpoch!=null&&!epoch.isEmpty()&&epoch.equals(expectedEpoch);
 }
}
