package org.localpilot.android;

/** Allocation budget for one canonical calibrated frame, not an Android performance claim. */
public final class VisionAllocationTest {
 public static void main(String[]args){
  var bean=java.lang.management.ManagementFactory.getThreadMXBean();
  if(!(bean instanceof com.sun.management.ThreadMXBean memory)||!memory.isThreadAllocatedMemorySupported()){
   System.out.println("Vision allocation budget: SKIP (JVM allocation counter unavailable)");return;
  }
  memory.setThreadAllocatedMemoryEnabled(true);
  var config=new CustomVisionConfig(new int[]{0xff20c0,0x20e0e0,0xff9020},new float[]{.5f,.5f,.8f},new float[]{.82f,.43f,.16f});
  CustomArcadeVision.Pixels pixels=(x,y)->x>=70&&x<88&&y>=182&&y<214?0xff20c0:x>=67&&x<91&&y>=84&&y<125?0x20e0e0:0x202020;
  for(int i=0;i<30;i++)CustomArcadeVision.detect(ArcadeProfile.TRAFFIC,pixels,0,0,160,240,0,0,0,0,i,null,config);
  long thread=Thread.currentThread().getId(),start=memory.getThreadAllocatedBytes(thread);int usable=0;
  for(int i=0;i<500;i++)if(CustomArcadeVision.detect(ArcadeProfile.TRAFFIC,pixels,0,0,160,240,0,0,0,0,1000+i,null,config).usable())usable++;
  long average=(memory.getThreadAllocatedBytes(thread)-start)/500;
  if(usable!=500||average>=65536)throw new AssertionError("Canonical frame allocation budget exceeded: "+average+" bytes/frame; usable="+usable);
  System.out.println("Vision allocation budget: PASS, "+average+" bytes/frame after warmup; JVM only, results still allocate");
 }
}
