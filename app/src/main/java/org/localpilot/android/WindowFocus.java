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
  // A recovered window cannot retroactively undo a timed-out focus loss.
  // This also preserves legacy 600ms semantics without visibility proof.
  if(lostAt>=0&&(now<lostAt||now-lostAt>=(unchangedTargetVisible?VISIBLE_GRACE_MS:GRACE_MS)))return State.STOP;
  if(reason==null){lostAt=-1;return State.ACTIVE;}
  if(!transientLoss(reason))return State.STOP;
  if(lostAt<0)lostAt=now;
  return State.WAITING;
 }
 /** Offscreen OEM accessibility metadata: pause only when the SAME window still
  * has its bound dimensions. Never translate a bad rectangle into gesture coordinates.
  * This is not evidence of correct focused window or a verified game frame.
  */
 public static boolean impossibleScreenOffset(int left,int top,int right,int bottom,
                                               int screenWidth,int screenHeight){
  if(screenWidth<=0||screenHeight<=0||right<=left||bottom<=top)return false;
  return left>=screenWidth||top>=screenHeight||right<=0||bottom<=0;
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

 public static final class BoundsRebinder {
  public static final long STABLE_REBIND_MS = 160;
  public static final long REBIND_WINDOW_MS = 1200;
  private String expectedOwner = "";
  private int candidateWindow = -1, left, top, right, bottom;
  private long firstSeenMs = -1, lastSeenMs = -1;
  private int stableCount;

  public void reset() {
   expectedOwner = "";
   candidateWindow = -1;
   left = top = right = bottom = 0;
   firstSeenMs = lastSeenMs = -1;
   stableCount = 0;
  }

  public boolean observeCandidate(String owner, int windowId, int l, int t, int r, int b, long nowMs) {
   if (owner == null || owner.isEmpty() || windowId < 0 || r - l < 120 || b - t < 180 || nowMs < 0) {
    reset();
    return false;
   }
   if (!owner.equals(expectedOwner) || windowId != candidateWindow
     || l != left || t != top || r != right || b != bottom
     || firstSeenMs < 0 || nowMs < lastSeenMs || nowMs - firstSeenMs > REBIND_WINDOW_MS) {
    expectedOwner = owner;
    candidateWindow = windowId;
    left = l; top = t; right = r; bottom = b;
    firstSeenMs = lastSeenMs = nowMs;
    stableCount = 1;
    return false;
   }
   lastSeenMs = nowMs;
   if (nowMs - firstSeenMs >= STABLE_REBIND_MS) {
    stableCount++;
    return true;
   }
   return false;
  }

  public int stableCount() { return stableCount; }

  public static int[] remapRoi(int oldWL, int oldWT, int oldWR, int oldWB,
    int newWL, int newWT, int newWR, int newWB,
    int roiL, int roiT, int roiR, int roiB) {
   int oldW = oldWR - oldWL, oldH = oldWB - oldWT;
   int newW = newWR - newWL, newH = newWB - newWT;
   if (oldW < 120 || oldH < 180 || newW < 120 || newH < 180
     || roiL < oldWL || roiT < oldWT || roiR > oldWR || roiB > oldWB
     || roiR - roiL < 60 || roiB - roiT < 60) return null;
   float scaleX = newW / (float) oldW, scaleY = newH / (float) oldH;
   if (Math.abs(scaleX - scaleY) / Math.max(scaleX, scaleY) > 0.08f) return null;
   int nl = newWL + Math.round((roiL - oldWL) * scaleX);
   int nt = newWT + Math.round((roiT - oldWT) * scaleY);
   int nr = newWL + Math.round((roiR - oldWL) * scaleX);
   int nb = newWT + Math.round((roiB - oldWT) * scaleY);
   nl = Math.max(newWL, Math.min(newWR - 1, nl));
   nt = Math.max(newWT, Math.min(newWB - 1, nt));
   nr = Math.max(nl + 1, Math.min(newWR, nr));
   nb = Math.max(nt + 1, Math.min(newWB, nb));
   if (nr - nl < 60 || nb - nt < 60) return null;
   return new int[]{nl, nt, nr, nb};
  }
 }

 public record WindowCandidate(int windowId, String packageName, boolean focused, boolean active,
                               int left, int top, int right, int bottom) {
  public boolean nonEmpty() { return right - left >= 120 && bottom - top >= 180; }
 }

 public static int selectApplicationWindow(java.util.List<WindowCandidate> windows,
   boolean explicitBinding, String expectedPackage, int expectedWindowId) {
  if (windows == null || windows.isEmpty()) return -1;
  int focusedId=-1, focusedCount=0, activeId=-1, activeCount=0, onlyId=-1, validCount=0;
  WindowCandidate matched=null;
  for (WindowCandidate w : windows) {
   if (w == null || !w.nonEmpty()) continue;
   validCount++; onlyId=w.windowId();
   if (w.focused()) {focusedCount++; focusedId=w.windowId();}
   if (w.active()) {activeCount++; activeId=w.windowId();}
   if (explicitBinding && expectedPackage != null && expectedPackage.equals(w.packageName())
      && w.windowId() == expectedWindowId) matched=w;
  }
  if (explicitBinding) {
   if (matched == null || expectedWindowId < 0 || expectedPackage == null || expectedPackage.isEmpty()) return -1;
   if (focusedCount > 1 || activeCount > 1) return -1;
   if (focusedCount == 1 && focusedId != expectedWindowId) return -1;
   if (activeCount == 1 && activeId != expectedWindowId) return -1;
   return matched.windowId();
  }
  if (focusedCount > 1) return -1;
  if (focusedCount == 1) return focusedId;
  if (activeCount > 1) return -1;
  if (activeCount == 1) return activeId;
  return validCount == 1 ? onlyId : -1;
 }
}
