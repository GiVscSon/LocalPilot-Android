package org.localpilot.android;

/**
 * Bounded, per-game online evidence about observable responses to grid moves.
 * Only a verified post-ACK board transition is a positive observation.
 * A timeout with a confirmed Android ACK is recorded separately as
 * unconfirmed, not misrepresented as a game defeat or incorrect move.
 * This model changes ranking by at most six points and never makes an illegal
 * action eligible. It does not train a neural network.
 */
public final class GridOnlineLearning {
 private static final int BUCKETS=9, MAX_COUNT=4000,MAX_AVOIDED=8;
 // Transient evidence belongs to one exact symbolic board, not a token type.
 // Never serialize it: a restored palette/session cannot prove the old board.
 private GridPuzzle avoidedBoard;
 private java.util.BitSet avoidedSpecial;
 private final int[] avoidedFrom=new int[MAX_AVOIDED],avoidedTo=new int[MAX_AVOIDED],avoidedMode=new int[MAX_AVOIDED];
 private int avoidedCount,avoidedCursor;
 /**
  * Opt-in exclusion after the caller has established Android ACK, a final
  * response timeout, and two stable unchanged full observations. Missing,
  * partial, changed or differently protected boards cannot supply this proof.
  * This does not update response statistics; recordNoResponse remains separate.
  */
 public boolean avoidUnchangedMove(GridPuzzle before,GridPuzzle after,java.util.BitSet beforeSpecial,java.util.BitSet afterSpecial,GridPuzzlePolicy.Decision move){
  if(before==null||after==null||beforeSpecial==null||afterSpecial==null||move==null)
   throw new IllegalArgumentException("missing unchanged move evidence");
  if(beforeSpecial.length()>before.rows*before.cols||afterSpecial.length()>after.rows*after.cols)
   throw new IllegalArgumentException("special mask outside board");
  if(!before.known()||!after.known()||!before.same(after)||!beforeSpecial.equals(afterSpecial))return false;
  int from=endpoint(before,move.fromRow,move.fromCol),to=endpoint(before,move.toRow,move.toCol);
  if(before.at(move.fromRow,move.fromCol)<=0||before.at(move.toRow,move.toCol)<=0
    ||beforeSpecial.get(from)||beforeSpecial.get(to))return false;
  for(int cell:move.clearedCells())if(cell<0||cell>=before.rows*before.cols
    ||before.at(cell/before.cols,cell%before.cols)<=0||beforeSpecial.get(cell))return false;
  prepareBoard(before,beforeSpecial);
  if(avoidedBoard==null){avoidedBoard=new GridPuzzle(before.rows,before.cols,before.cells());avoidedSpecial=(java.util.BitSet)beforeSpecial.clone();}
  int lo=Math.min(from,to),hi=Math.max(from,to);
  if(avoids(before,beforeSpecial,move))return true;
  int slot=avoidedCount<MAX_AVOIDED?avoidedCount++:avoidedCursor;
  if(avoidedCount==MAX_AVOIDED)avoidedCursor=(slot+1)%MAX_AVOIDED;
  avoidedFrom[slot]=lo;avoidedTo[slot]=hi;avoidedMode[slot]=move.mode.ordinal();return true;
 }
 private static int endpoint(GridPuzzle board,int row,int col){
  if(row<0||row>=board.rows||col<0||col>=board.cols)throw new IllegalArgumentException("move outside board");
  return row*board.cols+col;
 }
 /** Discard transient exclusions when the planner observes a different board. */
 public void prepareBoard(GridPuzzle board,java.util.BitSet special){
  if(board==null||special==null||special.length()>board.rows*board.cols)throw new IllegalArgumentException("invalid board evidence");
  if(avoidedBoard!=null&&(!avoidedBoard.same(board)||!avoidedSpecial.equals(special)))clearAvoidedMoves();
 }
 /** Copy RAM-only exclusions when the caller has independently preserved the
  * same game/profile/palette scope. They remain tied to their exact board and
  * are discarded by prepareBoard as soon as it differs.
  */
 public void copyAvoidedMovesFrom(GridOnlineLearning source){
  if(source==null)throw new IllegalArgumentException("missing learning model");
  if(source==this)return;
  clearAvoidedMoves();
  if(source.avoidedBoard==null)return;
  avoidedBoard=source.avoidedBoard;avoidedSpecial=(java.util.BitSet)source.avoidedSpecial.clone();
  avoidedCount=source.avoidedCount;avoidedCursor=source.avoidedCursor;
  System.arraycopy(source.avoidedFrom,0,avoidedFrom,0,MAX_AVOIDED);
  System.arraycopy(source.avoidedTo,0,avoidedTo,0,MAX_AVOIDED);
  System.arraycopy(source.avoidedMode,0,avoidedMode,0,MAX_AVOIDED);
 }
 public void clearAvoidedMoves(){avoidedBoard=null;avoidedSpecial=null;avoidedCount=avoidedCursor=0;}
 public boolean avoids(GridPuzzle board,java.util.BitSet special,GridPuzzlePolicy.Decision move){
  if(board==null||special==null||move==null)throw new IllegalArgumentException("missing move context");
  if(avoidedBoard==null||!avoidedBoard.same(board)||!avoidedSpecial.equals(special))return false;
  int from=endpoint(board,move.fromRow,move.fromCol),to=endpoint(board,move.toRow,move.toCol),lo=Math.min(from,to),hi=Math.max(from,to);
  for(int i=0;i<avoidedCount;i++)if(avoidedFrom[i]==lo&&avoidedTo[i]==hi&&avoidedMode[i]==move.mode.ordinal())return true;
  return false;
 }
 private final int[] confirmed=new int[BUCKETS],unconfirmed=new int[BUCKETS];
 private static int bucket(GridPuzzlePolicy.Decision d){
  if(d==null)throw new IllegalArgumentException("missing move");
  int mode=switch(d.mode){
   case SWAP_MATCH3->0;case TAP_GROUP->1;case TRACE_LINE->2;
  };
  int size=d.expectedCleared>=5?2:d.expectedCleared>=4?1:0;
  return 3*mode+size;
 }
 public void recordVerified(GridPuzzlePolicy.Decision d){update(bucket(d),true);}
 public void recordNoResponse(GridPuzzlePolicy.Decision d){update(bucket(d),false);}
 private void update(int i,boolean success){
  if(confirmed[i]+unconfirmed[i]>=MAX_COUNT){
   confirmed[i]=(confirmed[i]+1)/2;
   unconfirmed[i]=(unconfirmed[i]+1)/2;
  }
  if(success)confirmed[i]++;else unconfirmed[i]++;
 }
 /** Smoothed reliability bonus. Only influences ordering of ALREADY safe moves. */
 public double bonus(GridPuzzlePolicy.Decision d){
  int i=bucket(d),n=confirmed[i]+unconfirmed[i];
  if(n<3)return 0;
  double posterior=(confirmed[i]+2.0)/(n+4.0);
  return Math.max(-6,Math.min(6,(posterior-.65)*18.0));
 }
 public int verifiedCount(){int n=0;for(int v:confirmed)n+=v;return n;}
 public int notConfirmedCount(){int n=0;for(int v:unconfirmed)n+=v;return n;}
 public String encode(){
  StringBuilder b=new StringBuilder("v1");
  for(int i=0;i<BUCKETS;i++)b.append(';').append(confirmed[i]).append(',').append(unconfirmed[i]);
  return b.toString();
 }
 public static GridOnlineLearning decode(String saved){
  if(saved==null||saved.length()>256)throw new IllegalArgumentException("grid learning format");
  String[] parts=saved.split(";",-1);
  if(parts.length!=BUCKETS+1||!parts[0].equals("v1"))throw new IllegalArgumentException("grid learning version");
  GridOnlineLearning result=new GridOnlineLearning();
  try{
   for(int i=0;i<BUCKETS;i++){
    String[] counts=parts[i+1].split(",",-1);
    if(counts.length!=2)throw new IllegalArgumentException();
    int good=Integer.parseInt(counts[0]),unknown=Integer.parseInt(counts[1]);
    if(good<0||unknown<0||good>MAX_COUNT||unknown>MAX_COUNT||good+unknown>MAX_COUNT)
     throw new IllegalArgumentException();
    result.confirmed[i]=good;result.unconfirmed[i]=unknown;
   }
  }catch(NumberFormatException malformed){throw new IllegalArgumentException("grid learning counters",malformed);}
  return result;
 }
}
