package org.localpilot.android;
import java.util.Arrays;

public final class SceneRecoveryTest {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  SceneRecovery r=new SceneRecovery();
  check(r.searching()&&r.pass()==SceneRecovery.Pass.ORIGINAL,"initial search");
  check(!r.observe(true,1000),"single frame cannot resume input");
  check(!r.observe(true,1000),"duplicate cannot confirm");
  check(r.observe(true,1120)&&!r.searching(),"second new frame resumes");
  check(!r.observe(false,1240)&&r.searching(),"scene loss pauses decisions");
  check(!r.observe(true,1360),"one recovered frame still waits");
  check(!r.observe(false,1480),"interrupted confirmation resets streak");
  check(!r.observe(true,1600)&&r.observe(true,1720),"two consecutive frames recover");
  r.reset();long time=100;
  for(SceneRecovery.Pass expected:SceneRecovery.Pass.values()){
   check(r.pass()==expected,"exposure rotation "+expected);
   for(int i=0;i<4;i++)check(!r.observe(false,time+=120),"unknown scene does not authorize input");
  }
  check(r.pass()==SceneRecovery.Pass.ORIGINAL,"cycles repeat without stopping");
  for(int i=0;i<100;i++)check(!r.observe(false,time+=120),"long scene loss keeps searching");
  check(r.searching()&&r.report().contains("missing=112"),"bounded metadata after long loss");
  check(r.selectionRequired(),"six-second unknown scene requests a different mechanic/region");
  check(!r.observe(true,time+=120)&&!r.selectionRequired(),"first usable frame is allowed its confirmation opportunity");
  check(r.observe(true,time+=120),"second usable frame confirms recovery after long loss");
  check(!r.observe(true,time-1)&&!r.searching(),"out-of-order frame is not accepted");
  r.reset();check(r.pass()==SceneRecovery.Pass.ORIGINAL&&r.report().contains("missing=0"),"new session reset");
  check(!r.selectionRequired(),"new scene search has a grace period");
  for(SceneRecovery.Pass pass:SceneRecovery.Pass.values()){
   check(SceneRecovery.adjust(0xff000000,pass)==0xff000000,"exposure does not invent objects in black frame");
   check((SceneRecovery.adjust(0x128001ff,pass)>>>24)==0x12,"alpha preserved");
   for(ArcadeProfile p:new ArcadeProfile[]{ArcadeProfile.PADDLE,ArcadeProfile.TRAFFIC,ArcadeProfile.FLAPPY}){
    Scene blank=new Scene(324,720,0);check(!blank.arcade(p,pass).usable(),"blank stays unknown "+p+pass);
   }
  }
  check(SceneRecovery.adjust(0x804080c0,SceneRecovery.Pass.ORIGINAL)==0x804080c0,"original pixels unchanged");
  Scene dark=new Scene(324,720,0x80c9ec);dark.circle(.5f,.6f,.025f,0x460505);dark.rect(.36f,.84f,.64f,.88f,0x5f391a);
  check(!dark.arcade(ArcadeProfile.PADDLE,SceneRecovery.Pass.ORIGINAL).usable(),"dark palette fails original threshold");
  ArcadeObservation recovered=dark.arcade(ArcadeProfile.PADDLE,SceneRecovery.Pass.BRIGHTER);
  check(recovered.usable()&&Math.abs(recovered.player.y()-.6f)<.015f,"exposure retry reacquires real objects");
  dark.rect(.46f,.58f,.54f,.62f,0x80c9ec);dark.rect(.442f,.274f,.475f,.294f,0x713d2a);
  for(SceneRecovery.Pass pass:SceneRecovery.Pass.values())check(!dark.arcade(ArcadeProfile.PADDLE,pass).usable(),"retry does not turn wood into ball "+pass);
  Scene result=new Scene(324,720,0x3d8f28);result.rect(.2f,0,.8f,1,0x7b7578);result.rect(.44f,.75f,.56f,.93f,0xe43020);result.rect(.28f,.65f,.75f,.95f,0xeeeeee);
  for(SceneRecovery.Pass pass:SceneRecovery.Pass.values())check(result.arcade(ArcadeProfile.TRAFFIC,pass).terminal,"raw result wins over exposure retry "+pass);
  Scene ari=new Scene(850,1280,0x80c9ec);
  ari.fill(100,900,220,920,0x824100);ari.fill(560,700,680,720,0x824100);ari.fill(90,500,210,520,0x824100);ari.fill(620,350,740,370,0x824100);ari.fill(320,1100,440,1120,0x824100);ari.fill(440,780,530,825,0x5f1414);ari.fill(370,500,420,560,0x8c6e0a);
  check(!ari.jumper(SceneRecovery.Pass.ORIGINAL).usable(),"dark Ari originally unknown");
  check(ari.jumper(SceneRecovery.Pass.BRIGHTER).usable(),"Ari retries exposure and default palette");
  r.reset();for(int i=1;i<=1000;i++)check(!r.observe(false,i*120L),"prolonged uncertainty never authorizes an action");
  check(!r.observe(true,120120)&&r.searching(),"first recovered frame keeps waiting");
  check(r.observe(true,120240)&&!r.searching(),"scene automatically recovers after a prolonged gap");
  System.out.println("Scene recovery: "+checks+" assertions PASS");
 }
 private static final class Scene implements PixelBoardReader.Pixels{
  final int w,h;final int[] data;
  Scene(int w,int h,int color){this.w=w;this.h=h;data=new int[w*h];Arrays.fill(data,color);}
  public int rgb(int x,int y){return data[y*w+x];}
  void fill(int l,int t,int r,int b,int c){for(int y=t;y<b;y++)for(int x=l;x<r;x++)data[y*w+x]=c;}
  void rect(float l,float t,float r,float b,int c){fill((int)(l*w),(int)(t*h),(int)(r*w),(int)(b*h),c);}
  void circle(float x,float y,float radius,int c){int cx=(int)(x*w),cy=(int)(y*h),rr=(int)(radius*w);for(int j=cy-rr;j<=cy+rr;j++)for(int i=cx-rr;i<=cx+rr;i++)if((i-cx)*(i-cx)+(j-cy)*(j-cy)<=rr*rr)data[j*w+i]=c;}
  ArcadeObservation arcade(ArcadeProfile p,SceneRecovery.Pass pass){return ArcadeVision.detectRecovering(p,this,w,h,0,0,w,h,0,0,0,0,1000,null,pass);}
  JumperState jumper(SceneRecovery.Pass pass){return JumperVision.detectRecovering(this,w,h,0,0,w,h,0,0,0,0,1000,null,null,pass);}
 }
}
