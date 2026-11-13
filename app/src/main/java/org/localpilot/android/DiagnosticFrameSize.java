package org.localpilot.android;

/** RAM-only preview/export budget. Never changes a reader's frame or ROI. */
public final class DiagnosticFrameSize {
 public static final int MAX_EDGE=720,MAX_PIXELS=345600;
 public static final long MAX_ARGB_BYTES=4L*MAX_PIXELS;
 public final int width,height;
 public DiagnosticFrameSize(int sourceWidth,int sourceHeight){
  if(sourceWidth<=0||sourceHeight<=0)throw new IllegalArgumentException("diagnostic source bounds");
  double scale=Math.min(1d,Math.min(MAX_EDGE/(double)sourceWidth,
    Math.min(MAX_EDGE/(double)sourceHeight,
      Math.sqrt(MAX_PIXELS/((double)sourceWidth*sourceHeight)))));
  width=Math.max(1,(int)Math.floor(sourceWidth*scale));
  height=Math.max(1,(int)Math.floor(sourceHeight*scale));
  if(width>MAX_EDGE||height>MAX_EDGE||(long)width*height>MAX_PIXELS)
   throw new IllegalArgumentException("diagnostic allocation bounds");
 }
 public long argbBytes(){return 4L*width*height;}
}
