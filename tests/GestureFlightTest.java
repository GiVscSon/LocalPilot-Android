package org.localpilot.android;
public final class GestureFlightTest {
 private static int checks;
 private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  GestureFlight f=new GestureFlight();check(!f.active(),"idle");long first=f.begin(1000);
  check(first>0&&f.active(),"dispatch pending");
  try{f.begin(1001);throw new AssertionError("concurrent flight");}catch(IllegalStateException expected){checks++;}
  check(!f.complete(0)&&!f.complete(first+1),"foreign callbacks ignored");
  check(!f.timeout(first,999)&&!f.timeout(first,3999),"clock reversal and early timeout rejected");
  check(f.timeout(first,4000)&&!f.active(),"missing callback releases busy at deadline");
  check(!f.complete(first)&&!f.timeout(first,5000),"late old callback cannot count as completion");
  long second=f.begin(5000);check(second>first,"monotonic flight identity");
  check(!f.complete(first)&&f.active(),"old completion cannot release new gesture");
  check(!f.timeout(first,9000)&&f.active(),"old watchdog cannot release new gesture");
  check(f.complete(second)&&!f.active(),"only current callback releases execution");
  check(!f.timeout(second,9000),"completion wins over timeout");
  long third=f.begin(10000);check(f.report(10120).contains("age_ms=120"),"pending age diagnostic");
  // STOP changes the service epoch but keeps an in-flight gesture until its own callback/deadline.
  check(f.timeout(third,13000)&&!f.active(),"watchdog can settle gesture after STOP");
  long fourth=f.begin(14000);f.clear();check(!f.active()&&!f.complete(fourth),"service destruction invalidates callbacks");
  long fifth=f.begin(15000);check(fifth>fourth&&!f.complete(fourth)&&f.active(),"clear never reuses a sequence");
  check(f.complete(fifth)&&!f.complete(fifth),"duplicate callback ignored");
  check(f.report(16000).contains("pending=false"),"idle diagnostic");
  try{f.begin(-1);throw new AssertionError("negative clock");}catch(IllegalStateException expected){checks++;}
  System.out.println("Gesture flight: "+checks+" assertions PASS");
 }
}
