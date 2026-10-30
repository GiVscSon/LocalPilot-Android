package org.localpilot.android;

/** Emulates tablet-browser same-package events without an Android SDK. */
public final class BrowserSceneGateTest {
 private static int n;
 private static void test(boolean cond,String name){n++;if(!cond)throw new AssertionError(name);}
 public static void main(String[] args){
  BrowserSceneGate gate=new BrowserSceneGate();
  test(gate.observe(100,true),"no event: old input semantics unchanged");
  gate.changed(100);
  test(gate.quarantined(),"same-browser state event quarantines input");
  test(!gate.observe(99,true)&&!gate.observe(100,true),"old or equal timestamp cannot prove unchanged scene");
  test(!gate.observe(101,true),"first fresh scene may not authorize a gesture");
  test(!gate.observe(101,true),"duplicate frame cannot count twice");
  test(!gate.observe(102,false),"unknown/banner/external screen resets visual proof");
  test(!gate.observe(103,true),"first stable frame after unknown is not enough");
  test(gate.observe(104,true),"second fresh recognized frame clears quarantine");
  test(!gate.quarantined(),"trust must be restored only after two scenes");

  gate.changed(120);
  test(!gate.observe(121,true),"first confirmation on new transition");
  gate.changed(123);
  test(!gate.observe(122,true),"old in-flight callback after second transition rejected");
  test(!gate.observe(124,true),"first new frame after second transition");
  test(gate.observe(125,true),"two post-transition frames required");
  test(gate.transitions()==3,"bounded transition count persists for audit");
  gate.changed(200);
  gate.reset();
  test(gate.observe(199,true)&&!gate.quarantined(),"STOP explicit session reset clears quarantine only because input gate is stopped");
  try{gate.changed(-1);throw new AssertionError("negative event time accepted");}
  catch(IllegalArgumentException expected){n++;}
  System.out.println("Browser state / fresh-frame quarantine: "+n+" assertions PASS");
 }
}
