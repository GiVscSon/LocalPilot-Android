package org.localpilot.android;

import java.util.Arrays;

/** Immutable normal-bubble board in original ROI pixels. 0=empty; 1..4=known colors. */
public final class BubbleBoard {
 public static final int MAX_CELLS=384;
 public final int width,height,cols,rows,loadedColor,shortRowParity;
 public final float x0,y0,step,radius,launcherX,launcherY;
 private final int[] cells;
 public BubbleBoard(int width,int height,int cols,int rows,float x0,float y0,float step,
   float launcherX,float launcherY,int loadedColor,int[] cells){
  this(width,height,cols,rows,x0,y0,step,launcherX,launcherY,loadedColor,cells,1);
 }
 public BubbleBoard(int width,int height,int cols,int rows,float x0,float y0,float step,
   float launcherX,float launcherY,int loadedColor,int[] cells,int shortRowParity){
  if((shortRowParity!=0&&shortRowParity!=1)||width<80||height<120||cols<4||cols>16||rows<2||rows>24||cols*rows>MAX_CELLS
    ||cells==null||cells.length!=cols*rows||loadedColor<1||loadedColor>4
    ||!Float.isFinite(x0)||!Float.isFinite(y0)||!Float.isFinite(step)
    ||!Float.isFinite(launcherX)||!Float.isFinite(launcherY)||step<8
    ||x0<step*.5f||x0+(cols-1)*step+step*.5f>width||y0<step*.5f
    ||y0+(rows-1)*step*.8660254f+step*.5f>height
    ||launcherX<step||launcherX>width-step||launcherY>=height||launcherY<y0+step*3)
   throw new IllegalArgumentException("bubble geometry");
  this.width=width;this.height=height;this.cols=cols;this.rows=rows;this.x0=x0;this.y0=y0;
  this.step=step;this.radius=step/2;this.launcherX=launcherX;this.launcherY=launcherY;
  this.loadedColor=loadedColor;this.shortRowParity=shortRowParity;this.cells=cells.clone();
  for(int i=0;i<this.cells.length;i++)if(this.cells[i]<0||this.cells[i]>4||!slot(i)&&this.cells[i]!=0)
   throw new IllegalArgumentException("bubble cell");
 }
 public int size(){return cells.length;}
 public int color(int index){if(index<0||index>=cells.length)throw new IllegalArgumentException("bubble index");return cells[index];}
 public int[] cells(){return cells.clone();}
 public float x(int index){if(index<0||index>=cells.length)throw new IllegalArgumentException("bubble index");return x0+(index%cols+(index/cols%2==shortRowParity?.5f:0f))*step;}
 public float y(int index){if(index<0||index>=cells.length)throw new IllegalArgumentException("bubble index");return y0+(index/cols)*step*.8660254f;}
 public boolean slot(int index){return index>=0&&index<cols*rows&&!(index/cols%2==shortRowParity&&index%cols==cols-1);}
 public boolean neighbor(int a,int b){if(a==b||!slot(a)||!slot(b))return false;float dx=x(a)-x(b),dy=y(a)-y(b);return dx*dx+dy*dy<step*step*1.05f;}
 public boolean sameGeometry(BubbleBoard b){return b!=null&&width==b.width&&height==b.height&&cols==b.cols&&rows==b.rows
   &&shortRowParity==b.shortRowParity&&Math.abs(x0-b.x0)<radius*.12f&&Math.abs(y0-b.y0)<radius*.12f&&Math.abs(step-b.step)<radius*.04f
   &&Math.abs(launcherX-b.launcherX)<radius*.15f&&Math.abs(launcherY-b.launcherY)<radius*.15f;}
 public boolean same(BubbleBoard b){return sameGeometry(b)&&loadedColor==b.loadedColor&&Arrays.equals(cells,b.cells);}
 public int count(){int n=0;for(int c:cells)if(c>0)n++;return n;}
 public boolean ceilingConnected(){return connected(cells)==count();}
 int connected(int[] values){
  boolean[] seen=new boolean[size()];int[] queue=new int[size()];int head=0,tail=0;
  for(int i=0;i<cols;i++)if(values[i]>0){seen[i]=true;queue[tail++]=i;}
  while(head<tail){int at=queue[head++];for(int n=Math.max(0,at-cols-1);n<Math.min(size(),at+cols+2);n++)
   if(!seen[n]&&values[n]>0&&neighbor(at,n)){seen[n]=true;queue[tail++]=n;}}
  return tail;
 }
}
