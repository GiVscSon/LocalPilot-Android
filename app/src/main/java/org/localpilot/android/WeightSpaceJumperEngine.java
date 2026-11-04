package org.localpilot.android;

import org.localpilot.android.rl.WeightSpaceSkills;

/** Real-vision Ari branch of the primary WeightSpace engine.
 * A recommendation still passes through PilotService's ActionGate/permit,
 * STOP/TTL, gesture tracking and post-ACK visual progress verification.
 */
public final class WeightSpaceJumperEngine {
 private WeightSpaceJumperEngine(){}
 public static JumperPolicy.Action decide(JumperState frame,int repeatedVerifiedSteers,
      JumperPolicy.Action physics){
  if(physics==null)return JumperPolicy.Action.WAIT;
  if(frame==null||frame.player==null||repeatedVerifiedSteers<2
    ||repeatedVerifiedSteers>=4||frame.confidence<.90f)
   return physics;
  JumperWeightShadow.Review review=JumperWeightShadow.review(frame,repeatedVerifiedSteers);
  if(!review.usable()||!review.intervention()
    ||review.patched()!=WeightSpaceSkills.Action.LEFT)return physics;
  // A physically observed safe retreat is the sole new control behavior.
  // If the next gesture fails to achieve visual progress, the old guard stops it.
  return JumperPolicy.Action.TAP_LEFT;
 }
}
