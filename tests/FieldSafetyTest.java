package org.localpilot.android;
import java.util.List;
public final class FieldSafetyTest {
 private static int assertions;
 private static void check(boolean ok,String reason){assertions++;if(!ok)throw new AssertionError(reason);}
 private static BubblePanelSafety.Area area(int l,int t,int r,int b){return new BubblePanelSafety.Area(l,t,r,b);}
 private static JumperState jumper(float y,float scroll,long at){
  var player=new JumperState.ObjectBox(JumperState.Kind.PLAYER,390,y-30,450,y+30,.9f);
  var platforms=List.of(
   new JumperState.ObjectBox(JumperState.Kind.PLATFORM,100,190,240,210,.9f),
   new JumperState.ObjectBox(JumperState.Kind.PLATFORM,320,430,460,450,.9f),
   new JumperState.ObjectBox(JumperState.Kind.PLATFORM,400,760,540,780,.9f),
   new JumperState.ObjectBox(JumperState.Kind.PLATFORM,270,920,400,940,.9f),
   new JumperState.ObjectBox(JumperState.Kind.PLATFORM,580,1080,700,1100,.9f));
  return new JumperState(854,1280,0,0,854,1280,at,player,platforms,List.of(),.95f,0,0,scroll);
 }
 public static void main(String[] args){
  var roi=area(22,286,2129,3132);
  var panel=area(0,62,310,470);
  check(BubblePanelSafety.blocks(roi,panel),"real fullscreen ROI: expanded panel overlaps ceiling");
  var safe=area(0,24,2136,3176);
  var compact=area(0,62,300,130);
  var moved=BubblePanelSafety.dock(safe,roi,compact,8);
  check(moved!=null&&!BubblePanelSafety.blocks(roi,moved),"compact STOP must dock outside real bubble areas");
  check(BubblePanelSafety.blocks(roi,area(890,2700,1150,2800)),
    "center launcher may never be hidden");
  check(BubblePanelSafety.dock(safe,roi,area(0,0,2200,2900),8)==null,
    "large STOP cannot be silently marked safe");
  var guard=new JumperProgressGuard();
  check(guard.observe(jumper(800,0,1000),false),"initial observation");
  for(int i=1;i<4;i++)check(guard.observe(jumper(800-2*i,0,1000+i*180),true),
    "up/down on same platform cannot be called successful climbing");
  check(!guard.observe(jumper(797,0,1800),true),"four lateral gestures with no ascent must pause");
  guard.reset();guard.observe(jumper(800,0,2000),false);
  for(int i=1;i<=3;i++)check(guard.observe(jumper(790,0,2000+i*180),true),"pre-progress");
  check(guard.observe(jumper(720,0,2900),false)&&guard.repeatedSteers()==0,
    "visually observed ascent clears stagnation");
  for(int i=1;i<=3;i++)guard.observe(jumper(718,0,2900+i*180),true);
  check(guard.observe(jumper(718,80,3700),false)&&guard.repeatedSteers()==0,
    "platform scroll is an independent ascent witness");
  System.out.println("Real-field bubble/ARI safety: "+assertions+" checks PASS");
 }
}