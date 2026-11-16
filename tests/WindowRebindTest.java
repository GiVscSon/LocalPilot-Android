package org.localpilot.android;
/** Pure JVM checks: stable window geometry never grants gesture authority. */
public final class WindowRebindTest {
 private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  WindowFocus.RebindStability s=new WindowFocus.RebindStability();
  check(!s.observe(7,"ru.beru.android",10,0,0,1220,2712,1220,2712,0,1000),"first frame cannot rebind");
  check(!s.observe(7,"ru.beru.android",10,0,0,1220,2712,1220,2712,0,1599),"less than 600ms");
  check(s.observe(7,"ru.beru.android",10,0,0,1220,2712,1220,2712,0,1600),"600ms stable");
  check(!s.observe(7,"ru.beru.android",10,0,0,1200,2712,1220,2712,0,1610),"bounds change resets");
  check(!s.observe(7,"ru.beru.android",10,0,0,1200,2712,1220,2712,1,2300),"rotation resets");
  check(!s.observe(8,"ru.beru.android",10,0,0,1200,2712,1220,2712,1,3000),"epoch resets");
  check(!s.observe(8,"other.app",10,0,0,1200,2712,1220,2712,1,4000),"owner resets");
  check(!s.observe(8,"ru.beru.android",11,0,0,1200,2712,1220,2712,1,4600),"window id resets");
  check(!s.observe(8,"ru.beru.android",11,0,0,1200,2712,1220,2712,1,4500),"backward uptime resets");
  check(s.observe(8,"ru.beru.android",11,0,0,1200,2712,1220,2712,1,5100),"stable after monotonic restart");
  s.reset();check(!s.observe(8,"ru.beru.android",11,0,0,1200,2712,1220,2712,1,5700),"explicit reset");
  WindowFocus grace=new WindowFocus();
  check(grace.observe(9,"TARGET_WINDOW_UNAVAILABLE",1000,true)==WindowFocus.State.WAITING,"target visibly unchanged can wait read-only");
  check(grace.observe(9,"TARGET_WINDOW_UNAVAILABLE",1600,true)==WindowFocus.State.WAITING,"600ms is not mandatory STOP when visible");
  check(grace.observe(9,"TARGET_WINDOW_UNAVAILABLE",3499,true)==WindowFocus.State.WAITING,"bounded wait continues below 2500ms");
  check(grace.observe(9,"TARGET_WINDOW_UNAVAILABLE",3500,true)==WindowFocus.State.STOP,"visible wait must end at 2500ms");
  grace.reset();
  check(grace.observe(10,"TARGET_WINDOW_UNAVAILABLE",1000,false)==WindowFocus.State.WAITING,"missing target only short grace");
  check(grace.observe(10,"TARGET_WINDOW_UNAVAILABLE",1600,false)==WindowFocus.State.STOP,"invisible target stops at 600ms");
  grace.reset();
  check(grace.observe(11,"TARGET_WINDOW_UNAVAILABLE",1000,true)==WindowFocus.State.WAITING,"temporary visible focus loss");
  check(grace.observe(11,null,2500,true)==WindowFocus.State.ACTIVE,"recovered identity resets wait");
  check(grace.observe(12,"TARGET_BOUNDS_CHANGED",2600,true)==WindowFocus.State.STOP,"changed bounds never get focus grace");
  int[] selected=WindowFocus.visibleSelection(0,0,1082,1922,1080,1920);
  check(java.util.Arrays.equals(selected,new int[]{0,0,1080,1920}),"oversized API30 snapshot selects only physical display");
  check(!java.util.Arrays.equals(selected,new int[]{0,0,1079,1920}),"in-display bounds change is still different");
  check(java.util.Arrays.equals(WindowFocus.visibleSelection(50,70,900,1800,1080,1920),new int[]{50,70,900,1800}),"in-display window remains exact");
  check(WindowFocus.visibleSelection(1080,0,1100,100,1080,1920)==null,"off-display window cannot be selected");
  check(WindowFocus.visibleSelection(0,0,1080,1920,0,1920)==null,"unknown display cannot be selected");
  // Captured API30 failure: run42237951606 attempt2, window83. The same
  // focused window was 1080x1920, but the picker snapshot was 1082x1922.
  ActionGate gate=new ActionGate();
  gate.arm("org.localpilot.gamelab",83,selected[2]-selected[0],selected[3]-selected[1]);
  check(gate.matches("org.localpilot.gamelab",83,1080,1920),"normalized real snapshot binds actual display");
  check(!gate.matches("org.localpilot.gamelab",83,1082,1922),"raw oversized geometry is not authorized");
  check("TARGET_WINDOW_CHANGED".equals(gate.identityRejection("org.localpilot.gamelab",84,1080,1920)),"normalization never accepts another window");
  check("TARGET_APP_CHANGED".equals(gate.identityRejection("other.app",83,1080,1920)),"normalization never accepts another package");
  check("TARGET_BOUNDS_CHANGED".equals(gate.identityRejection("org.localpilot.gamelab",83,1079,1920)),"in-display geometry change still rejected");
  ActionGate.Ticket ticket=gate.offer(1000);
  check(gate.valid(ticket,1001,"org.localpilot.gamelab",83,1080,1920),"normalized binding permits a current ticket");
  check("TICKET_EXPIRED".equals(gate.rejection(ticket,1701,"org.localpilot.gamelab",83,1080,1920)),"normalized binding preserves 700ms TTL");
  gate.stop();
  check(!gate.valid(ticket,1002,"org.localpilot.gamelab",83,1080,1920),"STOP revokes normalized binding");
  gate.arm("org.localpilot.gamelab",83,1080,1920);
  check("SESSION_CHANGED".equals(gate.rejection(ticket,1003,"org.localpilot.gamelab",83,1080,1920)),"rearm never restores a prior epoch ticket");
  System.out.println("Window rebind stability, focus grace and visible selection: 34 assertions PASS");
 }
}
