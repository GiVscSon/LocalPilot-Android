package org.localpilot.android;

public final class PanelParkingTest {
 private static int checks;
 private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void verify(PanelParking.Position p,int width,int height,int topInset,int bottomInset,
   int panelW,int panelH,int l,int t,int r,int b,int gap){
  ok(p!=null,"position exists");if(p==null)return;
  ok(p.x>=0&&p.x+panelW<=width,"inside horizontal screen");
  ok(p.y>=topInset&&p.y+panelH<=height-bottomInset,"inside safe vertical screen");
  ok(p.x+panelW+gap<=l||p.x>=r+gap||p.y+panelH+gap<=t||p.y>=b+gap,
    "panel outside expanded ROI");
 }
 public static void main(String[] args){
  int w=1080,h=2400,pw=160,ph=54,gap=16;
  var bottom=PanelParking.place(w,h,24,24,pw,ph,80,450,1000,1700,gap,100,600);
  verify(bottom,w,h,24,24,pw,ph,80,450,1000,1700,gap);
  ok(bottom.y==h-24-ph,"bottom location preferred");
  var top=PanelParking.place(w,h,24,24,pw,ph,0,400,1080,2390,gap,100,600);
  verify(top,w,h,24,24,pw,ph,0,400,1080,2390,gap);
  ok(top.y==24,"top corner is fallback");
  var unchanged=PanelParking.place(w,h,24,24,pw,ph,200,300,900,1000,gap,20,2000);
  ok(unchanged!=null&&unchanged.x==20&&unchanged.y==2000,"keep safe location");
  ok(PanelParking.place(w,h,24,24,pw,ph,0,0,1080,2400,gap,100,600)==null,
    "full-screen ROI must abstain");
  for(int rows:new int[]{6,7,10})for(int density:new int[]{1,2,3}){
   int pw2=pw*density,ph2=ph*density;
   var p=PanelParking.place(w,h,24,24,pw2,ph2,120,220,960,rows==10?1800:1500,
     gap*density,100,500);
   verify(p,w,h,24,24,pw2,ph2,120,220,960,rows==10?1800:1500,gap*density);
  }
  ok(PanelParking.place(100,100,0,0,200,30,0,20,80,80,0,0,0)==null,
    "too-wide panel must abstain");
  System.out.println("PanelParking: "+checks+" assertions PASS");
 }
}
