package org.localpilot.android;

import java.nio.ByteBuffer;

/** A reusable owned pixel buffer. One outstanding lease prevents all overwrites.
 * Callers capture the returned primitive token, use it for EVERY read, then
 * release it only after all consumers finish. Tokens never wrap or repeat.
 * No array is exposed; stale or cross-session readers fail closed.
 */
public final class DecodedFrameBuffer implements BoundedPixels.Source {
 private final int width,height;
 private final int[] pixels;
 private volatile long activeToken;
 private long sequence,leaseSession;
 private int left,top,right,bottom;
 private boolean closed;
 private long decodedPixels,roiFrames,fullFrames;
 public DecodedFrameBuffer(int width,int height){
  if(width<=0||height<=0||(long)width*height>8_000_000)throw new IllegalArgumentException("decoded frame dimensions");
  this.width=width;this.height=height;pixels=new int[width*height];
 }
 /** Decode publishes a lease only after successful validation and pixel writes. */
 public synchronized long decode(ByteBuffer bytes,int rowStride,int pixelStride,long session,FrameDecodePlan plan){
  if(closed)throw new IllegalStateException("decoded frame buffer closed");
  if(activeToken!=0)throw new IllegalStateException("decoded frame buffer already leased");
  if(session<=0)throw new IllegalArgumentException("invalid capture session");
  if(sequence==Long.MAX_VALUE)throw new IllegalStateException("decoded frame generations exhausted");
  boolean roi=plan!=null&&plan.allowsRoi(session,width,height);
  int l=roi?plan.left:0,t=roi?plan.top:0,r=roi?plan.right:width,b=roi?plan.bottom:height;
  RgbaFrameDecoder.decodeRoiInPlace(bytes,rowStride,pixelStride,width,height,l,t,r,b,pixels);
  left=l;top=t;right=r;bottom=b;leaseSession=session;
  long n=(long)(r-l)*(b-t);decodedPixels=decodedPixels>Long.MAX_VALUE-n?Long.MAX_VALUE:decodedPixels+n;
  if(roi){if(roiFrames<Long.MAX_VALUE)roiFrames++;}else if(fullFrames<Long.MAX_VALUE)fullFrames++;
  activeToken=++sequence;
  return activeToken;
 }
 public boolean isLeased(){return activeToken!=0;}
 public int width(){return width;}
 public int height(){return height;}
 public void require(long token,long session){
  requireActive(token);
  if(leaseSession!=session)throw new IllegalStateException("decoded frame session changed");
  requireActive(token);
 }
 private void requireActive(long token){
  if(token<=0||activeToken!=token)throw new IllegalStateException("decoded frame lease expired");
 }
 public long session(long token){requireActive(token);long result=leaseSession;requireActive(token);return result;}
 public int validLeft(long token){requireActive(token);int value=left;requireActive(token);return value;}
 public int validTop(long token){requireActive(token);int value=top;requireActive(token);return value;}
 public int validRight(long token){requireActive(token);int value=right;requireActive(token);return value;}
 public int validBottom(long token){requireActive(token);int value=bottom;requireActive(token);return value;}
 public boolean fullFrame(long token){return contains(token,0,0,width,height);}
 public boolean contains(long token,int l,int t,int r,int b){
  requireActive(token);
  boolean result=l>=left&&t>=top&&r<=right&&b<=bottom&&r>l&&b>t;
  requireActive(token);return result;
 }
 public int rgb(long token,int x,int y){
  requireActive(token);
  if(x<left||x>=right||y<top||y>=bottom)throw new IndexOutOfBoundsException("pixel read outside valid decoded bounds");
  int color=pixels[y*width+x];
  requireActive(token);return color;
 }
 /** Full-frame conversion is explicit: ROI data may never become a fake full image. */
 public synchronized void copyFullTo(long token,int[] target){
  requireActive(token);
  if(!fullFrame(token))throw new IllegalStateException("full frame pixels required");
  if(target==null||target.length<pixels.length)throw new IllegalArgumentException("full frame output too small");
  System.arraycopy(pixels,0,target,0,pixels.length);
 }
 /** Idempotent for stale callbacks; an old lease cannot release its successor. */
 public synchronized boolean release(long token){
  if(token<=0||activeToken!=token)return false;
  activeToken=0;return true;
 }
 /** Session STOP invalidates even a reader that still retains its old token. */
 public synchronized void close(){closed=true;activeToken=0;}
 public synchronized long decodedPixels(){return decodedPixels;}
 public synchronized long roiFrames(){return roiFrames;}
 public synchronized long fullFrames(){return fullFrames;}
}
