package org.localpilot.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bounded reader for the four ordinary candy colors and staggered rows in Shipuchka. */
public final class BubbleVision {
 public interface Pixels {int get(int x,int y);}
 public record Read(BubbleBoard board,String reason){public boolean usable(){return board!=null;}}
 private static final class Scratch implements BubbleObjectVision.Pixels {
  final int[] rgb=new int[240*480],queue=new int[240*480],stamp=new int[240*480];
  final byte[] strong=new byte[240*480],color=new byte[240*480];
  final byte[] launcherDilated=new byte[240*480],launcherClosed=new byte[240*480];
  final boolean[] seen=new boolean[240*480];
  Pixels pixels;int sampleWidth,left,top,scale,generation;
  BubbleObjectVision.Kind lastObjectKind=BubbleObjectVision.Kind.UNKNOWN;
  void begin(Pixels pixels,int sampleWidth,int left,int top,int scale){
   this.pixels=pixels;this.sampleWidth=sampleWidth;this.left=left;this.top=top;this.scale=scale;
   if(generation==Integer.MAX_VALUE){Arrays.fill(stamp,0);generation=1;}else generation++;
  }
  void sample(int at){if(stamp[at]==generation)return;int c=pixels.get(left+(at%sampleWidth)*scale,top+(at/sampleWidth)*scale);
   rgb[at]=c;color[at]=(byte)color(c,false);strong[at]=(byte)color(c,true);stamp[at]=generation;}
  @Override public int get(int x,int y){int at=y*sampleWidth+x;sample(at);return rgb[at];}
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
   loaded=circle(work,w,h,lx,ly,step*.5f,true);
   if(loaded<=0)return new Read(null,"Ожидаю обычный шар в пушке");
  }else{
   List<Blob> blobs=blobs(work,w,h);if(blobs==null)return new Read(null,"Слишком много цветовых объектов");
   Blob launcher=null;float launcherRadius=0,score=Float.POSITIVE_INFINITY,second=Float.POSITIVE_INFINITY;
   // This skin's ordinary loaded ball is centered. The reserve sits only
   // ~15% of the arena width to its left and must never replace a missing gun.
   for(int pass=0;pass<2&&launcher==null;pass++){
    List<Blob> candidates=pass==0?blobs:closedLauncherBlobs(work,w,h);
    if(candidates==null)return new Read(null,"Слишком много объектов в области пушки");
    for(Blob b:candidates)if(b.color<=4&&b.round()&&b.width()>w*.045f&&b.width()<w*.15f&&b.x()>w*.42f&&b.x()<w*.58f&&b.y()>h*.68f&&b.y()<h*.98f){
     float radius=launcherObjectRadius(work,w,h,b);if(radius<=0)continue;
     float s=Math.abs(b.x()-w*.5f)/w+(h-b.y())/h*.2f;
     if(s<score){second=score;score=s;launcher=b;launcherRadius=radius;}else second=Math.min(second,s);
    }
   }
   if(launcher==null||second-score<.035f)return new Read(null,"Не найдена отдельная пушка: включите её в область");
   lx=launcher.x();ly=launcher.y();float diameter=launcherRadius*2;
   loaded=circle(work,w,h,lx,ly,diameter*.5f,true);
   if(loaded<=0)return new Read(null,"Бонус/неизвестный шар в пушке");
   List<Blob> anchors=new ArrayList<>();
   for(Blob b:blobs)if(b.color<=4&&b.round()&&b.y()<ly-diameter*3&&b.width()>diameter*.80f&&b.width()<diameter*1.18f
     &&b.height()>diameter*.80f&&b.height()<diameter*1.18f
     &&BubbleObjectVision.inspect(work,w,h,b.x(),b.y(),(b.width()+b.height())*.25f).kind()==BubbleObjectVision.Kind.BALL)anchors.add(b);
   if(anchors.size()<3)return new Read(null,"Нужны хотя бы три различимых круглых шара в рядах");
   int first=h;for(Blob b:anchors)first=Math.min(first,b.t);
   float best=Float.POSITIVE_INFINITY;float[] fit=null;
   for(int parity=0;parity<2;parity++)for(float d=diameter*.86f;d<=diameter*1.12f;d+=.25f){
    for(float oy=first+d*.30f;oy<first+d*.68f;oy+=.5f)for(float ox=d*.40f;ox<d*.90f;ox+=.5f){
     float error=0;int minRow=100,maxRow=-1,matched=0,rowPhases=0;
     for(Blob b:anchors){
      int row=Math.round((b.y()-oy)/(d*.8660254f));if(row<0||row>23)continue;
      int col=Math.round((b.x()-ox)/d-(row%2==parity?.5f:0f));if(col<0||col>15)continue;
      float dx=b.x()-(ox+(col+(row%2==parity?.5f:0f))*d),dy=b.y()-(oy+row*d*.8660254f),e=dx*dx+dy*dy;
      if(e>d*d*.045f){error+=d*d;continue;}error+=e;matched++;rowPhases|=1<<(row%2);minRow=Math.min(minRow,row);maxRow=Math.max(maxRow,row);
     }
     if(matched<3||matched<anchors.size()||maxRow<=minRow||rowPhases!=3)continue;
     error/=matched*d*d;
     if(error<best){best=error;fit=new float[]{ox,oy,d,parity};}
    }
   }
   if(fit==null||best>.006f)return new Read(null,"Круглые объекты не образуют устойчивые ряды");
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
    if(type<0)return new Read(null,"Пушка: "+ordinaryColorName(loaded)+" · "+(work.lastObjectKind==BubbleObjectVision.Kind.BALL?"круглый объект без класса":"неопределённый объект")+" R"+(row+1)+"C"+(col+1));
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
 private static String ordinaryColorName(int color){
  return switch(color){case 1->"красный";case 2->"оранжевый";case 3->"жёлтый";case 4->"зелёный";default->"неизвестный";};
 }
 private static int circle(Scratch s,int w,int h,float cx,float cy,float radius){
  return circle(s,w,h,cx,cy,radius,false);
 }
 /** A colour component only proposes a location. The isolated loaded ball
  * needs a mostly visible circular boundary in the original pixels. Allow a
  * small radius correction because highlights truncate the proposal's box. */
 private static float launcherObjectRadius(Scratch s,int w,int h,Blob b){
  float seed=(b.width()+b.height())*.25f;
  for(int i=0;i<3;i++){
   float radius=seed*(1+i*.08f);
   BubbleObjectVision.Probe object=BubbleObjectVision.inspect(s,w,h,b.x(),b.y(),radius);
   if(object.kind()==BubbleObjectVision.Kind.BALL&&object.confidence()>=.65f)return radius;
  }
  return -1;
 }
 private static int circle(Scratch s,int w,int h,float cx,float cy,float radius,boolean launcher){
  s.lastObjectKind=BubbleObjectVision.Kind.UNKNOWN;
  if(radius<3||cx-radius<0||cx+radius>=w||cy-radius<0||cy+radius>=h)return -1;
  // Locate an object before asking which ordinary candy colour it has.
  // Candidate lattice positions alone are not evidence that a ball exists.
  BubbleObjectVision.Probe object=BubbleObjectVision.inspect(s,w,h,cx,cy,radius);
  s.lastObjectKind=object.kind();
  if(object.kind()==BubbleObjectVision.Kind.UNKNOWN)return -1;
  if(launcher&&object.kind()!=BubbleObjectVision.Kind.BALL)return -1;
  if(!launcher){
   if(object.kind()==BubbleObjectVision.Kind.EMPTY){
    int center=s.get(Math.round(cx),Math.round(cy)),r=center>>16&255,g=center>>8&255,b=center&255;
    // A neutral opaque card/blackout is not evidence of a cleared game board.
    return Math.max(r,Math.max(g,b))<75||Math.min(r,Math.min(g,b))>235?-1:0;
   }
  }
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
  // The gun's independently acquired round contour can contain pale spiral
  // highlights. Keep stronger rejection of competing foreground colours there.
  if(best>=total*(launcher?.35f:.57f)&&known-best<=total*(launcher?.09f:.18f))return best==a?1:best==b?2:best==c?3:4;
  // A geometrically present ball without an ordinary class is occupied but
  // unknown. Neither a new skin nor a faded symbol can become empty space.
  return -1;
 }
 private static List<Blob> blobs(Scratch s,int w,int h){
  return blobs(s,w,h,s.strong);
 }
 /** Join one-sample highlights only in the bottom-centre launcher search area.
  * Each colour closes independently. Shape candidates never replace original
  * pixel probes: white/dark centres, mixed colours and missing guns still fail. */
 private static List<Blob> closedLauncherBlobs(Scratch s,int w,int h){
  Arrays.fill(s.launcherDilated,0,w*h,(byte)0);Arrays.fill(s.launcherClosed,0,w*h,(byte)0);
  int left=Math.max(2,(int)(w*.34f)),right=Math.min(w-2,(int)(w*.66f)+1);
  int top=Math.max(2,(int)(h*.64f)),bottom=Math.min(h-2,(int)(h*.99f)+1);
  for(int y=top-1;y<=bottom;y++)for(int x=left-1;x<=right;x++){
   int bits=0;
   for(int yy=y-1;yy<=y+1;yy++)for(int xx=x-1;xx<=x+1;xx++){
    int c=s.strong[yy*w+xx];if(c>=1&&c<=4)bits|=1<<(c-1);
   }
   s.launcherDilated[y*w+x]=(byte)bits;
  }
  for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){
   int bits=15;
   for(int yy=y-1;yy<=y+1;yy++)for(int xx=x-1;xx<=x+1;xx++)bits&=s.launcherDilated[yy*w+xx];
   // Overlapping colour closures are ambiguous, not a new ordinary candy.
   if(Integer.bitCount(bits)==1)s.launcherClosed[y*w+x]=(byte)(Integer.numberOfTrailingZeros(bits)+1);
  }
  return blobs(s,w,h,s.launcherClosed);
 }
 private static List<Blob> blobs(Scratch s,int w,int h,byte[] colors){
  Arrays.fill(s.seen,0,w*h,false);List<Blob> out=new ArrayList<>();
  for(int start=0;start<w*h;start++){
   int color=colors[start];if(color==0||s.seen[start])continue;
   int head=0,tail=1,l=start%w,r=l,t=start/w,b=t;s.queue[0]=start;s.seen[start]=true;
   while(head<tail){int at=s.queue[head++],x=at%w,y=at/w;l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);
    for(int direction=0;direction<4;direction++){int nx=x+(direction==0?-1:direction==1?1:0),ny=y+(direction==2?-1:direction==3?1:0);
     if(nx<0||nx>=w||ny<0||ny>=h)continue;int next=ny*w+nx;
     if(!s.seen[next]&&colors[next]==color){s.seen[next]=true;s.queue[tail++]=next;}}
   }
   if(tail>=12){out.add(new Blob(l,t,r,b,tail,color));if(out.size()>512)return null;}
  }
  return out;
 }
}
