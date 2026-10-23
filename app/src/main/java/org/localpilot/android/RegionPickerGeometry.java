package org.localpilot.android;

/** Screen/local coordinates for the explicit region editor. Never authorizes input. */
public final class RegionPickerGeometry {
 private RegionPickerGeometry(){}
 private static void window(GameViewport.Box window){
  if(window==null||window.left()<0||window.top()<0||(long)window.right()-window.left()<1
    ||(long)window.bottom()-window.top()<1)throw new IllegalArgumentException("region window");
 }
 public static GameViewport.Box initial(GameViewport.Box window,int rows,int cols){
  window(window);if(rows<1||rows>10||cols<1||cols>10)throw new IllegalArgumentException("grid size");
  int width=Math.max(1,(int)(window.width()*.8)),height=Math.max(1,(int)(window.height()*.6));
  // Prefer square cells when that still satisfies the viewport's minimum region.
  double cell=Math.min(width/(double)cols,height/(double)rows);
  int fittedWidth=Math.max(1,(int)(cell*cols)),fittedHeight=Math.max(1,(int)(cell*rows));
  if(fittedWidth>=window.width()*.15f&&fittedHeight>=window.height()*.25f){width=fittedWidth;height=fittedHeight;}
  int left=window.left()+(window.width()-width)/2,top=window.top()+(window.height()-height)/2;
  return GameViewport.selection(window,left,top,left+width,top+height);
 }
 private static int screen(int origin,float local){
  if(!Float.isFinite(local))throw new IllegalArgumentException("nonfinite coordinate");
  long value=Math.round(origin+(double)local);
  if(value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)throw new IllegalArgumentException("coordinate overflow");
  return (int)value;
 }
 public static GameViewport.Box selection(GameViewport.Box window,int originX,int originY,
   float localStartX,float localStartY,float localEndX,float localEndY){
  window(window);
  int x1=screen(originX,localStartX),y1=screen(originY,localStartY);
  if(x1<window.left()||x1>window.right()||y1<window.top()||y1>window.bottom())
   throw new IllegalArgumentException("start outside window");
  int x2=Math.max(window.left(),Math.min(window.right(),screen(originX,localEndX)));
  int y2=Math.max(window.top(),Math.min(window.bottom(),screen(originY,localEndY)));
  return GameViewport.selection(window,x1,y1,x2,y2);
 }
 public static GameViewport.Box local(GameViewport.Box screen,int originX,int originY){
  if(screen==null)throw new IllegalArgumentException("region");
  return new GameViewport.Box(Math.subtractExact(screen.left(),originX),Math.subtractExact(screen.top(),originY),
   Math.subtractExact(screen.right(),originX),Math.subtractExact(screen.bottom(),originY));
 }
}
