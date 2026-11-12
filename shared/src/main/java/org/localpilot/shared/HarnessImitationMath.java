package org.localpilot.shared;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/** Genuine lightweight supervised policy: learns to imitate verified NES
 * model actions from real pixel-derived 8x8 grids. NO reward/goal claim.
 * Versioned weights belong to this policy, NOT to imported GGUF.
 */
public final class HarnessImitationMath {
 public static final String[] ACTIONS={"A","B","UP","DOWN","LEFT","RIGHT"};
 public static final int FEATURES=65;
 public static final int WEIGHTS=ACTIONS.length*FEATURES;
 private HarnessImitationMath(){}
 public static final class Example {
  public final String episode,grid,action;
  public final double[] x;
  public final int label;
  public Example(String episode,String grid,String action) {
   if(episode==null||episode.isBlank()||grid==null||
      !grid.matches("[0-9A-F]{8}(?:/[0-9A-F]{8}){7}"))
    throw new IllegalArgumentException("Verified 8x8 NES grid and episode required");
   this.label=index(action);
   this.episode=episode;this.grid=grid;this.action=action;
   x=new double[FEATURES];int at=0;
   for(int i=0;i<grid.length();i++){
    char c=grid.charAt(i);
    if(c!='/')x[at++]=Character.digit(c,16)/15.0;
   }
   if(at!=64)throw new IllegalArgumentException("Malformed NES observation");
   x[64]=1;
  }
 }
 public static int index(String action){
  for(int i=0;i<ACTIONS.length;i++)if(ACTIONS[i].equals(action))return i;
  throw new IllegalArgumentException("Unsupported NES action");
 }
 public static double[] scores(double[] weights,double[] x){
  if(weights==null||weights.length!=WEIGHTS||x==null||x.length!=FEATURES)
   throw new IllegalArgumentException("Invalid policy tensor shape");
  double[] logits=new double[ACTIONS.length];
  for(int k=0;k<ACTIONS.length;k++)
   for(int f=0;f<FEATURES;f++)logits[k]+=weights[k*FEATURES+f]*x[f];
  return logits;
 }
 public static int predict(double[] weights,Example example){
  double[] scores=scores(weights,example.x);
  int best=0;
  for(int i=1;i<scores.length;i++)if(scores[i]>scores[best])best=i;
  return best;
 }
 public static double accuracy(double[] weights,List<Example> data){
  if(data.isEmpty())throw new IllegalArgumentException("No evaluation samples");
  int correct=0;
  for(Example ex:data)if(predict(weights,ex)==ex.label)correct++;
  return (double)correct/data.size();
 }
 public static double majorityBaseline(List<Example> train,List<Example> test){
  int[] n=new int[ACTIONS.length];
  for(Example ex:train)n[ex.label]++;
  int best=0;
  for(int k=1;k<n.length;k++)if(n[k]>n[best])best=k;
  int good=0;
  for(Example ex:test)if(ex.label==best)good++;
  return (double)good/test.size();
 }
 public static double[] train(List<Example> verifiedExamples){
  if(verifiedExamples==null||verifiedExamples.size()<8)
   throw new IllegalArgumentException("At least eight verified demonstrations required");
  double[] weights=new double[WEIGHTS];
  List<Example> order=new ArrayList<>(verifiedExamples);
  Random rng=new Random(1817);
  for(int epoch=0;epoch<75;epoch++){
   Collections.shuffle(order,rng);
   double eta=0.11/(1.0+epoch*0.03);
   for(Example ex:order){
    double[] logits=scores(weights,ex.x);
    double peak=logits[0];
    for(double logit:logits)peak=Math.max(peak,logit);
    double den=0;
    for(int k=0;k<ACTIONS.length;k++){logits[k]=Math.exp(logits[k]-peak);den+=logits[k];}
    for(int k=0;k<ACTIONS.length;k++){
     double delta=((k==ex.label?1.0:0.0)-logits[k]/den)*eta;
     for(int f=0;f<FEATURES;f++){
      int at=k*FEATURES+f;
      weights[at]+=delta*ex.x[f]-0.00001*weights[at];
     }
    }
   }
  }
  return weights;
 }
}
