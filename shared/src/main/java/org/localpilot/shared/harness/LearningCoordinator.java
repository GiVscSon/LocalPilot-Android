package org.localpilot.shared.harness;

import java.io.IOException;
import java.util.*;

/** Real CPU Q-learning updates on explicitly eligible task transitions only. */
public final class LearningCoordinator {
 public record Candidate(Policy baseline,Policy policy,Set<String> episodes,Set<Integer> seeds,int updates) {
  public Candidate {
   baseline=baseline.copy();policy=policy.copy();episodes=Set.copyOf(episodes);seeds=Set.copyOf(seeds);
   if(updates<1||episodes.isEmpty()||seeds.isEmpty()||baseline.sha256().equals(policy.sha256()))
    throw new IllegalArgumentException("No real policy update");
  }
  @Override public Policy baseline(){return baseline.copy();}
  @Override public Policy policy(){return policy.copy();}
 }
 public Candidate train(Policy previous,List<ExperienceStore.Transition> replay,Set<Integer> holdoutSeeds){
  List<ExperienceStore.Transition> eligible=new ArrayList<>();
  Set<String> episodes=new TreeSet<>();Set<Integer> seeds=new TreeSet<>();
  for(var t:replay){
   if(!t.eligible()||!t.before().episode().startsWith("train-")||holdoutSeeds.contains(t.before().seed()))continue;
   eligible.add(t);episodes.add(t.before().episode());seeds.add(t.before().seed());
  }
  if(eligible.size()<32||episodes.size()<4)throw new IllegalStateException("Insufficient confirmed training transitions");
  eligible.sort(Comparator.comparingInt((ExperienceStore.Transition t)->t.before().seed())
    .thenComparingInt(t->t.before().frame()).thenComparingInt(ExperienceStore.Transition::action));
  Policy next=previous.copy();int updates=0;
  Random shuffle=new Random(0x4c50);
  for(int epoch=0;epoch<24;epoch++){
   Collections.shuffle(eligible,shuffle);
   for(var t:eligible)if(next.update(t,0.12))updates++;
  }
  return new Candidate(previous,next,episodes,seeds,updates);
 }
 /** Persist candidate separately. Evaluation is allowed only against this pinned predecessor. */
 public void saveCandidate(PolicyRepository repository,Candidate candidate)throws IOException {
  if(!repository.active().sha256().equals(candidate.baseline().sha256()))
   throw new IOException("Active baseline changed during training");
  repository.saveCandidate(candidate);
 }
}
