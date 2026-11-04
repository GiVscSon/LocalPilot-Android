package org.localpilot.android;

/** Optional removal of matching, flat sidebars. Never authorizes an input action. */
public final class GameViewport {
 private GameViewport(){}
 public record Box(int left,int top,int right,int bottom){
  public int width(){return right-left;}public int height(){return bottom-top;}
  public boolean contains(Box b){return b!=null&&b.left>=left&&b.top>=top&&b.right<=right&&b.bottom<=bottom&&b.width()>0&&b.height()>0;}
 }
 public static Box selection(Box window,int x1,int y1,int x2,int y2){
  if(window==null)throw new IllegalArgumentException("window");
  Box b=new Box(Math.min(x1,x2),Math.min(y1,y2),Math.max(x1,x2),Math.max(y1,y2));
  if(!window.contains(b)||b.width()<window.width()*.15f||b.height()<window.height()*.25f)throw new IllegalArgumentException("small/outside region");
  return b;
 }
 public static Box detect(PixelBoardReader.Pixels pixels,Box window,Box excluded){
  if(pixels==null||window==null||window.width()<120||window.height()<180)throw new IllegalArgumentException("viewport");
  int w=window.width();int[] color=new int[w];java.util.Arrays.fill(color,-1);
  for(int i=0;i<w;i++){
   int x=window.left+i,n=0,minR=255,minG=255,minB=255,maxR=0,maxG=0,maxB=0,sumR=0,sumG=0,sumB=0;
   for(int j=2;j<30;j++){
    int y=window.top+j*window.height()/32;
    if(excluded!=null&&x>=excluded.left&&x<excluded.right&&y>=excluded.top&&y<excluded.bottom)continue;
    int c=pixels.rgb(x,y),r=c>>>16&255,g=c>>>8&255,b=c&255;
    minR=Math.min(minR,r);maxR=Math.max(maxR,r);minG=Math.min(minG,g);maxG=Math.max(maxG,g);minB=Math.min(minB,b);maxB=Math.max(maxB,b);sumR+=r;sumG+=g;sumB+=b;n++;
   }
   if(n>=4&&maxR-minR<=20&&maxG-minG<=20&&maxB-minB<=20)color[i]=(sumR/n)<<16|(sumG/n)<<8|sumB/n;
  }
  int center=w/2;if(color[center]>=0)return window;
  int left=0,right=w,run=0,minimum=Math.max(4,w/32);
  for(int i=center;i>=0;i--){if(color[i]>=0)run++;else run=0;if(run>=minimum){left=i+run;break;}}
  run=0;for(int i=center;i<w;i++){if(color[i]>=0)run++;else run=0;if(run>=minimum){right=i-run+1;break;}}
  if(left<w*.08f||w-right<w*.08f||right-left<Math.max(120,w*.25f)||right-left>w*.9f)return window;
  if(distance(color[left-1],color[right])>30)return window;
  return new Box(window.left+left,window.top,window.left+right,window.bottom);
 }
 private static int distance(int a,int b){return Math.max(Math.abs((a>>>16&255)-(b>>>16&255)),Math.max(Math.abs((a>>>8&255)-(b>>>8&255)),Math.abs((a&255)-(b&255))));}
}
