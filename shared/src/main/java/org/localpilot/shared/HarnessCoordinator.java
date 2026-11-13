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
 public enum Actor { MANUAL, HEURISTIC, GGUF }
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
  public final String epoch,button;
  public final int beforeFrame,beforeHash,holdFrames;
  public final long stopRevision;
  private ActionPermit(Actor actor,Mode mode,String epoch,String button,
    int beforeFrame,int beforeHash,int holdFrames,long stopRevision){
   this.actor=actor;this.mode=mode;this.epoch=epoch;this.button=button;
   this.beforeFrame=beforeFrame;this.beforeHash=beforeHash;
   this.holdFrames=holdFrames;this.stopRevision=stopRevision;
  }
 }
 private static final HarnessCoordinator PROCESS_NES=new HarnessCoordinator();
 public static HarnessCoordinator nes(){return PROCESS_NES;}

 private final SecureRandom random=new SecureRandom();
 private String epoch="";
 private Mode mode=Mode.REALTIME_MODE;
 private Ticket waiting,prepared;
 private ActionPermit active;
 private long stopRevision;
 private String stopReason="NOT_STARTED";

 public synchronized void reset(String newEpoch,Mode newMode){
  stop("ROM_RESET");
  if(newEpoch==null||!newEpoch.matches("[1-9][0-9]{0,18}")||newMode==null)
   throw new IllegalArgumentException("Invalid emulator reset");
  epoch=newEpoch;mode=newMode;
 }
 public synchronized Ticket begin(String expectedEpoch,int frame,int hash,long now){
  if(!matches(expectedEpoch)||waiting!=null||prepared!=null||active!=null||
     frame<15||hash==0||now<0)
   throw new IllegalStateException("Another NES input owner or unready observation");
  byte[] bytes=new byte[16];random.nextBytes(bytes);
  waiting=new Ticket(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
    epoch,frame,hash,now,stopRevision);
  return waiting;
 }
 public synchronized boolean changeMode(String expectedEpoch,Mode next){
  if(!matches(expectedEpoch)||next==null||waiting!=null||
     prepared!=null||active!=null)return false;
  mode=next;return true;
 }
 public synchronized Snapshot snapshot(){
  return new Snapshot(epoch,mode.name(),
    active==null?"NONE":active.actor.name(),
    waiting!=null||prepared!=null,active!=null,stopRevision,stopReason);
 }
 public synchronized boolean hasPending(){return waiting!=null||prepared!=null;}
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
  if(actor==null||!matches(expectedEpoch)||active!=null||waiting!=null||
      !ConsoleAgentContract.playable(button)||frame<15||hash==0||now<0)
   return null;
  if(actor==Actor.GGUF){
   Ticket t=prepared;
   if(t==null||!t.epoch.equals(expectedEpoch)||t.frame!=frame||
      t.hash!=hash||t.stopRevision!=stopRevision||
      now<t.issuedAt||now-t.issuedAt>ConsoleAgentContract.TTL_MS||
      holdFrames!=ConsoleAgentContract.HOLD_FRAMES)return null;
   prepared=null;
  }else{
   if(prepared!=null)return null;
   if(actor==Actor.MANUAL ? holdFrames!=0 : holdFrames<4||holdFrames>60)
    return null;
  }
  active=new ActionPermit(actor,mode,epoch,button,frame,hash,holdFrames,stopRevision);
  return active;
 }
 /**
  * Returns transport/observation validity ONLY. No score, win, training label
  * or positive reward can be inferred from this outcome or changed pixels.
  */
 public synchronized boolean complete(ActionPermit permit,String expectedEpoch,
      int afterFrame,int afterHash,boolean downAck,boolean upAck){
  if(permit==null||permit!=active||!matches(expectedEpoch)||
      permit.stopRevision!=stopRevision||!downAck||!upAck||
      afterFrame-permit.beforeFrame<15||afterHash==0){
   stop("UNVERIFIED_TRANSITION");
   return false;
  }
  active=null;
  return true;
 }
 public synchronized void stop(){stop("STOP");}
 public synchronized void stop(String reason){
  waiting=null;prepared=null;active=null;
  ++stopRevision;
  stopReason=reason==null||reason.isBlank()?"STOP":reason;
 }
 private boolean matches(String expectedEpoch){
  return expectedEpoch!=null&&!epoch.isEmpty()&&epoch.equals(expectedEpoch);
 }
}
