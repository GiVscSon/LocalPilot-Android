package org.localpilot.android;
public final class Verified2048RankingTest {
 private static int checks;
 private static void check(boolean value){checks++;if(!value)throw new AssertionError("2048 ranker safety");}
 private static void rejected(Runnable bad){try{bad.run();throw new AssertionError("accepted unsafe ranker");}catch(IllegalArgumentException correct){checks++;}}
 public static void main(String[] args){
  check(Verified2048Ranking.combine(.4,.4)==.4);
  check(Verified2048Ranking.combine(-.4,-.4)==-.4);
  check(Math.abs(Verified2048Ranking.combine(.1,-.25)+.15)<1e-9);
  rejected(()->Verified2048Ranking.combine(Double.NaN,0));
  rejected(()->Verified2048Ranking.combine(0,Double.POSITIVE_INFINITY));
  rejected(()->Verified2048Ranking.combine(.9,0));
  int[] cells=new int[16];cells[0]=2;
  Board2048 board=new Board2048(cells);
  check(board.choose()==board.choose(d->0));
  check(board.chooseWithBonus(new double[]{0,0,0,0})==board.choose());
  rejected(()->board.choose(d->.5));
  rejected(()->board.chooseWithBonus(new double[]{0,0,0,Double.NaN}));
  rejected(()->board.chooseWithBonus(new double[]{0,0,0}));
  // Illegal 2048 swipes cannot be chosen by a learned bonus.
  check(board.choose(d->d==Board2048.LEFT?.4:0)!=Board2048.LEFT);
  System.out.println("PASS "+checks+" merged 2048 ranking tests");
 }
}
