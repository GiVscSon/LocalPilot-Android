package org.localpilot.android;
/** Every alternate 2048 proposal is post-ACK, no-response and new-read gated. */
public final class Board2048StallRecoveryTest {
 private static int checks;
 private static void check(boolean b,String reason){checks++;if(!b)throw new AssertionError(reason);}
 private static void rejects(Runnable action,String reason){
  try{action.run();throw new AssertionError(reason);}catch(IllegalArgumentException expected){checks++;}
 }
 public static void main(String[] args){
  Board2048 before=new Board2048(new int[]{2,2,0,0,4,0,0,0,8,4,0,0,16,8,2,0});
  Board2048StallRecovery guard=new Board2048StallRecovery();
  int best=before.choose();
  check(best>=0,"board has legal best direction");
  guard.sent(before,best,10,1000);
  check(guard.unchanged(before,10,2300,2)==Board2048StallRecovery.Next.WAIT,
    "without ACK cannot retry");
  guard.ack(10,1100);
  check(guard.unchanged(before,10,2500,2)==Board2048StallRecovery.Next.WAIT,
    "ACK alone and too-short observation cannot retry");
  check(guard.unchanged(before,10,2800,2)==Board2048StallRecovery.Next.WAIT,
    "one delayed review is not enough");
  check(guard.unchanged(before,10,2900,2)==Board2048StallRecovery.Next.WAIT,
    "two reviews must be separated");
  check(guard.unchanged(before,10,3300,2)==Board2048StallRecovery.Next.REPLAN_READ_ONLY,
    "two fresh delayed checks quarantine direction for exact board");
  int mask=guard.failedMask(before,10);
  check(mask==(1<<best),"only failed direction quarantined");
  int other=before.chooseAvoiding(mask);
  check(other>=0&&other!=best,"different legal action exists");
  rejects(()->guard.sent(before,best,10,3350),"cannot repeat quarantined 2048 action");
  guard.sent(before,other,10,3400);guard.ack(10,3500);
  check(guard.unchanged(before,10,5300,2)==Board2048StallRecovery.Next.WAIT,
    "another unchanged action still needs second fresh review");
  check(guard.unchanged(before,10,5800,2)==Board2048StallRecovery.Next.REPLAN_READ_ONLY,
    "second bounded alternate observation allowed");
  int mask2=guard.failedMask(before,10);
  int third=before.chooseAvoiding(mask2);
  check(third>=0&&third!=other&&third!=best,"third proposal excludes both failed directions");
  guard.sent(before,third,10,6000);guard.ack(10,6100);
  check(guard.unchanged(before,10,7900,2)==Board2048StallRecovery.Next.WAIT,
    "third failure still needs independent second review");
  check(guard.unchanged(before,10,8400,2)==Board2048StallRecovery.Next.STOP,
    "third failed direction stops rather than looping forever");
  check(guard.failedMask(before,11)==0,"new epoch never inherits old move exclusions");
  Board2048 changed=before.move(best).board;
  check(guard.failedMask(changed,10)==0,"changed board never inherits old move exclusions");
  guard.verified();
  check(guard.failedMask(before,10)==0,"verified visual transition resets stale quarantine");
  System.out.println("2048 ACK/no-response script-bounded recovery: "+checks+" PASS");
 }
}
