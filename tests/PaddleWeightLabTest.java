package org.localpilot.android.rl;

import java.nio.file.*;
import java.util.Arrays;
import java.util.Random;

public final class PaddleWeightLabTest {
 private static int checks;
 private static void ok(boolean b,String reason){checks++;if(!b)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  var baseline=PaddleWeightLab.Weights.followBall();
  var patched=baseline.patchToImpact();
  ok(baseline.changedParameters(patched)==4,"only four readout weights may change");
  double[] initial=baseline.snapshot(),modified=patched.snapshot();
  ok(Arrays.equals(Arrays.copyOf(initial,8),Arrays.copyOf(modified,8)),"hidden weights preserved");
  ok(Arrays.equals(initial,baseline.snapshot()),"patch must not mutate baseline");
  var frame=new PaddleWeightLab.Frame(.6,.5,.45,.5,.5,true,true,true);
  double impact=PaddleWeightLab.impact(frame);
  ok(impact>.8&&impact<1,"landing prediction reflects right wall");
  ok(patched.steer(frame,new Random(2))>baseline.steer(frame,new Random(2)),"patch uses impact not ball position");
  ok(Math.abs(patched.steer(frame,new Random(2))-PaddleWeightLab.directPhysics().steer(frame,new Random(2)))<1e-9,
    "patched readout must exactly implement analytic impact controller");
  ok(PaddleWeightLab.reflect(1.6,.01,.99)<.4,"wall reflection");
  var missing=new PaddleWeightLab.Frame(.6,.5,.45,.5,.5,false,true,true);
  ok(patched.steer(missing,new Random(2))==0,"stale frame never steers");
  var hidden=new PaddleWeightLab.Frame(.6,.5,.45,.5,.5,true,true,false);
  ok(patched.steer(hidden,new Random(2))==0,"hidden paddle never steers");
  var upward=new PaddleWeightLab.Frame(.6,.5,.45,-.5,.5,true,true,true);
  ok(Double.isNaN(PaddleWeightLab.impact(upward)),"no invented landing while ascending");
  PaddleWeightLab.Episode e1=PaddleWeightLab.play(patched,100),e2=PaddleWeightLab.play(patched,100);
  ok(e1.equals(e2),"seeded physics must reproduce exact trajectory");
  int episodes=240;long seed=20261104L;
  var random=PaddleWeightLab.evaluate("random",(f,r)->r.nextDouble()*2-1,episodes,seed);
  var follow=PaddleWeightLab.evaluate("follow_ball",baseline,episodes,seed);
  var changed=PaddleWeightLab.evaluate("patched_impact",patched,episodes,seed);
  var physics=PaddleWeightLab.evaluate("direct_physics",PaddleWeightLab.directPhysics(),episodes,seed);
  System.out.printf("PADDLE_LAB seed=%d episodes=%d catches random=%d follow=%d patched=%d bricks random=%d follow=%d patched=%d%n",
    seed,episodes,random.catches,follow.catches,changed.catches,random.bricks,follow.bricks,changed.bricks);
  ok(changed.catches>follow.catches,"patched interception must improve on held-out seeded trajectories");
  ok(changed.catches==physics.catches&&changed.bricks==physics.bricks&&changed.missed==physics.missed,
    "no performance claim above the direct physics reference");
  ok(changed.catches>random.catches,"patched interception must improve on random steering");
  String report=PaddleWeightLab.comparisonJson(episodes,seed);
  ok(report.contains("\"patched_weights\":4")&&report.contains("\"visual_verified\":false")
    &&report.contains("\"eligible_for_android\":false"),"report may not authorize Android gameplay");
  Path output=Path.of("build/reports/paddle-weight-lab.json");Files.createDirectories(output.getParent());Files.writeString(output,report+"\n");
  ok(Files.size(output)>100,"report written");
  System.out.println("PADDLE_WEIGHT_LAB_PASS checks="+checks+" report="+output);
 }
}
