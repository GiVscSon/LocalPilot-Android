package org.localpilot.android.rl;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/** Offline, deterministic paddle physics and targeted neural-weight experiment.
 * No Android APIs, screenshots, gesture permits, or production policy calls.
 * This is a hypothesis test, NOT an authorization for real-world input.
 */
public final class PaddleWeightLab {
 private PaddleWeightLab() {}
 public static final double DT=1.0/120, BALL_RADIUS=.014, PADDLE_Y=.92;
 private static final double HALF_PADDLE=.075, MAX_PADDLE_SPEED=.90;
 private static double clip(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
 public record Frame(double x,double y,double vx,double vy,double paddleX,
                     boolean fresh,boolean ballVisible,boolean paddleVisible) {
  public boolean usable(){return fresh&&ballVisible&&paddleVisible
    &&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(vx)
    &&Double.isFinite(vy)&&Double.isFinite(paddleX)
    &&x>=BALL_RADIUS&&x<=1-BALL_RADIUS&&y>=0&&y<=1
    &&paddleX>=HALF_PADDLE&&paddleX<=1-HALF_PADDLE;}
 }
 /** True specular side-wall reflection, even for multiple bounces. */
 public static double reflect(double x,double lo,double hi){
  if(!Double.isFinite(x)||!Double.isFinite(lo)||!Double.isFinite(hi)||hi<=lo)
   throw new IllegalArgumentException("wall geometry");
  double width=hi-lo,phase=(x-lo)%(2*width);
  if(phase<0)phase+=2*width;
  return lo+(phase<=width?phase:2*width-phase);
 }
 /** A projected landing location is usable only for a fresh, descending ball. */
 public static double impact(Frame f){
  if(f==null||!f.usable()||f.vy()<=.03||f.y()+BALL_RADIUS>=PADDLE_Y)
   return Double.NaN;
  double t=(PADDLE_Y-BALL_RADIUS-f.y())/f.vy();
  if(t<=0||t>4)return Double.NaN;
  return reflect(f.x()+f.vx()*t,BALL_RADIUS,1-BALL_RADIUS);
 }
 public interface Control {double steer(Frame f,Random random);}
 /** Analytic controller is an explicit reference: weight patch does not beat physics by itself. */
 public static Control directPhysics(){return directPhysicsAngle(0);}
 public static Control directPhysicsAngle(double offset){
  if(!Double.isFinite(offset)||Math.abs(offset)>.08)throw new IllegalArgumentException("angle offset");
  return (f,unused)->{
  if(f==null||!f.usable())return 0;
  double projected=impact(f);
  double target=Double.isFinite(projected)?projected:f.x();
  return clip((target-f.paddleX()-offset)*6,-1,1);
 };}
 /** Two inputs, four rectified hidden units, one linear output.
  * x0 = current ball-paddle error, x1 = physically predicted impact error.
  * A hand-written readout patch changes four of thirteen parameters.
  * An optional calibrated output bias changes one additional parameter.
  */
 public static final class Weights implements Control {
  private final double[] hidden,readout;
  private final double bias;
  private Weights(double[] hidden,double[] readout,double bias){
   if(hidden.length!=8||readout.length!=4)throw new IllegalArgumentException("network shape");
   for(double w:hidden)if(!Double.isFinite(w)||Math.abs(w)>16)throw new IllegalArgumentException("hidden weight");
   for(double w:readout)if(!Double.isFinite(w)||Math.abs(w)>16)throw new IllegalArgumentException("readout weight");
   if(!Double.isFinite(bias)||Math.abs(bias)>16)throw new IllegalArgumentException("bias");
   this.hidden=hidden.clone();this.readout=readout.clone();this.bias=bias;
  }
  public static Weights followBall(){return new Weights(new double[]{1,0,-1,0,0,1,0,-1},new double[]{1,-1,0,0},0);}
  public Weights patchToImpact(){
   // No training, no new observations, no random initialization: only readout edit.
   return new Weights(hidden,new double[]{0,0,1,-1},0);
  }
  /** Output-bias correction calibrated only on training episodes. */
  public Weights patchToAngledImpact(double offset){
   if(!Double.isFinite(offset)||Math.abs(offset)>.08)throw new IllegalArgumentException("angle offset");
   return new Weights(hidden,new double[]{0,0,1,-1},-offset);
  }
  public int changedParameters(Weights other){
   int changed=0;for(int i=0;i<8;i++)if(hidden[i]!=other.hidden[i])changed++;
   for(int i=0;i<4;i++)if(readout[i]!=other.readout[i])changed++;
   if(bias!=other.bias)changed++;
   return changed;
  }
  public double[] snapshot(){double[] out=Arrays.copyOf(hidden,13);System.arraycopy(readout,0,out,8,4);out[12]=bias;return out;}
  @Override public double steer(Frame f,Random random){
   if(f==null||!f.usable())return 0; // unknown frame grants no action
   double predicted=impact(f);
   double[] inputs={f.x()-f.paddleX(),(Double.isFinite(predicted)?predicted:f.x())-f.paddleX()};
   double output=0;
   for(int j=0;j<4;j++){
    double activation=Math.max(0,hidden[j*2]*inputs[0]+hidden[j*2+1]*inputs[1]);
    output+=readout[j]*activation;
   }
   return clip((output+bias)*6,-1,1);
  }
 }
 public record Episode(int catches,int bricks,int steps,boolean missed) {
  /** Game-progress proxy: bricks dominate mere survival. */
  public double score(){return bricks+.1*catches;}
 }
 /** Real 2D timestep simulation: ball motion, side/top bounces, paddle collision,
  * angle-dependent rebound, and destructible bricks. Per-episode seeded layout.
  */
 /** Domain-randomized Karkanoid-like environments, sharing the production feature schema. */
 public enum Scenario { CLASSIC, NARROW_PADDLE, FAST_BALL, BANK_SHOT, BRICK_GAPS,
                        DENSE_BRICKS, INPUT_LATENCY, HEAVY_REBOUND }
 /** Immutable visual evidence for the *synthetic* game only, never a device screenshot. */
 public record Scene(double ballX,double ballY,double paddleX,double halfPaddle,int catches,int bricks,int step,boolean[] alive){
  public Scene{alive=alive.clone();}
  @Override public boolean[] alive(){return alive.clone();}
 }
 public static Episode play(Control policy,long seed){return play(policy,seed,Scenario.CLASSIC);}
 public static Episode play(Control policy,long seed,Scenario scenario){return play(policy,seed,scenario,null);}
 public static Episode play(Control policy,long seed,Scenario scenario,java.util.function.Consumer<Scene> observer){
  if(policy==null||scenario==null)throw new IllegalArgumentException("scenario");
  Random rng=new Random(seed);
  double x=.18+.64*rng.nextDouble(),y=.34+.05*rng.nextDouble();
  double vx=(rng.nextDouble()-.5)*1.35;
  double speed=.80+.12*rng.nextDouble();
  if(scenario==Scenario.FAST_BALL)speed*=1.28;
  if(scenario==Scenario.BANK_SHOT)vx=Math.copySign(speed*.70,vx);
  double vy=Math.sqrt(speed*speed-vx*vx);
  final double halfPaddle=scenario==Scenario.NARROW_PADDLE?.053:HALF_PADDLE;
  final double paddleSpeed=scenario==Scenario.INPUT_LATENCY?.68:MAX_PADDLE_SPEED;
  final double bounceScale=scenario==Scenario.HEAVY_REBOUND?.78:.62;
  double paddle=.15+.70*rng.nextDouble();
  boolean[] alive=new boolean[24];Arrays.fill(alive,true);
  double holeRate=scenario==Scenario.BRICK_GAPS?.38:scenario==Scenario.DENSE_BRICKS?.04:.17;
  for(int i=0;i<alive.length;i++)if(rng.nextDouble()<holeRate)alive[i]=false;
  int catches=0,bricks=0;
  Random policyRng=new Random(seed^0x59b57f48ca13L);
  double move=0;
  for(int step=0;step<2400;step++){
   if(observer!=null&&step%6==0)observer.accept(new Scene(x,y,paddle,halfPaddle,catches,bricks,step,alive));
   if(scenario!=Scenario.INPUT_LATENCY||step%12==0){
    Frame frame=new Frame(x,y,vx,vy,paddle,true,true,true);
    move=policy.steer(frame,policyRng);
   }
   if(!Double.isFinite(move)||Math.abs(move)>1.0000001)throw new IllegalStateException("invalid steering command");
   paddle=clip(paddle+move*paddleSpeed*DT,HALF_PADDLE,1-HALF_PADDLE);
   double oldY=y;
   x+=vx*DT;y+=vy*DT;
   if(x<BALL_RADIUS){x=2*BALL_RADIUS-x;vx=Math.abs(vx);}
   if(x>1-BALL_RADIUS){x=2*(1-BALL_RADIUS)-x;vx=-Math.abs(vx);}
   if(y<BALL_RADIUS){y=2*BALL_RADIUS-y;vy=Math.abs(vy);}
   if(vy<0){
    for(int row=0;row<3;row++){
     double top=.09+row*.067,bottom=top+.044;
     if(!(y-BALL_RADIUS<=bottom&&y+BALL_RADIUS>=top))continue;
     boolean hit=false;
     for(int col=0;col<8;col++){
      int id=row*8+col;if(!alive[id])continue;
      double left=.025+col*.119,right=left+.100;
      if(x+BALL_RADIUS<left||x-BALL_RADIUS>right)continue;
      alive[id]=false;bricks++;hit=true;break;
     }
     if(hit){vy=Math.abs(vy);y=bottom+BALL_RADIUS;break;}
    }
   }
   if(vy>0&&oldY+BALL_RADIUS<PADDLE_Y&&y+BALL_RADIUS>=PADDLE_Y){
    if(Math.abs(x-paddle)<=halfPaddle+BALL_RADIUS){
     catches++;
     double offset=clip((x-paddle)/(halfPaddle+BALL_RADIUS),-1,1);
     vx=offset*bounceScale;vy=-Math.sqrt(Math.max(.08,speed*speed-vx*vx));
     y=PADDLE_Y-BALL_RADIUS;
    }else return new Episode(catches,bricks,step+1,true);
   }
   if(y>1)return new Episode(catches,bricks,step+1,true);
   if(bricks==24)return new Episode(catches,bricks,step+1,false);
  }
  return new Episode(catches,bricks,2400,false);
 }
 public static final class Summary {
  public final String name;public final int episodes,missed,bricks,catches;
  public final double meanScore;
  private Summary(String name,int n,int miss,int b,int c,double score){
   this.name=name;episodes=n;missed=miss;bricks=b;catches=c;meanScore=score/n;
  }
  public String json(){return String.format(Locale.ROOT,
    "{\"policy\":\"%s\",\"episodes\":%d,\"catches\":%d,\"bricks\":%d,\"misses\":%d,\"mean_score\":%.6f}",
    name,episodes,catches,bricks,missed,meanScore);
  }
 }
 public static Summary evaluate(String name,Control model,int episodes,long seed){
  if(name==null||!name.matches("[a-z_-]{1,32}")||episodes<1||episodes>10000||model==null)throw new IllegalArgumentException("evaluation");
  int catches=0,bricks=0,misses=0;double scores=0;
  for(int i=0;i<episodes;i++){
   Episode e=play(model,seed+i*7919L);
   catches+=e.catches();bricks+=e.bricks();if(e.missed())misses++;scores+=e.score();
  }
  return new Summary(name,episodes,misses,bricks,catches,scores);
 }
 public static Summary evaluate(String name,Control model,int episodes,long seed,Scenario scenario){
  if(name==null||!name.matches("[a-z_-]{1,32}")||episodes<1||episodes>10000||model==null||scenario==null)
   throw new IllegalArgumentException("evaluation");
  int catches=0,bricks=0,misses=0;double score=0;
  for(int i=0;i<episodes;i++){
   Episode e=play(model,seed+i*7919L,scenario);
   catches+=e.catches();bricks+=e.bricks;if(e.missed())misses++;score+=e.score();
  }
  return new Summary(name,episodes,misses,bricks,catches,score);
 }
 public static final long CALIBRATION_SEED=2718L;
 public static final int CALIBRATION_EPISODES=240;
 /** Search only on training seeds. Held-out test seeds must differ. */
 public static double calibrateAngle(){
  Weights original=Weights.followBall();
  double[] candidates={-.08,-.07,-.06,-.04,-.02,-.01,0,.01,.02,.04,.06,.07,.08};
  double best=-Double.MAX_VALUE,selected=0;
  for(double offset:candidates){
   Summary trained=evaluate("train",original.patchToAngledImpact(offset),CALIBRATION_EPISODES,CALIBRATION_SEED);
   if(trained.meanScore>best){best=trained.meanScore;selected=offset;}
  }
  return selected;
 }
 public static String comparisonJson(int episodes,long seed){
  if(seed==CALIBRATION_SEED)throw new IllegalArgumentException("held-out seed overlaps training");
  Weights baseline=Weights.followBall(),patched=baseline.patchToImpact();
  Control random=(f,r)->r.nextDouble()*2-1;
  Summary a=evaluate("random",random,episodes,seed);
  Summary b=evaluate("follow_ball",baseline,episodes,seed);
  Summary c=evaluate("patched_impact",patched,episodes,seed);
  Summary d=evaluate("direct_physics",directPhysics(),episodes,seed);
  double angle=calibrateAngle();
  Summary e=evaluate("tuned_impact",baseline.patchToAngledImpact(angle),episodes,seed);
  Summary f=evaluate("direct_angled_physics",directPhysicsAngle(angle),episodes,seed);
  return "{\"experiment\":\"paddle-weight-lab-v1\",\"environment\":\"synthetic-paddle-v1\","
   +"\"seed\":"+seed+",\"episodes\":"+episodes+",\"patched_weights\":"+baseline.changedParameters(patched)+","
   +"\"score_definition\":\"bricks+0.1*catches\","
   +"\"training_seed\":"+CALIBRATION_SEED+",\"training_episodes\":"+CALIBRATION_EPISODES+","
   +"\"tuned_angle_offset\":"+String.format(Locale.ROOT,"%.6f",angle)+","
   +"\"realtime_android\":false,\"visual_verified\":false,\"eligible_for_android\":false,"
   +"\"policies\":["+a.json()+","+b.json()+","+c.json()+","+d.json()+","+e.json()+","+f.json()+"]}";
 }
}
