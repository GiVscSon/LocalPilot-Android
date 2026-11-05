package org.localpilot.gamelab;

/**
 * Uniform scaling of the 360x640 logical game to a physical Android View.
 * Never stretch spheres, grids or the gesture target geometry separately.
 */
public final class GameLabViewport {
 public static final int LOGICAL_WIDTH=360, LOGICAL_HEIGHT=640;
 public final int width,height;
 public final float scale,offsetX,offsetY;
 public GameLabViewport(int width,int height){
  if(width<1||height<1)throw new IllegalArgumentException("Invalid GameLab bounds");
  this.width=width;this.height=height;
  scale=Math.min(width/(float)LOGICAL_WIDTH,height/(float)LOGICAL_HEIGHT);
  if(!Float.isFinite(scale)||scale<=0)throw new IllegalArgumentException("Invalid scale");
  offsetX=(width-LOGICAL_WIDTH*scale)*.5f;
  offsetY=(height-LOGICAL_HEIGHT*scale)*.5f;
 }
 public float x(float px){return (px-offsetX)/(LOGICAL_WIDTH*scale);}
 public float y(float py){return (py-offsetY)/(LOGICAL_HEIGHT*scale);}
 public float px(float nx){return offsetX+nx*LOGICAL_WIDTH*scale;}
 public float py(float ny){return offsetY+ny*LOGICAL_HEIGHT*scale;}
 public boolean inGame(float px,float py){
  return Float.isFinite(px)&&Float.isFinite(py)&&x(px)>=0&&x(px)<=1&&y(py)>=0&&y(py)<=1;
 }
}
