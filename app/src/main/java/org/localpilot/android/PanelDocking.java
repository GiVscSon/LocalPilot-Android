package org.localpilot.android;

/**
 * Side-effect free placement of an accessibility STOP panel outside the game ROI.
 * Coordinates are screen pixels. Unknown or impossible geometry must not authorize input.
 */
public final class PanelDocking {
 private PanelDocking() {}

 public static final class Area {
  public final int left,top,right,bottom;
  public Area(int left,int top,int right,int bottom){
   if(right<=left||bottom<=top||(long)right-left>Integer.MAX_VALUE||(long)bottom-top>Integer.MAX_VALUE)throw new IllegalArgumentException("invalid rectangle");
   this.left=left;this.top=top;this.right=right;this.bottom=bottom;
  }
  public int width(){return right-left;}
  public int height(){return bottom-top;}
  public boolean contains(Area other){
   return other!=null&&left<=other.left&&top<=other.top&&right>=other.right&&bottom>=other.bottom;
  }
  public boolean intersects(Area other){
   return other!=null&&left<other.right&&other.left<right&&top<other.bottom&&other.top<bottom;
  }
 }

 /** Returns null when no safe location exists, never a partially overlapping panel. */
 public static Area choose(Area safe,Area current,Area roi,int gapPx){
  if(safe==null||current==null||roi==null||gapPx<0||gapPx>10000)throw new IllegalArgumentException("invalid docking geometry");
  int w=current.width(),h=current.height();
  if(w>safe.width()||h>safe.height())return null;
  // For a lower-half manual board prefer the upper free strip, even when the
  // current STOP panel happens to be below the board and does not overlap it.
  // Physical display cutouts/system bars are already excluded by the caller.
  if((long)roi.top-safe.top>=h+gapPx){
   int x=(int)Math.max(safe.left,Math.min((long)current.left,(long)safe.right-w));
   int y=(int)Math.max(safe.top,Math.min((long)current.top,(long)roi.top-h-gapPx));
   Area top=new Area(x,y,x+w,y+h);
   if(safe.contains(top)&&!intersectsExpanded(top,roi,gapPx))
    return current.left==x&&current.top==y&&safe.contains(current)?current:top;
  }
  if(safe.contains(current)&&!current.intersects(roi))return current;
  // Try a comfortable margin first, then exact non-overlap if the window is tight.
  for(int margin:new int[]{gapPx,0}){
   int[] xs={current.left,safe.left,safe.right-w,bounded((long)roi.left-w-margin),bounded((long)roi.right+margin)};
   int[] ys={current.top,safe.top,safe.bottom-h,bounded((long)roi.top-h-margin),bounded((long)roi.bottom+margin)};
   Area best=null;long bestMovement=Long.MAX_VALUE;
   for(int x:xs)for(int y:ys){
    if(x<safe.left||y<safe.top||(long)x+w>safe.right||(long)y+h>safe.bottom)continue;
    Area candidate=new Area(x,y,x+w,y+h);
    if(candidate.intersects(roi))continue;
    if(margin>0&&intersectsExpanded(candidate,roi,margin))continue;
    long movement=Math.abs((long)x-current.left)+Math.abs((long)y-current.top);
    if(movement<bestMovement){bestMovement=movement;best=candidate;}
   }
   if(best!=null)return best;
  }
  return null;
 }

 private static int bounded(long value){return (int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,value));}

 private static boolean intersectsExpanded(Area panel,Area roi,int gap){
  return (long)panel.left<((long)roi.right+gap)&&((long)roi.left-gap)<panel.right
    &&(long)panel.top<((long)roi.bottom+gap)&&((long)roi.top-gap)<panel.bottom;
 }
}
