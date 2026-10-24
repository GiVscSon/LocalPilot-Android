package org.localpilot.android;
import java.util.*;

/** Bounded colour segmentation for calibrated, visually distinct 2-D objects. */
public final class CustomArcadeVision {
 public interface Pixels {int get(int x,int y);}
 private static final class Scratch {
  final int[] rgb=new int[160*240],queue=new int[160*240];
  final boolean[] excluded=new boolean[160*240],seen=new boolean[160*240];
 }
 private static final ThreadLocal<Scratch> WORK=ThreadLocal.withInitial(Scratch::new);
 private CustomArcadeVision(){}
 public static ArcadeObservation detect(ArcadeProfile profile,Pixels pixels,int left,int top,int right,int bottom,
    int el,int et,int er,int eb,long time,ArcadeObservation previous,CustomVisionConfig config){
  if(profile==null||profile==ArcadeProfile.ARI||pixels==null||config==null||left<0||top<0||right-left<40||bottom-top<40||time<0)throw new IllegalArgumentException("custom vision context");
  int w=Math.min(160,right-left),h=Math.min(240,bottom-top),n=w*h;Scratch work=WORK.get();int[] rgb=work.rgb;boolean[] excluded=work.excluded;
  for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++){int x=left+xx*(right-left)/w,y=top+yy*(bottom-top)/h,i=yy*w+xx;excluded[i]=x>=el&&x<er&&y>=et&&y<eb;rgb[i]=pixels.get(x,y);}
  // Old scenes and other mechanics cannot attract the tracker to unrelated colours.
  boolean freshPrevious=previous!=null&&previous.profile==profile&&previous.usable()
    &&time>previous.timestampMs&&time-previous.timestampMs<=500;
  ArcadeObservation.Box oldPlayer=freshPrevious?previous.player:null;
  ArcadeObservation.Box oldControl=freshPrevious?previous.control:null;
  List<ArcadeObservation.Box> own=blobs(rgb,excluded,w,h,config.colors[0],work);
  ArcadeObservation.Box player=choose(own,oldPlayer,config.x[0],config.y[0],false);
  List<ArcadeObservation.Box> second=blobs(rgb,excluded,w,h,config.colors[1],work);
  List<ArcadeObservation.Box> third=blobs(rgb,excluded,w,h,config.colors[2],work);
  ArcadeObservation.Box control=profile==ArcadeProfile.PADDLE?choose(second,oldControl,config.x[1],config.y[1],true):player;
  List<ArcadeObservation.Box> obstacles=new ArrayList<>();
  if(profile!=ArcadeProfile.PADDLE){for(var b:second)add(obstacles,b,player);for(var b:third)add(obstacles,b,player);}
  float gapTop=0,gapBottom=1;
  if(profile==ArcadeProfile.FLAPPY&&player!=null){
   float nearest=Float.POSITIVE_INFINITY;
   for(var b:obstacles)if(b.right>player.left)nearest=Math.min(nearest,b.left);
   List<ArcadeObservation.Box> column=new ArrayList<>();
   for(var b:obstacles)if(b.left<=nearest+.04f&&b.right>=nearest)column.add(b);
   column.sort(Comparator.comparingDouble(b->b.top));float cursor=0,best=Float.POSITIVE_INFINITY;gapTop=gapBottom=0;
   for(int i=0;i<=column.size();i++){
    float end=i<column.size()?column.get(i).top:1;
    if(end-cursor>player.height()*1.5f){float distance=Math.max(0,Math.max(cursor+player.height()*.7f-player.y(),player.y()-end+player.height()*.7f));if(distance<best){best=distance;gapTop=cursor;gapBottom=end;}}
    if(i<column.size())cursor=Math.max(cursor,column.get(i).bottom);
   }
  }
  float vx=0,vy=0;boolean tracked=false;
  if(freshPrevious&&player!=null){float dt=(time-previous.timestampMs)/1000f;if(dt>=.03f&&dt<=.5f&&Math.abs(player.x()-previous.player.x())<.3f&&Math.abs(player.y()-previous.player.y())<.4f){vx=(player.x()-previous.player.x())/dt;vy=(player.y()-previous.player.y())/dt;tracked=true;}}
  boolean usable=player!=null&&control!=null&&(profile!=ArcadeProfile.FLAPPY||gapBottom-gapTop>player.height()*1.5f);
  return new ArcadeObservation(profile,time,player,control,obstacles,0,0,1,1,gapTop,gapBottom,vx,vy,usable?.85f:0,tracked,false);
 }
 private static boolean matches(int a,int b){int dr=(a>>16&255)-(b>>16&255),dg=(a>>8&255)-(b>>8&255),db=(a&255)-(b&255);return dr*dr+dg*dg+db*db<=48*48;}
 private static List<ArcadeObservation.Box> blobs(int[] rgb,boolean[] excluded,int w,int h,int color,Scratch work){
  int n=w*h;boolean[] seen=work.seen;Arrays.fill(seen,0,n,false);int[] q=work.queue;List<ArcadeObservation.Box> out=new ArrayList<>();
  for(int start=0;start<n;start++){
   if(seen[start]||excluded[start]||!matches(rgb[start],color))continue;
   int head=0,tail=1,l=start%w,r=l,t=start/w,b=t;q[0]=start;seen[start]=true;
   while(head<tail){int at=q[head++],x=at%w,y=at/w;l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);
    for(int dir=0;dir<4;dir++){int nx=x+(dir==0?-1:dir==1?1:0),ny=y+(dir==2?-1:dir==3?1:0);if(nx<0||nx>=w||ny<0||ny>=h)continue;int i=ny*w+nx;if(!seen[i]&&!excluded[i]&&matches(rgb[i],color)){seen[i]=true;q[tail++]=i;}}
   }
   if(tail>=4&&(r-l+1)*(b-t+1)<=n*.65f)out.add(new ArcadeObservation.Box(l/(float)w,t/(float)h,(r+1)/(float)w,(b+1)/(float)h));
   if(out.size()>128)return List.of();
  }return out;
 }
 private static ArcadeObservation.Box choose(List<ArcadeObservation.Box> boxes,ArcadeObservation.Box previous,float ax,float ay,boolean paddle){
  ArcadeObservation.Box best=null;float score=Float.POSITIVE_INFINITY,runner=Float.POSITIVE_INFINITY;
  for(var b:boxes){if(b.width()<(paddle?.06f:.01f)||b.width()>(paddle?.85f:.28f)||b.height()<.008f||b.height()>(paddle?.25f:.25f))continue;
   float dx=b.x()-(previous==null?ax:previous.x()),dy=b.y()-(previous==null?ay:previous.y()),d=dx*dx+dy*dy;
   // A sole same-colour blob elsewhere is not evidence that the player teleported.
   // Initial acquisition stays near the user's sample; continuation allows bounded motion.
   if(Math.abs(dx)>(previous==null?.25f:.30f)||Math.abs(dy)>(previous==null?.30f:.40f))continue;
   if(previous!=null&&(b.width()<previous.width()*.5f||b.width()>previous.width()*2
     ||b.height()<previous.height()*.5f||b.height()>previous.height()*2))continue;
   if(d<score){runner=score;score=d;best=b;}else runner=Math.min(runner,d);
  }
  // Quantized pixels must not break ties between two equally plausible objects.
  return best!=null&&runner-score>=.005f?best:null;
 }
 private static void add(List<ArcadeObservation.Box> boxes,ArcadeObservation.Box b,ArcadeObservation.Box player){
  if(player!=null&&overlap(b,player)>.25f)return;
  for(var existing:boxes)if(overlap(b,existing)>.7f)return;
  boxes.add(b);
 }
 private static float overlap(ArcadeObservation.Box a,ArcadeObservation.Box b){float area=Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left))*Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));return area/Math.max(.000001f,Math.min(a.width()*a.height(),b.width()*b.height()));}
}
