package org.localpilot.shared.harness;

import java.util.*;

/** A held-out task evaluation, not teacher imitation accuracy. No optimizer lives here. */
public final class EvaluationGate {
 public record Episode(String id,int seed,String policySha,double totalReturn,
                       boolean completed,int safetyStops,boolean verified) {
  public Episode {
   if(id==null||!id.matches("[A-Za-z0-9-]{1,80}")||policySha==null||!policySha.matches("[a-f0-9]{64}")||
      !Double.isFinite(totalReturn)||safetyStops<0)throw new IllegalArgumentException("Evaluation outcome");
  }
 }
 public record Report(boolean promote,String reason,String baselineSha,String candidateSha,
                      double baselineMedian,double candidateMedian,int pairs) {}
 public static Report compare(LearningCoordinator.Candidate candidate,List<Episode> baseline,List<Episode> next){
  String old=candidate.baseline().sha256(),sha=candidate.policy().sha256();
  String failure="";
  if(old.equals(sha)||candidate.updates()==0)failure="NO_PARAMETER_UPDATE";
  if(baseline.size()!=next.size()||baseline.size()<20)failure="INSUFFICIENT_PAIRED_EPISODES";
  Set<Integer> seeds=new HashSet<>();Set<String> episodes=new HashSet<>();
  int oldWins=0,newWins=0,oldStops=0,newStops=0;
  for(int i=0;i<Math.min(baseline.size(),next.size());i++){
   Episode a=baseline.get(i),b=next.get(i);seeds.add(a.seed());
   if(!a.verified()||!b.verified()||a.seed()!=b.seed()||!old.equals(a.policySha())||!sha.equals(b.policySha()))
    failure="UNVERIFIED_OR_UNPAIRED_EVALUATION";
   if(!episodes.add(a.id())||!episodes.add(b.id())||candidate.episodes().contains(a.id())||
      candidate.episodes().contains(b.id())||candidate.seeds().contains(a.seed())||candidate.seeds().contains(b.seed()))
    failure="TRAIN_HOLDOUT_OVERLAP";
   if(a.completed())oldWins++;if(b.completed())newWins++;
   oldStops+=a.safetyStops();newStops+=b.safetyStops();
  }
  if(seeds.size()<5)failure="INSUFFICIENT_DISTINCT_SEEDS";
  double a=median(baseline),b=median(next);
  if(failure.isEmpty()&&(newStops>oldStops||newWins<oldWins))failure="SAFETY_OR_COMPLETION_REGRESSION";
  // Relative improvement is defined even for zero or negative baseline return.
  if(failure.isEmpty()&&!(b>a+Math.max(0.05,Math.abs(a)*0.05)))failure="NO_PROVEN_RETURN_IMPROVEMENT";
  return new Report(failure.isEmpty(),failure.isEmpty()?"HELDOUT_IMPROVEMENT":failure,old,sha,a,b,baseline.size());
 }
 private static double median(List<Episode> episodes){
  if(episodes.isEmpty())return 0;
  double[] values=episodes.stream().mapToDouble(Episode::totalReturn).sorted().toArray();
  return (values[(values.length-1)/2]+values[values.length/2])/2;
 }
}
