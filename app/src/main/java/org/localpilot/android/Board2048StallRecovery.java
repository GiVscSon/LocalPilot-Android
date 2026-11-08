package org.localpilot.android;
import java.util.Arrays;
/**
 * Script-bounded 2048 stall recovery. An ACK is NOT a tile transition.
 * After ACK and multiple independently stable unchanged reads, quarantine the
 * attempted direction for the EXACT observed board. Never guess tiles, reuse
 * tickets, call Android input or record a positive training example.
 */
public final class Board2048StallRecovery {
 public enum Next { WAIT, REPLAN_READ_ONLY, STOP }
 private int[] scopedCells;
 private long session=-1,sentAt=-1,ackAt=-1,lastReviewAt=-1;
 private int pendingDirection=-1,failedMask,failedCount,unchangedReviews;
 public void reset(){scopedCells=null;session=sentAt=ackAt=lastReviewAt=-1;pendingDirection=-1;failedMask=failedCount=unchangedReviews=0;}
 public int failedMask(Board2048 b,long epoch){
  return b!=null&&epoch==session&&scopedCells!=null&&Arrays.equals(scopedCells,b.cells)?failedMask:0;
 }
 /** Records a proposal. Actual dispatch still needs original ActionGate permit. */
 public void sent(Board2048 b,int direction,long epoch,long now){
  if(b==null||direction<0||direction>3||epoch<0||now<0
    ||b.same(b.move(direction).board))throw new IllegalArgumentException("invalid proposed 2048 move");
  if(epoch!=session||scopedCells==null||!Arrays.equals(scopedCells,b.cells)){
   session=epoch;scopedCells=b.cells.clone();failedMask=failedCount=0;
  }
  if((failedMask&(1<<direction))!=0)throw new IllegalArgumentException("previously failed direction");
  pendingDirection=direction;sentAt=now;ackAt=lastReviewAt=-1;unchangedReviews=0;
 }
 public void cancelledUnsent(long epoch){
  if(epoch==session&&ackAt<0){pendingDirection=-1;sentAt=lastReviewAt=-1;unchangedReviews=0;}
 }
 /** Called only inside a VALID consumed-ticket onCompleted callback. */
 public void ack(long epoch,long now){
  if(epoch==session&&pendingDirection>=0&&sentAt>=0&&ackAt<0&&now>=sentAt)ackAt=now;
 }
 public Next unchanged(Board2048 unchanged,long epoch,long now,int maxReplans){
  if(maxReplans<0||maxReplans>2)throw new IllegalArgumentException("recovery script bound");
  if(unchanged==null||epoch!=session||pendingDirection<0||sentAt<0
    ||scopedCells==null||!Arrays.equals(scopedCells,unchanged.cells)||now<sentAt)
   return Next.STOP;
  if(ackAt<0||now<ackAt||now-ackAt<1700)return Next.WAIT;
  if(now-ackAt>6500)return Next.STOP;
  if(lastReviewAt>=0&&now-lastReviewAt<350)return Next.WAIT;
  lastReviewAt=now;unchangedReviews++;
  if(unchangedReviews<2)return Next.WAIT;
  if(failedCount>=maxReplans)return Next.STOP;
  failedMask|=1<<pendingDirection;failedCount++;pendingDirection=-1;
  sentAt=ackAt=lastReviewAt=-1;unchangedReviews=0;
  return Next.REPLAN_READ_ONLY;
 }
 public void verified(){reset();}
 public String status(){
  return "2048_STALL: pending="+(pendingDirection>=0)+" ack="+(ackAt>=0)
    +" quarantined_mask="+failedMask+" retries="+failedCount;
 }
}
