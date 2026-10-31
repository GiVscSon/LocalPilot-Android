package org.localpilot.android;

/** Bounded normal-bubble geometry with at most one conservatively inferred side-wall reflection. */
public final class BubblePolicy {
 public static final class Shot {
  /** Gesture endpoint follows the initial ray. For a reflection it ends at the first wall. */
  public final float aimX,aimY,impactX,impactY,bounceX,bounceY;
  public final int landing,removed,dropped,bounces;
  private final BubbleBoard source;
  private final int[] expected;
  private Shot(BubbleBoard source,float aimX,float aimY,float impactX,float impactY,
    float bounceX,float bounceY,int bounces,int landing,int removed,int dropped,int[] expected){
   this.source=source;this.aimX=aimX;this.aimY=aimY;this.impactX=impactX;this.impactY=impactY;
   this.bounceX=bounceX;this.bounceY=bounceY;this.bounces=bounces;
   this.landing=landing;this.removed=removed;this.dropped=dropped;this.expected=expected;
  }
  boolean belongsTo(BubbleBoard board){return source.same(board);}
  public int[] expectedCells(){return expected.clone();}
 }
 private static final class Trace {
  float travel,root,second;int hit;
 }
 private BubblePolicy(){}
 public static Shot choose(BubbleBoard b){return choose(b,true,null);}
 /** Learning may rank only trajectories already accepted by geometry and color gates. */
 public static Shot choose(BubbleBoard b,java.util.function.ToDoubleFunction<Shot> bonus){return choose(b,true,bonus);}
 /** Explicit direct-only plan, also used when a caller cannot validate a complete side-wall area. */
 public static Shot chooseDirect(BubbleBoard b){return choose(b,false,null);}
 private static Shot choose(BubbleBoard b,boolean allowBounce,java.util.function.ToDoubleFunction<Shot> bonus){
  if(b==null||b.count()==0||!b.ceilingConnected())return null;
  // With one remaining candy, only build a pair of its own color. Geometry
  // must already have been acquired; an unrelated loaded color is ambiguous.
  if(b.count()==1){int remaining=0;for(int i=0;i<b.size();i++)if(b.color(i)>0)remaining=b.color(i);
   if(remaining!=b.loadedColor)return null;}
  for(int i=0;i<b.size();i++)if(b.color(i)>0){float x=b.launcherX-b.x(i),y=b.launcherY-b.y(i);
   if(x*x+y*y<=b.step*b.step)return null;}
  float left=b.x0,right=b.x0+(b.cols-1)*b.step;
  if(b.launcherX<=left+b.radius*.12f||b.launcherX>=right-b.radius*.12f)return null;
  // The occupied lattice determines physical wall faces one projectile radius
  // beyond its extreme centers. A cropped/off-center ROI cannot prove those faces.
  float leftMargin=left-b.radius,rightMargin=b.width-(right+b.radius);
  boolean walls=allowBounce&&leftMargin>=0&&rightMargin>=0
    &&leftMargin<=b.radius*.6f&&rightMargin<=b.radius*.6f
    &&Math.abs(leftMargin-rightMargin)<=b.radius*.35f
    &&b.launcherX>left+b.radius*.12f&&b.launcherX<right-b.radius*.12f;
  Shot best=null;float bestScore=-Float.MAX_VALUE;Trace firstTrace=new Trace(),secondTrace=new Trace();
  for(int halfDegree=-160;halfDegree<=160;halfDegree++){
   double angle=Math.toRadians(halfDegree*.5);float dx=(float)Math.sin(angle),dy=-(float)Math.cos(angle);
   float wall=dx==0?Float.POSITIVE_INFINITY:(dx<0?left-b.launcherX:right-b.launcherX)/dx;
   if(wall<=0)continue;
   trace(b,b.launcherX,b.launcherY,dx,dy,firstTrace);
   float x,y,aimX,aimY,bounceX=Float.NaN,bounceY=Float.NaN;int bounces,hit;Trace finalTrace;
   if(firstTrace.travel<wall-b.radius*.12f){
    if(Math.abs(halfDegree)>130)continue; // Preserve the established direct aiming envelope.
    x=b.launcherX+dx*firstTrace.travel;y=b.launcherY+dy*firstTrace.travel;
    aimX=x;aimY=y;bounces=0;hit=firstTrace.hit;finalTrace=firstTrace;
   }else{
    if(!walls||!Float.isFinite(wall)||wall<b.radius*.5f
      ||firstTrace.travel<wall+b.radius*.18f)continue;
    bounceX=dx<0?left:right;bounceY=b.launcherY+dy*wall;
    // No reflection beside the ceiling or below the launcher's own hitbox.
    if(bounceY<=b.y0+b.radius*.35f||bounceY>=b.launcherY-b.radius*.5f)continue;
    trace(b,bounceX,bounceY,-dx,dy,secondTrace);
    float nextWall=(right-left)/Math.abs(dx);
    if(secondTrace.travel<b.radius*.5f||secondTrace.travel>=nextWall-b.radius*.18f)continue;
    x=bounceX-dx*secondTrace.travel;y=bounceY+dy*secondTrace.travel;
    aimX=bounceX;aimY=bounceY;bounces=1;hit=secondTrace.hit;finalTrace=secondTrace;
    // The first segment may end before its computed collision, so validate its
    // clearance explicitly rather than using the first hit's eventual incidence.
    if(!clearSegment(b,b.launcherX,b.launcherY,dx,dy,wall,-1))continue;
   }
   if(finalTrace.travel<=b.radius||finalTrace.second-finalTrace.travel<b.radius*.06f
     ||hit>=0&&finalTrace.root<b.radius*.18f)continue;
   float originX=bounces==0?b.launcherX:bounceX,originY=bounces==0?b.launcherY:bounceY;
   if(!clearSegment(b,originX,originY,bounces==0?dx:-dx,dy,finalTrace.travel,hit))continue;
   int landing=-1;float nearest=Float.POSITIVE_INFINITY,second=Float.POSITIVE_INFINITY;
   for(int i=0;i<b.size();i++)if(b.slot(i)&&b.color(i)==0&&(hit<0?i<b.cols:b.neighbor(i,hit))){
    float distance=(float)Math.hypot(x-b.x(i),y-b.y(i));
    if(distance<nearest){second=nearest;nearest=distance;landing=i;}else second=Math.min(second,distance);
   }
   if(landing<0||nearest>b.radius*.90f||second-nearest<b.radius*.28f||b.y(landing)>b.launcherY-b.step*2)continue;
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
   // Prefer a direct route when its normal-bubble result is otherwise equal.
   float score=removed*100+dropped*120+(removed==0?tail*8:0)-(landing/b.cols)*.2f-nearest/b.radius-bounces*3;
   Shot candidate=new Shot(b,aimX,aimY,x,y,bounceX,bounceY,bounces,landing,removed,dropped,cells);
   if(bonus!=null){double learned=bonus.applyAsDouble(candidate);if(!Double.isFinite(learned)||Math.abs(learned)>6.000001)throw new IllegalArgumentException("bubble learning bonus outside safety bound");score+=(float)learned;}
   if(score>bestScore){bestScore=score;best=candidate;}
  }
  return best;
 }
 private static void trace(BubbleBoard b,float x,float y,float dx,float dy,Trace result){
  result.travel=(b.y0-y)/dy;result.hit=-1;result.root=Float.POSITIVE_INFINITY;result.second=Float.POSITIVE_INFINITY;
  for(int i=0;i<b.size();i++)if(b.color(i)>0){
   float ox=x-b.x(i),oy=y-b.y(i),projection=ox*dx+oy*dy;
   float discriminant=projection*projection-(ox*ox+oy*oy-b.step*b.step);
   if(discriminant<0)continue;
   float root=(float)Math.sqrt(discriminant),contact=-projection-root;
   if(contact>0){
    if(contact<result.travel){result.second=result.travel;result.travel=contact;result.hit=i;result.root=root;}
    else result.second=Math.min(result.second,contact);
   }
  }
 }
 private static boolean clearSegment(BubbleBoard b,float x,float y,float dx,float dy,float distance,int hit){
  float clearance=b.step+b.radius*.04f;
  for(int i=0;i<b.size();i++)if(i!=hit&&b.color(i)>0){
   float ox=b.x(i)-x,oy=b.y(i)-y;
   float at=Math.max(0,Math.min(distance,ox*dx+oy*dy));
   float ex=ox-dx*at,ey=oy-dy*at;
   if(ex*ex+ey*ey<clearance*clearance)return false;
  }
  return true;
 }
}
