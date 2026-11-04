package org.localpilot.android;

import java.util.Arrays;

/** Fresh-frame gate for a grid puzzle. A changed board is a response, not a win. */
public final class GridPuzzleSession {
 public enum Status { WAIT_STABLE, READY, WAIT_RESPONSE, CHANGED, PAUSED }
 private static final long FRESH_MS=300, STABLE_MS=80, SETTLE_MS=180;
 private final long responseTimeoutMs,finalTimeoutMs;
 private boolean lateWaiting,lateRecoveryRevoked,unchangedFailure;
 private java.util.BitSet beforeSpecial;
 private Status status=Status.WAIT_STABLE;
 private int rows,cols;
 private java.util.BitSet candidateSpecial,stableSpecial;
 private int stableReads;
 private int[] candidate,stable,before;
 private long candidateAt=-1,lastFrameAt=-1,dispatchedAt=-1,completedAt=-1;
 private int verifiedChanges;
 private boolean partialPending;
 private java.util.BitSet predictedImpact;
 private int swapFrom=-1,swapTo=-1;

 public GridPuzzleSession(){this(2500);}
 public GridPuzzleSession(long responseTimeoutMs){this(responseTimeoutMs,responseTimeoutMs);}
 /** Final deadline is absolute from dispatch, never renewed by frames or callbacks. */
 public GridPuzzleSession(long responseTimeoutMs,long finalTimeoutMs){
  if(responseTimeoutMs<1000||responseTimeoutMs>10000||finalTimeoutMs<responseTimeoutMs||finalTimeoutMs>15000)throw new IllegalArgumentException("response timeout outside 1000..10000 ms or invalid final deadline");
  this.responseTimeoutMs=responseTimeoutMs;this.finalTimeoutMs=finalTimeoutMs;
 }
 public void reset(){status=Status.WAIT_STABLE;rows=cols=0;candidate=stable=before=null;stableSpecial=null;stableReads=0;candidateAt=lastFrameAt=dispatchedAt=completedAt=-1;verifiedChanges=0;partialPending=false;predictedImpact=null;swapFrom=swapTo=-1;lateWaiting=lateRecoveryRevoked=unchangedFailure=false;beforeSpecial=null;}
 /** Invalidate pre-focus observations without forgetting an unresolved gesture. */
 public void invalidateCandidate(){clearCandidate();stable=null;if(pending())lateRecoveryRevoked=true;
  if(lateWaiting){lateWaiting=false;status=Status.PAUSED;}
  if(!paused())status=pending()?Status.WAIT_RESPONSE:Status.WAIT_STABLE;
 }
 public Status status(){return status;}
 public boolean pending(){return before!=null;}
 public boolean acknowledged(){return pending()&&completedAt>=0;}
 public boolean paused(){return status==Status.PAUSED||lateWaiting;}
 public boolean awaitingLateVerification(){return lateWaiting;}
 public boolean finalPaused(){return paused()&&!lateWaiting;}
 /** Only a final timeout with two fresh unchanged full frames supplies this evidence. */
 public boolean unchangedAtTimeout(){return unchangedFailure;}
 public int verifiedChanges(){return verifiedChanges;}
 /** A partial planner must use the conservative intersection of two reads. */
 public int[] stableCells(){return (status==Status.READY||status==Status.CHANGED)&&stable!=null?stable.clone():null;}
 public java.util.BitSet stableSpecialCells(){return stableSpecial==null?new java.util.BitSet():(java.util.BitSet)stableSpecial.clone();}
 public int stableReads(){return stableReads;}

