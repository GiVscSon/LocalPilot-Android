package org.localpilot.android;

import org.localpilot.android.rl.WeightSpaceSkills;

/** Read-only bridge from real screen observations of Ari to the weight-space lab.
 *
 * No action method, no AccessibilityService, no InputPermit, no gesture dispatch.
 * Its predictions are a shadow baseline for later verified replay, never proof
 * of ascent or of a valid landing.
 */
public final class JumperWeightShadow {
 private JumperWeightShadow(){}
 public record Review(boolean usable,WeightSpaceSkills.Action original,
                      WeightSpaceSkills.Action patched,int repeatedSteers,
                      boolean intervention,boolean inputAuthorized){
  public String status(){
   if(!usable)return "weight-shadow=unknown";
   return "weight-shadow="+original+"->"+patched
     +" stalled="+repeatedSteers+(intervention?" experimental-retreat":"")
     +" input=disabled";
  }
 }
 private static final WeightSpaceSkills.Network INITIAL=
   WeightSpaceSkills.Network.program(WeightSpaceSkills.Genre.PLATFORM_RECOVERY);
 private static final WeightSpaceSkills.Network PATCHED=
   WeightSpaceSkills.patchRecovery(INITIAL);
 private static Review unknown(int repetitions){
  return new Review(false,WeightSpaceSkills.Action.STAY,
    WeightSpaceSkills.Action.STAY,repetitions,false,false);
 }
 public static Review review(JumperState s,int repetitions){
  if(s==null||!s.usable()||s.confidence<.82f||s.player==null
    ||s.player.confidence<.82f||repetitions<0||repetitions>20)
   return unknown(repetitions);
  float scale=JumperPolicy.coordinateScale(s);
  if(!Float.isFinite(scale)||scale<.20f)return unknown(repetitions);
  float width=s.roiRight-s.roiLeft;
  if(width<=0)return unknown(repetitions);
  float px=s.player.centerX(),py=s.player.centerY();
  float reachY=430*scale,reachX=390*scale;
  double left=0,right=0,stay=.55;
  for(JumperState.ObjectBox platform:s.platforms){
   if(platform==null||platform.confidence<.80f)continue;
   float dy=py-platform.centerY();
   if(dy<52*scale||dy>reachY)continue; // Upper reachable ledge only
   float dx=JumperState.horizontalDistance(px,platform.centerX(),width);
   if(Math.abs(dx)>reachX)continue;
   if(hazardAtLanding(platform,s))continue;
   double score=Math.max(.01,1-.32*Math.abs(dx)/reachX-.20*dy/reachY);
   if(Math.abs(dx)<Math.max(30*scale,s.player.width()*.45f))
    stay=Math.max(stay,score);
   else if(dx<0)left=Math.max(left,score);
   else right=Math.max(right,score);
  }
  boolean stalled=repetitions>=2;
  var input=new WeightSpaceSkills.Frame(left,stay,right,stalled?1:0,true,true);
  var old=INITIAL.predict(WeightSpaceSkills.Genre.PLATFORM_RECOVERY,input);
  var tuned=PATCHED.predict(WeightSpaceSkills.Genre.PLATFORM_RECOVERY,input);
  // No candidate path with verified safe geometry means absolutely no proposal.
  if(tuned==WeightSpaceSkills.Action.LEFT&&left<.25)tuned=WeightSpaceSkills.Action.STAY;
  if(tuned==WeightSpaceSkills.Action.RIGHT&&right<.25)tuned=WeightSpaceSkills.Action.STAY;
  return new Review(true,old,tuned,repetitions,stalled&&old!=tuned,false);
 }
 private static boolean hazardAtLanding(JumperState.ObjectBox platform,JumperState s){
  for(JumperState.ObjectBox danger:s.hazards){
   float dx=Math.abs(JumperState.horizontalDistance(platform.centerX(),
     danger.centerX(),s.roiRight-s.roiLeft));
   float dy=Math.abs(danger.centerY()-platform.centerY());
   if(dx<platform.width()*.5f+danger.width()*.55f+s.player.width()*.25f
      &&dy<danger.height()*.65f+s.player.height()*.7f)return true;
  }
  return false;
 }
}
