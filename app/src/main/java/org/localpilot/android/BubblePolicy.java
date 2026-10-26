package org.localpilot.android;

/** Bounded direct-shot geometry. No bounce, hidden color, booster or refill is guessed. */
public final class BubblePolicy {
 public static final class Shot {
  public final float aimX,aimY;public final int landing,removed,dropped;
  private final BubbleBoard source;
  private final int[] expected;
  private Shot(BubbleBoard source,float x,float y,int landing,int removed,int dropped,int[] expected){this.source=source;aimX=x;aimY=y;this.landing=landing;this.removed=removed;this.dropped=dropped;this.expected=expected;}
  boolean belongsTo(BubbleBoard board){return source.same(board);}
  public int[] expectedCells(){return expected.clone();}
 }
 private BubblePolicy(){}
 public static Shot choose(BubbleBoard b){
  if(b==null||b.count()<2||!b.ceilingConnected())return null;
  for(int i=0;i<b.size();i++)if(b.color(i)>0){float x=b.launcherX-b.x(i),y=b.launcherY-b.y(i);
   if(x*x+y*y<=b.step*b.step)return null;}
  Shot best=null;float bestScore=-Float.MAX_VALUE;
  for(int halfDegree=-130;halfDegree<=130;halfDegree++){
   double angle=Math.toRadians(halfDegree*.5);float dx=(float)Math.sin(angle),dy=-(float)Math.cos(angle);
   float wall=dx==0?Float.POSITIVE_INFINITY:(dx<0?b.x0-b.launcherX:b.x0+(b.cols-1)*b.step-b.launcherX)/dx;
   float travel=(b.y0-b.launcherY)/dy;int hit=-1;
   for(int i=0;i<b.size();i++)if(b.color(i)>0){
    float ox=b.launcherX-b.x(i),oy=b.launcherY-b.y(i),projection=ox*dx+oy*dy;
    float disc=projection*projection-(ox*ox+oy*oy-b.step*b.step);
    if(disc<0)continue;float contact=-projection-(float)Math.sqrt(disc);
    if(contact>0&&contact<travel){travel=contact;hit=i;}
   }
   if(travel<=b.radius||travel>=wall-b.radius*.12f)continue;
   float x=b.launcherX+dx*travel,y=b.launcherY+dy*travel;
   int landing=-1;float first=Float.POSITIVE_INFINITY,second=Float.POSITIVE_INFINITY;
   for(int i=0;i<b.size();i++)if(b.slot(i)&&b.color(i)==0&&(hit<0?i<b.cols:b.neighbor(i,hit))){
    float distance=(float)Math.hypot(x-b.x(i),y-b.y(i));
    if(distance<first){second=first;first=distance;landing=i;}else second=Math.min(second,distance);
   }
   if(landing<0||first>b.radius*.90f||second-first<b.radius*.28f||b.y(landing)>b.launcherY-b.step*2)continue;
   int[] cells=b.cells();cells[landing]=b.loadedColor;
   boolean[] marked=new boolean[b.size()];int[] queue=new int[b.size()];int head=0,tail=1;queue[0]=landing;marked[landing]=true;
   while(head<tail){int at=queue[head++];for(int n=Math.max(0,at-b.cols-1);n<Math.min(b.size(),at+b.cols+2);n++)
    if(!marked[n]&&cells[n]==b.loadedColor&&b.neighbor(at,n)){marked[n]=true;queue[tail++]=n;}}
   int removed=0,dropped=0;
   if(tail>=3){
    removed=tail;for(int i=0;i<tail;i++)cells[queue[i]]=0;
    java.util.Arrays.fill(marked,false);head=tail=0;
    for(int i=0;i<b.cols;i++)if(cells[i]>0){marked[i]=true;queue[tail++]=i;}
    while(head<tail){int at=queue[head++];for(int n=Math.max(0,at-b.cols-1);n<Math.min(b.size(),at+b.cols+2);n++)
     if(!marked[n]&&cells[n]>0&&b.neighbor(at,n)){marked[n]=true;queue[tail++]=n;}}
    for(int i=0;i<cells.length;i++)if(cells[i]>0&&!marked[i]){cells[i]=0;dropped++;}
   }
   float score=removed*100+dropped*120+(removed==0?tail*8:0)-(landing/b.cols)*.2f-first/b.radius;
   if(score>bestScore){bestScore=score;best=new Shot(b,x,y,landing,removed,dropped,cells);}
  }
  return best;
 }
}
