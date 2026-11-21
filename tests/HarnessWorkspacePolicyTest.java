package org.localpilot.android;
public final class HarnessWorkspacePolicyTest {
 private static void eq(boolean actual,boolean expected,String label){
  if(actual!=expected)throw new AssertionError(label);
 }
 public static void main(String[] ignored){
  var idle=new HarnessWorkspacePolicy.Occupancy(false,false);
  eq(idle.canStartNes(),true,"idle NES");eq(idle.canStartAndroid(),true,"idle Android");
  var nes=new HarnessWorkspacePolicy.Occupancy(true,false);
  eq(nes.canStartNes(),true,"NES same environment");eq(nes.canStartAndroid(),false,"no Android during NES");
  var android=new HarnessWorkspacePolicy.Occupancy(false,true);
  eq(android.canStartNes(),false,"no NES during Android");eq(android.canStartAndroid(),true,"Android same environment");
  var conflict=new HarnessWorkspacePolicy.Occupancy(true,true);
  eq(conflict.canStartNes(),false,"conflict NES");eq(conflict.canStartAndroid(),false,"conflict Android");
  eq(conflict.conflict(),true,"conflict detected");
  if(!conflict.activeLabel().contains("STOP"))throw new AssertionError("conflict requires STOP");
  System.out.println("HarnessWorkspacePolicy: 10 admission checks PASS (HOST_UNIT)");
 }
}
