package org.localpilot.android;

/** Active Shipuchka zones are smaller than the manually selected full capture ROI.
 * Keep STOP visible but never mask the ceiling board, launcher, or central shot lane.
 * Geometry-only; camera pixels, unknown colors and gesture permits are untouched.
 */
public final class BubblePanelSafety {
 private BubblePanelSafety(){}
 public record Area(int left,int top,int right,int bottom){
  public Area{if(right<=left||bottom<=top)throw new IllegalArgumentException("area");}
  public int width(){return right-left;}
  public int height(){return bottom-top;}
  public boolean overlaps(Area other){return other!=null&&left<other.right&&right>other.left&&top<other.bottom&&bottom>other.top;}
  public boolean contains(Area other){return other!=null&&left<=other.left&&top<=other.top&&right>=other.right&&bottom>=other.bottom;}
 }
 private static Area region(Area r,double x0,double y0,double x1,double y1){
  return new Area(r.left+(int)(r.width()*x0),r.top+(int)(r.height()*y0),
    r.left+(int)(r.width()*x1),r.top+(int)(r.height()*y1));
 }
 public static boolean blocks(Area roi,Area panel){
  if(roi==null||panel==null||!roi.overlaps(panel))return false;
  // Entire ceiling lattice. A panel may not cover even one possible row.
  if(region(roi,0,0,1,.36).overlaps(panel))return true;
  // Actual loaded ball, reserve and buttons near bottom center.
  if(region(roi,.24,.76,.76,1).overlaps(panel))return true;
  // Possible direct or reflected central trajectory, kept conservative.
  return region(roi,.23,.35,.77,.84).overlaps(panel);
 }
 /** During rebind, reuse the capture safety policy for the selected game.
  * The result never grants an input permit or revives a stopped ticket.
  */
 public static boolean panelClearForRebind(Area roi,Area panel,boolean bubbleProfile){
  if(roi==null||panel==null)return false;
  return bubbleProfile?!blocks(roi,panel):!roi.overlaps(panel);
 }
 /** Choose a side position in the sky gap. Return null if none exists.
  * The caller MUST remeasure the actual WindowManager rectangle after layout.
  */
 public static Area dock(Area safe,Area roi,Area panel,int margin){
  if(safe==null||roi==null||panel==null||margin<0||panel.width()>safe.width()||panel.height()>safe.height())return null;
  if(safe.contains(panel)&&!blocks(roi,panel))return panel;
  int cy=roi.top+(int)(roi.height()*.57);
  int[] xs={safe.left+margin,safe.right-margin-panel.width()};
  int[] ys={cy-panel.height()/2,roi.top+(int)(roi.height()*.40)};
  for(int y:ys)for(int x:xs){
   Area candidate=new Area(x,y,x+panel.width(),y+panel.height());
   if(safe.contains(candidate)&&!blocks(roi,candidate))return candidate;
  }
  return null;
 }
}
