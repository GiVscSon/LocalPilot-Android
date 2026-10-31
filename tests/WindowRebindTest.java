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
  System.out.println("Window rebind stability: 11 assertions PASS");
 }
}
