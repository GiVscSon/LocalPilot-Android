package org.localpilot.android;

/** Colour-agnostic object geometry at a proposed bubble position.
 * Hue labels and game actions deliberately do not enter this detector. */
public final class BubbleObjectVision {
 public record Circle(float x,float y,float radius,float support){}
 /** Bounded geometric proposals. RGB differences measure edges, never hue classes. */
 public static java.util.List<Circle> find(Pixels pixels,int width,int height,int left,int top,int right,int bottom,float minRadius,float maxRadius){
  return find(pixels,width,height,left,top,right,bottom,minRadius,maxRadius,false);
 }
 public static java.util.List<Circle> find(Pixels pixels,int width,int height,int left,int top,int right,int bottom,float minRadius,float maxRadius,boolean isolated){
  if(pixels==null||width<1||height<1||width>240||height>480||!Float.isFinite(minRadius)||!Float.isFinite(maxRadius)||left<0||top<0||right>width||bottom>height||left>=right||top>=bottom||minRadius<3||maxRadius<minRadius||maxRadius>width*.2f)throw new IllegalArgumentException("search bounds");
  var proposals=new java.util.ArrayList<Circle>();
  for(float radius=minRadius;radius<=maxRadius;radius+=1)for(int y=top;y<bottom;y+=2)for(int x=left;x<right;x+=2){
   Probe p=inspect(pixels,width,height,x,y,radius);
   if(p.kind==Kind.BALL&&p.confidence>=(isolated?.72f:.50f)){Circle fitted=fit(pixels,width,height,x,y,radius,isolated);if(fitted!=null)proposals.add(fitted);if(proposals.size()>8192)return java.util.List.of();}
  }
  proposals.sort(java.util.Comparator.comparingDouble((Circle c)->-c.support));
  var found=new java.util.ArrayList<Circle>();
  for(Circle c:proposals){
   boolean duplicate=false;for(Circle other:found)if(Math.hypot(c.x-other.x,c.y-other.y)<Math.max(c.radius,other.radius)*.9f){duplicate=true;break;}
   if(duplicate)continue;
   float x=0,y=0,r=0,weight=0;
   for(Circle near:proposals)if(Math.hypot(c.x-near.x,c.y-near.y)<=c.radius*.3f&&Math.abs(c.radius-near.radius)<=1){float w=near.support;weight+=w;x+=near.x*w;y+=near.y*w;r+=near.radius*w;}
   found.add(new Circle(x/weight,y/weight,r/weight,c.support));if(found.size()>512)return java.util.List.of();
  }
  return found;
 }
 private static Circle fit(Pixels pixels,int width,int height,float x,float y,float radius,boolean isolated){
  double[][] m=new double[3][4];int n=0;double[] xx=new double[18],yy=new double[18];
  for(int direction=0;direction<24;direction++){
   if(direction%4==0)continue;float dx=DX[direction],dy=DY[direction];int best=0;float atBest=0;int previous=0;boolean have=false;
   for(int step=0;step<=8;step++){
    float at=.65f+step*.1f;int px=Math.round(x+dx*radius*at),py=Math.round(y+dy*radius*at);
    if(px<0||px>=width||py<0||py>=height){have=false;break;}
    int current=pixels.get(px,py);if(have){int delta=distanceSquared(previous,current);if(isolated?delta>=32*32:delta>best){best=delta;atBest=at-.05f;}}previous=current;have=true;
   }
   if(!have||best<32*32)continue;
   double a=dx*radius*atBest,b=dy*radius*atBest,q=a*a+b*b;xx[n]=a;yy[n++]=b;
   double[] v={a,b,1};for(int i=0;i<3;i++){for(int j=0;j<3;j++)m[i][j]+=v[i]*v[j];m[i][3]+=v[i]*q;}
  }
  if(n<10)return null;
  for(int k=0;k<3;k++){int pivot=k;for(int i=k+1;i<3;i++)if(Math.abs(m[i][k])>Math.abs(m[pivot][k]))pivot=i;double[] swap=m[k];m[k]=m[pivot];m[pivot]=swap;if(Math.abs(m[k][k])<1e-6)return null;double divisor=m[k][k];for(int j=k;j<4;j++)m[k][j]/=divisor;for(int i=0;i<3;i++)if(i!=k){double factor=m[i][k];for(int j=k;j<4;j++)m[i][j]-=factor*m[k][j];}}
  double cx=m[0][3]/2,cy=m[1][3]/2,r=Math.sqrt(m[2][3]+cx*cx+cy*cy),error=0;
  if(!Double.isFinite(r)||r<radius*.8||r>radius*1.2||Math.hypot(cx,cy)>radius*.4)return null;
  for(int i=0;i<n;i++){double e=Math.hypot(xx[i]-cx,yy[i]-cy)-r;error+=e*e;}
  error=Math.sqrt(error/n)/r;if(error>.075)return null;
  return new Circle(x+(float)cx,y+(float)cy,(float)r,(float)(1-error));
 }
 public interface Pixels {int get(int x,int y);}
 public enum Kind { BALL, EMPTY, UNKNOWN }
 public record Probe(Kind kind,float confidence,int visibleArcs,int circularArcs){}
 private static final float[] RADII={.56f,.70f,.84f,.98f,1.12f,1.26f,1.40f};
 private static final float[] DX=new float[24],DY=new float[24];
 static{for(int i=0;i<24;i++){DX[i]=(float)Math.cos(i*Math.PI/12);DY[i]=(float)Math.sin(i*Math.PI/12);}}
 private BubbleObjectVision(){}

