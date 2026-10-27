package org.localpilot.android;

import java.util.Arrays;
import java.util.Random;

/** Independent sampled flight and graph-resolution oracles, not a copy of the ray solver. */
public final class BubbleBounceTest {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 private static BubbleBoard board(int[] cells,int color){return new BubbleBoard(320,640,6,12,32,80,50,160,600,color,cells);}
 private static void sampledLeg(BubbleBoard b,double ax,double ay,double bx,double by,boolean impact){
  double length=Math.hypot(bx-ax,by-ay);int samples=(int)Math.ceil(length/(b.step/80.0));
  for(int n=0;n<samples;n++){
   double distance=n*length/samples;if(impact&&distance>length-b.step*.025)break;
   double x=ax+(bx-ax)*n/samples,y=ay+(by-ay)*n/samples;
   for(int i=0;i<b.size();i++)if(b.color(i)>0&&Math.hypot(x-b.x(i),y-b.y(i))<b.step*.999)
    throw new AssertionError("Flight intersects a nearer bubble at index "+i+" before advertised impact");
  }
  checks++;
 }
 private static void independentlyResolve(BubbleBoard b,BubblePolicy.Shot shot){
  int[] state=b.cells();state[shot.landing]=b.loadedColor;boolean[] group=new boolean[state.length];group[shot.landing]=true;
  // Geometry-based adjacency, deliberately independent of BubbleBoard.neighbor and queue order.
  boolean changed=true;while(changed){changed=false;for(int i=0;i<state.length;i++)if(group[i])for(int j=0;j<state.length;j++)
   if(!group[j]&&state[j]==b.loadedColor&&Math.hypot(b.x(i)-b.x(j),b.y(i)-b.y(j))<b.step*1.01){group[j]=true;changed=true;}}
  int removed=0;for(boolean value:group)if(value)removed++;
  int dropped=0;
  if(removed>=3){
   for(int i=0;i<state.length;i++)if(group[i])state[i]=0;
   boolean[] ceiling=new boolean[state.length];for(int i=0;i<b.cols;i++)ceiling[i]=state[i]>0;
   changed=true;while(changed){changed=false;for(int i=0;i<state.length;i++)if(ceiling[i])for(int j=0;j<state.length;j++)
    if(!ceiling[j]&&state[j]>0&&Math.hypot(b.x(i)-b.x(j),b.y(i)-b.y(j))<b.step*1.01){ceiling[j]=true;changed=true;}}
   for(int i=0;i<state.length;i++)if(state[i]>0&&!ceiling[i]){state[i]=0;dropped++;}
  }else removed=0;
  check(shot.removed==removed&&shot.dropped==dropped&&Arrays.equals(state,shot.expectedCells()),"Independent cluster and ceiling connectivity agree with prediction");
 }
 private static void route(BubbleBoard b,BubblePolicy.Shot s){
  check(s.bounces>=0&&s.bounces<=1,"At most one wall reflection");
  check(Float.isFinite(s.aimX)&&Float.isFinite(s.aimY)&&Float.isFinite(s.impactX)&&Float.isFinite(s.impactY),"Finite gesture and impact coordinates");
  check(b.slot(s.landing)&&b.color(s.landing)==0,"Only an empty visible slot may be occupied");
  double lastX=b.launcherX,lastY=b.launcherY;
  if(s.bounces==1){
   check(Math.abs(s.bounceX-b.x0)<.01||Math.abs(s.bounceX-(b.x0+(b.cols-1)*b.step))<.01,"Wall contact accounts for projectile radius");
   check(s.aimX==s.bounceX&&s.aimY==s.bounceY&&s.aimY<b.launcherY&&s.impactY<s.bounceY,"Gesture follows incoming ray, not reflected destination");
   double inLength=Math.hypot(s.bounceX-b.launcherX,s.bounceY-b.launcherY),outLength=Math.hypot(s.impactX-s.bounceX,s.impactY-s.bounceY);
   double inX=(s.bounceX-b.launcherX)/inLength,inY=(s.bounceY-b.launcherY)/inLength;
   double outX=(s.impactX-s.bounceX)/outLength,outY=(s.impactY-s.bounceY)/outLength;
   check(Math.abs(inX+outX)<.0001&&Math.abs(inY-outY)<.0001,"Only horizontal velocity reflects at a vertical wall");
   sampledLeg(b,b.launcherX,b.launcherY,s.bounceX,s.bounceY,false);lastX=s.bounceX;lastY=s.bounceY;
  }else check(s.aimX==s.impactX&&s.aimY==s.impactY,"Direct gesture remains backward compatible");
  sampledLeg(b,lastX,lastY,s.impactX,s.impactY,true);
  check(s.impactX>=b.x0-.01&&s.impactX<=b.x0+(b.cols-1)*b.step+.01&&s.impactY>=b.y0-.01,"Impact precedes second wall and ceiling escape");
  double nearest=Double.POSITIVE_INFINITY;for(int i=0;i<b.size();i++)if(b.color(i)>0)nearest=Math.min(nearest,Math.hypot(s.impactX-b.x(i),s.impactY-b.y(i)));
  check(Math.abs(nearest-b.step)<.08||Math.abs(s.impactY-b.y0)<.08,"Advertised impact touches a bubble or ceiling");
  independentlyResolve(b,s);
 }
 private static void seededScenes(){
  Random random=new Random(731);int reflected=0,left=0,right=0;
  for(int scene=0;scene<96;scene++){
   int[] cells=new int[72];int depth=2+scene%4;
   for(int row=0;row<depth;row++)for(int col=0;col<6-row%2;col++)cells[row*6+col]=1+random.nextInt(4);
   BubbleBoard b=board(cells,1+random.nextInt(4));BubblePolicy.Shot direct=BubblePolicy.chooseDirect(b),chosen=BubblePolicy.choose(b);
   check(direct!=null&&direct.bounces==0,"Direct-only selection cannot silently reflect");
   check(chosen!=null,"Ordinary connected seeded scene has a route");route(b,chosen);
   if(chosen.bounces==1){reflected++;if(chosen.bounceX<b.launcherX)left++;else right++;}
  }
  System.out.println("Random connected fields choosing ricochets: "+reflected+" left="+left+" right="+right);
 }
 private static void profitableRicochets(){
  int[] pattern={4,3,3,1,2,1,0,2,2,3,2,0,4,1,2,1,1,0,1,4,3,4,3,0,1,0,0,4,4,1,0,0,4,3,2,0};
  for(float scale:new float[]{.75f,1f,1.5f})for(boolean mirrored:new boolean[]{false,true}){
   int[] cells=new int[72];System.arraycopy(pattern,0,cells,0,pattern.length);
   if(mirrored){int[] original=cells.clone();Arrays.fill(cells,0);for(int row=0;row<12;row++)for(int col=0;col<6-row%2;col++)cells[row*6+(5-row%2-col)]=original[row*6+col];}
   BubbleBoard b=new BubbleBoard(Math.round(320*scale),Math.round(640*scale),6,12,35*scale,80*scale,50*scale,160*scale,600*scale,4,cells);
   BubblePolicy.Shot s=BubblePolicy.choose(b),direct=BubblePolicy.chooseDirect(b);
   check(s!=null&&s.bounces==1,"Shielded green group requires a profitable wall route");
   check(s.removed==6&&direct!=null&&direct.removed==5,"Ricochet removes six ordinary bubbles rather than five");
   check(mirrored?s.bounceX>b.launcherX:s.bounceX<b.launcherX,"Mirroring reflects the chosen side wall");route(b,s);
   BubbleSession session=new BubbleSession();session.observe(b,1000,1000);session.observe(b,1200,1200);session.sent(b,s,1200);
   BubbleBoard result=new BubbleBoard(b.width,b.height,b.cols,b.rows,b.x0,b.y0,b.step,b.launcherX,b.launcherY,2,s.expectedCells());
   session.observe(result,1700,1700);session.observe(result,1900,1900);check(session.verified()==0,"Reflected impact without Android completion cannot verify");
   session.acknowledge(1901);check(session.verified()==0,"Reflected Android completion is not a hit");
   session.observe(result,2100,2100);check(session.observe(result,2300,2300)==BubbleSession.Status.VERIFIED,"Two fresh predicted frames verify a reflected shot");
  }
 }
 private static int[] fixtureCells(){return new int[]{3,3,4,3,3,2,2,3,3,1,2,0,0,0,1,3,4,4,0,2,2,1,2,0};}
 private static java.awt.image.BufferedImage fixtureImage(int width,int height){return fixtureImage(width,height,fixtureCells(),2);}
 private static java.awt.image.BufferedImage fixtureImage(int width,int height,int[] cells,int loaded){
  int[] rgb={0x87ceeb,0xe52c32,0xf08016,0xf0cf20,0x4ab63b};
  float step=width/6.4f,x0=step*.7f;
  java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB);
  var brush=image.createGraphics();brush.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
  brush.setColor(new java.awt.Color(rgb[0]));brush.fillRect(0,0,width,height);
  for(int i=0;i<cells.length;i++)if(cells[i]>0){
   int row=i/6,col=i%6;brush.setColor(new java.awt.Color(rgb[cells[i]]));float cx=x0+(col+row%2*.5f)*step,cy=(1.5f+row*.8660254f)*step;
   brush.fill(new java.awt.geom.Ellipse2D.Float(cx-step*.46f,cy-step*.46f,step*.92f,step*.92f));
  }
  brush.setColor(new java.awt.Color(rgb[loaded]));brush.fill(new java.awt.geom.Ellipse2D.Float(width*.5f-step*.46f,height*.88f-step*.46f,step*.92f,step*.92f));brush.dispose();return image;
 }
 private static void renderedFixture(){
  for(int width:new int[]{320,480,672,1036})for(float aspect:new float[]{1.2f,1.3f,1.7f,2f}){
   int height=Math.round(width*aspect);var image=fixtureImage(width,height);
   var observed=BubbleVision.read(image::getRGB,width,height,0,0,width,height,0,0,0,0,null);
   check(observed.usable(),"Native fixture geometry is recognizable width="+width+" aspect="+aspect+": "+observed.reason());
   BubbleBoard b=observed.board();BubblePolicy.Shot shot=BubblePolicy.choose(b);
   check(b.count()==19&&b.loadedColor==2&&shot!=null&&shot.bounces==1&&shot.landing==13,"Rendered orange shot chooses the open reflected cell");
   check(shot.removed==4&&shot.dropped==0,"Rendered source predicts removal of three source candies and the projectile");route(b,shot);
   int[] expected=b.cells();expected[6]=expected[19]=expected[20]=0;
   check(Arrays.equals(expected,shot.expectedCells()),"Rendered fixture removes only manually annotated source cells");
  }
 }
 private static void shortArena(){
  int width=1032,height=1130;var image=fixtureImage(width,height);
  var read=BubbleVision.read(image::getRGB,width,height,0,0,width,height,0,0,0,0,null);
  check(read.usable(),"Short Android arena is still a recognizable ordinary field");
  BubbleBoard b=read.board();BubblePolicy.Shot shot=BubblePolicy.choose(b);
  check(shot!=null&&shot.bounces==1&&shot.landing==18&&shot.removed==3&&shot.dropped==0,"Short arena has a legal alternate triple, not the taller arena's four-bubble match");
  route(b,shot);int[] expected=b.cells();expected[19]=expected[20]=0;
  check(expected[6]==2&&Arrays.equals(expected,shot.expectedCells()),"Alternate triple preserves the upper orange cell and removes only the lower pair");
 }
 /** Original screenshot coordinates from the API30 failure; the sky is not rescaled or cropped by vision. */
 private static java.awt.image.BufferedImage sourceFixture(int[] cells,int loaded){
  var image=new java.awt.image.BufferedImage(1080,1920,java.awt.image.BufferedImage.TYPE_INT_RGB);
  var brush=image.createGraphics();brush.drawImage(fixtureImage(1038,1143,cells,loaded),21,630,null);brush.dispose();return image;
 }
 /** Small-step contact against the fixture's own dimensions and integer-dispatched direction. */
 private static int roundedFixtureLanding(BubbleBoard b,BubblePolicy.Shot shot){
  float d=1038/6.4f,left=.7f*d,right=left+5*d,x=Math.round(b.launcherX),y=Math.round(b.launcherY);
  float dx=Math.round(shot.aimX)-x,dy=Math.round(shot.aimY)-y,length=(float)Math.hypot(dx,dy);dx/=length;dy/=length;
  int[] cells=fixtureCells();int reflections=0;
  for(int tick=0;tick<4096;tick++){
   x+=dx*d*.01f;y+=dy*d*.01f;
   if(x<left){x=2*left-x;dx=-dx;reflections++;}else if(x>right){x=2*right-x;dx=-dx;reflections++;}
   if(reflections>1||y<d*1.5f)return -1;
   int hit=-1;float hitDistance=Float.POSITIVE_INFINITY;
   for(int i=0;i<cells.length;i++)if(cells[i]>0){
    float distance=(float)Math.hypot(x-(left+(i%6+(i/6%2)*.5f)*d),y-(1.5f+i/6*.8660254f)*d);
    if(distance<d&&distance<hitDistance){hit=i;hitDistance=distance;}
   }
   if(hit<0)continue;
   int nearest=-1;float distance=Float.POSITIVE_INFINITY;
   float hx=left+(hit%6+(hit/6%2)*.5f)*d,hy=(1.5f+hit/6*.8660254f)*d;
   for(int row=0;row<6;row++)for(int col=0;col<6-row%2;col++){
    int i=row*6+col;if(i<cells.length&&cells[i]>0)continue;
    float cx=left+(col+(row%2)*.5f)*d,cy=(1.5f+row*.8660254f)*d;
    if(Math.hypot(cx-hx,cy-hy)>d*1.03f)continue;
    float at=(float)Math.hypot(x-cx,y-cy);if(at<distance){distance=at;nearest=i;}
   }
   return reflections==1&&distance<d*.46f?nearest:-1;
  }
  return -1;
 }
 private static void nativeSourceCoordinates(){
  var image=sourceFixture(fixtureCells(),2);
  var read=BubbleVision.read(image::getRGB,1080,1920,21,630,1059,1773,0,0,0,0,null);
  check(read.usable(),"Exact API30 source rectangle is recognizable: "+read.reason());
  BubbleBoard b=read.board();
  check(b.width==1038&&b.height==1143&&b.cols==6&&b.rows==4&&b.count()==19&&b.loadedColor==2,"Source ROI retains the native six-by-four board");
  check(Math.abs(b.x0-114.740005f)<.01f&&Math.abs(b.y0-243.55501f)<.01f&&Math.abs(b.step-161.85f)<.01f
    &&Math.abs(b.launcherX-517.5f)<.01f&&Math.abs(b.launcherY-1005f)<.01f,"Source-coordinate fit reproduces the API30 annotation");
  BubblePolicy.Shot shot=BubblePolicy.choose(b);
  check(shot!=null&&shot.bounces==1&&shot.landing==18&&shot.removed==3&&shot.dropped==0,"Native short arena chooses the lower orange triple");
  route(b,shot);
  check(Math.round(b.launcherX)==518&&Math.round(b.launcherY)==1005&&Math.round(shot.aimX)==115&&Math.round(shot.aimY)==723
    &&roundedFixtureLanding(b,shot)==18,"Rounded native gesture reaches the independent fixture's lower orange cell");
  int[] expected=fixtureCells();expected[19]=expected[20]=0;
  check(Arrays.equals(expected,shot.expectedCells())&&expected[6]==2,"The expected result removes only source cells19 and20");
  var nextImage=sourceFixture(expected,3);
  var post=BubbleVision.read(nextImage::getRGB,1080,1920,21,630,1059,1773,0,0,0,0,b);
  check(post.usable()&&post.board().sameGeometry(b)&&post.board().count()==17&&post.board().loadedColor==3
    &&Arrays.equals(expected,post.board().cells()),"Cached source-coordinate read sees17 retained source cells and the next loaded color");
  BubbleBoard next=post.board();BubbleSession session=new BubbleSession();
  check(session.observe(b,1000,1000)==BubbleSession.Status.SEARCHING&&session.observe(b,2100,2100)==BubbleSession.Status.READY,"Two API30-spaced source frames open policy");
  session.sent(b,shot,2100);session.acknowledge(2320);
  check(session.observe(next,3420,3420)==BubbleSession.Status.WAIT_RESPONSE&&session.observe(next,4520,4520)==BubbleSession.Status.VERIFIED
    &&session.verified()==1&&!session.pending(),"Two fresh post-ACK source frames verify the native alternate triple");
  session.reset();session.observe(b,1000,1000);session.observe(b,2100,2100);session.sent(b,shot,2100);session.acknowledge(2320);
  check(session.observe(next,3420,3820)==BubbleSession.Status.WAIT_RESPONSE&&session.verified()==0,"A matching but stale source frame cannot verify the native shot");
  check(session.observe(next,9050,9100)==BubbleSession.Status.PAUSED&&session.pending()&&session.verified()==0
    &&session.report().contains("android_ack=true"),"Expired native verification preserves pending Android ACK without asserting a game response");
 }
 private static void uncertainWalls(){
  int[] cells=new int[72];System.arraycopy(new int[]{1,2,1,3,4,4},0,cells,0,6);
  BubbleBoard loose=new BubbleBoard(640,640,6,12,192,80,50,320,600,1,cells);
  BubblePolicy.Shot s=BubblePolicy.choose(loose);
  check(s==null||s.bounces==0,"Inset lattice cannot invent the location of a side wall");
  int[] outsideCells=new int[48];System.arraycopy(new int[]{1,2,3,4},0,outsideCells,0,4);
  BubbleBoard outside=new BubbleBoard(400,640,4,12,150,80,50,50,600,1,outsideCells);
  check(BubblePolicy.choose(outside)==null&&BubblePolicy.chooseDirect(outside)==null,"A launcher outside the center walls cannot shoot into the lattice");
  BubbleBoard blocked=board(cells,1);int[] packed=blocked.cells();for(int row=1;row<12;row++)for(int col=0;col<6-row%2;col++)packed[row*6+col]=2;
  check(BubblePolicy.choose(board(packed,1))==null,"A launcher overlapping the board cannot escape by reflection");
 }
 public static void main(String[] args){seededScenes();profitableRicochets();renderedFixture();shortArena();nativeSourceCoordinates();uncertainWalls();System.out.println("Bubble ricochet oracle: "+checks+" assertions PASS; physical Market walls unverified");}
}
