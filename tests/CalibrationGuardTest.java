package org.localpilot.android;

import java.util.Arrays;

/** Synthetic, Android-free negative tests for frozen-frame calibration. */
public final class CalibrationGuardTest {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 private static final int BACKGROUND=0x27404f,PLAYER=0xe83239,PLATFORM=0x22be4f,HAZARD=0xf7ae20;
 private static final int WIDTH=240,HEIGHT=240;
 private static ColorSampleGuard.Pixels scene(int centerColor){
  return (x,y)->x>=82&&x<119&&y>=82&&y<119?centerColor:BACKGROUND;
 }
 private static ColorSampleGuard.Result sample(ColorSampleGuard.Pixels pixels,int x,int y,int[] previous,int count,boolean flappy){
  return ColorSampleGuard.inspect(pixels,WIDTH,HEIGHT,x,y,previous,count,flappy);
 }
 public static void main(String[] args){
  var ordinary=scene(PLAYER);
  var ok=sample(ordinary,100,100,new int[3],0,false);
  check(ok.accepted()&&ok.rgb()==PLAYER,"homogeneous player sample accepted using 9x9 core");
  check(sample(ordinary,118,100,new int[3],0,false).reason()==ColorSampleGuard.Reason.MIXED_SAMPLE,
    "sample crossing sprite/background edge cannot update palette");
  check(sample(ordinary,100,100,new int[]{PLAYER,0,0},1,false).reason()==ColorSampleGuard.Reason.CLASS_COLLISION,
    "two semantic classes cannot share one calibrated colour");
  check(sample((x,y)->BACKGROUND,100,100,new int[3],0,false).reason()==ColorSampleGuard.Reason.BACKGROUND,
    "dominant border-colour background cannot become player colour");
  check(sample(ordinary,3,100,new int[3],0,false).reason()==ColorSampleGuard.Reason.EDGE_SAMPLE,
    "frame edge cannot be extrapolated with repeated border pixels");
  check(sample(ordinary,-1,100,new int[3],0,false).reason()==ColorSampleGuard.Reason.OUTSIDE_FRAME,
    "point outside the screenshot cannot teach the model");
  ColorSampleGuard.Pixels brightCenter=(x,y)->{
   int rgb=ordinary.rgb(x,y);
   return x>=97&&x<103&&y>=97&&y<103?0xffffff:rgb;
  };
  check(sample(brightCenter,100,100,new int[3],0,false).reason()==ColorSampleGuard.Reason.MIXED_SAMPLE,
    "bright animation/highlight under centre tap must be rejected");

  var upper=scene(PLATFORM);
  check(sample(upper,100,100,new int[]{PLAYER,PLATFORM,0},2,false).reason()==ColorSampleGuard.Reason.CLASS_COLLISION,
    "ordinary arcade different object roles cannot alias");
  check(sample(upper,100,100,new int[]{PLAYER,PLATFORM,0},2,true).accepted(),
    "FLAPPY upper and lower pipes may intentionally have identical colours");
  check(sample(ordinary,100,100,new int[]{PLAYER,PLATFORM,0},2,true).reason()==ColorSampleGuard.Reason.CLASS_COLLISION,
    "FLAPPY exception must not allow a pipe to look like the player");
  var yellow=scene(HAZARD);
  check(sample(yellow,100,100,new int[]{PLAYER,PLATFORM,0},2,false).accepted(),
    "third visually distinct hazard sample remains eligible");

  // The square aspect guard is opt-in. It must not change arcade or
  // irregular 6x7 board ROI behavior.
  for(int n:new int[]{4,6,7}){
   check(RegionPickerGeometry.squareBoardAspect(new GameViewport.Box(0,0,200,200),n,n),
     "square "+n+"x"+n+" board accepted");
   check(RegionPickerGeometry.squareBoardAspect(new GameViewport.Box(0,0,110,100),n,n),
     "slightly stretched "+n+"x"+n+" board allowed");
   check(!RegionPickerGeometry.squareBoardAspect(new GameViewport.Box(0,0,220,100),n,n),
     "phone-shaped menu region rejected for "+n+"x"+n);
   check(!RegionPickerGeometry.squareBoardAspect(new GameViewport.Box(0,0,60,100),n,n),
     "narrow strip rejected for "+n+"x"+n);
  }
  check(RegionPickerGeometry.squareBoardAspect(new GameViewport.Box(0,0,300,500),6,7),
    "non-square 6x7 grids keep their explicit geometry contract");
  // The tablet editor previously accepted 15% stretched 2048 ROIs, although
  // both PixelBoardReader and Color2048Reader reject them at the first read.
  check(RegionPickerGeometry.readable2048(new GameViewport.Box(100,100,500,500)),
    "square 400x400 2048 region is readable on tablet");
  check(RegionPickerGeometry.readable2048(new GameViewport.Box(0,0,324,300)),
    "8 percent border accepted by the actual readers");
  check(!RegionPickerGeometry.readable2048(new GameViewport.Box(0,0,345,300)),
    "15 percent stretched 2048 ROI cannot be saved as working");
  check(!RegionPickerGeometry.readable2048(new GameViewport.Box(0,0,300,345)),
    "15 percent vertically stretched 2048 ROI rejected");
  check(!RegionPickerGeometry.readable2048(new GameViewport.Box(0,0,150,150)),
    "too-small 2048 board rejected at calibration, not after start");
  check(!RegionPickerGeometry.readable2048(new GameViewport.Box(0,0,1500,350)),
    "tablet full-landscape window cannot masquerade as 2048 board");
  check(RegionPickerGeometry.squareBoardAspect(new GameViewport.Box(0,0,345,300),6,6),
    "other square-cell games preserve their own 18 percent tolerance");
  for(GameViewport.Box bad:new GameViewport.Box[]{new GameViewport.Box(0,0,0,30),new GameViewport.Box(0,0,30,0)}){
   try{RegionPickerGeometry.squareBoardAspect(bad,4,4);throw new AssertionError("invalid rectangle accepted");}
   catch(IllegalArgumentException expected){checks++;}
  }
  System.out.println("Calibration 9x9 sample / square ROI: "+checks+" assertions PASS (synthetic only)");
 }
}
