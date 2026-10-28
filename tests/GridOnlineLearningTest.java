package org.localpilot.android;

import java.util.Arrays;
import java.util.BitSet;

public final class GridOnlineLearningTest {
 private static int checks;
 private static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
 private static void rejected(String s){try{GridOnlineLearning.decode(s);throw new AssertionError("accepted invalid model");}catch(IllegalArgumentException ok){checks++;}}
 public static void main(String[] args){
  int[] cells={1,2,1,2,1,3,3,2,3};
  GridPuzzle board=new GridPuzzle(3,3,cells);
  var action=GridPuzzlePolicy.choose(board,GridPuzzlePolicy.Mode.SWAP_MATCH3);
  check(action!=null,"baseline has one safe action");
  GridOnlineLearning model=new GridOnlineLearning();
  check(model.verifiedCount()==0&&model.notConfirmedCount()==0&&model.bonus(action)==0,
    "fresh model does not bias move selection");
  String first=model.encode();
  check(first.equals(GridOnlineLearning.decode(first).encode()),"empty model roundtrip");
  // In production only the separate post-ACK grid verification calls recordVerified.
  for(int i=0;i<10;i++)model.recordVerified(action);
  check(model.verifiedCount()==10&&model.notConfirmedCount()==0,"confirmed effects accumulate");
  check(model.bonus(action)>0&&model.bonus(action)<=6,"learned effect has a bounded positive bonus");
  String saved=model.encode();
  GridOnlineLearning restored=GridOnlineLearning.decode(saved);
  check(restored.verifiedCount()==10&&restored.bonus(action)==model.bonus(action),
    "learned preferences survive process restart");
  for(int i=0;i<30;i++)restored.recordNoResponse(action);
  check(restored.notConfirmedCount()==30&&restored.bonus(action)<0,
    "unconfirmed effects reduce the preference without claiming a game loss");
  for(int i=0;i<10000;i++)restored.recordVerified(action);
  check(restored.encode().length()<256,"bounded model remains compact during long play");
  var chosen=GridPuzzlePolicy.chooseAdaptive(board,GridPuzzlePolicy.Mode.SWAP_MATCH3,
    2,0,0,new BitSet(),false,restored);
  check(chosen!=null&&Arrays.equals(chosen.clearedCells(),action.clearedCells()),
    "learning changes ranking only, never action legality or clear-set proof");
  int[] partial=cells.clone();partial[8]=-1;
  GridPuzzle incomplete=new GridPuzzle(3,3,partial);
  check(GridPuzzlePolicy.chooseAdaptive(incomplete,GridPuzzlePolicy.Mode.SWAP_MATCH3,
    2,0,0,new BitSet(),false,restored)==null,"strict route stays fail closed");
  var partialChoice=GridPuzzlePolicy.chooseAdaptive(incomplete,GridPuzzlePolicy.Mode.SWAP_MATCH3,
    2,0,0,new BitSet(),true,restored);
  check(partialChoice!=null&&partial[partialChoice.fromRow*3+partialChoice.fromCol]>0
    &&partial[partialChoice.toRow*3+partialChoice.toCol]>0,"partial route only uses known cells");
  GridPuzzle alternatives=new GridPuzzle(3,3,new int[]{1,1,3,4,5,6,2,2,7});
  GridOnlineLearning avoiding=new GridOnlineLearning();BitSet none=new BitSet();
  var preferred=GridPuzzlePolicy.chooseAdaptive(alternatives,GridPuzzlePolicy.Mode.TAP_GROUP,2,0,0,none,false,avoiding);
  check(preferred!=null&&preferred.fromRow==0,"fixture chooses first of two equal groups");
  GridPuzzleSession timed=new GridPuzzleSession(1000,4000);int[] baseline=alternatives.cells();
  timed.observe(3,3,baseline,none,1000,1000);timed.observe(3,3,baseline,none,1100,1100);
  timed.sent(baseline,1200,preferred);timed.acknowledge(1250);
  check(timed.observe(3,3,baseline,none,2200,2200)==GridPuzzleSession.Status.PAUSED&&timed.awaitingLateVerification(),"first deadline pauses without rejection");
  timed.observe(3,3,baseline,none,4100,4100);
  check(timed.observe(3,3,baseline,none,5200,5200)==GridPuzzleSession.Status.PAUSED&&timed.unchangedAtTimeout(),"two stable unchanged frames prove final timeout");
  GridOnlineLearning lifecycle=new GridOnlineLearning();
  check(lifecycle.avoidUnchangedMove(alternatives,alternatives,none,none,preferred),"final evidence enables transient exclusion");
  timed.reset();timed.observe(3,3,baseline,none,5300,5300);timed.observe(3,3,baseline,none,5400,5400);
  var resumed=GridPuzzlePolicy.chooseAdaptive(alternatives,GridPuzzlePolicy.Mode.TAP_GROUP,2,0,0,none,false,lifecycle);
  check(resumed!=null&&resumed.fromRow!=preferred.fromRow,"same board chooses another legal move after restart");
  String unchangedEncoding=avoiding.encode();
  check(avoiding.avoidUnchangedMove(alternatives,alternatives,none,none,preferred),"stable unchanged full board may exclude an exact move");
  check(avoiding.encode().equals(unchangedEncoding)&&avoiding.notConfirmedCount()==0,"transient exclusion neither changes checkpoint nor double-counts evidence");
  var alternative=GridPuzzlePolicy.chooseAdaptive(alternatives,GridPuzzlePolicy.Mode.TAP_GROUP,2,0,0,none,false,avoiding);
  check(alternative!=null&&alternative.fromRow==2,"planner chooses independent legal alternative on same board");
  check(GridPuzzlePolicy.choose(alternatives,GridPuzzlePolicy.Mode.TAP_GROUP).fromRow==0,"legacy planning does not use opt-in exclusions");
  GridOnlineLearning decoded=GridOnlineLearning.decode(avoiding.encode());
  check(!decoded.avoids(alternatives,none,preferred),"process restart never restores unproven board identity");
  int[] changed=alternatives.cells();changed[8]=8;GridPuzzle otherBoard=new GridPuzzle(3,3,changed);
  check(!avoiding.avoidUnchangedMove(alternatives,otherBoard,none,none,preferred),"changed board is not no-response proof");
  var afterChange=GridPuzzlePolicy.chooseAdaptive(otherBoard,GridPuzzlePolicy.Mode.TAP_GROUP,2,0,0,none,false,avoiding);
  check(afterChange!=null&&afterChange.fromRow==0&&!avoiding.avoids(alternatives,none,preferred),"new board clears old coordinate exclusions");
  check(avoiding.avoidUnchangedMove(alternatives,alternatives,none,none,preferred),"can record another current-board exclusion");
  BitSet changedMask=new BitSet();changedMask.set(8);
  check(!avoiding.avoidUnchangedMove(alternatives,alternatives,none,changedMask,preferred),"changed mask is not unchanged evidence");
  GridPuzzlePolicy.chooseAdaptive(alternatives,GridPuzzlePolicy.Mode.TAP_GROUP,2,0,0,changedMask,false,avoiding);
  check(!avoiding.avoids(alternatives,none,preferred),"new mask clears exclusions even when cell IDs match");
  int[] hidden=alternatives.cells();hidden[8]=-1;GridPuzzle hiddenBoard=new GridPuzzle(3,3,hidden);
  check(!avoiding.avoidUnchangedMove(hiddenBoard,hiddenBoard,none,none,preferred),"unknown cells can hide a response and cannot blacklist a move");
  check(avoiding.avoidUnchangedMove(alternatives,alternatives,none,none,preferred),"current board can be recorded after clear");
  GridOnlineLearning copied=GridOnlineLearning.decode(avoiding.encode());copied.copyAvoidedMovesFrom(avoiding);
  check(copied.avoids(alternatives,none,preferred),"explicit same-scope RAM transfer preserves board exclusions");
  avoiding.clearAvoidedMoves();check(!avoiding.avoids(alternatives,none,preferred),"explicit scope reset removes exclusions");
  check(copied.avoids(alternatives,none,preferred),"clearing source does not clear independent RAM transfer");
  int[] manyCells=new int[100];for(int i=0;i<100;i++)manyCells[i]=1+i/10;
  GridPuzzle many=new GridPuzzle(10,10,manyCells);GridOnlineLearning bounded=new GridOnlineLearning();
  for(int i=0;i<20;i++){
   var next=GridPuzzlePolicy.chooseAdaptive(many,GridPuzzlePolicy.Mode.TAP_GROUP,2,0,0,none,false,bounded);
   check(next!=null&&bounded.avoidUnchangedMove(many,many,none,none,next),"bounded exclusions keep independent legal alternatives available");
  }
  check(bounded.encode().equals(unchangedEncoding),"exclusion ring never leaks into persistent model");
  for(String invalid:new String[]{"","v0","v1;1,2","v1;NaN","v1;999999,0",
    saved.replace("v1","v2"),saved.replaceFirst("10,0","-1,0"),saved+";0,0"})rejected(invalid);
  System.out.println("Grid online learning: "+checks+" assertions PASS (symbolic only)");
 }
}
