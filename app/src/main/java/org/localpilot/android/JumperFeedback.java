package org.localpilot.android;

/** Own gesture ACK plus fresh directional motion; never rewards a pre-dispatch frame. */
public final class JumperFeedback {
 public enum State { READY, WAITING, VERIFIED, SCENE_PROGRESS, RETRY, PAUSED }
 private long serial,token,dispatchedAt,verified;
 private float startX;
 private JumperState before;private long progressFrame=-1,sceneProgress;private float progressShift;
 private JumperPolicy.Action action;
 private boolean acknowledged,paused,failedWithAck,observedResponse;
 private int failures;
 public void reset(){serial++;token=0;action=null;acknowledged=paused=failedWithAck=observedResponse=false;failures=0;verified=sceneProgress=0;before=null;progressFrame=-1;}
 public long dispatched(JumperPolicy.Action next,float x,long now){
  if(next==null||next==JumperPolicy.Action.WAIT||!Float.isFinite(x)||now<0)throw new IllegalArgumentException("jumper dispatch");
  if(action!=null||paused)throw new IllegalStateException("jumper feedback pending or paused");
  before=null;progressFrame=-1;action=next;startX=x;dispatchedAt=now;acknowledged=false;failedWithAck=false;observedResponse=false;token=++serial;return token;
 }
 public long dispatched(JumperPolicy.Action next,JumperState observation,long now){
  if(observation==null||!observation.usable()||observation.timestampMs>now||now-observation.timestampMs>300)throw new IllegalArgumentException("fresh jumper baseline required");
  long id=dispatched(next,observation.player.centerX(),now);before=observation;return id;
 }
 public void started(long id,long now){if(id==token&&action!=null&&now>=dispatchedAt)dispatchedAt=now;}
 public boolean acknowledge(long id){if(id!=token||action==null)return false;acknowledged=true;return true;}
 public State observe(JumperState after,long now){
  if(paused)return State.PAUSED;
  if(action==null)return State.READY;
  boolean fresh=acknowledged&&after!=null&&after.usable()&&after.timestampMs>dispatchedAt
    &&after.timestampMs-dispatchedAt<=650&&after.timestampMs<=now&&now-after.timestampMs<=300;
  if(fresh){
   observedResponse=true;
   float dx=after.player.centerX()-startX,minimum=Math.max(2,7*JumperPolicy.coordinateScale(after));
   boolean moved=action==JumperPolicy.Action.TAP_LEFT?dx<=-minimum:dx>=minimum;
   if(moved){verified++;failures=0;clear();return State.VERIFIED;}
   float shift=worldShift(after);
   if(Math.abs(dx)<minimum&&Float.isFinite(shift)){
    if(progressFrame>=0&&after.timestampMs-progressFrame>=40&&shift>=progressShift-2){
     // A camera can move without this tap: release the wait, but do not reward steering.
     sceneProgress++;failures=0;failedWithAck=false;clear();return State.SCENE_PROGRESS;
    }
    if(progressFrame<0){progressFrame=after.timestampMs;progressShift=shift;}
   }else progressFrame=-1;
  }
  if(now<dispatchedAt||now-dispatchedAt<650)return State.WAITING;
  failedWithAck=acknowledged&&observedResponse;clear();failures++;
  if(failures>=3){paused=true;return State.PAUSED;}
  return State.RETRY;
 }
 private float worldShift(JumperState after){
  if(before==null||before.timestampMs>dispatchedAt||dispatchedAt-before.timestampMs>300
    ||before.frameWidth!=after.frameWidth||before.frameHeight!=after.frameHeight
    ||before.roiLeft!=after.roiLeft||before.roiTop!=after.roiTop||before.roiRight!=after.roiRight||before.roiBottom!=after.roiBottom
    ||before.platforms.size()>128||after.platforms.size()>128)return Float.NaN;
  float scale=JumperPolicy.coordinateScale(after),tolerance=Math.max(2,4*scale);
  float edge=Math.max(before.player.width(),12*scale);
  if(action==JumperPolicy.Action.TAP_LEFT?before.player.left-before.roiLeft>edge:before.roiRight-before.player.right>edge)return Float.NaN;
  if(Math.abs(before.player.width()-after.player.width())>tolerance||Math.abs(before.player.height()-after.player.height())>tolerance)return Float.NaN;
  float[] shifts=new float[before.platforms.size()];boolean[] used=new boolean[after.platforms.size()];int count=0;
  for(JumperState.ObjectBox a:before.platforms){
   if(a.confidence<.7f)continue;int found=-1;float delta=0;
   for(int i=0;i<after.platforms.size();i++){var b=after.platforms.get(i);
    if(used[i]||b.confidence<.7f||Math.abs(a.centerX()-b.centerX())>tolerance||Math.abs(a.width()-b.width())>tolerance||Math.abs(a.height()-b.height())>tolerance)continue;
    float dy=b.centerY()-a.centerY();if(dy<Math.max(3,5*scale)||dy>after.frameHeight*.15f)continue;
    if(found>=0){found=-2;break;}found=i;delta=dy;
   }
   if(found>=0){used[found]=true;shifts[count++]=delta;}
  }
  if(count<3||count*2<Math.max(before.platforms.size(),after.platforms.size()))return Float.NaN;
  java.util.Arrays.sort(shifts,0,count);float median=shifts[count/2];int agreeing=0;
  for(int i=0;i<count;i++)if(Math.abs(shifts[i]-median)<=tolerance)agreeing++;
  if(agreeing<3||agreeing<Math.max(before.platforms.size(),after.platforms.size())*.8f||after.player.centerY()-before.player.centerY()-median>=-Math.max(3,5*scale))return Float.NaN;
  return median;
 }
 private void clear(){token=0;action=null;acknowledged=false;before=null;progressFrame=-1;}
 public void cancel(long id){if(id==token){clear();failedWithAck=false;}}
 public boolean paused(){return paused;}
 /** True only if own ACK and a usable post-dispatch observation actually existed. */
 public boolean failedWithAck(){return failedWithAck;}
 public String report(){return "JUMPER FEEDBACK: pending="+(action==null?"none":action)+" ack="+acknowledged+" failures="+failures+" verified="+verified+" scene_progress="+sceneProgress+" paused="+paused;}
}
