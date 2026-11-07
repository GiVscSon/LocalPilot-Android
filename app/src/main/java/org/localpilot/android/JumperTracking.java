package org.localpilot.android;

/** Retains recent identity context, never as an action frame or an input permit. */
public final class JumperTracking {
 private JumperState last;
 private long lastFrame=-1,session,generation,verifiedWraps,wrapAt=-1;
 private boolean contextSet,wrapping=true,stompEnemies;
 private int missingFrames,wrapDirection;
 private float wrapX,wrapY,wrapWidth,wrapHeight;
 public void reset(){clearHistory();contextSet=false;session=generation=0;wrapping=true;stompEnemies=false;}
 private void clearHistory(){last=null;lastFrame=-1;missingFrames=0;verifiedWraps=0;wrapAt=-1;}
 public void configureMechanics(boolean allowWrapAround,boolean stompEnemies){
  if(wrapping!=allowWrapAround||this.stompEnemies!=stompEnemies)clearHistory();
  wrapping=allowWrapAround;this.stompEnemies=stompEnemies;
 }
 public void observe(JumperState current){observe(current,contextSet?session:0,contextSet?generation:0);}
 /** A new session, frame generation or physical ROI invalidates all pending identity proofs. */
 public void observe(JumperState current,long session,long generation){
  if(session<0||generation<0)throw new IllegalArgumentException("jumper tracking context");
  if(contextSet&&(session<this.session||session==this.session&&generation<this.generation))return;
  if(!contextSet||this.session!=session||this.generation!=generation){
   clearHistory();this.session=session;this.generation=generation;contextSet=true;
  }
  if(current==null){missingFrames=increment(missingFrames);wrapAt=-1;return;}
  if(current.timestampMs<=lastFrame)return;
  if(last!=null&&!JumperState.sameGeometry(last,current))clearHistory();
  lastFrame=current.timestampMs;
  if(!current.usableForMechanics(stompEnemies)){
   missingFrames=increment(missingFrames);wrapAt=-1;return;
  }
  missingFrames=0;
  if(wrapping&&last!=null&&current.timestampMs-last.timestampMs<=650){
   if(wrapAt>=0){
    long elapsed=current.timestampMs-wrapAt;
    float width=current.roiRight-current.roiLeft,dx=current.player.centerX()-wrapX;
    boolean newSide=wrapDirection>0?current.player.centerX()<current.roiLeft+width*.28f
      :current.player.centerX()>current.roiRight-width*.28f;
    if(elapsed>=40&&elapsed<=300&&newSide&&Math.abs(dx)<=width*.18f
      &&dx*wrapDirection>=-Math.max(2,width*.005f)
      &&Math.abs(current.player.centerY()-wrapY)<=Math.max(current.player.height()*1.25f,(current.roiBottom-current.roiTop)*.08f)
      &&current.player.width()>=wrapWidth*.6f&&current.player.width()<=wrapWidth*1.6f
      &&current.player.height()>=wrapHeight*.6f&&current.player.height()<=wrapHeight*1.6f
      &&current.player.confidence>=.75f&&current.confidence>=.65f){
     verifiedWraps=verifiedWraps==Long.MAX_VALUE?verifiedWraps:verifiedWraps+1;wrapAt=-1;
    }else if(elapsed>300||!newSide||elapsed>=40)wrapAt=-1;
   }
   float raw=current.player.centerX()-last.player.centerX();
   float delta=JumperState.observedDeltaX(last,current),width=current.roiRight-current.roiLeft;
   if(Math.abs(raw)>width*.5f&&Float.isFinite(delta)&&Math.abs(delta)>0
     &&current.player.confidence>=.75f&&last.player.confidence>=.75f
     &&current.confidence>=.65f&&last.confidence>=.65f){
    wrapAt=current.timestampMs;wrapDirection=delta>0?1:-1;
    wrapX=current.player.centerX();wrapY=current.player.centerY();wrapWidth=current.player.width();wrapHeight=current.player.height();
   }
  }else wrapAt=-1;
  last=current;
 }
 public JumperState previous(long now){return last!=null&&now>last.timestampMs&&now-last.timestampMs<=650?last:null;}
 public long verifiedWraps(){return verifiedWraps;}
 public int missingFrames(){return missingFrames;}
 public String report(){return "JUMPER TRACKING: missing_frames="+missingFrames+" verified_wraps="+verifiedWraps+" wrap_pending="+(wrapAt>=0);}
 private static int increment(int value){return value==Integer.MAX_VALUE?value:value+1;}
}
