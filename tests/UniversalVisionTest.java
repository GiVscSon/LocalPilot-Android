package org.localpilot.android;
import java.nio.file.Files;

public final class UniversalVisionTest {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static boolean box(float x,float y,float l,float t,float r,float b){return x>=l&&x<r&&y>=t&&y<b;}
 private static int scene(ArcadeProfile p,float x,float y,int[] c,float shift){
  if(p==ArcadeProfile.PADDLE){if(box(x,y,.65f,.3f,.70f,.35f))return c[0];if(box(x,y,.25f+shift,.85f,.5f+shift,.9f))return c[1];if(box(x,y,.6f,.1f,.8f,.15f))return c[2];}
  if(p==ArcadeProfile.TRAFFIC){if(box(x,y,.44f+shift,.76f,.55f+shift,.89f))return c[0];if(box(x,y,.42f,.35f,.57f,.52f))return c[1];if(box(x,y,.74f,.1f,.85f,.23f))return c[2];}
  if(p==ArcadeProfile.FLAPPY){if(box(x,y,.12f,.45f+shift,.18f,.51f+shift))return c[0];if(box(x,y,.60f,0,.73f,.34f))return c[1];if(box(x,y,.60f,.65f,.73f,1))return c[2];}
  return 0xff202020;
 }
 private static CustomVisionConfig config(ArcadeProfile p,int[] c){return switch(p){case PADDLE->new CustomVisionConfig(c,new float[]{.675f,.375f,.7f},new float[]{.325f,.875f,.12f});case TRAFFIC->new CustomVisionConfig(c,new float[]{.5f,.5f,.8f},new float[]{.82f,.43f,.16f});default->new CustomVisionConfig(c,new float[]{.15f,.66f,.66f},new float[]{.48f,.2f,.8f});};}
 public static void main(String[]args)throws Exception{
  int[][] palettes={{0xff20c0,0x20e0e0,0xff9020},{0xf0e020,0x20d030,0x3060f0},{0xffb0e0,0x305020,0xb030f0}};
  for(var p:new ArcadeProfile[]{ArcadeProfile.PADDLE,ArcadeProfile.TRAFFIC,ArcadeProfile.FLAPPY})for(int[] colors:palettes)for(int w:new int[]{160,320,480}){
   int h=w*2,l=30,t=20;CustomVisionConfig conf=config(p,colors);
   var first=CustomArcadeVision.detect(p,(x,y)->scene(p,(x-l)/(float)w,(y-t)/(float)h,colors,0),l,t,l+w,t+h,0,0,0,0,1000,null,conf);
   check(first.usable(),"calibrated detection "+p+" width="+w);
   var second=CustomArcadeVision.detect(p,(x,y)->scene(p,(x-l)/(float)w,(y-t)/(float)h,colors,p==ArcadeProfile.FLAPPY?-.02f:.03f),l,t,l+w,t+h,0,0,0,0,1100,first,conf);
   check(second.usable()&&second.tracked,"fresh calibrated tracking "+p);
   if(p==ArcadeProfile.PADDLE)check(ArcadePolicy.next(first).action==ArcadePolicy.Action.MOVE_PADDLE,"generic paddle interception");
   if(p==ArcadeProfile.TRAFFIC){check(first.obstacles.size()==2,"two colored obstacles");var d=new ArcadeController().decide(first,1000);check(d.action==ArcadePolicy.Action.LEFT,"predictive avoidance works with another palette");}
   if(p==ArcadeProfile.FLAPPY)check(Math.abs(first.gapTop-.34f)<.02f&&Math.abs(first.gapBottom-.65f)<.02f,"pipe gap found without Market colors");
   if(p!=ArcadeProfile.FLAPPY){
    var decision=new ArcadeController().decide(first,1000);var gesture=ArcadeControls.map(decision,first,p==ArcadeProfile.TRAFFIC?1:0);
    float shift=gesture.toX()<gesture.fromX()?-.04f:.04f;
    var response=CustomArcadeVision.detect(p,(x,y)->scene(p,(x-l)/(float)w,(y-t)/(float)h,colors,shift),l,t,l+w,t+h,0,0,0,0,1100,first,conf);
    var feedback=new ArcadeFeedback();long id=feedback.dispatched(decision,first,1000);feedback.acknowledge(id);
    check(feedback.observe(response,1100)==ArcadeFeedback.State.READY&&feedback.verified()==1,"pixels → controller → gesture → changed pixels → visual ACK "+p);
   }
   var missing=CustomArcadeVision.detect(p,(x,y)->0xff202020,l,t,l+w,t+h,0,0,0,0,1200,second,conf);
   check(!missing.usable()&&!new ArcadeController().decide(missing,1200).sendsInput(),"empty scene cannot issue input");
   check(conf.encode().equals(CustomVisionConfig.decode(conf.encode()).encode()),"visual config roundtrip");
  }
  int[] c=palettes[0];var conf=new CustomVisionConfig(c,new float[]{.5f,.7f,.7f},new float[]{.5f,.2f,.8f});
  var ambiguous=CustomArcadeVision.detect(ArcadeProfile.FLAPPY,(x,y)->box(x/320f,y/640f,.17f,.47f,.23f,.53f)||box(x/320f,y/640f,.77f,.47f,.83f,.53f)?c[0]:0xff202020,0,0,320,640,0,0,0,0,1000,null,conf);
  check(!ambiguous.usable(),"two equally likely players require new evidence");
  var masked=CustomArcadeVision.detect(ArcadeProfile.FLAPPY,(x,y)->scene(ArcadeProfile.FLAPPY,x/320f,y/640f,c,0),0,0,320,640,0,200,100,400,1000,null,config(ArcadeProfile.FLAPPY,c));
  check(!masked.usable(),"panel cannot become player evidence");
  try{CustomVisionConfig.decode("v1,1,NaN,0,2,0,0,3,0,0");throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){checks++;}
  var left=new ArcadePolicy.Decision(ArcadePolicy.Action.LEFT,.25f,.7f,"test",120);
  var right=new ArcadePolicy.Decision(ArcadePolicy.Action.RIGHT,.75f,.7f,"test",120);
  check(ArcadeControls.map(left,null,0).fromX()==.25f&&ArcadeControls.map(left,null,0).toX()==.25f,"touch remains stationary");
  check(ArcadeControls.map(left,null,1).fromX()==.5f&&ArcadeControls.map(left,null,1).toX()<.5f,"left swipe");
  check(ArcadeControls.map(right,null,1).toX()>.5f,"right swipe");
  var observation=CustomArcadeVision.detect(ArcadeProfile.PADDLE,(x,y)->scene(ArcadeProfile.PADDLE,x/320f,y/640f,c,0),0,0,320,640,0,0,0,0,1000,null,config(ArcadeProfile.PADDLE,c));
  var move=ArcadePolicy.next(observation);
  check(ArcadeControls.map(move,observation,0).fromX()==observation.control.x(),"drag starts at paddle");
  check(ArcadeControls.map(move,observation,2).fromX()==move.x,"direct paddle tap at target");
  String baseline=VisualGameTarget.modelSlot("same game",0,null),custom=VisualGameTarget.modelSlot("same game",0,conf.encode()),swipe=VisualGameTarget.modelSlot("same game",1,conf.encode());
  check(!baseline.equals(custom)&&!swipe.equals(custom),"control/vision changes isolate physics models");
  var store=new ArcadeLearningStore(Files.createTempDirectory("universal-settings-").resolve("model.log").toFile());store.save("org.example.game",custom,ArcadeProfile.FLAPPY,"gravity=2");
  check(store.get("org.example.game",swipe,ArcadeProfile.FLAPPY)==null,"swipe model never inherits touch calibration");
  System.out.println("Universal vision/control: "+checks+" assertions PASS; 27 palette/resolution/profile scenes");
 }
}
