package org.localpilot.android;

/** Last window rejection for one binding; no package names, window IDs or pixels. */
public final class WindowRejectionDiagnostics {
 private String reason="NONE";
 private long epoch=-1,uptime=-1,elapsed=-1;
 private int left,top,right,bottom,observedLeft,observedTop,observedRight,observedBottom;
 private boolean observed;

 public void reset(){reason="NONE";epoch=uptime=elapsed=-1;left=top=right=bottom=observedLeft=observedTop=observedRight=observedBottom=0;observed=false;}

 /** Invalid or non-window failures do not alter the last observation. */
 public void record(String code,long gateEpoch,long uptimeMs,long elapsedMs,int l,int t,int r,int b,
   boolean hasObserved,int ol,int ot,int oright,int obottom){
  if(!allowed(code)||gateEpoch<0||uptimeMs<0||elapsedMs<0)return;
  reason=code;epoch=gateEpoch;uptime=uptimeMs;elapsed=elapsedMs;left=l;top=t;right=r;bottom=b;
  observed=hasObserved;observedLeft=ol;observedTop=ot;observedRight=oright;observedBottom=obottom;
 }
 private static boolean allowed(String code){return "TARGET_WINDOW_UNAVAILABLE".equals(code)||"WINDOW_STATE_UNAVAILABLE".equals(code)
   ||"TARGET_APP_CHANGED".equals(code)||"TARGET_WINDOW_CHANGED".equals(code)||"TARGET_BOUNDS_CHANGED".equals(code);}
 public String report(){
  return "WINDOW_REJECTION reason="+reason+" gate_epoch="+epoch+" uptime_ms="+uptime+" elapsed_ms="+elapsed
    +" expected_bounds="+(epoch<0?"none":bounds(left,top,right,bottom))
    +" observed_bounds="+(observed?bounds(observedLeft,observedTop,observedRight,observedBottom):"unavailable");
 }
 private static String bounds(int l,int t,int r,int b){return "["+l+","+t+"]["+r+","+b+"]";}
}
