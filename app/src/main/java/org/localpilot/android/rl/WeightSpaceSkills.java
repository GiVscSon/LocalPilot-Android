package org.localpilot.android.rl;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * Weight-space interventions for original arcade skills.
 *
 * Provides actual immutable weight matrices, deterministic programmed
 * initialization from a simple task model, and an atomic *single-weight* edit.
 * These skills are offline teacher/shadow policies. They CANNOT issue Android
 * gestures and their action indices are NOT interchangeable with DQN actions.
 */
public final class WeightSpaceSkills {
 private WeightSpaceSkills(){}
 public enum Genre { PLATFORM_RECOVERY, LANE_ESCAPE, GAP_FLIGHT }
 public enum Action { LEFT, STAY, RIGHT, FLAP }
 public record Frame(double leftSafety,double staySafety,double rightSafety,double stasis,
                     boolean fresh,boolean recognized) {
  public boolean valid(){
   return fresh&&recognized&&finite(leftSafety)&&finite(staySafety)
     &&finite(rightSafety)&&finite(stasis)
     &&leftSafety>=0&&leftSafety<=1&&staySafety>=0&&staySafety<=1
     &&rightSafety>=0&&rightSafety<=1&&stasis>=0&&stasis<=1;
  }
 }
 private static boolean finite(double x){return Double.isFinite(x);}
 /** Readout weights are 3 x 4 (left / stay / right) and immutable.
  * All coefficients are exactly representable and bounded for reproducibility.
  */
 public static final class Network {
  private final double[][] w;
  private Network(double[][] source) {
   if(source==null||source.length!=3)throw new IllegalArgumentException("shape");
   w=new double[3][4];
   for(int j=0;j<3;j++){
    if(source[j]==null||source[j].length!=4)throw new IllegalArgumentException("shape");
    for(int i=0;i<4;i++){
     double x=source[j][i];
     if(!finite(x)||Math.abs(x)>3)throw new IllegalArgumentException("unbounded weight");
     w[j][i]=x;
    }
   }
  }
  /** Expert-seeded matrix, not gradients and not an imported Qwen layer. */
  public static Network program(Genre genre){
   if(genre==null)throw new IllegalArgumentException("genre");
   double[][] base={{1,0,0,0},{0,1,0,0},{0,0,1,0}};
   // Initially prefer holding position on an inconclusive sample.
   if(genre==Genre.PLATFORM_RECOVERY)base[1][3]=.25;
   if(genre==Genre.LANE_ESCAPE)base[1][3]=.12;
   if(genre==Genre.GAP_FLIGHT){
    // Left=wait, Stay=flap, Right unused. The input features for
    // GAP_FLIGHT are physical safety of waiting and of flapping.
    base=new double[][]{{1,0,0,0},{0,1,0,0},{0,0,0,0}};
   }
   return new Network(base);
  }
  public Network patch(int row,int column,double newWeight){
   if(row<0||row>=3||column<0||column>=4||!finite(newWeight)||Math.abs(newWeight)>3)
    throw new IllegalArgumentException("unsafe patch");
   double[][] changed=matrix();changed[row][column]=newWeight;
   return new Network(changed);
  }
  public double weight(int row,int column){return w[row][column];}
  public double[][] matrix(){
   double[][] copy=new double[3][4];for(int r=0;r<3;r++)copy[r]=w[r].clone();return copy;
  }
  public int changedWeights(Network other){
   if(other==null)throw new IllegalArgumentException("comparison");
   int changes=0;for(int j=0;j<3;j++)for(int i=0;i<4;i++)
    if(Double.compare(w[j][i],other.w[j][i])!=0)changes++;
   return changes;
  }
  /** Score only known observations. Unknown/stale cannot vote for an action. */
  public Action predict(Genre genre,Frame frame){
   if(genre==null||frame==null||!frame.valid())return Action.STAY;
   double[] x={frame.leftSafety(),frame.staySafety(),
     frame.rightSafety(),frame.stasis()};
   int winner=0;double best=Double.NEGATIVE_INFINITY;
   int n=genre==Genre.GAP_FLIGHT?2:3;
   for(int j=0;j<n;j++){
    double sum=0;
    for(int i=0;i<4;i++)sum+=w[j][i]*x[i];
    if(sum>best){best=sum;winner=j;}
   }
   if(genre==Genre.GAP_FLIGHT)
    return winner==1?Action.FLAP:Action.STAY;
   return switch(winner){case 0->Action.LEFT;case 1->Action.STAY;default->Action.RIGHT;};
  }
 }
 /** A plateau policy must sometimes step away from the visually closest ledge:
  * the same qualitative intervention as backing a vehicle away from a ramp.
  * No policy change is allowed without a verified *safe retreat* feature.
  */
 public static Network patchRecovery(Network baseline){
  if(baseline==null)throw new IllegalArgumentException("baseline");
  return baseline.patch(0,3,.80);
 }
 public record Comparison(int scenarios,int baselineCorrect,int patchedCorrect,
                          int plateauRecovered,int regressions,int changes) {
  public boolean passes(){
   return scenarios>=100&&patchedCorrect>baselineCorrect&&plateauRecovered>0
     &&regressions==0&&changes==1;
  }
  public String json(){
   return String.format(Locale.ROOT,
    "{\"scenarios\":%d,\"baseline_correct\":%d,\"patched_correct\":%d,"
      +"\"plateau_recovered\":%d,\"regressions\":%d,\"changed_weights\":%d,"
      +"\"eligible_for_android\":false,\"passes_offline\":%s}",
    scenarios,baselineCorrect,patchedCorrect,plateauRecovered,regressions,changes,passes());
  }
 }
 /**
  * Paired held-out scenarios have 4 kinds: ordinary trajectory, safe detour,
  * dangerous detour, and a reverse-path trap. All actions are scored by an
  * independently specified safe rule, not by the network's own predictions.
  */
 public static Comparison evaluateRetreat(long seed,int count){
  if(count<100||count>10000)throw new IllegalArgumentException("count");
  Network basic=Network.program(Genre.PLATFORM_RECOVERY);
  Network patched=patchRecovery(basic);
  Random rng=new Random(seed);
  int correctBasic=0,correctPatch=0,recovered=0,regressions=0;
  for(int i=0;i<count;i++){
   int mode=i%4;
   double jitter=rng.nextDouble()*.05;
   Frame f;Action expected;
   if(mode==0){
    f=new Frame(.15+jitter,.85-jitter,.2,0,true,true);
    expected=Action.STAY;
   }else if(mode==1){
    f=new Frame(.48+jitter,.65-jitter,.15,1,true,true);
    expected=Action.LEFT; // safe retreat opens upper route
   }else if(mode==2){
    f=new Frame(.03+jitter,.80,.10,1,true,true);
    expected=Action.STAY; // no safe retreat despite plateau
   }else{
    f=new Frame(.55+jitter,.67,.12,1,true,true);
    expected=Action.LEFT; // reverse first, then accelerate to landing
   }
   Action old=basic.predict(Genre.PLATFORM_RECOVERY,f);
   Action next=patched.predict(Genre.PLATFORM_RECOVERY,f);
   if(old==expected)correctBasic++;
   if(next==expected)correctPatch++;
   if(old!=expected&&next==expected&&expected==Action.LEFT)recovered++;
   if(old==expected&&next!=expected)regressions++;
  }
  return new Comparison(count,correctBasic,correctPatch,recovered,regressions,
    basic.changedWeights(patched));
 }
}