 public static Probe inspect(Pixels pixels,int width,int height,float cx,float cy,float radius){
  if(pixels==null||width<1||height<1||width>16_384||height>16_384||!Float.isFinite(cx)||!Float.isFinite(cy)||!Float.isFinite(radius)||radius<3)
   throw new IllegalArgumentException("object geometry");
  int supported=0,visible=0,sectors=0,visibleSectors=0;
  for(int direction=0;direction<24;direction++){
   // In the hexagonal lattice, neighbouring balls touch at multiples of
   // 60 degrees. Inspect the exposed arcs between those contact directions.
   if(direction%4==0)continue;
   float dx=DX[direction],dy=DY[direction];boolean in=true;
   for(float distance:RADII){int x=Math.round(cx+dx*radius*distance),y=Math.round(cy+dy*radius*distance);if(x<0||x>=width||y<0||y>=height){in=false;break;}}
   if(!in)continue;visible++;visibleSectors|=1<<(direction/3);
   int previous=pixels.get(Math.round(cx+dx*radius*RADII[0]),Math.round(cy+dy*radius*RADII[0]));
   int edge=0,outside=0;
   for(int at=1;at<RADII.length;at++){
    int current=pixels.get(Math.round(cx+dx*radius*RADII[at]),Math.round(cy+dy*radius*RADII[at]));
    int difference=distanceSquared(previous,current);previous=current;
    if(at>=2&&at<=4)edge=Math.max(edge,difference);
    else outside=Math.max(outside,at==1?difference/4:difference);
   }
   // A boundary must be concentrated near the object's circumference,
   // rather than merely belonging to a broad gradient or a distant neighbour.
   if(edge>=32*32&&edge>outside*1.5625f){supported++;sectors|=1<<(direction/3);}
  }
  if(visible<10)return new Probe(Kind.UNKNOWN,0,visible,supported);
  float confidence=supported/(float)visible;
  boolean distributed=Integer.bitCount(sectors)*4>=Integer.bitCount(visibleSectors)*3;
  Fill fill=filledDisk(pixels,width,height,cx,cy,radius);
  // Dense clusters hide parts of a perimeter. Six separated radial edges,
  // a filled interior and missing square corners jointly establish the shape.
  if(supported>=6&&Integer.bitCount(sectors)>=4&&fill.centered&&fill.disk&&!fill.square)
   return new Probe(Kind.BALL,confidence,visible,supported);
  // Non-circular compact obstacles remain unknown. An arc of an adjacent
  // ball or a smooth cloud is not itself a centred object in this slot.
  boolean otherBoundary=compactBoundary(pixels,width,height,cx,cy,radius);
  if(distributed&&supported*2>=visible||fill.centered&&(supported>=4||otherBoundary)
    ||otherBoundary&&filledDisk(pixels,width,height,cx,cy,radius*1.7f).centered)
   return new Probe(Kind.UNKNOWN,confidence,visible,supported);
  return new Probe(Kind.EMPTY,1-confidence,visible,supported);
 }

