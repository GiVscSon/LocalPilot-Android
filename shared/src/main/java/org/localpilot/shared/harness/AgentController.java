package org.localpilot.shared.harness;

import org.localpilot.shared.HarnessCoordinator;

/** Validates a proposal; the existing coordinator remains the only permit issuer. */
public final class AgentController {
 private final HarnessCoordinator owner;
 public AgentController(HarnessCoordinator owner){this.owner=owner;}
 public HarnessCoordinator.ActionPermit policy(EnvironmentAdapter.Observation observation,int action,
                                               long observedAt,long now){
  long ttl=observation.mode()==HarnessCoordinator.Mode.REALTIME_MODE?100:2500;
  if(!observation.verified()||action<0||action>1||observedAt<0||now<observedAt||now-observedAt>ttl)
   return null;
  return owner.start(HarnessCoordinator.Actor.FAST_POLICY,observation.epoch(),Policy.ACTIONS[action],
    observation.frame(),observation.pixelHash(),16,now);
 }
}
