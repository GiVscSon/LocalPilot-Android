package org.localpilot.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bounded reader for the four ordinary candy colors and staggered rows in Shipuchka. */
public final class BubbleVision {
 public interface Pixels {int get(int x,int y);}
 public record Read(BubbleBoard board,String reason){public boolean usable(){return board!=null;}}
 private static final class Scratch {
  final int[] rgb=new int[240*480],queue=new int[240*480],stamp=new int[240*480];
  final byte[] strong=new byte[240*480],color=new byte[240*480];
  final boolean[] seen=new boolean[240*480];
  Pixels pixels;int sampleWidth,left,top,scale,generation;
  void begin(Pixels pixels,int sampleWidth,int left,int top,int scale){
   this.pixels=pixels;this.sampleWidth=sampleWidth;this.left=left;this.top=top;this.scale=scale;
   if(generation==Integer.MAX_VALUE){Arrays.fill(stamp,0);generation=1;}else generation++;
  }
  void sample(int at){if(stamp[at]==generation)return;int c=pixels.get(left+(at%sampleWidth)*scale,top+(at/sampleWidth)*scale);
   rgb[at]=c;color[at]=(byte)color(c,false);strong[at]=(byte)color(c,true);stamp[at]=generation;}
 }
 private static final ThreadLocal<Scratch> WORK=ThreadLocal.withInitial(Scratch::new);
 private record Blob(int l,int t,int r,int b,int count,int color){
  float x(){return (l+r)*.5f;}float y(){return (t+b)*.5f;}
  int width(){return r-l+1;}int height(){return b-t+1;}
  boolean round(){return width()>=7&&height()>=7&&width()/(float)height()>.80f&&width()/(float)height()<1.23f&&count/(float)(width()*height())>.52f;}
 }
 private BubbleVision(){}
 public static Read read(Pixels pixels,int width,int height,int left,int top,int right,int bottom,
   int excludedLeft,int excludedTop,int excludedRight,int excludedBottom,BubbleBoard previous){
  return read(pixels,width,height,left,top,right,bottom,excludedLeft,excludedTop,excludedRight,excludedBottom,previous,true);
 }
 /** Soft mode relaxes only object geometry, never ROI or UNKNOWN/special rejection. */
 public static Read read(Pixels pixels,int width,int height,int left,int top,int right,int bottom,
   int excludedLeft,int excludedTop,int excludedRight,int excludedBottom,BubbleBoard previous,boolean strict){
  return read(pixels,width,height,left,top,right,bottom,excludedLeft,excludedTop,excludedRight,excludedBottom,previous,strict,Float.NaN);
 }
 /** Only a hint for searching a real observed ball. Cannot synthesize a ball. */
 public static Read read(Pixels pixels,int width,int height,int left,int top,int right,int bottom,
   int excludedLeft,int excludedTop,int excludedRight,int excludedBottom,BubbleBoard previous,boolean strict,float learnedLauncherFraction){
  if(pixels==null||width<1||height<1||width>16_384||height>16_384||left<0||top<0||right>width||bottom>height||right-left<80||bottom-top<120)
   throw new IllegalArgumentException("bubble image bounds");
  if(excludedLeft<right&&excludedRight>left&&excludedTop<bottom&&excludedBottom>top)
   return new Read(null,"Панель перекрывает игровую область");
  int rw=right-left,rh=bottom-top,scale=Math.max(1,Math.max((rw+239)/240,(rh+479)/480));
  int w=(rw+scale-1)/scale,h=(rh+scale-1)/scale,n=w*h;Scratch work=WORK.get();
  work.begin(pixels,w,left,top,scale);
  try{
  // Initial acquisition needs components; a tracked board samples only the
  // bounded circle probes. Never copy/parse an entire screen for cached geometry.
  if(previous==null)for(int i=0;i<n;i++)work.sample(i);
  float x0,y0,step,lx,ly;int cols,rows,loaded,shortParity;
  if(previous!=null){
   if(previous.width!=rw||previous.height!=rh)return new Read(null,"Геометрия области изменилась");
   x0=previous.x0/scale;y0=previous.y0/scale;step=previous.step/scale;
   lx=previous.launcherX/scale;ly=previous.launcherY/scale;cols=previous.cols;rows=previous.rows;shortParity=previous.shortRowParity;
   loaded=circle(work,w,h,lx,ly,step*.5f);
   if(loaded<=0)return new Read(null,"Ожидаю обычный шар в пушке");
  }else{
   List<Blob> blobs=blobs(work,w,h);if(blobs==null)return new Read(null,"Слишком много цветовых объектов");
   Blob launcher=null;float score=Float.POSITIVE_INFINITY,second=Float.POSITIVE_INFINITY;
   float centerX=Float.isFinite(learnedLauncherFraction)
      &&learnedLauncherFraction>=.35f&&learnedLauncherFraction<=.65f
      ?learnedLauncherFraction*w:w*.5f;
   float radiusX=strict?.08f:.10f;
   // This skin's ordinary loaded ball is centered. The reserve sits only
   // ~15% of the arena width to its left and must never replace a missing gun.
   for(Blob b:blobs)if(b.color<=4&&b.round()&&b.width()>w*(strict?.045f:.04f)&&b.width()<w*(strict?.15f:.18f)
     &&b.x()>centerX-radiusX*w&&b.x()<centerX+radiusX*w
     &&b.y()>h*(strict?.68f:.60f)&&b.y()<h*(strict?.98f:.995f)){
    float s=Math.abs(b.x()-centerX)/w+(h-b.y())/h*.2f;
    if(s<score){second=score;score=s;launcher=b;}else second=Math.min(second,s);
   }
   if(launcher==null||second-score<(strict?.035f:.012f))return new Read(null,"Не найдена отдельная пушка: включите её в область");
   lx=launcher.x();ly=launcher.y();float diameter=(launcher.width()+launcher.height())*.5f;
   loaded=circle(work,w,h,lx,ly,diameter*.47f);
   if(loaded<=0)return new Read(null,"Бонус/неизвестный шар в пушке");
   List<Blob> anchors=new ArrayList<>();
   for(Blob b:blobs)if(b.color<=4&&b.round()&&b.y()<ly-diameter*3&&b.width()>diameter*.80f&&b.width()<diameter*1.18f
     &&b.height()>diameter*.80f&&b.height()<diameter*1.18f)anchors.add(b);
   if(anchors.size()<3)return new Read(null,"Нужны хотя бы три различимых круглых шара в рядах");
   int first=-1;
   for(int y=0;y<ly-diameter*3;y++){int count=0;for(int x=0;x<w;x++)if(work.strong[y*w+x]>0)count++;
    if(count>w*.12f){first=y;break;}}
   if(first<0)return new Read(null,"Не найдена верхняя граница рядов");
   float best=Float.POSITIVE_INFINITY;float[] fit=null;
   for(int parity=0;parity<2;parity++)for(float d=diameter*.86f;d<=diameter*1.12f;d+=.25f){
    for(float oy=first+d*.30f;oy<first+d*.68f;oy+=.5f)for(float ox=d*.40f;ox<d*.90f;ox+=.5f){
     float error=0;int minRow=100,maxRow=-1,matched=0,rowPhases=0;
     for(Blob b:anchors){
      int row=Math.round((b.y()-oy)/(d*.8660254f));if(row<0||row>23)continue;
      int col=Math.round((b.x()-ox)/d-(row%2==parity?.5f:0f));if(col<0||col>15)continue;
      float dx=b.x()-(ox+(col+(row%2==parity?.5f:0f))*d),dy=b.y()-(oy+row*d*.8660254f),e=dx*dx+dy*dy;
      if(e>d*d*(strict?.045f:.0675f)){continue;}error+=e;matched++;rowPhases|=1<<(row%2);minRow=Math.min(minRow,row);maxRow=Math.max(maxRow,row);
     }
     // Gapped islands do not require contiguous balls. A few textured
     // foreground fragments can be outliers, but geometry still needs
     // both row phases and at least four independent anchors.
     int tolerated=Math.min(2,anchors.size()/5);
     if(matched<Math.max(4,anchors.size()-tolerated)||maxRow<=minRow||rowPhases!=3)continue;
     error=(error+(anchors.size()-matched)*d*d*.018f)/(anchors.size()*d*d);
     if(error<best){best=error;fit=new float[]{ox,oy,d,parity};}
    }
   }
   if(fit==null||best>(strict?.008f:.011f))return new Read(null,"Круглые объекты не образуют устойчивые ряды");
   x0=fit[0];y0=fit[1];step=fit[2];shortParity=(int)fit[3];
   cols=(int)Math.floor((w-x0-step*.40f)/step)+1;
   rows=Math.min(24,(int)Math.floor((ly-step*2-y0)/(step*.8660254f))+1);
   if(cols<4||cols>16||rows<2||cols*rows>BubbleBoard.MAX_CELLS)return new Read(null,"Неподдержанная геометрия рядов");
  }
  int[] cells=new int[rows*cols];int emptyRows=0,count=0;
  for(int row=0;row<rows;row++){
   int occupied=0;
   for(int col=0;col<cols-(row%2==shortParity?1:0);col++){
    int type=circle(work,w,h,x0+(col+(row%2==shortParity?.5f:0f))*step,y0+row*step*.8660254f,step*.5f);
    if(type<0)return new Read(null,"Анимация, бонус или неразличимый шар в ряду "+(row+1));
    cells[row*cols+col]=type;if(type>0){occupied++;count++;}
   }
   emptyRows=occupied==0?emptyRows+1:0;
   // A ceiling-attached board cannot cross two fully empty rows. Background
   // scenery below the board is not silently promoted to more game objects.
   if(emptyRows>=2)break;
  }
  if(count<2&&previous==null)return new Read(null,"Недостаточно шаров для определения поля");
  BubbleBoard board;
  try{board=new BubbleBoard(rw,rh,cols,rows,x0*scale,y0*scale,step*scale,lx*scale,ly*scale,loaded,cells,shortParity);}
  catch(IllegalArgumentException invalid){return new Read(null,"Геометрия шаров выходит за область");}
  if(!board.ceilingConnected())return new Read(null,"Ожидаю завершения падения шаров");
  return new Read(board,"Обычные шары: "+count+" · цвет пушки "+loaded);
  }finally{work.pixels=null;}
 }
 private static int color(int c,boolean strong){
  int r=c>>16&255,g=c>>8&255,b=c&255,hi=Math.max(r,Math.max(g,b)),lo=Math.min(r,Math.min(g,b)),d=hi-lo;
  if(hi<(strong?120:85)||d<(strong?85:38)||d/(float)hi<(strong?.52f:.25f))return 0;
  float hue=hi==r?60f*(g-b)/d:hi==g?120+60f*(b-r)/d:240+60f*(r-g)/d;if(hue<0)hue+=360;
  if(hue<14||hue>342)return 1;if(hue<38)return 2;if(hue<72)return 3;if(hue>=75&&hue<155)return 4;
  if(hue>220&&hue<335&&d/(float)hi>.5f)return 5;return 0;
 }
 private static int circle(Scratch s,int w,int h,float cx,float cy,float radius){
  if(cx-radius<0||cx+radius>=w||cy-radius<0||cy+radius>=h)return -1;
  int a=0,b=0,c=0,d=0,total=0,unknown=0;
  for(int yy=-2;yy<=2;yy++)for(int xx=-2;xx<=2;xx++)if(xx*xx+yy*yy<=5){
   int x=Math.round(cx+xx*radius*.33f),y=Math.round(cy+yy*radius*.33f),i=y*w+x;s.sample(i);int type=s.color[i];
   if(type==1)a++;else if(type==2)b++;else if(type==3)c++;else if(type==4)d++;else if(type==5)unknown++;total++;
  }
  int known=a+b+c+d,best=Math.max(Math.max(a,b),Math.max(c,d));
  if(unknown>2)return -1;
  int centerAt=Math.round(cy)*w+Math.round(cx);s.sample(centerAt);int center=s.rgb[centerAt];
  int cr=center>>16&255,cg=center>>8&255,cb=center&255;
  // The observed ordinary candy centers are chromatic. A dark bomb or a
  // white special symbol cannot inherit the surrounding ring's normal color.
  if(Math.max(cr,Math.max(cg,cb))<75||Math.min(cr,Math.min(cg,cb))>235)return -1;
  if(best>=total*.57f&&known-best<=total*.18f)return best==a?1:best==b?2:best==c?3:4;
  // Broad cloud gradients can span a larger RGB range than a candy. Only
  // recognized foreground colors or a compact radial boundary establish an
  // object here; RGB range alone must not fabricate an unknown bubble.
  if(known>total*.12f)return -1;
  // Unknown compact, saturated objects must not be treated as clear sky.
  int contrast=0,samples=0;
  for(int i=0;i<8;i++){
   double angle=i*Math.PI/4;int x=Math.max(0,Math.min(w-1,Math.round(cx+(float)Math.cos(angle)*radius*1.18f)));
   int y=Math.max(0,Math.min(h-1,Math.round(cy+(float)Math.sin(angle)*radius*1.18f))),at=y*w+x;
   s.sample(at);
   if(s.color[at]>0)continue;samples++;int rgb=s.rgb[at];
   int dr=(center>>16&255)-(rgb>>16&255),dg=(center>>8&255)-(rgb>>8&255),db=(center&255)-(rgb&255);
   if(dr*dr+dg*dg+db*db>75*75)contrast++;
  }
  return samples>=4&&contrast>=samples*.75f?-1:0;
 }
 private static List<Blob> blobs(Scratch s,int w,int h){
  Arrays.fill(s.seen,0,w*h,false);List<Blob> out=new ArrayList<>();
  for(int start=0;start<w*h;start++){
   int color=s.strong[start];if(color==0||s.seen[start])continue;
   int head=0,tail=1,l=start%w,r=l,t=start/w,b=t;s.queue[0]=start;s.seen[start]=true;
   while(head<tail){int at=s.queue[head++],x=at%w,y=at/w;l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);
    for(int direction=0;direction<4;direction++){int nx=x+(direction==0?-1:direction==1?1:0),ny=y+(direction==2?-1:direction==3?1:0);
     if(nx<0||nx>=w||ny<0||ny>=h)continue;int next=ny*w+nx;
     if(!s.seen[next]&&s.strong[next]==color){s.seen[next]=true;s.queue[tail++]=next;}}
   }
   if(tail>=12){out.add(new Blob(l,t,r,b,tail,color));if(out.size()>512)return null;}
  }
  return out;
 }
}