 private record Fill(boolean centered,boolean disk,boolean square){}
 private static Fill filledDisk(Pixels pixels,int width,int height,float cx,float cy,float radius){
  float r=0,g=0,b=0;int n=0;
  // In-between contacts are the visible gaps of the hexagonal arrangement.
  // This is geometry, not a learned sky colour or an ordinary-candy palette.
  for(int i=0;i<6;i++){
   int direction=2+i*4,x=Math.round(cx+DX[direction]*radius*1.18f),y=Math.round(cy+DY[direction]*radius*1.18f);
   if(x<0||x>=width||y<0||y>=height)continue;
   int c=pixels.get(x,y);n++;r+=c>>16&255;g+=c>>8&255;b+=c&255;
  }
  if(n<4)return new Fill(false,false,false);r/=n;g/=n;b/=n;
  float bodyR=0,bodyG=0,bodyB=0;
  for(int i=0;i<5;i++){
   int x=Math.round(cx+(i==1?-.30f:i==2?.30f:0)*radius),y=Math.round(cy+(i==3?-.30f:i==4?.30f:0)*radius);
   if(x<0||x>=width||y<0||y>=height)return new Fill(false,false,false);
   int c=pixels.get(x,y);bodyR+=c>>16&255;bodyG+=c>>8&255;bodyB+=c&255;
  }
  bodyR/=5;bodyG/=5;bodyB/=5;
  float dr=bodyR-r,dg=bodyG-g,db=bodyB-b,mean=dr*dr+dg*dg+db*db;int agreeing=0;
  for(int i=0;i<6;i++){
   int direction=2+i*4,x=Math.round(cx+DX[direction]*radius*1.18f),y=Math.round(cy+DY[direction]*radius*1.18f);
   if(x<0||x>=width||y<0||y>=height)continue;
   int c=pixels.get(x,y);float rr=bodyR-(c>>16&255),gg=bodyG-(c>>8&255),bb=bodyB-(c&255),length=rr*rr+gg*gg+bb*bb,dot=rr*dr+gg*dg+bb*db;
   if(length>=18*18&&dot>0&&dot*dot>=.36f*length*mean)agreeing++;
  }
  boolean centered=mean>=24*24&&agreeing*5>=n*4;
  int inside=0,corners=0;
  for(int i=0;i<8;i++){
   int x=Math.round(cx+DX[i*3]*radius*.66f),y=Math.round(cy+DY[i*3]*radius*.66f);
   if(x<0||x>=width||y<0||y>=height)continue;
   if(contrast(pixels.get(x,y),r,g,b)>=24*24)inside++;
  }
  for(int sy=-1;sy<=1;sy+=2)for(int sx=-1;sx<=1;sx+=2){
   int x=Math.round(cx+sx*radius*.75f),y=Math.round(cy+sy*radius*.75f);
   if(x<0||x>=width||y<0||y>=height)continue;
   int pixel=pixels.get(x,y);float foreground=contrast(pixel,bodyR,bodyG,bodyB),background=contrast(pixel,r,g,b);
   if(background>=40*40&&foreground<64*64&&foreground<background*.5f)corners++;
  }
  return new Fill(centered,inside>=6,corners==4);
 }
 private static float contrast(int pixel,float r,float g,float b){
  float dr=(pixel>>16&255)-r,dg=(pixel>>8&255)-g,db=(pixel&255)-b;return dr*dr+dg*dg+db*db;
 }
 private static int distanceSquared(int a,int b){
  int r=(a>>16&255)-(b>>16&255),g=(a>>8&255)-(b>>8&255),blue=(a&255)-(b&255);
  return r*r+g*g+blue*blue;
 }
 /** Look for a closed, sharper boundary at another size before declaring an
  * unresolved compact obstacle empty. Broad cloud shading has no such peak. */
 private static boolean compactBoundary(Pixels pixels,int width,int height,float cx,float cy,float radius){
  int closed=0,visible=0;
  for(int i=0;i<8;i++){
   float dx=DX[i*3],dy=DY[i*3];int before=-1,peak=0;boolean found=false;
   int previous=0;boolean havePrevious=false;
   for(int step=0;step<=13;step++){
    float at=.20f+step*.15f;int x=Math.round(cx+dx*radius*at),y=Math.round(cy+dy*radius*at);
    if(x<0||x>=width||y<0||y>=height)break;
    int current=pixels.get(x,y);
    if(havePrevious){
     int difference=distanceSquared(previous,current);
     if(before>=0&&peak>=24*24&&peak>before*1.5625f&&peak>difference*1.5625f)found=true;
     before=peak;peak=difference;
    }
    previous=current;havePrevious=true;
   }
   if(havePrevious){visible++;if(found)closed++;}
  }
  return visible>=6&&closed*4>=visible*3;
 }
}
