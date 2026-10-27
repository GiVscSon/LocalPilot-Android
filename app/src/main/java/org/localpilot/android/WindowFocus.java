package org.localpilot.android;
/** Main-thread bounded loss-of-focus observation. Never grants input or changes binding. */
public final class WindowFocus {
 public enum State { ACTIVE, WAITING, STOP }
 public static final long GRACE_MS=600;
 private long epoch=-1,lostAt=-1;
 public void reset(){epoch=lostAt=-1;}
 public boolean waiting(){return lostAt>=0;}
 public State observe(long session,String reason,long now){
  if(now<0)return State.STOP;
  if(epoch!=session){epoch=session;lostAt=-1;}
  if(lostAt>=0&&(now<lostAt||now-lostAt>=GRACE_MS))return State.STOP;
  if(reason==null){lostAt=-1;return State.ACTIVE;}
  if(!transientLoss(reason))return State.STOP;
  if(lostAt<0)lostAt=now;
  return State.WAITING;
 }
 /** An interrupted arcade gesture cannot be safely recovered by dropping feedback. */
 public static boolean interruptedArcade(boolean playingArcade,boolean arcadePending,boolean jumperPending){return playingArcade&&(arcadePending||jumperPending);}
 public static boolean interrupted2048(boolean playing,boolean pending){return playing&&pending;}
 public static boolean transientLoss(String reason){return "TARGET_WINDOW_UNAVAILABLE".equals(reason)||"WINDOW_STATE_UNAVAILABLE".equals(reason)||"TARGET_APP_CHANGED".equals(reason)||"FOCUS_PAUSED".equals(reason);}
}