 public Status observe(int nextRows,int nextCols,int[] cells,long frameAt,long now){return observe(nextRows,nextCols,cells,new java.util.BitSet(),frameAt,now);}
 public Status observe(int nextRows,int nextCols,int[] cells,java.util.BitSet special,long frameAt,long now){
  return observeCore(nextRows,nextCols,cells,special,frameAt,now,false);
 }
 /** Opt-in partial read; the caller must first authenticate this as a game board.
  * Unlike observe(), this accepts -1 as an unknown obstacle, not as a piece.
  */
 public Status observePartial(int nextRows,int nextCols,int[] cells,java.util.BitSet special,long frameAt,long now){
  return observeCore(nextRows,nextCols,cells,special,frameAt,now,true);
 }
 private Status observeCore(int nextRows,int nextCols,int[] cells,java.util.BitSet special,long frameAt,long now,boolean allowPartial){
  if(special==null||special.length()>nextRows*nextCols)throw new IllegalArgumentException("special mask outside board");
  if(finalPaused())return status;
  // Even invalid frames cannot extend either absolute deadline.
  if(pending()&&now>=dispatchedAt&&now-dispatchedAt>=finalTimeoutMs){
   unchangedFailure=unchangedEvidence(nextRows,nextCols,cells,special,frameAt,now);
   lateWaiting=false;clearCandidate();status=Status.PAUSED;return status;
  }
  if(pending()&&!lateWaiting&&now>=dispatchedAt&&now-dispatchedAt>=responseTimeoutMs){
   lateWaiting=finalTimeoutMs>responseTimeoutMs&&!lateRecoveryRevoked&&acknowledged()&&predictedImpact!=null&&!partialPending;
   unchangedFailure=!lateWaiting&&unchangedEvidence(nextRows,nextCols,cells,special,frameAt,now);
   clearCandidate();status=Status.PAUSED;return status;
  }
  if(frameAt<0||now<frameAt||now-frameAt>FRESH_MS||frameAt<=lastFrameAt){return waitAgain();}
  lastFrameAt=frameAt;
  if(lateWaiting&&!complete(nextRows,nextCols,cells))return waitAgain();
  if(!(complete(nextRows,nextCols,cells)||(allowPartial&&validPartial(nextRows,nextCols,cells)))
      ||pending()&&(nextRows!=rows||nextCols!=cols))return waitAgain();
  if(!pending()&&(nextRows!=rows||nextCols!=cols)){rows=nextRows;cols=nextCols;stable=null;clearCandidate();}
  if(pending()&&(completedAt<0||frameAt<=completedAt||frameAt-dispatchedAt<SETTLE_MS))return waitAgain();
  if(candidate==null){beginCandidate(cells,special,frameAt);status=waitingStatus();return status;}
  // Partial pre-move reads may alternate between known and hidden cells.
  // Keep only types independently observed in both frames. Known-to-known
  // disagreements always reset stability. Post-ACK stays exact-match only.
  if(!Arrays.equals(candidate,cells)||!special.equals(candidateSpecial)){
   if(pending()||!allowPartial||!intersectPartial(cells,special)){
    beginCandidate(cells,special,frameAt);status=waitingStatus();return status;
   }
  }
  stableReads++;
  if(frameAt-candidateAt<STABLE_MS){status=waitingStatus();return status;}
  if(pending()){
   if(Arrays.equals(before,cells)){status=waitingStatus();return status;}
   // A newly recognized UNKNOWN or a moving hint is not game progress.
   // Partial snapshots and action-bound verification require at least two
   // known-to-known changes on two distinct stable post-ACK frames.
   // A swap must also affect a predicted cell beyond its endpoints.
   if(partialPending||hasUnknown(cells)||predictedImpact!=null){
    int shared=0,changed=0;
    for(int i=0;i<cells.length;i++)if(before[i]>=0&&cells[i]>=0){
     shared++;if(before[i]!=cells[i])changed++;
    }
    boolean impacted=predictedImpact==null;
    if(predictedImpact!=null)for(int index=predictedImpact.nextSetBit(0);index>=0;index=predictedImpact.nextSetBit(index+1)){
     if(index!=swapFrom&&index!=swapTo&&before[index]>=0&&cells[index]>=0&&before[index]!=cells[index]){impacted=true;break;}
    }
    if(shared<Math.max(6,(cells.length+1)/2)||changed<2||!impacted){
     status=waitingStatus();return status;
    }
   }
   lateWaiting=false;unchangedFailure=false;beforeSpecial=null;stable=cells.clone();stableSpecial=(java.util.BitSet)special.clone();before=null;partialPending=false;predictedImpact=null;swapFrom=swapTo=-1;dispatchedAt=completedAt=-1;verifiedChanges++;status=Status.CHANGED;return status;
  }
  if(!complete(nextRows,nextCols,candidate)&&!validPartial(nextRows,nextCols,candidate)){
   clearCandidate();status=Status.WAIT_STABLE;return status;
  }
  stable=candidate.clone();stableSpecial=(java.util.BitSet)candidateSpecial.clone();status=Status.READY;return status;
 }

