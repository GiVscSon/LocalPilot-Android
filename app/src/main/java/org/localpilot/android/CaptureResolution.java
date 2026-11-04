package org.localpilot.android;

/**
 * Bounded MediaProjection mirror geometry. Screens remain in physical coordinates:
 * JumperFrameGeometry maps this smaller mirror back to touchscreen space.
 *
 * On tablets, the phone-sized 480 x 720 mirror can shrink a pillarboxed
 * portrait game to roughly 300 px wide. Permit a bounded higher-detail
 * mirror only for Android tablet-width configurations (smallestWidthDp >= 600).
 * No changes to ROI, gesture safety or screen orientation assumptions.
 */
public final class CaptureResolution {
 public static final int PHONE_SHORT=480, PHONE_LONG=720;
 public static final int TABLET_SHORT=640, TABLET_LONG=960;
 public static final int MAX_PIXELS=TABLET_SHORT*TABLET_LONG;
 public final int width,height;
 public final boolean tabletDetail;

 public CaptureResolution(int screenWidth,int screenHeight){
  this(screenWidth,screenHeight,false);
 }
 public static CaptureResolution forDevice(int screenWidth,int screenHeight,int smallestWidthDp){
  return new CaptureResolution(screenWidth,screenHeight,smallestWidthDp>=600);
 }
 public CaptureResolution(int screenWidth,int screenHeight,boolean tabletDetail){
  if(screenWidth<120||screenHeight<120||screenWidth>16384||screenHeight>16384)
   throw new IllegalArgumentException("display bounds");
  this.tabletDetail=tabletDetail;
  int shortLimit=tabletDetail?TABLET_SHORT:PHONE_SHORT;
  int longLimit=tabletDetail?TABLET_LONG:PHONE_LONG;
  double scale=Math.min(1,Math.min(longLimit/(double)Math.max(screenWidth,screenHeight),
       shortLimit/(double)Math.min(screenWidth,screenHeight)));
  width=Math.max(2,((int)(screenWidth*scale)/2)*2);
  height=Math.max(2,((int)(screenHeight*scale)/2)*2);
  if((long)width*height>MAX_PIXELS)throw new IllegalArgumentException("mirror pixel bound");
 }
}
