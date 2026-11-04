package org.localpilot.android.web;
/** Explicit outcome evidence. A changed picture or Android gesture ACK is not a reward. */
public final class GoalReward {
 public enum Source{UNKNOWN,MANUAL,PROGRESS,DISTANCE}
 public final Source source;public final float value;public final double before,after,scale;
 private GoalReward(Source source,float value,double before,double after,double scale){this.source=source;this.value=value;this.before=before;this.after=after;this.scale=scale;}
 public static GoalReward unknown(){return new GoalReward(Source.UNKNOWN,Float.NaN,Double.NaN,Double.NaN,Double.NaN);}
 public static GoalReward manual(float reward){if(!Float.isFinite(reward)||Math.abs(reward)>1)throw new IllegalArgumentException("reward range");return new GoalReward(Source.MANUAL,reward,Double.NaN,Double.NaN,Double.NaN);}
 public static GoalReward metric(Source mode,double before,double after,double scale){
  if(mode!=Source.PROGRESS&&mode!=Source.DISTANCE||!Double.isFinite(before)||!Double.isFinite(after)||!Double.isFinite(scale)||scale<=0||Math.abs(before)>1e12||Math.abs(after)>1e12||scale>1e12)throw new IllegalArgumentException("goal metric");
  double delta=(mode==Source.PROGRESS?after-before:before-after)/scale;
  return new GoalReward(mode,(float)Math.max(-1,Math.min(1,delta)),before,after,scale);
 }
 public static GoalReward observed(Source mode,double before,double after,double scale,boolean interrupted,long actionEnd,long measuredAt,long now){
  if(interrupted||!Double.isFinite(before)||!Double.isFinite(after)||actionEnd<0||measuredAt<actionEnd||measuredAt-actionEnd<200||now<measuredAt||now-measuredAt>450)return unknown();
  return metric(mode,before,after,scale);
 }
 public static double parseMetricText(String text){
  if(text==null||text.length()>128)throw new IllegalArgumentException("metric text");String s=text.trim().replace('\u00a0',' ').replace('\u202f',' ').replaceAll(" +"," ");
  if(!s.matches("-?(?:[0-9]+|[0-9]{1,3}(?: [0-9]{3})+)(?:[.,][0-9]+)?"))throw new IllegalArgumentException("metric is not a single number");double value=Double.parseDouble(s.replace(" ","").replace(',','.'));if(!Double.isFinite(value)||Math.abs(value)>1e12)throw new IllegalArgumentException("metric bounds");return value;
 }
 public boolean known(){return source!=Source.UNKNOWN;}
 public String encode(){return source+","+value+","+before+","+after+","+scale;}
 public static GoalReward decode(String text){
  String[] p=text.split(",",-1);if(p.length!=5)throw new IllegalArgumentException("reward fields");
  Source s=Source.valueOf(p[0]);float v=Float.parseFloat(p[1]);double b=Double.parseDouble(p[2]),a=Double.parseDouble(p[3]),scale=Double.parseDouble(p[4]);
  GoalReward r=s==Source.UNKNOWN?unknown():s==Source.MANUAL?manual(v):metric(s,b,a,scale);
  if(!r.encode().equals(text))throw new IllegalArgumentException("reward evidence mismatch");return r;
 }
}
