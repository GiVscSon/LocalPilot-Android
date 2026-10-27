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
  for(String invalid:new String[]{"","v0","v1;1,2","v1;NaN","v1;999999,0",
    saved.replace("v1","v2"),saved.replaceFirst("10,0","-1,0"),saved+";0,0"})rejected(invalid);
  System.out.println("Grid online learning: "+checks+" assertions PASS (symbolic only)");
 }
}
