package org.localpilot.android;

/** Fail-closed regression for same-browser navigation, animations and stale callbacks. */
public final class BrowserSceneGateTest {
 private static int n;
 private static void test(boolean cond,String name){n++;if(!cond)throw new AssertionError(name);}
 public static void main(String[] args){
  BrowserSceneGate gate=new BrowserSceneGate();
  test(gate.observe(100,true),"no transition: existing policy is unchanged");
  gate.changed(100);
  test(gate.quarantined(),"browser transition quarantines input");
  test(!gate.observe(99,true)&&!gate.observe(100,true),"pre-transition frame cannot restore trust");
  test(!gate.observe(101,true),"one fresh frame is not enough");
  test(!gate.observe(101,true),"duplicate frame is not an independent observation");
  test(!gate.observe(102,true),"two frames 1ms apart must not bypass animation settling");
  test(!gate.observe(180,true),"79ms still insufficient");
  test(!gate.observe(181,false),"scene rejected at deadline must reset proof");
  test(!gate.observe(182,true),"fresh valid frame restarts proof");
  test(!gate.observe(261,true),"new 79ms interval cannot grant input");
  test(gate.observe(262,true),"two valid fresh frames spanning 80ms release quarantine");
  test(!gate.quarantined(),"quarantine cleared on sufficient evidence");

  gate.changed(1200);
  test(!gate.observe(1210,true),"first frame after another transition");
  gate.changed(1230);
  test(!gate.observe(1220,true),"old in-flight callback rejected after newer transition");
  test(!gate.observe(1240,true),"first frame after newer transition");
  test(!gate.observe(1319,true),"no early unlock across 79ms");
  test(gate.observe(1320,true),"later independent frame releases gate");
  test(gate.transitions()==3,"transition counter remains bounded and auditable");

  gate.changed(2000);
  gate.reset();
  test(gate.observe(1999,true)&&!gate.quarantined(),"STOP reset clears local quarantine only while input gate is stopped");

  gate.changed(3000);
  test(!gate.observe(-1,true),"negative timestamp cannot complete transition");
  test(!gate.observe(3001,true),"first positive frame");
  test(!gate.observe(3002,false),"unknown frame invalidates consecutive stable evidence");
  test(!gate.observe(3080,true),"first valid frame after unknown");
  test(!gate.observe(3159,true),"79ms is insufficient after unknown");
  test(gate.observe(3160,true),"80ms after new valid frame");
  try{gate.changed(-1);throw new AssertionError("negative event time accepted");}
  catch(IllegalArgumentException expected){n++;}
  System.out.println("Browser transition temporal quarantine: "+n+" assertions PASS");
 }
}
