package org.localpilot.android;

/** Synthetic field fixture. Original user screenshots are checked locally,
 * never committed into the repository or bundled with the APK. */
public final class DynamiteObjectTest {
 private static int checks;
 private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 private static int pixel(int x,int y){
  int row=y/100,col=x/100;float u=(x%100)/100f,v=(y%100)/100f;
  if((row==1&&col==1)||(row==4&&col==4)){
   if(u>.56f&&u<.80f&&v>.09f&&v<.31f)return 0xffd527; // upper fuse
   if(u>.31f&&u<.67f&&v>.32f&&v<.85f)return 0xe53625; // narrow red tube
   return 0xd4ece5;
  }
  if(row==2&&col==3){
   float dx=u-.5f,dy=v-.5f;float radius=(float)Math.hypot(dx,dy);
   if(radius<.19f)return 0xffcb29; // yellow flower center
   if(radius<.38f)return 0xf47720; // orange petals, no red tube
   return 0xd4ece5;
  }
  if(row==1&&col==5){
   return Math.hypot(u-.5f,v-.5f)<.34?0xf7ca03:0xd4ece5; // coin
  }
  return Math.hypot(u-.5f,v-.5f)<.34?0xe73a42:0xd4ece5; // red tomatoes
 }
 public static void main(String[] args){
  var protectedRead=GridPuzzleVision.detect(DynamiteObjectTest::pixel,0,0,600,600,6,6,0,0,0,0,1000,null,true);
  for(int[] rc:new int[][]{{1,1},{4,4}}){
   int r=rc[0],c=rc[1],index=r*6+c;
   check(protectedRead.reason(r,c)==GridPuzzleVision.CellReason.PROTECTED_DYNAMITE,
     "red cylinder + yellow fuse protected, got "+protectedRead.reason(r,c));
   check(protectedRead.type(r,c)==0&&protectedRead.specialCellsMask().get(index),"protected explosive never touchable");
  }
  for(int[] rc:new int[][]{{0,0},{2,3},{1,5}})
   check(protectedRead.reason(rc[0],rc[1])!=GridPuzzleVision.CellReason.PROTECTED_DYNAMITE,"tomato/flower/coin not explosive");
  var genericRead=GridPuzzleVision.detect(DynamiteObjectTest::pixel,0,0,600,600,6,6,0,0,0,0,1100,null,false);
  check(genericRead.reason(1,1)!=GridPuzzleVision.CellReason.PROTECTED_DYNAMITE,"generic 6x6 does not inherit protected preset");
  check(!protectedRead.unknownReasonCounts().contains("PROTECTED_DYNAMITE"),"protected objects are not UNKNOWN");
  System.out.println("Dynamite-object classification: "+checks+" checks PASS (synthetic)");
 }
}
