package org.localpilot.android;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/** Shape tests intentionally include colours that the game palette does not support. */
public final class BubbleObjectVisionTest {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 private static BufferedImage scene(int color,float scale,int shape){
  return scene(color,scale,shape,0x87ceeb);
 }
 private static BufferedImage scene(int color,float scale,int shape,int background){
  int w=Math.round(180*scale),h=Math.round(200*scale);var im=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
  var g=im.createGraphics();g.scale(scale,scale);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
  g.setColor(new Color(background));g.fillRect(0,0,180,200);g.setColor(new Color(color));
  if(shape==0)g.fill(new Ellipse2D.Float(70,70,40,40));
  else if(shape==1)g.fill(new Rectangle2D.Float(70,70,40,40));
  else if(shape==2)g.fillPolygon(new int[]{90,112,68},new int[]{68,112,112},3);
  else if(shape==3){g.fill(new Rectangle2D.Float(60,84,60,12));g.fill(new Rectangle2D.Float(84,60,12,60));}
  g.dispose();return im;
 }
 public static void main(String[] args){
  for(float scale:new float[]{.75f,1,1.5f,2}){
   for(int color:new int[]{0xe52c32,0xf08016,0xf0cf20,0x4ab63b,0x3040df,0x101010,0xffffff,0x808080}){
    var im=scene(color,scale,0);
    var p=BubbleObjectVision.inspect(im::getRGB,im.getWidth(),im.getHeight(),90*scale,90*scale,22*scale);
    check(p.kind()==BubbleObjectVision.Kind.BALL,"same round object survives hue/brightness/scale "+Integer.toHexString(color)+" "+scale+": "+p);
   }
   for(int shape:new int[]{1,2,3}){
    var im=scene(0xe52c32,scale,shape);
    var p=BubbleObjectVision.inspect(im::getRGB,im.getWidth(),im.getHeight(),90*scale,90*scale,22*scale);
    check(p.kind()==BubbleObjectVision.Kind.UNKNOWN,"compact non-ball obstacle is not ordinary candy or empty space: shape="+shape+" scale="+scale+" "+p);
   }
  }
  for(int background:new int[]{0x87ceeb,0xe3adbf,0x909090}){
   var p=BubbleObjectVision.inspect((x,y)->background,180,200,90,90,22);
   check(p.kind()==BubbleObjectVision.Kind.EMPTY,"uniform background is not an object, independent of its hue");
  }
  for(int background:new int[]{0x224455,0xe3adbf,0xd8d8d8})for(int color:new int[]{0xe52c32,0xf0cf20,0x4ab63b,0x3040df}){
   var im=scene(color,1,0,background);
   check(BubbleObjectVision.inspect(im::getRGB,180,200,90,90,22).kind()==BubbleObjectVision.Kind.BALL,"object identity survives a different surrounding palette");
  }
  BubbleObjectVision.Pixels clouds=(x,y)->{
   int r=225+(int)(5*Math.sin(x/50.)),g=180+(int)(12*Math.cos(y/55.)),b=195+(int)(10*Math.sin((x+y)/65.));
   return r<<16|g<<8|b;
  };
  check(BubbleObjectVision.inspect(clouds,180,200,90,90,22).kind()==BubbleObjectVision.Kind.EMPTY,"cloud shading does not create a centred object");
  var p=BubbleObjectVision.inspect((x,y)->0x87ceeb,180,200,1,1,22);
  check(p.kind()==BubbleObjectVision.Kind.UNKNOWN,"insufficient visible contour abstains");
  System.out.println("Bubble objects before colour: "+checks+" assertions PASS");
 }
}
