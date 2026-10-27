package org.localpilot.android;

import java.util.Arrays;

/** Fresh-frame gate for a grid puzzle. A changed board is a response, not a win. */
public final class GridPuzzleSession {
 public enum Status { WAIT_STABLE, READY, WAIT_RESPONSE, CHANGED, PAUSED }
 private static final long FRESH_MS=300, STABLE_MS=80, SETTLE_MS=180;
 private final long responseTimeoutMs;
 private Status status=Status.WAIT_STABLE;
 private int rows,cols;
 private java.util.BitSet candidateSpecial,stableSpecial,beforeSpecial;
 private int[] candidate,stable,before;
 private long candidateAt=-1,lastFrameAt=-1,dispatchedAt=-1,completedAt=-1;
 private int verifiedChanges;

 public GridPuzzleSession(){this(2500);}
 public GridPuzzleSession(long responseTimeoutMs){if(responseTimeoutMs<1000||responseTimeoutMs>10000)throw new IllegalArgumentException("response timeout outside 1000..10000 ms");this.responseTimeoutMs=responseTimeoutMs;}
 public void reset(){status=Status.WAIT_STABLE;rows=cols=0;candidate=stable=before=null;stableSpecial=beforeSpecial=null;candidateAt=lastFrameAt=dispatchedAt=completedAt=-1;verifiedChanges=0;}
 public Status status(){return status;}
 public boolean pending(){return before!=null;}
 public boolean paused(){return status==Status.PAUSED;}
 public int verifiedChanges(){return verifiedChanges;}

 public Status observe(int nextRows,int nextCols,int[] cells,long frameAt,long now){return observe(nextRows,nextCols,cells,new java.util.BitSet(),frameAt,now);}
 public Status observe(int nextRows,int nextCols,int[] cells,java.util.BitSet special,long frameAt,long now){
  if(special==null||special.length()>nextRows*nextCols)throw new IllegalArgumentException("special mask outside board");
  if(paused())return status;
  // Check the response deadline even when the next frame is missing or invalid.
  if(pending()&&now>=dispatchedAt&&now-dispatchedAt>=responseTimeoutMs){clearCandidate();status=Status.PAUSED;return status;}
  if(frameAt<0||now<frameAt||now-frameAt>FRESH_MS||frameAt<=lastFrameAt){return waitAgain();}
  lastFrameAt=frameAt;
  if(!complete(nextRows,nextCols,cells)||pending()&&(nextRows!=rows||nextCols!=cols))return waitAgain();
  if(!pending()&&(nextRows!=rows||nextCols!=cols)){rows=nextRows;cols=nextCols;stable=null;stableSpecial=null;clearCandidate();}
  if(pending()&&(completedAt<0||frameAt<=completedAt||frameAt-dispatchedAt<SETTLE_MS))return waitAgain();
  if(candidate==null||!Arrays.equals(candidate,cells)||!special.equals(candidateSpecial)){candidate=cells.clone();candidateSpecial=(java.util.BitSet)special.clone();candidateAt=frameAt;status=pending()?Status.WAIT_RESPONSE:Status.WAIT_STABLE;return status;}
  if(frameAt-candidateAt<STABLE_MS){status=pending()?Status.WAIT_RESPONSE:Status.WAIT_STABLE;return status;}
  if(pending()){
   if(Arrays.equals(before,cells)&&special.equals(beforeSpecial)){status=Status.WAIT_RESPONSE;return status;}
   stable=cells.clone();stableSpecial=(java.util.BitSet)special.clone();before=null;beforeSpecial=null;dispatchedAt=completedAt=-1;verifiedChanges++;status=Status.CHANGED;return status;
  }
  stable=cells.clone();stableSpecial=(java.util.BitSet)special.clone();status=Status.READY;return status;
 }

 /** Call when dispatching the action selected from the latest stable board. */
 public void sent(int[] snapshot,long at){
  if(paused()||pending()||(status!=Status.READY&&status!=Status.CHANGED)||stable==null||!Arrays.equals(stable,snapshot)||at<lastFrameAt||at-lastFrameAt>FRESH_MS)
   throw new IllegalStateException("a fresh stable board is required before dispatch");
  before=stable.clone();beforeSpecial=(java.util.BitSet)stableSpecial.clone();dispatchedAt=at;completedAt=-1;clearCandidate();status=Status.WAIT_RESPONSE;
 }
 /** Android completion only opens response observation; it never counts a success. */
 public void acknowledge(long at){
  if(!pending()||paused())return;
  if(at<dispatchedAt)throw new IllegalArgumentException("completion precedes dispatch");
  if(completedAt<0){completedAt=at;clearCandidate();}
 }
 public String report(){return "GRID_SESSION: status="+status+" pending="+pending()+" android_ack="+(completedAt>=0)+" visual_changes="+verifiedChanges+" response_timeout_ms="+responseTimeoutMs;}
 private Status waitAgain(){clearCandidate();status=pending()?Status.WAIT_RESPONSE:Status.WAIT_STABLE;return status;}
 private void clearCandidate(){candidate=null;candidateSpecial=null;candidateAt=-1;}
 private static boolean complete(int rows,int cols,int[] cells){
  if(rows<3||rows>10||cols<3||cols>10||cells==null||cells.length!=rows*cols)return false;
  for(int cell:cells)if(cell<0)return false;
  return true;
 }
}
