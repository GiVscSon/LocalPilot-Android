package org.localpilot.android;

import java.util.Random;
import org.localpilot.android.rl.PaddleWeightLab;

/** Schema-locked bridge: the SAME small weights trained in the native simulator
 * calculate a bounded proposal for the real PADDLE policy. No Android imports.
 * An app-level explicit opt-in is required to persist a candidate.
 */
public final class PaddleTransfer {
 private PaddleTransfer(){}
 private static final String PREFIX="LP-PADDLE-WEIGHTS-v1,";
 private static final float MAX_OFFSET=.02f;
 private static final Random NO_RANDOM=new Random(0);
 /** Calibration and evaluation have completely disjoint seeds. A simulator
  * winner is a *candidate*, never proof of visual Android gameplay. */
 public record Candidate(float offset,int trainingEpisodes,int validationEpisodes,
                         int catchesBaseline,int catchesCandidate,
                         int bricksBaseline,int bricksCandidate) {
  public boolean eligible(){
   return Float.isFinite(offset)&&Math.abs(offset)<=MAX_OFFSET
     &&trainingEpisodes>=100&&validationEpisodes>=400
     &&catchesCandidate>catchesBaseline&&bricksCandidate>bricksBaseline;
  }
  public String report(){
   return "Кандидат Карканоид: угол="+offset+" · обучение "+trainingEpisodes
     +" · отдельная оценка "+validationEpisodes+" · перехваты "+catchesBaseline+" → "+catchesCandidate
     +" · блоки "+bricksBaseline+" → "+bricksCandidate
     +" · "+(eligible()?"готов к ручному переносу":"НЕ ДОПУЩЕН");
  }
 }
 public static Candidate trainCandidate(){
  double angle=PaddleWeightLab.calibrateAngle();
  PaddleWeightLab.Weights baseline=PaddleWeightLab.Weights.followBall();
  PaddleWeightLab.Weights trained=baseline.patchToAngledImpact(angle);
  int catchesBase=0,catchesNew=0,bricksBase=0,bricksNew=0;
  // Two independently seeded hold-out cohorts; the calibration seed is 2718.
  for(long seed:new long[]{20261104L,177777L}){
   PaddleWeightLab.Summary b=PaddleWeightLab.evaluate("baseline",baseline,240,seed);
   PaddleWeightLab.Summary c=PaddleWeightLab.evaluate("candidate",trained,240,seed);
   catchesBase+=b.catches;catchesNew+=c.catches;
   bricksBase+=b.bricks;bricksNew+=c.bricks;
  }
  return new Candidate((float)angle,PaddleWeightLab.CALIBRATION_EPISODES,480,
    catchesBase,catchesNew,bricksBase,bricksNew);
 }
 /** Serialized output-bias of the immutable, fixed 2->4->1 network.
  * We deliberately reject arbitrary hidden weights or checkpoint formats. */
 public static String encode(Candidate candidate){
  if(candidate==null||!candidate.eligible())throw new IllegalArgumentException("unvalidated candidate");
  int units=Math.round(candidate.offset()*10000);
  if(units==0||Math.abs(units)>200)throw new IllegalArgumentException("degenerate patch");
  return PREFIX+units;
 }
 public static float decode(String serialized){
  if(serialized==null||!serialized.startsWith(PREFIX))throw new IllegalArgumentException("transfer schema");
  String suffix=serialized.substring(PREFIX.length());
  if(!suffix.matches("-?(0|[1-9][0-9]{0,2})"))throw new IllegalArgumentException("transfer encoding");
  int units=Integer.parseInt(suffix);
  if(units==0||Math.abs(units)>200)throw new IllegalArgumentException("transfer bound");
  return units/10000f;
 }
 /** Pure prediction from real recognized geometry. Never grants input.
  * Every invalid/stale/low-confidence input retains the original policy.
  */
 public static ArcadePolicy.Decision decide(ArcadeObservation s,String encoded){
  ArcadePolicy.Decision fallback=ArcadePolicy.next(s);
  if(s==null||s.profile!=ArcadeProfile.PADDLE||!s.usable()
    ||s.confidence<.85f||!s.tracked||s.vy<=.03f||fallback.action!=ArcadePolicy.Action.MOVE_PADDLE
    ||!Float.isFinite(s.vx)||!Float.isFinite(s.vy)||s.right-s.left<.4f
    ||s.control.width()<.04f)return fallback;
  final float offset;
  try{offset=decode(encoded);}catch(IllegalArgumentException rejected){return fallback;}
  // Map the actual game-field coordinates to the *same* normalized network
  // features used in training. The vertical scale cancels in time-to-impact.
  float width=s.right-s.left;
  double ballX=(s.player.x()-s.left)/width;
  double paddleX=(s.control.x()-s.left)/width;
  double gap=s.control.top-s.player.bottom;
  if(gap<=0||gap>.90||ballX<.014||ballX>.986||paddleX<.075||paddleX>.925)
   return fallback;
  double simY=.92-.014-gap*.80;
  double simVY=s.vy*.80;
  double simVX=s.vx/width;
  PaddleWeightLab.Frame observation=new PaddleWeightLab.Frame(
    ballX,simY,simVX,simVY,paddleX,true,true,true);
  if(!observation.usable()||!Double.isFinite(PaddleWeightLab.impact(observation)))
   return fallback;
  // Run precisely the training network, not a separate mocked test actor.
  PaddleWeightLab.Weights live=PaddleWeightLab.Weights.followBall().patchToAngledImpact(offset);
  double steering=live.steer(observation,NO_RANDOM);
  if(!Double.isFinite(steering))return fallback;
  double impact=PaddleWeightLab.impact(observation);
  // Reconstruct the target when the network output saturates. The learned
  // bias still applies; final shift from established policy is <=2% of width.
  double targetNormalized=Math.abs(steering)>.999
    ?impact-offset:paddleX+steering/6.0;
  float target=(float)(s.left+targetNormalized*width);
  float min=s.left+s.control.width()/2,max=s.right-s.control.width()/2;
  if(min>=max)return fallback;
  target=Math.max(min,Math.min(max,target));
  // Domain-shift guard: if simulator geometry disagrees with live prediction,
  // preserve the known policy and do NOT gamble on the new network.
  if(!Float.isFinite(target)||Math.abs(target-fallback.x)>.045f*width)
   return fallback;
  if(Math.abs(target-s.control.x())<Math.max(.012f,s.control.width()*.14f))
   return new ArcadePolicy.Decision(ArcadePolicy.Action.WAIT,0,0,"Перенос: платформа на цели");
  return new ArcadePolicy.Decision(ArcadePolicy.Action.MOVE_PADDLE,
    target,s.control.y(),"Обученная модель: перехват + угол отскока",120);
 }
}
