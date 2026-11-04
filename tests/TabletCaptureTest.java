package org.localpilot.android;

/** Real tablet and phone dimensions, no Android SDK or screenshot assumptions. */
public final class TabletCaptureTest {
 private static int assertions;
 private static void check(boolean ok,String reason){assertions++;if(!ok)throw new AssertionError(reason);}
 private static void near(float a,float b,float delta,String why){
  check(Math.abs(a-b)<=delta,why+" expected "+a+" got "+b);
 }
 private static void geometry(int physicalW,int physicalH,int swdp,boolean tablet){
  CaptureResolution c=CaptureResolution.forDevice(physicalW,physicalH,swdp);
  check(c.tabletDetail==tablet,"tablet-width profile must be explicit");
  check(c.width%2==0&&c.height%2==0,"mirror has even dimensions");
  check((long)c.width*c.height<=CaptureResolution.MAX_PIXELS,"bounded decode pixels");
  check(Math.max(c.width,c.height)<=(tablet?960:720),"bounded long side");
  check(Math.min(c.width,c.height)<=(tablet?640:480),"bounded short side");
  JumperFrameGeometry g=new JumperFrameGeometry(c.width,c.height,physicalW,physicalH,
    physicalW/8,physicalH/16,physicalW*7/8,physicalH*15/16,true);
  for(float portion:new float[]{.10f,.25f,.50f,.75f,.90f}){
   float physicalX=physicalW*portion,physicalY=physicalH*portion;
   near(physicalX,g.toScreenX(g.toFrameX(physicalX)),4,"frame→touch X");
   near(physicalY,g.toScreenY(g.toFrameY(physicalY)),4,"frame→touch Y");
  }
 }
 private static boolean box(float x,float y,float l,float t,float r,float b){
  return x>=l&&x<r&&y>=t&&y<b;
 }
 public static void main(String[] args){
  geometry(1080,2400,393,false); // Dense portrait phone remains on proven path.
  geometry(1200,1920,700,true);  // Tablet portrait: high-detail mirror.
  geometry(1920,1200,700,true);  // Same tablet in landscape.
  geometry(1920,1282,700,true);  // Letterboxed portrait game on landscape tablet.
  geometry(2560,1600,800,true);
  geometry(1600,2560,800,true);
  geometry(1080,2400,599,false); // Do not silently force all phones to hi-res.
  for(int[] shape:new int[][]{{1920,1282},{2560,1600},{1200,1920}}){
   CaptureResolution size=CaptureResolution.forDevice(shape[0],shape[1],700);
   CaptureResolution base=new CaptureResolution(shape[0],shape[1]);
   check((long)size.width*size.height>(long)base.width*base.height,
         "tablet mirror has more pixels than phone mirror");
  }
  // Explicit landscape game field 532..1388 physical pixels in 1920x1282.
  CaptureResolution cap=CaptureResolution.forDevice(1920,1282,700);
  JumperFrameGeometry letterbox=new JumperFrameGeometry(
    cap.width,cap.height,1920,1282,532,36,1388,1255,true);
  check(letterbox.left>0&&letterbox.right<cap.width,
        "letterboxed game ROI remains inside mirrored capture");
  near(532,letterbox.toScreenX(letterbox.toFrameX(532)),4,"letterbox left");
  near(1388,letterbox.toScreenX(letterbox.toFrameX(1388)),4,"letterbox right");

  // The calibrated 3-colour arcade detector must still identify a small ball
  // and a basket on a tablet-sized frame; this is synthetic recognition only.
  int red=0xe63339,brown=0x8d582a,blue=0x276bd8;
  CustomVisionConfig profile=new CustomVisionConfig(
    new int[]{red,brown,blue},
    new float[]{.68f,.40f,.76f},
    new float[]{.32f,.88f,.15f});
  int w=448,h=880;
  CustomArcadeVision.Pixels scene=(x,y)->{
   float nx=x/(float)w,ny=y/(float)h;
   if(box(nx,ny,.655f,.305f,.705f,.335f))return red;
   if(box(nx,ny,.27f,.86f,.53f,.90f))return brown;
   if(box(nx,ny,.70f,.10f,.82f,.16f))return blue;
   return 0x213d59;
  };
  ArcadeObservation seen=CustomArcadeVision.detect(ArcadeProfile.PADDLE,scene,
     0,0,w,h,0,0,0,0,1000,null,profile);
  check(seen.usable()&&seen.player!=null&&seen.control!=null,
      "tablet-detail calibrated ball and basket recovered");
  near(.68f,seen.player.x(),.04f,"red ball position");
  near(.40f,seen.control.x(),.04f,"brown basket position");
  ArcadeObservation unknown=CustomArcadeVision.detect(ArcadeProfile.PADDLE,
     (x,y)->0x213d59,0,0,w,h,0,0,0,0,1100,seen,profile);
  check(!unknown.usable(),"unknown tablet scene cannot authorize input");

  try{CaptureResolution.forDevice(18000,1200,700);throw new AssertionError("oversize accepted");}
  catch(IllegalArgumentException expected){assertions++;}
  System.out.println("Tablet capture geometry and calibrated vision: "+
    assertions+" assertions PASS (synthetic, no physical-device claim)");
 }
}
