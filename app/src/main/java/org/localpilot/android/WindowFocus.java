package org.localpilot.android;
/** Main-thread bounded loss-of-focus observation. Never grants input or changes binding. */
public final class WindowFocus {
 public enum State { ACTIVE, WAITING, STOP }
 public static final long GRACE_MS=600;
 /** Extra read-only grace is allowed only while the same target window remains
  * visible at its originally bound bounds, without a foreign full-screen app. */
 public static final long VISIBLE_GRACE_MS=2500;
 private long epoch=-1,lostAt=-1;
 public void reset(){epoch=lostAt=-1;}
 public boolean waiting(){return lostAt>=0;}
 public State observe(long session,String reason,long now){return observe(session,reason,now,false);}
 public State observe(long session,String reason,long now,boolean unchangedTargetVisible){
  if(now<0)return State.STOP;
  if(epoch!=session){epoch=session;lostAt=-1;}
  if(reason==null){lostAt=-1;return State.ACTIVE;}
  if(!transientLoss(reason))return State.STOP;
  if(lostAt<0)lostAt=now;
  if(now<lostAt||now-lostAt>=(unchangedTargetVisible?VISIBLE_GRACE_MS:GRACE_MS))return State.STOP;
  return State.WAITING;
 }
 /** An interrupted arcade gesture cannot be safely recovered by dropping feedback. */
 public static boolean interruptedArcade(boolean playingArcade,boolean arcadePending,boolean jumperPending){return playingArcade&&(arcadePending||jumperPending);}
 public static boolean interrupted2048(boolean playing,boolean pending){return playing&&pending;}
 public static boolean transientLoss(String reason){return "TARGET_WINDOW_UNAVAILABLE".equals(reason)||"WINDOW_STATE_UNAVAILABLE".equals(reason)||"TARGET_APP_CHANGED".equals(reason)||"FOCUS_PAUSED".equals(reason);}
 /** Rebinding evidence is separate from input authority. All physical dimensions and
  * the focused window ID must remain identical for at least 600 ms. */
 public static final class RebindStability {
  public static final long STABLE_MS=600;
  private long epoch=-1,firstMs=-1,lastMs=-1;
  private String packageName="";
  private int id,left,top,right,bottom,width,height,rotation,samples;
  public void reset(){epoch=firstMs=lastMs=-1;packageName="";samples=0;}
  public boolean observe(long session,String pkg,int windowId,int l,int t,int r,int b,int w,int h,int rotate,long now){
   if(now<0||pkg==null||pkg.isEmpty()||r<=l||b<=t||w<=0||h<=0||rotate<0||rotate>3){reset();return false;}
   if(epoch!=session||!pkg.equals(packageName)||id!=windowId||left!=l||top!=t||right!=r||bottom!=b||width!=w||height!=h||rotation!=rotate||now<lastMs){
    epoch=session;packageName=pkg;id=windowId;left=l;top=t;right=r;bottom=b;width=w;height=h;rotation=rotate;
    firstMs=now;lastMs=now;samples=1;return false;
   }
   samples++;lastMs=now;
   return samples>=2&&now-firstMs>=STABLE_MS;
  }
 }
}
