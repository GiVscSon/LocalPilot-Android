package org.localpilot.android;

/** A read-only view of one decoded region. Pipeline views bind an owned lease.
 * The legacy array constructor requires its caller to keep that array immutable.
 */
public final class BoundedPixels implements PixelBoardReader.Pixels {
 public interface Source {
  int width();int height();long session(long token);
  int validLeft(long token);int validTop(long token);int validRight(long token);int validBottom(long token);
  void require(long token,long session);int rgb(long token,int x,int y);
 }
 private final int[] pixels;private final Source source;
 private final int width,height,left,top,right,bottom;private final long generation,session;
 public BoundedPixels(int[] pixels,int width,int height,int left,int top,int right,int bottom,long generation){
  if(pixels==null||width<=0||height<=0||(long)width*height>pixels.length||left<0||top<0||right<=left||bottom<=top||right>width||bottom>height||generation<0)throw new IllegalArgumentException("invalid decoded bounds");
  this.pixels=pixels;source=null;session=0;this.width=width;this.height=height;this.left=left;this.top=top;this.right=right;this.bottom=bottom;this.generation=generation;
 }
 public BoundedPixels(Source source,long session,long generation){
  if(source==null)throw new IllegalArgumentException("decoded frame source");
  source.require(generation,session);this.source=source;this.session=session;this.generation=generation;pixels=null;
  width=source.width();height=source.height();left=source.validLeft(generation);top=source.validTop(generation);
  right=source.validRight(generation);bottom=source.validBottom(generation);source.require(generation,session);
 }
 public long generation(){return generation;}
 public long session(){return session;}
 public int width(){return width;}public int height(){return height;}
 public boolean contains(int l,int t,int r,int b){
  if(source!=null)source.require(generation,session);
  return l>=left&&t>=top&&r<=right&&b<=bottom&&r>l&&b>t;
 }
 public int rgb(int x,int y){
  if(source!=null){source.require(generation,session);return source.rgb(generation,x,y);}
  if(x<left||x>=right||y<top||y>=bottom)throw new IndexOutOfBoundsException("pixel read outside valid decoded bounds");
  return pixels[y*width+x];
 }
 public int getPixel(int x,int y){return rgb(x,y);}
}
