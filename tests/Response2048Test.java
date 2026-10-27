package org.localpilot.android;

public final class Response2048Test {
 private static void require(boolean good,String msg){if(!good)throw new AssertionError(msg);}
 private static Board2048 board(int... cells){return new Board2048(cells);}
 public static void main(String[] ignored){
  Board2048 before=board(2,2,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
  Board2048 after=board(4,2,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
  Response2048 r=new Response2048(9000);
  require(!r.pending()&&!r.timedOut(999999),"idle");
  r.sent(before,Board2048.LEFT,100);
  require(r.pending()&&r.stage()==Response2048.Stage.SENT,"sent");
  require(!r.verify(after,200),"visual movement before ACK must not be rewarded");
  r.acknowledge(300);
  r.readFailed();r.unstable();require(!r.verify(before,500),"unchanged image rejected");
  require(!r.verify(board(4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),600),"zero spawn rejected");
  require(!r.verify(board(4,8,0,0,0,0,0,0,0,0,0,0,0,0,0,0),700),"impossible spawn rejected");
  require(!r.timedOut(9299)&&r.timedOut(9300),"bounded post ACK timeout");
  require(r.report().contains("unknown_reads=1")&&r.report().contains("unstable=1")&&r.report().contains("mismatched=3"),"diagnostics");
  require(!r.verify(after,9301),"timeout cannot be bypassed by a late frame");
  require(r.pending()&&r.timedOut(9301),"timed-out transition remains pending until STOP");
  r.reset();require(r.stage()==Response2048.Stage.IDLE,"reset");
  r.sent(before,Board2048.LEFT,100);r.acknowledge(120);
  require(r.verify(after,150),"valid transition");
  require(!r.pending()&&!r.timedOut(100000),"verified clears pending");
  require(!r.verify(after,160),"no double reward");
  r.reset();
  try{r.sent(before,Board2048.UP,0);throw new AssertionError("illegal no-op sent");}catch(IllegalArgumentException expected){}
  try{r.acknowledge(100);throw new AssertionError("spurious ACK");}catch(IllegalStateException expected){}
  System.out.println("Response2048Test PASS");
 }
}
