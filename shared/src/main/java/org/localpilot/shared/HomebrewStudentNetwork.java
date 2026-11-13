package org.localpilot.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;

/** Tiny real trainable 7 → 12 → 6 CPU Q-network (tanh hidden units).
 * Learns ONLY from integrity-checked real 6502 homebrew task transitions.
 * TD holdout is NOT a proven on-policy game win. This does not modify GGUF.
 */
public final class HomebrewStudentNetwork {
 public static final String SCHEMA="NES_GOAL_STUDENT_7_12_6_V1";
 public static final int INPUTS=7,HIDDEN=12,ACTIONS=6;
 public static final int WEIGHTS=INPUTS*HIDDEN+HIDDEN+HIDDEN*ACTIONS+ACTIONS;
 public static final String[] BUTTONS={"A","B","UP","DOWN","LEFT","RIGHT"};
 private static final int W1=0,B1=INPUTS*HIDDEN,W2=B1+HIDDEN,B2=W2+HIDDEN*ACTIONS;
 private HomebrewStudentNetwork(){}
 public record Prediction(String button,double value,double margin){}
 public static final class Candidate {
  public final double[] weights;
  public final double heldoutTdMae,zeroBaselineMae;
  public final int trainingTransitions,holdoutTransitions,sourceSessions;
  public final String trainingEpochs,holdoutEpoch;
  Candidate(double[] weights,double err,double baseline,int train,int test,int sessions,
    String trainingEpochs,String holdoutEpoch){
   this.weights=weights.clone();
   this.heldoutTdMae=err;this.zeroBaselineMae=baseline;
   this.trainingTransitions=train;this.holdoutTransitions=test;
   this.sourceSessions=sessions;this.trainingEpochs=trainingEpochs;this.holdoutEpoch=holdoutEpoch;
  }
  public boolean eligibleForSandboxTrial(){
   return heldoutTdMae<zeroBaselineMae*0.98 &&
     zeroBaselineMae>0.05 && trainingTransitions>=8&&holdoutTransitions>=2;
  }
 }
 private static int action(String button){
  for(int i=0;i<BUTTONS.length;i++)if(BUTTONS[i].equals(button))return i;
  throw new IllegalArgumentException("Unrecognized model action");
 }
 private static double[] features(int x,int y){
  HomebrewGoalReward.distance(x,y);
  double dx=(HomebrewGoalReward.TARGET_X-x)/128.0;
  double dy=(HomebrewGoalReward.TARGET_Y-y)/128.0;
  return new double[]{1.0,dx,dy,Math.abs(dx),Math.abs(dy),x/255.0,y/255.0};
 }
 private static double[] output(double[] w,double[] f,double[] hidden){
  if(w==null||w.length!=WEIGHTS)throw new IllegalArgumentException("Wrong student tensor shape");
  double[] scores=new double[ACTIONS];
  for(int h=0;h<HIDDEN;h++){
   double z=w[B1+h];
   for(int i=0;i<INPUTS;i++)z+=w[W1+h*INPUTS+i]*f[i];
   hidden[h]=Math.tanh(z);
  }
  for(int a=0;a<ACTIONS;a++){
   scores[a]=w[B2+a];
   for(int h=0;h<HIDDEN;h++)scores[a]+=w[W2+a*HIDDEN+h]*hidden[h];
  }
  return scores;
 }
 private static double[] output(double[] w,int x,int y){
  return output(w,features(x,y),new double[HIDDEN]);
 }
 private static double clamp(double d,double max){return Math.max(-max,Math.min(max,d));}
 private static double target(double[] w,ConsoleExperienceMemory.GoalEpisode e){
  double r=clamp(e.reward()/16.0,3.0);
  if(e.reached())return r;
  double next=-1e9;
  for(double v:output(w,e.afterX(),e.afterY()))next=Math.max(v,next);
  return clamp(r+0.8*next,4.0);
 }
 private static void learn(double[] w,ConsoleExperienceMemory.GoalEpisode e,double eta){
  int a=action(e.button());
  double[] x=features(e.beforeX(),e.beforeY()),hidden=new double[HIDDEN];
  double[] q=output(w,x,hidden);
  double error=clamp(target(w,e)-q[a],4.0);
  double[] upstream=new double[HIDDEN];
  for(int h=0;h<HIDDEN;h++)upstream[h]=
    error*w[W2+a*HIDDEN+h]*(1.0-hidden[h]*hidden[h]);
  for(int h=0;h<HIDDEN;h++){
   w[W2+a*HIDDEN+h]=clamp(w[W2+a*HIDDEN+h]+eta*error*hidden[h],12);
  }
  w[B2+a]=clamp(w[B2+a]+eta*error,12);
  for(int h=0;h<HIDDEN;h++){
   for(int i=0;i<INPUTS;i++)
    w[W1+h*INPUTS+i]=clamp(w[W1+h*INPUTS+i]+eta*upstream[h]*x[i],12);
   w[B1+h]=clamp(w[B1+h]+eta*upstream[h],12);
  }
 }
 private static double holdoutLoss(double[] w,List<ConsoleExperienceMemory.GoalEpisode> rows){
  double sum=0;
  for(var e:rows){
   double actual=output(w,e.beforeX(),e.beforeY())[action(e.button())];
   sum+=Math.abs(target(w,e)-actual);
  }
  return sum/rows.size();
 }
 private static void validate(ConsoleExperienceMemory.GoalEpisode e){
  if(e==null||e.epoch()==null||!e.epoch().matches("[1-9][0-9]{0,18}")||
    e.modelSha()==null||!e.modelSha().matches("[0-9a-f]{64}")||
    e.modelId()==null||!e.modelId().matches("[A-Za-z0-9:_.-]{1,96}")||
    e.frameAfter()-e.frameBefore()!=HomebrewGoalReward.REQUIRED_FRAMES)
   throw new IllegalArgumentException("Unverified task sample");
  HomebrewGoalReward.Result r=HomebrewGoalReward.verify(e.button(),e.frameBefore(),
    e.frameAfter(),e.beforeX(),e.beforeY(),e.afterX(),e.afterY());
  if(r.reward()!=e.reward()||r.reached()!=e.reached())
   throw new IllegalArgumentException("Cannot reproduce task reward from CPU state");
 }
 public static Candidate train(List<ConsoleExperienceMemory.GoalEpisode> verified){
  if(verified==null||verified.size()<12||verified.size()>10000)
   throw new IllegalArgumentException("Need 12-10000 verified task transitions");
  Map<String,List<ConsoleExperienceMemory.GoalEpisode>> groups=new TreeMap<>();
  for(var e:verified){validate(e);groups.computeIfAbsent(e.epoch(),k->new ArrayList<>()).add(e);}
  if(groups.size()<3)throw new IllegalStateException("Need three independent real NES sessions");
  List<String> epochs=new ArrayList<>(groups.keySet());
  String holdoutEpoch=epochs.get(epochs.size()-1);
  List<ConsoleExperienceMemory.GoalEpisode> train=new ArrayList<>(),
    holdout=new ArrayList<>();
  for(var entry:groups.entrySet()){
   if(entry.getKey().equals(holdoutEpoch))holdout.addAll(entry.getValue());
   else train.addAll(entry.getValue());
  }
  if(train.size()<8||holdout.size()<2)
   throw new IllegalStateException("Need 8 train + 2 held-out actions by ROM session");
  Random seed=new Random(13072026);
  double[] w=new double[WEIGHTS];
  for(int i=0;i<w.length;i++)w[i]=(seed.nextDouble()-.5)*.045;
  List<ConsoleExperienceMemory.GoalEpisode> order=new ArrayList<>(train);
  for(int epoch=0;epoch<110;epoch++){
   Collections.shuffle(order,new Random(1517+epoch));
   double eta=0.03/(1+epoch*.014);
   for(var e:order)learn(w,e,eta);
  }
  for(double v:w)if(!Double.isFinite(v))throw new IllegalStateException("Unstable student weights");
  double zero=0;
  for(var e:holdout)zero+=Math.abs(e.reward()/16.0);
  zero/=holdout.size();
  return new Candidate(w,holdoutLoss(w,holdout),zero,train.size(),holdout.size(),
    groups.size(),String.join(",",epochs.subList(0,epochs.size()-1)),holdoutEpoch);
 }
 /** No confident action => NONE, the existing Harness controller performs no input. */
 public static Prediction propose(double[] weights,int x,int y){
  double[] scores=output(weights,x,y);
  int best=0;double second=Double.NEGATIVE_INFINITY;
  for(int i=1;i<ACTIONS;i++)if(scores[i]>scores[best])best=i;
  for(int i=0;i<ACTIONS;i++)if(i!=best)second=Math.max(second,scores[i]);
  if(!Double.isFinite(scores[best])||scores[best]<=0.06||
     scores[best]-second<0.035)return new Prediction("NONE",scores[best],scores[best]-second);
  return new Prediction(BUTTONS[best],scores[best],scores[best]-second);
 }
}
