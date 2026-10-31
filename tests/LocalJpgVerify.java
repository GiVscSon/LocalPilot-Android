import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Private local JPEG probe. Never uploads image bytes to CI or the APK.
 * Run: java -Djava.awt.headless=true -Dlocalpilot.jpgDir=/path/to/jpgs tests/LocalJpgVerify.java
 * The exact October 30 screenshot must be present. Missing input is an error,
 * never a synthetic PASS or a guessed RGB fingerprint.
 */
public final class LocalJpgVerify {
 private static final String IMAGE = "Screenshot_2026-10-30-14-32-00-613_ru.beru.android.jpg";
 private static final int LEFT=54, TOP=825, RIGHT=638, BOTTOM=1409;
 private static final int ROW=1, COL=3, GRID=4; // zero-based R2C4
 private LocalJpgVerify(){}

 public static void main(String[] args) throws IOException {
  String dir=System.getProperty("localpilot.jpgDir");
  if(dir==null||dir.isBlank())dir=System.getenv("LOCALPILOT_JPG_DIR");
  if(dir==null||dir.isBlank())
   throw new IllegalArgumentException("Set -Dlocalpilot.jpgDir=/path/to/jpgs or LOCALPILOT_JPG_DIR");
  File source=new File(dir,IMAGE);
  if(!source.isFile())throw new IOException("Original 2048 JPEG missing: "+source.getAbsolutePath());
  BufferedImage img=ImageIO.read(source);
  if(img==null)throw new IOException("Not a decodable JPEG: "+source);
  if(img.getWidth()<RIGHT||img.getHeight()<BOTTOM)
   throw new IOException("Image too small for annotated ROI: "+img.getWidth()+"x"+img.getHeight());
  if(RIGHT-LEFT!=584||BOTTOM-TOP!=584||((RIGHT-LEFT)%GRID)!=0)
   throw new IllegalStateException("2048 annotation geometry changed");

  int cell=(RIGHT-LEFT)/GRID;
  int cellLeft=LEFT+COL*cell,cellTop=TOP+ROW*cell;
  int cx=cellLeft+cell/2,cy=cellTop+cell/2;
  if(cell!=146||cellLeft!=492||cellTop!=971||cx!=565||cy!=1044)
   throw new IllegalStateException("2048 R2C4 cell is not the expected annotated rectangle");

  long rSum=0,gSum=0,bSum=0,n=0;
  // The annulus inside the annotated R2C4 cell, excluding the central glyph.
  for(int y=993;y<=1095;y+=4){
   for(int x=514;x<=616;x+=4){
    int dx=x-cx,dy=y-cy,d2=dx*dx+dy*dy;
    if(d2>=22*22&&d2<=48*48){
     int p=img.getRGB(x,y);
     rSum+=(p>>16)&0xff;gSum+=(p>>8)&0xff;bSum+=p&0xff;n++;
    }
   }
  }
  if(n==0)throw new IllegalStateException("No pixels sampled from 2048 ring");
  int r=(int)(rSum/n),g=(int)(gSum/n),b=(int)(bSum/n);
  float[] hsv=Color.RGBtoHSB(r,g,b,null);
  int center=img.getRGB(cx,cy);
  int cr=(center>>16)&0xff,cg=(center>>8)&0xff,cb=center&0xff;
  float[] centerHsv=Color.RGBtoHSB(cr,cg,cb,null);
  System.out.printf(java.util.Locale.ROOT,
   "DIAG_2048_CELL_R2C4 ring_avg=RGB(%d,%d,%d) #%02X%02X%02X HSV(%.1f,%.3f,%.3f) center=RGB(%d,%d,%d) HSV(%.1f,%.3f,%.3f)%n",
   r,g,b,r,g,b,hsv[0]*360f,hsv[1],hsv[2],
   cr,cg,cb,centerHsv[0]*360f,centerHsv[1],centerHsv[2]);
  System.out.printf(java.util.Locale.ROOT,
   "DIAG_2048_CELL_R2C4_META source=%s roi=[%d,%d][%d,%d] cell=[%d,%d][%d,%d] sampled=%d%n",
   IMAGE,LEFT,TOP,RIGHT,BOTTOM,cellLeft,cellTop,cellLeft+cell,cellTop+cell,n);
 }
}
