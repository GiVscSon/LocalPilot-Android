package org.localpilot.android;

/** Pure JVM regression matrix for ROI docking; no Android UI or private screenshots. */
public final class PanelDockingTest {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 private static PanelDocking.Area a(int l,int t,int r,int b){return new PanelDocking.Area(l,t,r,b);}
 private static void assertDock(PanelDocking.Area safe,PanelDocking.Area old,PanelDocking.Area roi,int padding){
  PanelDocking.Area target=PanelDocking.choose(safe,old,roi,padding);
  if(target!=null){
   check(target.width()==old.width()&&target.height()==old.height(),"same STOP button dimensions");
   check(safe.contains(target),"target must fit safe window");
   check(!target.intersects(roi),"target must not cover ROI");
  }
 }
 private static boolean brute(PanelDocking.Area safe,PanelDocking.Area original,PanelDocking.Area roi){
  for(int x=safe.left;x+original.width()<=safe.right;x++)
   for(int y=safe.top;y+original.height()<=safe.bottom;y++)
    if(!a(x,y,x+original.width(),y+original.height()).intersects(roi))return true;
  return false;
 }
 public static void main(String[] args){
  // Actual dimensions are illustrative screen pixels, not saved user geometry.
  PanelDocking.Area phone=a(0,24,691,1512);
  for(int[] game:new int[][]{{10,430,680,1110},{60,700,645,1260},{205,420,505,720},{0,170,691,1100}}){
   PanelDocking.Area roi=a(game[0],game[1],game[2],game[3]);
   PanelDocking.Area old=a(170,430,466,560);
   assertDock(phone,old,roi,16);
  }
  // 31 Oct 1536x1025 field layout: game has large sidebars. The expanded
  // STOP panel is dragged over the left part of an active 6x6 grid.
  // A re-dock must select a side region without masking any board pixels.
  var landscape=PanelDocking.choose(a(0,24,1536,996),a(418,108,734,552),
    a(507,249,1000,754),16);
  check(landscape!=null&&!landscape.intersects(a(507,249,1000,754)),
    "active field's dragged panel can dock outside actual 6x6 ROI");
  check(landscape.width()==316&&landscape.height()==444,"recovery keeps STOP visible");
  // Phone diagnostic: 1220x2712, manual 6x6 ROI [103,1241][1099,2262].
  var phoneRoi=a(103,1241,1099,2262);
  var dock=PanelDocking.choose(a(0,82,1220,2630),a(310,2280,430,2350),phoneRoi,24);
  check(dock!=null&&dock.bottom<=1241-24&&dock.top>=82,"phone STOP docks above the 6x6 ROI");
  check(!dock.intersects(phoneRoi),"phone STOP never covers manually selected ROI");
  PanelDocking.Area safe=a(0,0,400,400),roi=a(90,0,310,400),current=a(140,130,210,180);
  var side=PanelDocking.choose(safe,current,roi,8);
  check(side!=null&&safe.contains(side)&&!roi.intersects(side),"find narrow left/right pocket, not just corners");
  var alreadySafe=a(4,4,74,54);
  check(PanelDocking.choose(safe,alreadySafe,a(100,100,300,300),8)==alreadySafe,
    "existing safe panel stays put");
  check(PanelDocking.choose(safe,current,safe,0)==null,"fullscreen ROI fails closed");
  check(PanelDocking.choose(safe,a(0,0,420,50),roi,8)==null,"oversized panel fails closed");
  check(PanelDocking.choose(a(0,0,130,100),a(50,40,100,90),a(0,0,130,80),8)==null,
    "no safe vertical area fails closed");
  // At zero margin, touch of a rectangle edge is non-overlap.
  var snug=PanelDocking.choose(a(0,0,120,100),a(25,20,65,60),a(0,0,120,60),10);
  check(snug!=null&&snug.top==60,"fallback to zero margin works when exact fit exists");
  java.util.Random random=new java.util.Random(0x334c);
  for(int n=0;n<5000;n++){
   int sw=9+random.nextInt(19),sh=9+random.nextInt(19);
   int w=1+random.nextInt(sw),h=1+random.nextInt(sh);
   int rx=random.nextInt(sw),ry=random.nextInt(sh);
   int rr=rx+1+random.nextInt(sw-rx),rb=ry+1+random.nextInt(sh-ry);
   PanelDocking.Area s=a(0,0,sw,sh),r=a(rx,ry,rr,rb);
   int ox=random.nextInt(sw-w+1),oy=random.nextInt(sh-h+1);
   PanelDocking.Area p=a(ox,oy,ox+w,oy+h);
   PanelDocking.Area result=PanelDocking.choose(s,p,r,random.nextInt(5));
   boolean feasible=brute(s,p,r);
   check((result!=null)==feasible,"return placement iff a safe placement exists ("+n+")");
   if(result!=null)check(s.contains(result)&&!r.intersects(result),"safe result ("+n+")");
  }
  var edgeSafe=a(Integer.MAX_VALUE-200,0,Integer.MAX_VALUE,100);
  var edgeRoi=a(Integer.MAX_VALUE-100,0,Integer.MAX_VALUE,100);
  var edgePanel=a(Integer.MAX_VALUE-90,10,Integer.MAX_VALUE-40,60);
  var edgeResult=PanelDocking.choose(edgeSafe,edgePanel,edgeRoi,50);
  check(edgeResult!=null&&edgeSafe.contains(edgeResult)&&!edgeResult.intersects(edgeRoi),"docking near integer boundary cannot overflow");
  try{a(Integer.MIN_VALUE,0,Integer.MAX_VALUE,10);throw new AssertionError("overflow dimensions accepted");}
  catch(IllegalArgumentException expected){checks++;}
  try{PanelDocking.choose(phone,current,roi,-1);throw new AssertionError("negative gap accepted");}
  catch(IllegalArgumentException expected){checks++;}
  System.out.println("Panel docking: "+checks+" assertions PASS; 5000 randomized safe/fail-closed layouts");
 }
}
