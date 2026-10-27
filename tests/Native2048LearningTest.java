package org.localpilot.android;
public final class Native2048LearningTest {
 static void check(boolean ok){if(!ok)throw new AssertionError("native 2048 learning");}
 public static void main(String[]args){
  Native2048Learning model=new Native2048Learning();
  int[] cells=new int[16];cells[0]=2;cells[1]=2;
  Board2048 before=new Board2048(cells);
  int[] after=before.move(Board2048.LEFT).board.cells.clone();after[1]=2;
  Board2048 verified=new Board2048(after);
  check(before.verifies(verified,Board2048.LEFT));
  for(int i=0;i<12;i++)model.record(before,Board2048.LEFT,verified);
  check(model.evidence()==12);
  check(model.bonus()[Board2048.LEFT]>0);
  check(model.bonus()[Board2048.RIGHT]==0);
  check(Native2048Learning.decode(model.encode()).evidence()==12);
  boolean rejected=false;try{model.record(before,Board2048.LEFT,before);}catch(IllegalArgumentException expected){rejected=true;}check(rejected);
  check(model.evidence()==12);
  rejected=false;try{Native2048Learning.decode("v1;100,101;0,0;0,0;0,0");}catch(IllegalArgumentException expected){rejected=true;}check(rejected);
  check(new Board2048(new int[16]).chooseWithBonus(model.bonus())==-1);
  rejected=false;try{before.chooseWithBonus(new double[]{1,0,0,0});}catch(IllegalArgumentException expected){rejected=true;}check(rejected);
  check(before.chooseWithBonus(model.bonus())>=0);
  System.out.println("PASS verified real 2048 learning, isolation, legality and persistence");
 }
}
