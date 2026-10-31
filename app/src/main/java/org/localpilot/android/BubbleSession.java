package org.localpilot.android;

import java.util.Arrays;

/** ACK opens observation only; a shot is verified against its predicted normal-bubble board. */
public final class BubbleSession {
 public enum Status { SEARCHING, READY, WAIT_RESPONSE, VERIFIED, PAUSED }
 /** Fixed-size metadata; reasons describe the branch actually taken, never a guessed game outcome. */
 public enum Reason { RESET, SENT, ANDROID_ACK, NULL_SCENE, STALE, BEFORE_ACK, SETTLING, UNSTABLE, GEOMETRY_MISMATCH, CELLS_MISMATCH, EXPIRED, READY, VERIFIED, CONTEXT_INVALIDATED, DISPATCH_NOT_SENT }
 private static final int VISION_REASON_LIMIT=120;
 private final long responseTimeoutMs,finalTimeoutMs;
 private boolean lateWaiting,lateRecoveryRevoked;
 /** Legacy deadline: expiry is final unless a longer final deadline is opted in. */
 public BubbleSession(){this(7000,7000);}
 /** Both deadlines are measured from dispatch; ACK and later frames never extend them. */
 public BubbleSession(long responseTimeoutMs,long finalTimeoutMs){
  if(responseTimeoutMs<1000||responseTimeoutMs>10000||finalTimeoutMs<responseTimeoutMs||finalTimeoutMs>15000)
   throw new IllegalArgumentException("bubble response deadlines");
  this.responseTimeoutMs=responseTimeoutMs;this.finalTimeoutMs=finalTimeoutMs;
 }
 private BubbleBoard candidate,stable,before;
 private int[] expected;
 private long candidateAt=-1,lastFrame=-1,sentAt=-1,ackAt=-1;
 private int verified;private Status status=Status.SEARCHING;
 private Reason reason=Reason.RESET,lastWait=Reason.RESET;
 private final long[] reasonCounts=new long[Reason.values().length];
 private long observations,frameAge=-1,sentAge=-1,ackAge=-1,candidateAge=-1;
 private int observedCells=-1,expectedCells=-1,mismatchedCells=-1;
 private String visionReason="none";
 public void reset(){candidate=stable=before=null;expected=null;candidateAt=lastFrame=sentAt=ackAt=-1;verified=0;status=Status.SEARCHING;lateWaiting=lateRecoveryRevoked=false;
  reason=lastWait=Reason.RESET;Arrays.fill(reasonCounts,0);observations=0;frameAge=sentAge=ackAge=candidateAge=-1;observedCells=expectedCells=mismatchedCells=-1;visionReason="none";}
 public boolean pending(){return before!=null;}
 public boolean paused(){return status==Status.PAUSED;}
 /** A soft pause may only accept exact visual proof for the still-pending shot. */
 public boolean awaitingLateVerification(){return paused()&&lateWaiting;}
 public boolean finalPaused(){return paused()&&!lateWaiting;}
 /** Only a final natural timeout is evidence of an unverified shot.
     A focus/window/ROI interruption is not a game outcome. */
 public boolean finalResponseExpired(){return finalPaused()&&reason==Reason.EXPIRED;}
 public boolean acknowledged(){return pending()&&ackAt>=0;}
 public Reason lastWait(){return lastWait;}
 public int verified(){return verified;}
 /** Only the bounded reader's explanation is retained; no frame, pixels or board array enters the report. */
 public void visionReason(String value){
  if(value==null){visionReason="none";return;}
  StringBuilder out=new StringBuilder(Math.min(value.length(),VISION_REASON_LIMIT));
  for(int i=0;i<value.length()&&i<VISION_REASON_LIMIT;i++){char c=value.charAt(i);out.append(Character.isISOControl(c)?' ':c);}
  visionReason=out.toString();
 }
 /** A caller invalidating context may not later auto-resume this pending shot. */
 public void invalidateCandidate(){
  clearCandidate();
  if(pending()){
   lateRecoveryRevoked=true;
   if(lateWaiting){lateWaiting=false;observed(Status.PAUSED,Reason.CONTEXT_INVALIDATED);}
  }
 }
 private void clearCandidate(){candidate=stable=null;candidateAt=-1;}
 public Status observe(BubbleBoard board,long frameAt,long now){
  if(finalPaused())return status;
  observations=increment(observations);ages(frameAt,now);observedCells=board==null?-1:board.count();mismatchedCells=mismatches(board);
  if(pending()&&now>=sentAt&&now-sentAt>=finalTimeoutMs){lateWaiting=false;clearCandidate();return observed(Status.PAUSED,Reason.EXPIRED);}
  if(pending()&&!lateWaiting&&now>=sentAt&&now-sentAt>=responseTimeoutMs){
   // Do not learn from a gesture with no Android completion. A late ACK cannot
   // reopen it. Acknowledged shots get one bounded visual-only recovery window.
   lateWaiting=ackAt>=0&&!lateRecoveryRevoked&&finalTimeoutMs>responseTimeoutMs;
   clearCandidate();return observed(Status.PAUSED,Reason.EXPIRED);
  }
  if(frameAt<0||now<frameAt||now-frameAt>300||frameAt<=lastFrame){clearCandidate();return observed(waitingStatus(),Reason.STALE);}
  lastFrame=frameAt;
  if(board==null){clearCandidate();return observed(waitingStatus(),Reason.NULL_SCENE);}
  if(pending()&&(ackAt<0||frameAt<=ackAt||frameAt-sentAt<350)){clearCandidate();return observed(waitingStatus(),ackAt<0||frameAt<=ackAt?Reason.BEFORE_ACK:Reason.SETTLING);}
  if(candidate==null||!candidate.same(board)){candidate=board;candidateAt=frameAt;candidateAge=age(now,frameAt);return observed(waitingStatus(),Reason.UNSTABLE);}
  if(frameAt-candidateAt<100)return observed(waitingStatus(),Reason.UNSTABLE);
  if(pending()){
   if(!before.sameGeometry(board))return observed(waitingStatus(),Reason.GEOMETRY_MISMATCH);
   if(!Arrays.equals(expected,board.cells()))return observed(waitingStatus(),Reason.CELLS_MISMATCH);
   before=null;expected=null;sentAt=ackAt=-1;lateWaiting=lateRecoveryRevoked=false;verified++;stable=board;return observed(Status.VERIFIED,Reason.VERIFIED);
  }
  stable=board;return observed(Status.READY,Reason.READY);
 }
 public void sent(BubbleBoard board,BubblePolicy.Shot shot,long now){
  if(shot==null||!shot.belongsTo(board)||paused()||pending()||stable==null||!stable.same(board)||now<lastFrame||now-lastFrame>300)
   throw new IllegalStateException("fresh stable bubble board required");
  before=board;expected=shot.expectedCells();lateWaiting=lateRecoveryRevoked=false;sentAt=now;ackAt=-1;clearCandidate();status=Status.WAIT_RESPONSE;
  expectedCells=0;for(int color:expected)if(color>0)expectedCells++;reason=Reason.SENT;ages(lastFrame,now);mismatchedCells=-1;
 }
 /** Caller must prove Android dispatch was not accepted before cancelling this pending plan.
  * Keep completed visual outcomes; only a new reset starts a new verification counter.
  * No cached candidate survives: another shot requires two fresh stable observations.
  */
 public void cancelUnsent(){
  if(!pending()||ackAt>=0)throw new IllegalStateException("unacknowledged unsent bubble plan required");
  before=null;expected=null;sentAt=ackAt=-1;lateWaiting=lateRecoveryRevoked=false;clearCandidate();
  expectedCells=mismatchedCells=-1;sentAge=ackAge=candidateAge=-1;
  observed(Status.SEARCHING,Reason.DISPATCH_NOT_SENT);
 }
 public void acknowledge(long now){if(!pending()||paused())return;if(now<sentAt)throw new IllegalArgumentException("ACK before shot");if(now-sentAt>=responseTimeoutMs)return;if(ackAt<0){ackAt=now;clearCandidate();reason=Reason.ANDROID_ACK;ages(lastFrame,now);}}
 public String report(){
  StringBuilder out=new StringBuilder("BUBBLE_SESSION: status=").append(status).append(" pending=").append(pending()).append(" android_ack=").append(ackAt>=0).append(" verified_shots=").append(verified).append(" late_waiting=").append(lateWaiting).append(" late_recovery_revoked=").append(lateRecoveryRevoked).append(" final_paused=").append(finalPaused()).append(" response_timeout_ms=").append(responseTimeoutMs).append(" final_timeout_ms=").append(finalTimeoutMs)
   .append(" reason=").append(reason).append(" last_wait=").append(lastWait).append(" frame_age_ms=").append(frameAge).append(" sent_age_ms=").append(sentAge).append(" ack_age_ms=").append(ackAge).append(" candidate_age_ms=").append(candidateAge)
   .append(" observations=").append(observations).append(" observed_cells=").append(observedCells).append(" expected_cells=").append(expectedCells).append(" mismatched_cells=").append(mismatchedCells).append(" reason_counts=");
  boolean first=true;for(Reason value:Reason.values())if(reasonCounts[value.ordinal()]>0){if(!first)out.append(',');out.append(value).append(':').append(reasonCounts[value.ordinal()]);first=false;}
  if(first)out.append('-');return out.append(" vision_reason=").append(visionReason).toString();
 }
 private Status observed(Status next,Reason why){reason=why;reasonCounts[why.ordinal()]=increment(reasonCounts[why.ordinal()]);
  if(next==Status.SEARCHING||next==Status.WAIT_RESPONSE||lateWaiting&&why!=Reason.EXPIRED)lastWait=why;return status=next;}
 private Status waitingStatus(){return lateWaiting?Status.PAUSED:pending()?Status.WAIT_RESPONSE:Status.SEARCHING;}
 /** Ages are sampled at the last observation/send/ACK, so a paused report preserves the failing frame. */
 private void ages(long frameAt,long now){frameAge=age(now,frameAt);sentAge=age(now,sentAt);ackAge=age(now,ackAt);candidateAge=age(now,candidateAt);}
 private static long age(long now,long at){return at<0||now<at?-1:now-at;}
 private static long increment(long value){return value==Long.MAX_VALUE?value:value+1;}
 private int mismatches(BubbleBoard board){
  if(expected==null||board==null)return -1;
  int count=Math.abs(expected.length-board.size());for(int i=0;i<Math.min(expected.length,board.size());i++)if(expected[i]!=board.color(i))count++;return count;
 }
}
