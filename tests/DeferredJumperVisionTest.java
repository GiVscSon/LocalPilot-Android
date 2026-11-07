package org.localpilot.android;

/** Explicitly calibrated synthetic enemy scene through the real detector and feedback path. */
public final class DeferredJumperVisionTest {
 private static int assertions;
 private static final int W=854,H=1280,PLAYER=0x20dc50,PLATFORM=0x2050dc,ENEMY=0xdc2050;
 private static final JumperVision.ColorProfile COLORS=new JumperVision.ColorProfile(PLAYER,PLATFORM,ENEMY);
 private static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
 private static final class Frame implements PixelBoardReader.Pixels {
  final int[] rgb=new int[W*H];
  Frame(int x,int bottom){
   java.util.Arrays.fill(rgb,0xfaf8ef);rect(100,100,220,120,PLATFORM);rect(650,950,770,970,PLATFORM);
   rect(470,600,550,640,ENEMY);rect(x-20,bottom-60,x+20,bottom,PLAYER);
  }
  private void rect(int l,int t,int r,int b,int color){for(int y=t;y<b;y++)java.util.Arrays.fill(rgb,y*W+l,y*W+r,color);}
  public int rgb(int x,int y){return rgb[y*W+x];}
 }
 private static JumperState detect(Frame frame,long at,JumperState before){return JumperVision.detect(frame,W,H,0,0,W,H,0,0,0,0,at,before,COLORS,true);}
 public static void main(String[] args){
  JumperState baseline=detect(new Frame(400,540),900,null);
  JumperState falling=detect(new Frame(400,560),1000,baseline);
  check(falling.platforms.size()==2&&falling.hazards.size()==1,"Real calibrated detector sees two ledges and one enemy");
  check(!falling.usable()&&falling.usableForMechanics(true),"Enemy scene is usable only in explicit mechanic mode");
  check(falling.playerVy>0&&JumperPolicy.nextAction(falling,false,true,false)==JumperPolicy.Action.TAP_RIGHT,"Measured falling motion selects reachable enemy head");
  JumperFeedback feedback=new JumperFeedback();feedback.configureMechanics(false,true);
  long token=feedback.dispatched(JumperPolicy.Action.TAP_RIGHT,falling,1000);feedback.acknowledge(token,1080);
  JumperState contact=detect(new Frame(510,600),1100,falling);feedback.observe(contact,1100);
  check(feedback.verifiedStomps()==0,"Detected contact does not assert a completed stomp");
  JumperState up=detect(new Frame(510,580),1200,contact);feedback.observe(up,1200);
  check(up.playerVy<0&&feedback.verifiedStomps()==0,"One measured upward frame remains provisional");
  JumperState up2=detect(new Frame(510,570),1240,up);feedback.observe(up2,1240);
  check(feedback.verifiedStomps()==1,"Two real detector observations confirm the post-contact bounce");
  try{JumperVision.detect(new Frame(400,560),W,H,0,0,W,H,0,0,0,0,1000,null,null,true);throw new AssertionError("Uncalibrated stomp detection was accepted");}
  catch(IllegalArgumentException expected){assertions++;}
  JumperState occluded=JumperVision.detect(new Frame(400,560),W,H,0,0,W,H,375,495,425,565,1000,null,COLORS,true);
  check(!occluded.usableForMechanics(true),"An overlay occluding the player does not become a stomp action frame");
  System.out.println("Deferred Jumper vision: "+assertions+" assertions PASS; calibrated synthetic pixels only");
 }
}
