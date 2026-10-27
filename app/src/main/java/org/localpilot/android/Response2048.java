package org.localpilot.android;

/** Bounded, ACK-gated post-swipe result for the 2048 screen loop. No guesses become rewards. */
public final class Response2048 {
 public enum Stage { IDLE, SENT, ACKED, VERIFIED }
 private final long timeoutMs;
 private Stage stage=Stage.IDLE;
 private Board2048 before;
 private int action=-1,unreadable,unstable,mismatched;
 private long sentAt=-1,ackAt=-1;
 public Response2048(long timeoutMs){if(timeoutMs<1000||timeoutMs>60000)throw new IllegalArgumentException("timeout");this.timeoutMs=timeoutMs;}
 public void reset(){stage=Stage.IDLE;before=null;action=-1;sentAt=ackAt=-1;unreadable=unstable=mismatched=0;}
 public Stage stage(){return stage;}
 public boolean pending(){return stage==Stage.SENT||stage==Stage.ACKED;}
 public void sent(Board2048 board,int direction,long now){
  if(board==null||pending()||now<0||direction<Board2048.LEFT||direction>Board2048.DOWN
    ||board.same(board.move(direction).board))throw new IllegalArgumentException("legal 2048 move required");
  reset();before=board;action=direction;sentAt=now;stage=Stage.SENT;
 }
 public void acknowledge(long now){
  if(stage!=Stage.SENT||now<sentAt)throw new IllegalStateException("ACK without dispatched swipe");
  ackAt=now;stage=Stage.ACKED;
 }
 public void readFailed(){if(stage==Stage.ACKED&&unreadable<Integer.MAX_VALUE)unreadable++;}
 public void unstable(){if(stage==Stage.ACKED&&unstable<Integer.MAX_VALUE)unstable++;}
 public boolean verify(Board2048 after,long now){
  if(stage!=Stage.ACKED||now<ackAt||timedOut(now)||after==null)return false;
  if(!before.verifies(after,action)){
   if(mismatched<Integer.MAX_VALUE)mismatched++;
   return false;
  }
  before=null;stage=Stage.VERIFIED;return true;
 }
 public boolean timedOut(long now){return stage==Stage.ACKED&&now>=ackAt&&now-ackAt>=timeoutMs;}
 public String report(){return "stage="+stage+" ack="+(ackAt>=0)+" pending="+pending()
  +" timeout_ms="+timeoutMs+" unknown_reads="+unreadable+" unstable="+unstable+" mismatched="+mismatched;}
}
