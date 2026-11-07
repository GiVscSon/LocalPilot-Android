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
 private boolean wrapping=true,stompEnemies,farmEdgeWraps;
 private final float[] worldShifts=new float[128];
 private final boolean[] worldUsed=new boolean[128];
 private JumperState stompOrigin;
 private JumperState.ObjectBox stompTarget;
 private long stompToken,stompStartedAt,stompAckAt=-1,stompLastFrame=-1,contactAt=-1,bounceAt=-1,verifiedStomps;
 private boolean stompAck;
 private float contactBottom,contactTop,contactScroll,bounceLift;
 /** Call after reset and before dispatch, only for an explicitly calibrated mechanic. */
 public void configureMechanics(boolean allowWrapAround,boolean stompEnemies,boolean farmEdgeWraps){
  if(action!=null)throw new IllegalStateException("jumper plan pending");
  wrapping=allowWrapAround;this.stompEnemies=stompEnemies;this.farmEdgeWraps=farmEdgeWraps;clearStomp();
 }
 public void configureMechanics(boolean allowWrapAround,boolean stompEnemies){configureMechanics(allowWrapAround,stompEnemies,false);}
 public long verifiedStomps(){return verifiedStomps;}
 public boolean awaitingStompProof(){return stompOrigin!=null;}

 public void reset(){serial++;token=0;action=null;acknowledged=paused=failedWithAck=observedResponse=false;failures=0;verified=sceneProgress=0;before=null;progressFrame=-1;wrapping=true;stompEnemies=farmEdgeWraps=false;verifiedStomps=0;clearStomp();}
 public long dispatched(JumperPolicy.Action next,float x,long now){
  if(next==null||next==JumperPolicy.Action.WAIT||!Float.isFinite(x)||now<0)throw new IllegalArgumentException("jumper dispatch");
  if(action!=null||paused)throw new IllegalStateException("jumper feedback pending or paused");
  before=null;progressFrame=-1;action=next;startX=x;dispatchedAt=now;acknowledged=false;failedWithAck=false;observedResponse=false;token=++serial;return token;
 }
 public long dispatched(JumperPolicy.Action next,JumperState observation,long now){
  if(observation==null||!observation.usableForMechanics(stompEnemies)||observation.timestampMs>now||now-observation.timestampMs>300)throw new IllegalArgumentException("fresh jumper baseline required");
  long id=dispatched(next,observation.player.centerX(),now);before=observation;
  JumperState.ObjectBox target=stompEnemies?JumperPolicy.selectedStompTarget(observation,wrapping,farmEdgeWraps):null;
  if(target!=null)watchStomp(id,observation,target,now);
  return id;
 }
 public void started(long id,long now){if(id==token&&action!=null&&now>=dispatchedAt){dispatchedAt=now;if(id==stompToken)stompStartedAt=now;}}
 public boolean acknowledge(long id){if(id!=token||action==null)return false;acknowledged=true;return true;}
 /** Timestamped own ACK is required for a stomp proof; the legacy overload verifies steering only. */
 public boolean acknowledge(long id,long now){
  if(id!=token||action==null||now<dispatchedAt)return false;
  acknowledged=true;if(id==stompToken&&!stompAck){stompAck=true;stompAckAt=now;}return true;
 }
 public State observe(JumperState after,long now){
  if(paused)return State.PAUSED;
  boolean stompProgress=observeStomp(after,now);
  if(action==null)return State.READY;
  if(stompProgress&&acknowledged){sceneProgress++;failures=0;failedWithAck=false;clear();return State.SCENE_PROGRESS;}
  boolean fresh=acknowledged&&after!=null&&after.usableForMechanics(stompEnemies)&&after.timestampMs>dispatchedAt
    &&after.timestampMs-dispatchedAt<=650&&after.timestampMs<=now&&now-after.timestampMs<=300;
  if(fresh){
   observedResponse=true;
   float dx=before==null?after.player.centerX()-startX:motionDelta(before,after);
   float minimum=Math.max(2,7*JumperPolicy.coordinateScale(after));
   boolean plausible=Float.isFinite(dx)&&Math.abs(dx)<=(after.roiRight-after.roiLeft)*.35f;
   boolean moved=plausible&&(action==JumperPolicy.Action.TAP_LEFT?dx<=-minimum:dx>=minimum);
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
 private float motionDelta(JumperState source,JumperState after){
  float observed=JumperState.observedDeltaX(source,after);
  if(!wrapping && Float.isFinite(observed)){
   float direct=after.player.centerX()-source.player.centerX();
   if(Math.abs(direct-observed)>1)return Float.NaN;
  }
  return observed;
 }
 private void watchStomp(long id,JumperState observation,JumperState.ObjectBox target,long now){
  // Keep an already witnessed contact through a following horizontal correction.
  if(stompOrigin!=null&&contactAt>=0&&now>=stompStartedAt&&now-stompStartedAt<=650
    &&matchingEnemy(observation)!=null)return;
  clearStomp();stompOrigin=observation;stompTarget=target;stompToken=id;stompStartedAt=now;
 }
 private void clearStomp(){stompOrigin=null;stompTarget=null;stompToken=0;stompAck=false;stompAckAt=stompLastFrame=contactAt=bounceAt=-1;}
 private JumperState.ObjectBox matchingEnemy(JumperState after){
  if(stompOrigin==null||stompTarget==null||!JumperState.sameGeometry(stompOrigin,after))return null;
  float scale=JumperPolicy.coordinateScale(after),seconds=(after.timestampMs-stompOrigin.timestampMs)/1000f;
  if(seconds<0||!Float.isFinite(after.scrollVy))return null;
  float expectedTop=stompTarget.top+after.scrollVy*seconds;
  float xt=Math.max(16*scale,stompTarget.width()*.65f),yt=Math.max(16*scale,stompTarget.height()*.65f);
  JumperState.ObjectBox found=null;
  for(int i=0;i<after.hazards.size();i++){
   JumperState.ObjectBox candidate=after.hazards.get(i);
   float dx=wrapping?JumperState.horizontalDistance(stompTarget.centerX(),candidate.centerX(),after.roiRight-after.roiLeft)
     :candidate.centerX()-stompTarget.centerX();
   if(candidate.confidence<.75f||Math.abs(dx)>xt||Math.abs(candidate.top-expectedTop)>yt
     ||Math.abs(candidate.width()-stompTarget.width())>stompTarget.width()*.25f
     ||Math.abs(candidate.height()-stompTarget.height())>stompTarget.height()*.25f)continue;
   if(found!=null)return null;
   found=candidate;
  }
  return found;
 }
 private boolean observeStomp(JumperState after,long now){
  if(stompOrigin==null)return false;
  if(now<stompStartedAt||now-stompStartedAt>650){clearStomp();return false;}
  if(after==null||!after.usableForMechanics(true)||!JumperState.sameGeometry(stompOrigin,after)){
   clearStomp();return false;
  }
  if(!stompAck||after.timestampMs<=stompAckAt||after.timestampMs<=stompStartedAt||after.timestampMs<=stompLastFrame
    ||after.timestampMs>now||now-after.timestampMs>300)return false;
  stompLastFrame=after.timestampMs;
  float relativeVy=after.playerVy-after.scrollVy,tolerance=JumperPolicy.stompTolerance(after);
  if(!Float.isFinite(relativeVy)||!Float.isFinite(after.playerVy)){clearStomp();return false;}
  if(contactAt<0){
   JumperState.ObjectBox enemy=matchingEnemy(after);
   if(enemy==null)return false;
   float dx=wrapping?JumperState.horizontalDistance(enemy.centerX(),after.player.centerX(),after.roiRight-after.roiLeft)
     :after.player.centerX()-enemy.centerX();
   float gap=enemy.top-after.player.bottom;
   if(after.playerVy>0&&relativeVy>0&&gap>=-tolerance&&gap<=tolerance
     &&Math.abs(dx)<=Math.max(enemy.width()*.45f,after.player.width()*.15f)){
    contactAt=after.timestampMs;contactBottom=after.player.bottom;contactTop=enemy.top;contactScroll=after.scrollVy;
   }
   return false;
  }
  float seconds=(after.timestampMs-contactAt)/1000f;
  float cameraShift=(contactScroll+after.scrollVy)*.5f*seconds;
  if(after.player.bottom>contactTop+cameraShift+tolerance*2){clearStomp();return false;}
  float lift=contactBottom+cameraShift-after.player.bottom;
  float minimum=Math.max(2,2*JumperPolicy.coordinateScale(after));
  if(after.playerVy<0&&relativeVy<0&&lift>=minimum){
   if(bounceAt>=0&&after.timestampMs-bounceAt>=40&&lift>=bounceLift-minimum){
    verifiedStomps=verifiedStomps==Long.MAX_VALUE?verifiedStomps:verifiedStomps+1;clearStomp();return true;
   }
   if(bounceAt<0){bounceAt=after.timestampMs;bounceLift=lift;}
  }else bounceAt=-1;
  return false;
 }
 private float worldShift(JumperState after){
  if(before==null||before.timestampMs>dispatchedAt||dispatchedAt-before.timestampMs>300
    ||before.frameWidth!=after.frameWidth||before.frameHeight!=after.frameHeight
    ||before.roiLeft!=after.roiLeft||before.roiTop!=after.roiTop||before.roiRight!=after.roiRight||before.roiBottom!=after.roiBottom
    ||before.platforms.size()>128||after.platforms.size()>128)return Float.NaN;
  float scale=JumperPolicy.coordinateScale(after),tolerance=Math.max(2,4*scale);
  float edge=Math.max(before.player.width(),12*scale);
  if(action==JumperPolicy.Action.TAP_LEFT?before.player.left-before.roiLeft>edge:before.roiRight-before.player.right>edge)return Float.NaN;
  boolean sameClippedEdge=Math.abs(before.player.left-before.roiLeft)<=tolerance&&Math.abs(after.player.left-after.roiLeft)<=tolerance
    ||Math.abs(before.roiRight-before.player.right)<=tolerance&&Math.abs(after.roiRight-after.player.right)<=tolerance;
  if(!sameClippedEdge&&Math.abs(before.player.width()-after.player.width())>tolerance
    ||Math.abs(before.player.height()-after.player.height())>tolerance)return Float.NaN;
  float[] shifts=worldShifts;boolean[] used=worldUsed;java.util.Arrays.fill(used,0,after.platforms.size(),false);int count=0;
  for(int aIndex=0;aIndex<before.platforms.size();aIndex++){
   JumperState.ObjectBox a=before.platforms.get(aIndex);
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
 public void cancel(long id){if(id==token){clear();failedWithAck=false;}if(id==stompToken)clearStomp();}
 public boolean paused(){return paused;}
 /** True only if own ACK and a usable post-dispatch observation actually existed. */
 public boolean failedWithAck(){return failedWithAck;}
 public String report(){return "JUMPER FEEDBACK: pending="+(action==null?"none":action)+" ack="+acknowledged+" failures="+failures+" verified="+verified+" scene_progress="+sceneProgress+" paused="+paused+" verified_stomps="+verifiedStomps+" stomp_pending="+(stompOrigin!=null);}
}
