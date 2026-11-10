import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.net.URLClassLoader;
import javax.tools.ToolProvider;

/** Reproduces actual Pixel C dimensions without Android or detector relaxations. */
public final class BubbleFixtureGeometryVerify {
 private static int checks;
 private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
 private static BufferedImage render(int w,int h,boolean ricochet){
  BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
  int[] colors={0x87ceeb,0xe52c32,0xf08016,0xf0cf20,0x4ab63b};g.setColor(new Color(colors[0]));g.fillRect(0,0,w,h);
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
  int[][] rows=ricochet?new int[][]{{3,3,4,3,3,2},{2,3,3,1,2},{0,0,1,3,4,4},{0,2,2,1,2}}:
   new int[][]{{1,1,2,3,4,4},{0,0,4,1,2}};
  float d=w/(ricochet?6.4f:7f),radius=d*.46f,x0=(ricochet?.7f:.8f)*d;
  for(int row=0;row<rows.length;row++)for(int col=0;col<rows[row].length;col++)if(rows[row][col]>0){
   g.setColor(new Color(colors[rows[row][col]]));float x=x0+(col+(row%2)*.5f)*d,y=(1.5f+row*.8660254f)*d;
   g.fill(new java.awt.geom.Ellipse2D.Float(x-radius,y-radius,radius*2,radius*2));
  }
  g.setColor(new Color(colors[ricochet?2:1]));g.fill(new java.awt.geom.Ellipse2D.Float(w*.5f-radius,h*.88f-radius,radius*2,radius*2));g.dispose();return image;
 }
 public static void main(String[] args)throws Exception{
  Path classes=Files.createTempDirectory("bubble-tablet-geometry-");
  String base="app/src/main/java/org/localpilot/android/";
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17","-d",classes.toString(),
    base+"BubbleVision.java",base+"BubbleBoard.java",base+"BubblePolicy.java",base+"BubblePanelSafety.java",
    "app/src/androidTest/java/org/localpilot/android/BubbleFixtureGeometry.java")!=0)throw new AssertionError("fixture geometry compile failed");
  try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()})){
   Class<?> geometry=loader.loadClass("org.localpilot.android.BubbleFixtureGeometry"),pixels=loader.loadClass("org.localpilot.android.BubbleVision$Pixels");
   Class<?> vision=loader.loadClass("org.localpilot.android.BubbleVision"),board=loader.loadClass("org.localpilot.android.BubbleBoard");
   java.lang.reflect.Method fit=geometry.getMethod("fit",int.class,int.class,int.class,int.class,int.class);
   java.lang.reflect.Method read=vision.getMethod("read",pixels,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,board);
   // The original wide tablet fixture cannot be acquired: its circles and gun
   // were stretched according to incompatible width/height scales.
   for(boolean ricochet:new boolean[]{false,true}){
    BufferedImage invalid=render(2528,1464,ricochet);
    Object px=java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{pixels},(p,m,a)->invalid.getRGB((int)a[0],(int)a[1]));
    Object result=read.invoke(null,px,2528,1464,0,0,2528,1464,0,0,0,0,null);
    check(!(boolean)result.getClass().getMethod("usable").invoke(result),"original stretched Pixel C fixture must reproduce rejection");
   }
   for(int[] window:new int[][]{{1080,1920,21,420,21},{2560,1800,16,320,16},{2560,1512,16,320,16},{1800,2560,16,320,16}}){
    Object area=fit.invoke(null,window[0],window[1],window[2],window[3],window[4]);
    int w=(int)area.getClass().getMethod("width").invoke(area),h=(int)area.getClass().getMethod("height").invoke(area);
    check(w*4<=h*3,"portrait viewport preserved");
    check((int)area.getClass().getMethod("left").invoke(area)>=window[2],"viewport stays inside real window");
    if(window[0]==1080)check(w==1038&&h==1479,"existing phone geometry unchanged");
    for(boolean ricochet:new boolean[]{false,true}){
     BufferedImage image=render(w,h,ricochet);
     Object px=java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{pixels},(p,m,a)->image.getRGB((int)a[0],(int)a[1]));
     Object result=read.invoke(null,px,w,h,0,0,w,h,0,0,0,0,null);
     check((boolean)result.getClass().getMethod("usable").invoke(result),"unmodified vision acquires "+w+"x"+h+" ricochet="+ricochet+": "+result.getClass().getMethod("reason").invoke(result));
     Object seen=result.getClass().getMethod("board").invoke(result);
     check((int)board.getMethod("count").invoke(seen)==(ricochet?19:9),"all independent fixture cells preserved");
    }
   }
  }
  System.out.println("BUBBLE_FIXTURE_VIEWPORT_PASS checks="+checks+" phone/tablet and direct/ricochet, production vision unchanged");
 }
}
