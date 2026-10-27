package org.localpilot.android;

public final class GridPuzzleSessionTest {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static int[] board(){return new int[]{1,2,3,2,3,1,3,1,2};}
 private static GridPuzzleSession ready(){GridPuzzleSession s=new GridPuzzleSession();s.observe(3,3,board(),1000,1000);check(s.observe(3,3,board(),1100,1100)==GridPuzzleSession.Status.READY,"two stable fresh frames become ready");return s;}
 private static void illegal(Runnable action,String message){try{action.run();throw new AssertionError(message);}catch(IllegalStateException expected){checks++;}}
 public static void main(String[] args){
  GridPuzzleSession s=new GridPuzzleSession();int[] a=board(),b=board();b[0]=4;
  check(s.observe(3,3,a,1000,1000)==GridPuzzleSession.Status.WAIT_STABLE,"one frame cannot authorize input");
  check(s.observe(3,3,a,1050,1050)==GridPuzzleSession.Status.WAIT_STABLE,"50ms is too short for stability");
  check(s.observe(3,3,a,1080,1080)==GridPuzzleSession.Status.READY,"80ms stable interval accepted");
  s.sent(a,1100);check(s.pending()&&s.verifiedChanges()==0,"dispatch is not game progress");
  check(s.observe(3,3,b,1400,1400)==GridPuzzleSession.Status.WAIT_RESPONSE,"changed frame before Android completion is not accepted");
  s.acknowledge(1450);check(s.verifiedChanges()==0,"Android ACK never counts a win or board response");
  check(s.observe(3,3,b,1440,1450)==GridPuzzleSession.Status.WAIT_RESPONSE,"pre-ACK frame rejected");
  check(s.observe(3,3,b,1500,1500)==GridPuzzleSession.Status.WAIT_RESPONSE,"one post-ACK changed frame is insufficient");
  check(s.observe(3,3,b,1580,1580)==GridPuzzleSession.Status.CHANGED,"two stable changed frames confirm a board response");
  check(!s.pending()&&s.verifiedChanges()==1,"response clears pending and counts once");
  check(s.observe(3,3,b,1680,1680)==GridPuzzleSession.Status.READY&&s.verifiedChanges()==1,"static changed board never counts twice");
  s.sent(b,1700);s.acknowledge(1740);
  check(s.observe(3,3,a,1800,1800)==GridPuzzleSession.Status.WAIT_RESPONSE,"settling period applies even after fast ACK");
  check(s.observe(3,3,a,1880,1880)==GridPuzzleSession.Status.WAIT_RESPONSE,"first frame after settle restarts stable candidate");
  check(s.observe(3,3,a,1960,1960)==GridPuzzleSession.Status.CHANGED&&s.verifiedChanges()==2,"next action has an independent response");

  s=ready();s.sent(a,1200);s.acknowledge(1250);
  check(s.observe(3,3,a,1500,1500)==GridPuzzleSession.Status.WAIT_RESPONSE,"unchanged board waits");
  check(s.observe(3,3,a,1600,1600)==GridPuzzleSession.Status.WAIT_RESPONSE&&s.verifiedChanges()==0,"stable unchanged board is not success");
  check(s.observe(3,3,a,3700,3700)==GridPuzzleSession.Status.PAUSED,"no response pauses at 2500ms");
  check(s.paused()&&s.pending(),"paused action remains unresolved in diagnostics");
  check(s.observe(3,3,b,4000,4000)==GridPuzzleSession.Status.PAUSED&&s.verifiedChanges()==0,"later changes cannot resume automatically");
  GridPuzzleSession paused=s;illegal(()->paused.sent(a,4100),"paused controller sent an automatic retry");
  s.reset();check(!s.paused()&&!s.pending()&&s.verifiedChanges()==0&&s.status()==GridPuzzleSession.Status.WAIT_STABLE,"explicit reset clears the session");

  GridPuzzleSession interrupted=ready();interrupted.sent(board(),1200);interrupted.invalidateCandidate();
  check(interrupted.pending()&&!interrupted.acknowledged()&&interrupted.status()==GridPuzzleSession.Status.WAIT_RESPONSE,"focus loss preserves unresolved gesture without inventing ACK");
  int[] changed=board();changed[0]=4;interrupted.observe(3,3,changed,1500,1500);
  check(interrupted.observe(3,3,changed,1600,1600)==GridPuzzleSession.Status.WAIT_RESPONSE,"no ACK after focus loss cannot verify changed field");
  illegal(()->interrupted.sent(board(),1600),"focus recovery cannot dispatch while response unresolved");
  check(interrupted.observe(3,3,changed,3700,3700)==GridPuzzleSession.Status.PAUSED,"unresolved gesture still expires");
  GridPuzzleSession recovered=ready();recovered.invalidateCandidate();
  illegal(()->recovered.sent(board(),1200),"focus loss invalidates previously stable board");
  check(recovered.observe(3,3,board(),1300,1300)==GridPuzzleSession.Status.WAIT_STABLE,"recovery requires a new first frame");
  check(recovered.observe(3,3,board(),1400,1400)==GridPuzzleSession.Status.READY,"two fresh stable frames recover readiness");
  GridPuzzleSession acknowledged=ready();acknowledged.sent(board(),1200);acknowledged.acknowledge(1250);acknowledged.invalidateCandidate();
  check(acknowledged.pending()&&acknowledged.acknowledged(),"focus loss retains ACK and pending baseline");
  acknowledged.observe(3,3,changed,1500,1500);
  check(acknowledged.observe(3,3,changed,1600,1600)==GridPuzzleSession.Status.CHANGED,"two post-recovery frames can verify acknowledged action");

  for(int kind=0;kind<6;kind++){
   s=ready();s.sent(a,1200);s.acknowledge(1240);
   s.observe(3,3,b,1500,1500);
   int[] invalid=b.clone();invalid[4]=-1;
   if(kind==0)s.observe(3,3,invalid,1600,1600);
   if(kind==1)s.observe(3,3,b,1600,1950);
   if(kind==2)s.observe(3,3,b,1600,1599);
   if(kind==3)s.observe(3,3,b,1500,1600);
   if(kind==4)s.observe(3,4,new int[12],1600,1600);
   if(kind==5)s.observe(3,3,null,1600,1600);
   check(s.observe(3,3,b,2000,2000)==GridPuzzleSession.Status.WAIT_RESPONSE,"invalid frame resets stability kind="+kind);
   check(s.observe(3,3,b,2100,2100)==GridPuzzleSession.Status.CHANGED,"valid evidence recovers after invalid frame kind="+kind);
  }
  s=ready();s.sent(a,1200);
  check(s.observe(3,3,null,-1,3700)==GridPuzzleSession.Status.PAUSED,"invalid frames do not suppress pending timeout");
  s=new GridPuzzleSession();
  check(s.observe(3,3,a,1000,1301)==GridPuzzleSession.Status.WAIT_STABLE,"301ms old frame rejected");
  check(s.observe(3,3,a,1400,1400)==GridPuzzleSession.Status.WAIT_STABLE,"fresh frame after stale starts new candidate");
  check(s.observe(3,3,a,1500,1500)==GridPuzzleSession.Status.READY,"subsequent fresh stable frame accepted");
  s=ready();int[] modified=a.clone();modified[0]=9;GridPuzzleSession fresh=s;
  illegal(()->fresh.sent(modified,1200),"action used a different board");
  illegal(()->fresh.sent(a,1099),"action preceded stable frame");
  illegal(()->fresh.sent(a,1401),"action used expired stable frame");
  s.sent(a,1200);a[0]=8;s.acknowledge(1240);
  s.observe(3,3,board(),1500,1500);
  check(s.observe(3,3,board(),1600,1600)==GridPuzzleSession.Status.WAIT_RESPONSE,"caller mutation cannot change saved baseline");
  int[] empty=new int[9];s.reset();s.observe(3,3,empty,1000,1000);
  check(s.observe(3,3,empty,1100,1100)==GridPuzzleSession.Status.READY,"known empty cells supported; vision decides blank-scene validity");
  check(s.report().contains("visual_changes=0")&&!s.report().contains("win"),"diagnostics name visual changes rather than rewards");
  for(int rows=3;rows<=10;rows++)for(int cols=3;cols<=10;cols++){
   GridPuzzleSession sized=new GridPuzzleSession();int[] cells=new int[rows*cols];
   for(int i=0;i<cells.length;i++)cells[i]=1+i%5;
   sized.observe(rows,cols,cells,1000,1000);
   check(sized.observe(rows,cols,cells,1100,1100)==GridPuzzleSession.Status.READY,"rectangular dimensions "+rows+"x"+cols);
  }
  GridPuzzleSession slow=new GridPuzzleSession(4500);int[] slowBefore=board(),slowAfter=board();slowAfter[0]=4;
  slow.observe(3,3,slowBefore,1000,1000);slow.observe(3,3,slowBefore,2100,2100);slow.sent(slowBefore,2200);slow.acknowledge(2400);
  check(slow.observe(3,3,slowAfter,3700,3700)==GridPuzzleSession.Status.WAIT_RESPONSE,"first API30 response capture starts stable candidate");
  check(slow.observe(3,3,slowAfter,5000,5000)==GridPuzzleSession.Status.CHANGED,"4500ms timeout allows two slow captures beyond default deadline");
  slow.sent(slowAfter,5100);slow.acknowledge(5300);
  check(slow.observe(3,3,slowAfter,8000,8000)==GridPuzzleSession.Status.WAIT_RESPONSE&&!slow.paused(),"slow unchanged response remains pending beyond2500ms");
  check(slow.observe(3,3,slowAfter,9599,9599)==GridPuzzleSession.Status.WAIT_RESPONSE,"custom deadline remains open at4499ms");
  check(slow.observe(3,3,null,-1,9600)==GridPuzzleSession.Status.PAUSED,"custom deadline pauses at4500ms even without valid frame");
  slow.reset();check(slow.report().contains("response_timeout_ms=4500"),"reset preserves configured timeout");
  for(long invalid:new long[]{-1,0,999,10001,Long.MAX_VALUE})try{new GridPuzzleSession(invalid);throw new AssertionError("invalid timeout accepted");}catch(IllegalArgumentException expected){checks++;}
  check(new GridPuzzleSession(1000).report().contains("1000")&&new GridPuzzleSession(10000).report().contains("10000"),"timeout limits inclusive");
  System.out.println("Grid puzzle lifecycle: "+checks+" assertions PASS; synthetic snapshots only");
 }
}
