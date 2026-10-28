package org.localpilot.android;

/** Retains a recent observation for identity matching, never as an action frame. */
public final class JumperTracking {
 private JumperState last;
 public void reset(){last=null;}
 public void observe(JumperState current){if(current!=null&&current.usable()&&(last==null||current.timestampMs>last.timestampMs))last=current;}
 public JumperState previous(long now){return last!=null&&now>last.timestampMs&&now-last.timestampMs<=650?last:null;}
}
