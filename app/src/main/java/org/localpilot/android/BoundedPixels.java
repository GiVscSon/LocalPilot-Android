package org.localpilot.android;

/** A read-only view of one decoded region. A new frame needs a new view. */
public final class BoundedPixels implements PixelBoardReader.Pixels {
 private final int[] pixels;private final int width,height,left,top,right,bottom;private final long generation;
 public BoundedPixels(int[] pixels,int width,int height,int left,int top,int right,int bottom,long generation){
  if(pixels==null||width<=0||height<=0||(long)width*height>pixels.length||left<0||top<0||right<=left||bottom<=top||right>width||bottom>height||generation<0)throw new IllegalArgumentException("invalid decoded bounds");
  this.pixels=pixels;this.width=width;this.height=height;this.left=left;this.top=top;this.right=right;this.bottom=bottom;this.generation=generation;
 }
 public long generation(){return generation;}
 public int rgb(int x,int y){
  if(x<left||x>=right||y<top||y>=bottom)throw new IndexOutOfBoundsException("pixel read outside valid decoded bounds");
  return pixels[y*width+x];
 }
}