 /** Call when dispatching the action selected from the latest stable board. */
 public void sent(int[] snapshot,long at){sent(snapshot,at,null);}
 /**
  * Optional immediate-impact proof: for incomplete fields, an unrelated
  * animation elsewhere must not validate an otherwise unobserved gesture.
  */
 public void sent(int[] snapshot,long at,GridPuzzlePolicy.Decision action){
  if(paused()||pending()||(status!=Status.READY&&status!=Status.CHANGED)||stable==null||!Arrays.equals(stable,snapshot)||at<lastFrameAt||at-lastFrameAt>FRESH_MS)
   throw new IllegalStateException("a fresh stable board is required before dispatch");
  java.util.BitSet impact=null;
  if(action!=null){
   impact=new java.util.BitSet(snapshot.length);
   int from=action.fromRow*cols+action.fromCol,to=action.toRow*cols+action.toCol;
   if(action.fromRow<0||action.fromRow>=rows||action.toRow<0||action.toRow>=rows
     ||action.fromCol<0||action.fromCol>=cols||action.toCol<0||action.toCol>=cols
     ||snapshot[from]<0||snapshot[to]<0)throw new IllegalArgumentException("action outside known grid");
   impact.set(from);impact.set(to);
   for(int index:action.clearedCells()){
    if(index<0||index>=snapshot.length||snapshot[index]<0)throw new IllegalArgumentException("predicted clear unknown");
    impact.set(index);
   }
  }
  before=stable.clone();beforeSpecial=stableSpecial==null?new java.util.BitSet():(java.util.BitSet)stableSpecial.clone();partialPending=hasUnknown(stable);predictedImpact=impact;lateWaiting=lateRecoveryRevoked=unchangedFailure=false;
  swapFrom=action!=null&&action.mode==GridPuzzlePolicy.Mode.SWAP_MATCH3?action.fromRow*cols+action.fromCol:-1;
  swapTo=action!=null&&action.mode==GridPuzzlePolicy.Mode.SWAP_MATCH3?action.toRow*cols+action.toCol:-1;
  dispatchedAt=at;completedAt=-1;clearCandidate();status=Status.WAIT_RESPONSE;
 }
 /** Android completion only opens response observation; it never counts a success. */
 public void acknowledge(long at){
  if(!pending()||paused())return;
  if(at<dispatchedAt)throw new IllegalArgumentException("completion precedes dispatch");
  if(completedAt<0){completedAt=at;clearCandidate();}
 }
 public String report(){return "GRID_SESSION: status="+status+" pending="+pending()+" android_ack="+(completedAt>=0)+" visual_changes="+verifiedChanges+" response_timeout_ms="+responseTimeoutMs+" final_timeout_ms="+finalTimeoutMs+" late_wait="+lateWaiting+" unchanged_failure="+unchangedFailure+" stableReads="+stableReads+" post_ack_two_frames="+(completedAt>=0&&stableReads>=2);}
 private Status waitingStatus(){return lateWaiting?Status.PAUSED:pending()?Status.WAIT_RESPONSE:Status.WAIT_STABLE;}
 private Status waitAgain(){clearCandidate();status=waitingStatus();return status;}
 private boolean unchangedEvidence(int nextRows,int nextCols,int[] cells,java.util.BitSet special,long frameAt,long now){
  return acknowledged()&&candidate!=null&&beforeSpecial!=null&&nextRows==rows&&nextCols==cols&&complete(nextRows,nextCols,cells)
   &&frameAt>lastFrameAt&&frameAt>completedAt&&now>=frameAt&&now-frameAt<=FRESH_MS
   &&candidateAt>=completedAt&&frameAt-candidateAt>=STABLE_MS
   &&Arrays.equals(before,cells)&&Arrays.equals(candidate,cells)&&beforeSpecial.equals(special)&&special.equals(candidateSpecial);
 }
 private void beginCandidate(int[] cells,java.util.BitSet special,long at){
  candidate=cells.clone();candidateSpecial=(java.util.BitSet)special.clone();candidateAt=at;stableReads=1;
 }
 private boolean intersectPartial(int[] cells,java.util.BitSet special){
  if(candidate.length!=cells.length)return false;
  int[] safe=candidate.clone();
  java.util.BitSet protectedCells=(java.util.BitSet)candidateSpecial.clone();
  for(int i=0;i<safe.length;i++){
   if(safe[i]>=0&&cells[i]>=0){
    if(safe[i]!=cells[i]||candidateSpecial.get(i)!=special.get(i))return false;
   }else{safe[i]=-1;protectedCells.set(i);}
  }
  if(!validPartial(rows,cols,safe))return false;
  candidate=safe;candidateSpecial=protectedCells;return true;
 }
 private void clearCandidate(){candidate=null;candidateSpecial=null;candidateAt=-1;stableReads=0;}
 private static boolean hasUnknown(int[] cells){for(int cell:cells)if(cell<0)return true;return false;}
 private static boolean validPartial(int rows,int cols,int[] cells){
  if(rows<3||rows>10||cols<3||cols>10||cells==null||cells.length!=rows*cols)return false;
  int known=0;for(int cell:cells){if(cell< -1)return false;if(cell>=0)known++;}
  return known>=Math.max(6,(cells.length*72+99)/100);
 }
 private static boolean complete(int rows,int cols,int[] cells){
  if(rows<3||rows>10||cols<3||cols>10||cells==null||cells.length!=rows*cols)return false;
  for(int cell:cells)if(cell<0)return false;
  return true;
 }
}
