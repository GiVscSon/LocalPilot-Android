package org.localpilot.android;

import org.localpilot.android.rl.WeightSpaceSkills;

/**
 * Primary decision router for real arcade vision. Its modules:
 *  - exact, validated PADDLE weight transfer (same weights used by the trainer);
 *  - expert-programmed weights for Ari's verified plateau recovery;
 *  - established temporal physics for traffic/flappy until their action schemas
 *    have independent real-game training and validation.
 *
 * This class cannot dispatch input, renew permits or mark a gesture verified.
 * Every proposal still traverses PilotService's ActionGate, InputPermit,
 * window/ROI TTL and visual witness. Old physical control stays as rollback.
 */
public final class WeightSpaceGameEngine {
 private WeightSpaceGameEngine(){}
 private static final WeightSpaceSkills.Network LANE=
   WeightSpaceSkills.Network.program(WeightSpaceSkills.Genre.LANE_ESCAPE);
 private static final WeightSpaceSkills.Network FLIGHT=
   WeightSpaceSkills.Network.program(WeightSpaceSkills.Genre.GAP_FLIGHT);

 public static ArcadePolicy.Decision arcade(ArcadeObservation frame,
      ArcadePolicy.Decision physics,String installedPaddleWeights){
  if(frame==null||!frame.usable()||physics==null)
   return new ArcadePolicy.Decision(ArcadePolicy.Action.WAIT,0,0,"WeightSpace: нет надёжного кадра");
  return switch(frame.profile){
   case PADDLE -> installedPaddleWeights==null ? physics
     : PaddleTransfer.decide(frame,installedPaddleWeights);
   // The older DQN output schema is not compatible with these real controls.
   // Expert output must first agree with a real, collision-checked trajectory.
   case TRAFFIC -> traffic(frame,physics);
   case FLAPPY -> flight(frame,physics);
   default -> physics;
  };
 }
 private static ArcadePolicy.Decision traffic(ArcadeObservation frame,ArcadePolicy.Decision physics){
  if(frame.confidence<.85f||!frame.tracked||frame.right<=frame.left)return physics;
  double[] scores=laneSafety(frame);
  WeightSpaceSkills.Frame features=new WeightSpaceSkills.Frame(
    scores[0],scores[1],scores[2],0,true,true);
  WeightSpaceSkills.Action voted=LANE.predict(WeightSpaceSkills.Genre.LANE_ESCAPE,features);
  // The network can confirm already collision-checked output, but never
  // invent a lane change while the temporal controller is waiting for ACK.
  if(physics.action==ArcadePolicy.Action.LEFT&&voted==WeightSpaceSkills.Action.LEFT)
   return physics;
  if(physics.action==ArcadePolicy.Action.RIGHT&&voted==WeightSpaceSkills.Action.RIGHT)
   return physics;
  return physics;
 }
 private static double[] laneSafety(ArcadeObservation s){
  double[] safe={1,1,1};
  float laneWidth=(s.right-s.left)/3;
  int current=Math.max(0,Math.min(2,(int)((s.player.x()-s.left)/laneWidth)));
  for(int i=0;i<3;i++){
   if(Math.abs(i-current)>1){safe[i]=0;continue;}
   float center=s.left+(i+.5f)*laneWidth;
   for(ArcadeObservation.Box obstacle:s.obstacles){
    if(obstacle.left>=center+s.player.width()*.6f
      ||obstacle.right<=center-s.player.width()*.6f)continue;
    float vertical=s.player.top-obstacle.bottom;
    if(vertical>=-.04f&&vertical<.32f)
     safe[i]=Math.min(safe[i],Math.max(0,vertical/.32f));
   }
  }
  return safe;
 }
 private static ArcadePolicy.Decision flight(ArcadeObservation frame,ArcadePolicy.Decision physics){
  if(frame.confidence<.85f||!frame.tracked||frame.gapBottom<=frame.gapTop)return physics;
  double gap=(frame.gapTop+frame.gapBottom)*.5;
  double projection=frame.player.y()+frame.vy*.2;
  double waitSafety=1-Math.min(1,Math.abs(projection-gap)*3);
  double flapProjection=projection-.05;
  double flapSafety=1-Math.min(1,Math.abs(flapProjection-gap)*3);
  var features=new WeightSpaceSkills.Frame(waitSafety,flapSafety,0,0,true,true);
  // No permission to bypass the temporal controller's flap cooldown.
  FLIGHT.predict(WeightSpaceSkills.Genre.GAP_FLIGHT,features);
  return physics;
 }

 /** Only plateau recovery on a fresh, confident Ari scene may adjust the
  * existing intent. Does not bypass STOP or the four-steer progress limit.
  */
 public static JumperPolicy.Action jumper(JumperState frame,int repeatedVerifiedSteers,
      JumperPolicy.Action physics){
  if(physics==null)return JumperPolicy.Action.WAIT;
  if(frame==null||frame.player==null||repeatedVerifiedSteers<2
    ||repeatedVerifiedSteers>=4||frame.confidence<.90f)
   return physics;
  JumperWeightShadow.Review review=JumperWeightShadow.review(frame,repeatedVerifiedSteers);
  if(!review.usable()||!review.intervention()
    ||review.patched()!=WeightSpaceSkills.Action.LEFT)return physics;
  // The vision adapter has already rejected unsafe landing geometry.
  // The visual progress guard still stops if the detour makes no ascent.
  return JumperPolicy.Action.TAP_LEFT;
 }
}
