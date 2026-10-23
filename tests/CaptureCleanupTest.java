package org.localpilot.android;

/** Fault injection for independent releases; does not claim Android lifecycle execution. */
public final class CaptureCleanupTest {
 private static int checks;
 private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("capture cleanup "+checks);}
 private static void invalid(Runnable task){try{task.run();throw new AssertionError("invalid cleanup accepted");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  for(int failed=-1;failed<8;failed++)for(boolean oom:new boolean[]{false,true}){
   StringBuilder released=new StringBuilder();Runnable[] steps=new Runnable[8];
   for(int i=0;i<steps.length;i++){
    final int index=i;final int failAt=failed;
    steps[i]=()->{released.append(index);if(index==failAt){if(oom)throw new OutOfMemoryError("private message");throw new IllegalStateException("private message");}};
   }
   check(CaptureCleanup.run(steps)==(failed<0));
   check(released.toString().equals("01234567"));
  }
  check(CaptureCleanup.run());
  invalid(()->CaptureCleanup.run((Runnable[])null));
  invalid(()->CaptureCleanup.run(new Runnable[9]));
  StringBuilder events=new StringBuilder();
  invalid(()->CaptureCleanup.run(()->events.append('x'),null));
  check(events.length()==0);
  System.out.println("Capture cleanup: "+checks+" assertions PASS; fault-injected JVM releases, no Android lifecycle test");
 }
}
