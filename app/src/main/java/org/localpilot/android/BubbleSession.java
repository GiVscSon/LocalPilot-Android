package org.localpilot.android;

import java.util.Arrays;

/** ACK opens observation only; a shot is verified against its predicted normal-bubble board. */
public final class BubbleSession {
 public enum Status { SEARCHING, READY, WAIT_RESPONSE, VERIFIED, PAUSED }
 /** Fixed-size metadata; reasons describe the branch actually taken, never a guessed game outcome. */
 public enum Reason { RESET, SENT, ANDROID_ACK, NULL_SCENE, STALE, BEFORE_ACK, SETTLING, UNSTABLE, GEOMETRY_MISMATCH, CELLS_MISMATCH, EXPIRED, READY, VERIFIED }
 private static final int VISION_REASON_LIMIT=120;
 private BubbleBoard candidate,stable,before;
 private int[] expected;
 private long candidateAt=-1,lastFrame=-1,sentAt=-1,ackAt=-1;
 private int verified;private Status status=Status.SEARCHING;
 private Reason reason=Reason.RESET,lastWait=Reason.RESET;
 private final long[] reasonCounts=new long[Reason.values().length];
 private long observations,frameAge=-1,sentAge=-1,ackAge=-1,candidateAge=-1;
 private int observedCells=-1,expectedCells=-1,mismatchedCells=-1;
 private String visionReason="none";
 public void reset(){candidate=stable=before=null;expected=null;candidateAt=lastFrame=sentAt=ackAt=-1;verified=0;status=Status.SEARCHING;
  reason=lastWait=Reason.RESET;Arrays.fill(reasonCounts,0);observations=0;frameAge=sentAge=ackAge=candidateAge=-1;observedCells=expectedCells=mismatchedCells=-1;visionReason="none";}
 public boolean pending(){return before!=null;}
 public boolean paused(){return status==Status.PAUSED;}
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
 public void invalidateCandidate(){candidate=stable=null;candidateAt=-1;}
 public Status observe(BubbleBoard board,long frameAt,long now){
  if(paused())return status;
  observations=increment(observations);ages(frameAt,now);observedCells=board==null?-1:board.count();mismatchedCells=mismatches(board);
  if(pending()&&now>=sentAt&&now-sentAt>=7000){invalidateCandidate();return observed(Status.PAUSED,Reason.EXPIRED);}
  if(frameAt<0||now<frameAt||now-frameAt>300||frameAt<=lastFrame){invalidateCandidate();return observed(pending()?Status.WAIT_RESPONSE:Status.SEARCHING,Reason.STALE);}
  lastFrame=frameAt;
  if(board==null){invalidateCandidate();return observed(pending()?Status.WAIT_RESPONSE:Status.SEARCHING,Reason.NULL_SCENE);}
  if(pending()&&(ackAt<0||frameAt<=ackAt||frameAt-sentAt<350)){invalidateCandidate();return observed(Status.WAIT_RESPONSE,ackAt<0||frameAt<=ackAt?Reason.BEFORE_ACK:Reason.SETTLING);}
  if(candidate==null||!candidate.same(board)){candidate=board;candidateAt=frameAt;candidateAge=age(now,frameAt);return observed(pending()?Status.WAIT_RESPONSE:Status.SEARCHING,Reason.UNSTABLE);}
  if(frameAt-candidateAt<100)return observed(pending()?Status.WAIT_RESPONSE:Status.SEARCHING,Reason.UNSTABLE);
  if(pending()){
   if(!before.sameGeometry(board))return observed(Status.WAIT_RESPONSE,Reason.GEOMETRY_MISMATCH);
   if(!Arrays.equals(expected,board.cells()))return observed(Status.WAIT_RESPONSE,Reason.CELLS_MISMATCH);
   before=null;expected=null;sentAt=ackAt=-1;verified++;stable=board;return observed(Status.VERIFIED,Reason.VERIFIED);
  }
  stable=board;return observed(Status.READY,Reason.READY);
 }
 public void sent(BubbleBoard board,BubblePolicy.Shot shot,long now){
  if(shot==null||!shot.belongsTo(board)||paused()||pending()||stable==null||!stable.same(board)||now<lastFrame||now-lastFrame>300)
   throw new IllegalStateException("fresh stable bubble board required");
  before=board;expected=shot.expectedCells();sentAt=now;ackAt=-1;invalidateCandidate();status=Status.WAIT_RESPONSE;
  expectedCells=0;for(int color:expected)if(color>0)expectedCells++;reason=Reason.SENT;ages(lastFrame,now);mismatchedCells=-1;
 }
 public void acknowledge(long now){if(!pending()||paused())return;if(now<sentAt)throw new IllegalArgumentException("ACK before shot");if(ackAt<0){ackAt=now;invalidateCandidate();reason=Reason.ANDROID_ACK;ages(lastFrame,now);}}
 public String report(){
  StringBuilder out=new StringBuilder("BUBBLE_SESSION: status=").append(status).append(" pending=").append(pending()).append(" android_ack=").append(ackAt>=0).append(" verified_shots=").append(verified)
   .append(" reason=").append(reason).append(" last_wait=").append(lastWait).append(" frame_age_ms=").append(frameAge).append(" sent_age_ms=").append(sentAge).append(" ack_age_ms=").append(ackAge).append(" candidate_age_ms=").append(candidateAge)
   .append(" observations=").append(observations).append(" observed_cells=").append(observedCells).append(" expected_cells=").append(expectedCells).append(" mismatched_cells=").append(mismatchedCells).append(" reason_counts=");
  boolean first=true;for(Reason value:Reason.values())if(reasonCounts[value.ordinal()]>0){if(!first)out.append(',');out.append(value).append(':').append(reasonCounts[value.ordinal()]);first=false;}
  if(first)out.append('-');return out.append(" vision_reason=").append(visionReason).toString();
 }
 private Status observed(Status next,Reason why){reason=why;reasonCounts[why.ordinal()]=increment(reasonCounts[why.ordinal()]);
  if(next==Status.SEARCHING||next==Status.WAIT_RESPONSE)lastWait=why;return status=next;}
 /** Ages are sampled at the last observation/send/ACK, so a paused report preserves the failing frame. */
 private void ages(long frameAt,long now){frameAge=age(now,frameAt);sentAge=age(now,sentAt);ackAge=age(now,ackAt);candidateAge=age(now,candidateAt);}
 private static long age(long now,long at){return at<0||now<at?-1:now-at;}
 private static long increment(long value){return value==Long.MAX_VALUE?value:value+1;}
 private int mismatches(BubbleBoard board){
  if(expected==null||board==null)return -1;
  int count=Math.abs(expected.length-board.size());for(int i=0;i<Math.min(expected.length,board.size());i++)if(expected[i]!=board.color(i))count++;return count;
 }
}
