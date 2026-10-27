package org.localpilot.android;

/** Pure-Java placement for the compact STOP overlay. Never permits ROI overlap. */
final class PanelParking {
 static final class Position {
  final int x, y;
  Position(int x,int y){this.x=x;this.y=y;}
 }
 private PanelParking(){}
 static Position place(int width,int height,int safeTop,int safeBottom,
   int panelWidth,int panelHeight,int left,int top,int right,int bottom,
   int gap,int currentX,int currentY){
  if(width<=0||height<=0||safeTop<0||safeBottom<0||gap<0||
     panelWidth<=0||panelHeight<=0||left>=right||top>=bottom||
     safeTop+safeBottom>=height)return null;
  int maxX=width-panelWidth,maxY=height-safeBottom-panelHeight;
  if(maxX<0||maxY<safeTop)return null;
  if(fits(currentX,currentY,panelWidth,panelHeight,maxX,safeTop,maxY,left,top,right,bottom,gap))
   return new Position(currentX,currentY);
  int[] xs={clamp(currentX,0,maxX),maxX,0,
   clamp(left-gap-panelWidth,0,maxX),clamp(right+gap,0,maxX)};
  int[] ys={maxY,safeTop,clamp(bottom+gap,safeTop,maxY),
   clamp(top-gap-panelHeight,safeTop,maxY)};
  for(int y:ys)for(int x:xs)
   if(fits(x,y,panelWidth,panelHeight,maxX,safeTop,maxY,left,top,right,bottom,gap))
    return new Position(x,y);
  return null;
 }
 private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(v,hi));}
 private static boolean fits(int x,int y,int w,int h,int maxX,int minY,int maxY,
   int left,int top,int right,int bottom,int gap){
  if(x<0||x>maxX||y<minY||y>maxY)return false;
  return (long)x+w+gap<=left||(long)x>=right+(long)gap||
         (long)y+h+gap<=top||(long)y>=bottom+(long)gap;
 }
}
