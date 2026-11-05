package org.localpilot.gamelab;

/**
 * Preserve sprite/board geometry on every Android aspect ratio.
 * The legacy independent X/Y scale stretched square 2048 cells into
 * rectangles on phones/tablets and made Pilot ROI recognition impossible.
 * Exact same mapping is used for rendering AND OS-delivered MotionEvents.
 */
public final class GameLabViewport {
 public static final int LOGICAL_WIDTH=360,LOGICAL_HEIGHT=640;
 public final float scale,offsetX,offsetY,physicalWidth,physicalHeight;
 public GameLabViewport(float width,float height){
  if(!Float.isFinite(width)||!Float.isFinite(height)||width<=0||height<=0)
   throw new IllegalArgumentException("Invalid Android surface geometry");
  physicalWidth=width;physicalHeight=height;
  scale=Math.min(width/LOGICAL_WIDTH,height/LOGICAL_HEIGHT);
  offsetX=(width-LOGICAL_WIDTH*scale)/2;
  offsetY=(height-LOGICAL_HEIGHT*scale)/2;
 }
 public float xFromScreen(float x){return (x-offsetX)/(scale*LOGICAL_WIDTH);}
 public float yFromScreen(float y){return (y-offsetY)/(scale*LOGICAL_HEIGHT);}
 public float screenX(float normalizedX){return offsetX+normalizedX*scale*LOGICAL_WIDTH;}
 public float screenY(float normalizedY){return offsetY+normalizedY*scale*LOGICAL_HEIGHT;}
 public float gameWidth(){return LOGICAL_WIDTH*scale;}
 public float gameHeight(){return LOGICAL_HEIGHT*scale;}
 public boolean contains(float x,float y){
  return x>=offsetX&&x<offsetX+gameWidth()
    &&y>=offsetY&&y<offsetY+gameHeight();
 }
}
