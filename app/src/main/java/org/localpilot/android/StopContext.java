package org.localpilot.android;

/** Bounded metadata only. No authority, target names, frames or input retries. */
public final class StopContext {
 private long boundsEpoch=-1,boundsAt=-1;
 private String boundsEvent="none";
 public void clearBounds(){boundsEpoch=-1;boundsAt=-1;boundsEvent="none";}
 public void boundsChanged(long epoch,long now,int expectedWindow,int observedWindow,
   int el,int et,int er,int eb,int ol,int ot,int or,int ob){
  boundsEpoch=epoch;boundsAt=now;
  boundsEvent="bounds_event_elapsed_ms="+now+" bounds_event_epoch="+epoch
    +" expected_window="+expectedWindow+" observed_window="+observedWindow
    +" expected_bounds="+box(el,et,er,eb)+" observed_bounds="+box(ol,ot,or,ob);
 }
 /** Call after revoking authority, but pass identity captured before revocation. */
 public String report(String reason,long now,long before,long after,boolean wasArmed,
   boolean wasPlaying,int profile,int left,int top,int right,int bottom){
  return "STOP_CONTEXT elapsed_ms="+now+" epoch_before="+before+" epoch_after="+after
    +" bound_before="+wasArmed+" playing_before="+wasPlaying+" selected_profile_id="+profile
    +" armed_bounds="+box(left,top,right,bottom)+" reason="+boundedReason(reason)
    +"\n"+(boundsEpoch==before&&boundsAt>=0&&boundsAt<=now?boundsEvent:"bounds_event=none_for_this_epoch");
 }
 private static String box(int l,int t,int r,int b){return "["+l+","+t+"]["+r+","+b+"]";}
 private static String boundedReason(String reason){
  if(reason==null)return "unknown";
  return reason.substring(0,Math.min(240,reason.length())).replace('\n',' ').replace('\r',' ');
 }
}
