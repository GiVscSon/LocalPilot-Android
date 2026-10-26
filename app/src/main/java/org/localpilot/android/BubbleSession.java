package org.localpilot.android;

import java.util.Arrays;

/** ACK opens observation only; a shot is verified against its predicted normal-bubble board. */
public final class BubbleSession {
 public enum Status { SEARCHING, READY, WAIT_RESPONSE, VERIFIED, PAUSED }
 private BubbleBoard candidate,stable,before;
 private int[] expected;
 private long candidateAt=-1,lastFrame=-1,sentAt=-1,ackAt=-1;
 private int verified;private Status status=Status.SEARCHING;
 public void reset(){candidate=stable=before=null;expected=null;candidateAt=lastFrame=sentAt=ackAt=-1;verified=0;status=Status.SEARCHING;}
 public boolean pending(){return before!=null;}
 public boolean paused(){return status==Status.PAUSED;}
 public int verified(){return verified;}
 public void invalidateCandidate(){candidate=stable=null;candidateAt=-1;}
 public Status observe(BubbleBoard board,long frameAt,long now){
  if(paused())return status;
  if(pending()&&now>=sentAt&&now-sentAt>=7000){invalidateCandidate();return status=Status.PAUSED;}
  if(frameAt<0||now<frameAt||now-frameAt>300||frameAt<=lastFrame){invalidateCandidate();return status=pending()?Status.WAIT_RESPONSE:Status.SEARCHING;}
  lastFrame=frameAt;
  if(board==null){invalidateCandidate();return status=pending()?Status.WAIT_RESPONSE:Status.SEARCHING;}
  if(pending()&&(ackAt<0||frameAt<=ackAt||frameAt-sentAt<350)){invalidateCandidate();return status=Status.WAIT_RESPONSE;}
  if(candidate==null||!candidate.same(board)){candidate=board;candidateAt=frameAt;return status=pending()?Status.WAIT_RESPONSE:Status.SEARCHING;}
  if(frameAt-candidateAt<100)return status=pending()?Status.WAIT_RESPONSE:Status.SEARCHING;
  if(pending()){
   if(!before.sameGeometry(board)||!Arrays.equals(expected,board.cells()))return status=Status.WAIT_RESPONSE;
   before=null;expected=null;sentAt=ackAt=-1;verified++;stable=board;return status=Status.VERIFIED;
  }
  stable=board;return status=Status.READY;
 }
 public void sent(BubbleBoard board,BubblePolicy.Shot shot,long now){
  if(shot==null||!shot.belongsTo(board)||paused()||pending()||stable==null||!stable.same(board)||now<lastFrame||now-lastFrame>300)
   throw new IllegalStateException("fresh stable bubble board required");
  before=board;expected=shot.expectedCells();sentAt=now;ackAt=-1;invalidateCandidate();status=Status.WAIT_RESPONSE;
 }
 public void acknowledge(long now){if(!pending()||paused())return;if(now<sentAt)throw new IllegalArgumentException("ACK before shot");if(ackAt<0){ackAt=now;invalidateCandidate();}}
 public String report(){return "BUBBLE_SESSION: status="+status+" pending="+pending()+" android_ack="+(ackAt>=0)+" verified_shots="+verified;}
}
