package org.localpilot.android;
/** Hard limit on the sum of independently trained 2048 biases. */
public final class Verified2048Ranking {
 private Verified2048Ranking(){}
 public static double combine(double actualGame,double gradedWebLesson){
  if(!Double.isFinite(actualGame)||!Double.isFinite(gradedWebLesson)
     ||Math.abs(actualGame)>0.400001||Math.abs(gradedWebLesson)>0.400001)
   throw new IllegalArgumentException("invalid 2048 learning input");
  return Math.max(-.4,Math.min(.4,actualGame+gradedWebLesson));
 }
}
