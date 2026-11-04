package org.localpilot.android;

/** Predecode admission test. Deliberate frame drops are not decoder failures. */
public final class CapturePacerTest {
 private static int checks;
 private static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
 public static void main(String[] args){
  CapturePacer p=new CapturePacer();
  check(p.accept(0,true),"first frame must pass");
  check(!p.accept(20_000_000L,true),"high frame rate deliberately throttled");
  check(p.accept(33_000_000L,true),"frame passes at 33 ms");
  check(!p.accept(34_000_000L,false),"unwanted frame rejected");
  check(!p.accept(34_000_000L,true),"pacing still active after unwanted frame");
  check(!p.accept(-1,true),"bad monotonic time rejected");
  check(!p.accept(10_000_000L,true),"clock reversal rejected");
  check(p.accept(66_000_000L,true),"a later valid frame still accepted");
  check(p.accept(99_000_000L,true),"pacing unaffected by discarded callbacks");
  check(p.acceptedFrames()==4,"four predecode accepted frames");
  check(p.skippedFrames()==5,"five skipped callbacks");
  check(p.throttledFrames()==2,"two throttled");
  check(p.unwantedFrames()==1,"one unwanted");
  check(p.invalidClockFrames()==2,"two invalid timestamps");
  check(p.skippedFrames()==p.throttledFrames()+p.unwantedFrames()+p.invalidClockFrames(),
        "skip categories add up exactly");
  System.out.println("CapturePacerTest: "+checks+" assertions PASS; no ImageReader or Android required");
 }
}
