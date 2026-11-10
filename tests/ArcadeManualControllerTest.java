package org.localpilot.android;
import org.localpilot.android.rl.ArcadeEnv;
public final class ArcadeManualControllerTest {
 private static int checks;
 private static void require(boolean valid,String reason){checks++;if(!valid)throw new AssertionError(reason);}
 public static void main(String[] args){
  for(ArcadeEnv.Genre genre:ArcadeEnv.Genre.values()){
   ArcadeManualController ctl=new ArcadeManualController(genre,20261108L);
   require(ctl.controls().length==ctl.env.actions().length,
     "Every visible manual button maps to one supported action: "+genre);
   require(ctl.active()&&ctl.steps()==0, "Manual mode starts on a live original environment");
   int neutral=ctl.neutral();
   require(neutral>=0&&neutral<ctl.env.actions().length,"Neutral valid for "+genre);
   require(ctl.step(neutral),"Manual button actually advances scene: "+genre);
   require(ctl.steps()==1,"Visible control must advance physical simulation");
   require(Float.isFinite(ctl.reward()),"Synthetic reward remains finite, separate from DQN");
   ctl.env.terminated=true;
   require(!ctl.step(neutral),"Terminal state must not accept ghost button press");
   require(!ctl.press(7,neutral)&&!ctl.tick(),"Terminal state rejects holds and clock ticks");
   ctl.reset();
   require(ctl.steps()==0&&ctl.active(),"Reset must visibly restore play");
   require(ctl.actionForGesture(-1,0,.5f,.5f)==-1,"Out of bounds screen gesture rejected");
   require(ctl.actionForGesture(Float.NaN,0,.5f,.5f)==-1,"NaN gesture rejected");
   require(ctl.actionForGesture(.5f,0,Float.POSITIVE_INFINITY,.5f)==-1,"Infinite gesture rejected");
   ctl.env.truncated=true;
   require(!ctl.press(7,neutral)&&!ctl.tick(),"Truncated episode cannot restart itself");
   ctl.reset();
   require(ctl.press(7,0)&&ctl.steps()==1,"DOWN applies once immediately, even between clock ticks");
   require(ctl.holding(),"A pressed live pointer is visible to diagnostics");
   require(!ctl.press(8,1),"Second pointer cannot overwrite a held action");
   require(!ctl.release(8)&&ctl.steps()==1,"Unrelated pointer cannot release or apply an action");
   require(ctl.tick()&&ctl.steps()==2&&ctl.env.lastAction==0,"Clock repeats pointer owner's action");
   require(ctl.release(7)&&ctl.steps()==2,"UP only releases; it must not submit an extra action");
   require(!ctl.holding(),"Release clears diagnostic holding state");
   require(!ctl.release(7)&&ctl.steps()==2,"Duplicate UP does not submit an action");
   require(ctl.tick()&&ctl.env.lastAction==neutral,"Released hold uses neutral on next clock tick");
   require(ctl.press(4,0),"A released hold permits a new owner");
   ctl.cancelHold();
   require(ctl.tick()&&ctl.env.lastAction==neutral,"CANCEL releases hold without extra movement");
   require(ctl.press(3,0),"Can hold before reset");
   ctl.reset();
   require(ctl.steps()==0&&ctl.tick()&&ctl.env.lastAction==neutral,"Reset clears old pointer and action");
   ctl.stop();int stoppedSteps=ctl.steps();
   require(!ctl.active()&&!ctl.step(0)&&!ctl.tick()&&!ctl.press(9,0),"STOP rejects step, clock and new hold");
   require(ctl.steps()==stoppedSteps,"STOP freezes simulation counters");
   ctl.reset();
   require(ctl.active()&&ctl.steps()==0,"Only explicit reset can restart a stopped controller");
  }
  ArcadeManualController lanes=new ArcadeManualController(ArcadeEnv.Genre.LANES,2);
  require(lanes.actionForGesture(.5f,.6f,.2f,.6f)==0,"Swipe left => real lane left");
  require(lanes.actionForGesture(.5f,.6f,.8f,.6f)==1,"Swipe right => lane right");
  require(lanes.actionForGesture(.5f,.7f,.5f,.3f)==2,"Swipe up => jump");
  require(lanes.actionForGesture(.5f,.3f,.5f,.7f)==3,"Swipe down => slide");
  require(lanes.actionForGesture(.5f,.5f,.5f,.5f)==4,"Tap => forward");
  int startingLane=lanes.env.lane;
  require(lanes.step(0)&&lanes.env.lane==startingLane-1,
    "Live manual LEFT button actually moves game lane");
  ArcadeManualController platformer=new ArcadeManualController(ArcadeEnv.Genre.PLATFORMER,3);
  require(platformer.actionForGesture(.3f,.7f,.1f,.3f)==4,
    "Diagonal up-left emits game's real left+jump index");
  require(platformer.actionForGesture(.3f,.7f,.5f,.3f)==5,
    "Diagonal up-right emits game's real right+jump index");
  ArcadeManualController jumper=new ArcadeManualController(ArcadeEnv.Genre.JUMPER,4);
  require(jumper.actionForGesture(.6f,.5f,.1f,.5f)==0,"Jumper swipe left");
  require(jumper.actionForGesture(.1f,.5f,.9f,.5f)==1,"Jumper swipe right");
  ArcadeManualController jet=new ArcadeManualController(ArcadeEnv.Genre.JETPACK,5);
  require(jet.neutral()==0&&jet.actionForGesture(.5f,.5f,.5f,.5f)==1,
    "Jetpack hold maps to thrust and release maps to fall");
  require(jet.press(0,1)&&jet.env.lastAction==1,"Jetpack DOWN applies thrust immediately");
  int thrustSteps=jet.steps();
  require(jet.release(0)&&jet.steps()==thrustSteps,"Jetpack release must not pulse thrust again");
  require(jet.tick()&&jet.env.lastAction==0,"Jetpack release applies no thrust on next tick");
  boolean illegal=false;
  try{jet.step(99);}catch(IllegalArgumentException expected){illegal=true;}
  require(illegal,"An unknown action cannot reach emulator engine");
  boolean invalidHold=false;
  try{jet.press(4,99);}catch(IllegalArgumentException expected){invalidHold=true;}
  require(invalidHold&&jet.press(5,0),"Rejected action must not retain pointer ownership");
  System.out.println("LOCALPILOT_MANUAL_CONTROLS_PASS checks="+checks);
 }
}
