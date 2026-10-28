package org.localpilot.android;

import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;

public final class BubbleTest {
 private static int checks;
 private static final int[] COLORS={0x87ceeb,0xe52c32,0xf08016,0xf0cf20,0x4ab63b};
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static BubbleBoard board(int color){int[] cells=new int[6*12];System.arraycopy(new int[]{1,1,2,3,4,4},0,cells,0,6);return new BubbleBoard(320,640,6,12,32,80,50,160,600,color,cells);}
 private static BubbleBoard after(BubbleBoard b,int[] cells){return new BubbleBoard(b.width,b.height,b.cols,b.rows,b.x0,b.y0,b.step,b.launcherX,b.launcherY,2,cells);}
 private static void geometryAndPolicy(){
  BubbleBoard b=board(1);check(b.ceilingConnected(),"top row is connected to ceiling");
  check(!b.neighbor(5,6)&&!b.slot(11),"row ends do not wrap and short rows have no phantom cell");
  check(b.neighbor(0,6)&&b.neighbor(1,6)&&!b.neighbor(2,6),"staggered neighbors");
  BubblePolicy.Shot shot=BubblePolicy.choose(b);check(shot!=null&&shot.removed>=3,"exposed pair can be matched by a direct shot");
  check(shot.aimY<b.launcherY&&shot.aimX>=b.x0&&shot.aimX<=b.x0+(b.cols-1)*b.step,"aim remains in playable area");
  int count=0;for(int type:shot.expectedCells())if(type>0)count++;
  check(count==b.count()+1-shot.removed-shot.dropped,"predicted normal-bubble count conserves shot and removals");
  int[] copy=shot.expectedCells();Arrays.fill(copy,4);check(!Arrays.equals(copy,shot.expectedCells()),"shot result cannot be mutated externally");
  int[] detached=b.cells();detached[6*5+2]=1;BubbleBoard floating=after(b,detached);
  check(!floating.ceilingConnected()&&BubblePolicy.choose(floating)==null,"falling/detached objects do not authorize a shot");
  try{new BubbleBoard(320,640,6,12,Float.NaN,80,50,160,600,1,b.cells());throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){checks++;}
  try{b.x(-1);throw new AssertionError("negative geometry index accepted");}catch(IllegalArgumentException expected){checks++;}
  try{new BubbleBoard(320,640,6,24,32,80,50,160,600,1,new int[144]);throw new AssertionError("offscreen rows accepted");}catch(IllegalArgumentException expected){checks++;}
  int[] bridge=b.cells();bridge[6]=2;bridge[12]=3;
  BubblePolicy.Shot drop=BubblePolicy.choose(new BubbleBoard(320,640,6,12,32,80,50,160,600,1,bridge));
  check(drop!=null&&drop.removed==3&&drop.dropped==2,"matching a ceiling pair drops its two differently colored descendants");
  check(drop.expectedCells()[6]==0&&drop.expectedCells()[12]==0,"detached descendants are absent from predicted result");
  int[] packed=b.cells();for(int row=1;row<12;row++)for(int col=0;col<6-row%2;col++)packed[row*6+col]=2;
  check(BubblePolicy.choose(after(b,packed))==null,"overlapping loaded ball cannot shoot through a low board");
 }
 private static void singleRemaining(){
  int[] cells=new int[72];cells[2]=1;
  BubbleBoard b=new BubbleBoard(320,640,6,12,32,80,50,160,600,1,cells);
  BubblePolicy.Shot shot=BubblePolicy.choose(b);
  check(shot!=null&&shot.removed==0&&shot.dropped==0,"one candy can be joined by a matching shot");
  BubbleBoard pair=new BubbleBoard(b.width,b.height,b.cols,b.rows,b.x0,b.y0,b.step,b.launcherX,b.launcherY,1,shot.expectedCells());
  check(pair.count()==2&&pair.neighbor(2,shot.landing),"first shot builds a connected pair");
  BubbleSession session=new BubbleSession();session.observe(b,1000,1000);session.observe(b,1200,1200);session.sent(b,shot,1200);
  session.observe(pair,1800,1800);session.observe(pair,2000,2000);check(session.verified()==0,"pair cannot be verified before ACK");
  session.acknowledge(2050);session.observe(pair,2200,2200);
  check(session.observe(pair,2400,2400)==BubbleSession.Status.VERIFIED,"two fresh predicted frames verify the pair");
  BubblePolicy.Shot finish=BubblePolicy.choose(pair);
  check(finish!=null&&finish.removed==3&&Arrays.stream(finish.expectedCells()).allMatch(x->x==0),"next matching shot clears the triple");
  BubbleBoard wrong=new BubbleBoard(320,640,6,12,32,80,50,160,600,2,cells);
  check(BubblePolicy.choose(wrong)==null,"unknown next color cannot justify a mismatched shot at the final candy");
  Arrays.fill(cells,0);check(BubblePolicy.choose(new BubbleBoard(320,640,6,12,32,80,50,160,600,1,cells))==null,"empty board never authorizes input");
 }
 private static void session(){
  BubbleBoard b=board(1);BubblePolicy.Shot shot=BubblePolicy.choose(b);BubbleBoard next=after(b,shot.expectedCells());
  BubbleSession s=new BubbleSession();check(s.observe(b,1000,1000)==BubbleSession.Status.SEARCHING,"first frame is not enough");
  check(s.observe(b,1200,1200)==BubbleSession.Status.READY,"two distinct stable frames open policy");s.sent(b,shot,1200);
  s.observe(next,1700,1700);s.observe(next,1900,1900);check(s.verified()==0,"image changes before Android completion cannot release pending shot");
  s.acknowledge(1901);check(s.verified()==0,"ACK alone never counts a shot");
  s.observe(next,2100,2100);check(s.observe(next,2300,2300)==BubbleSession.Status.VERIFIED&&s.verified()==1,"two predicted post-ACK frames verify one shot");
  s.reset();s.observe(b,1000,1000);s.observe(b,1200,1200);s.sent(b,shot,1200);s.acknowledge(1400);
  int[] wrong=b.cells();wrong[8]=3;BubbleBoard unrelated=after(b,wrong);
  s.observe(unrelated,1800,1800);s.observe(unrelated,2200,2200);check(s.verified()==0&&s.pending(),"unrelated board change is not the predicted impact");
  s.invalidateCandidate();s.observe(next,2600,2600);check(s.observe(next,2600,2800)!=BubbleSession.Status.VERIFIED,"repeated capture after focus loss cannot count twice");
  check(s.observe(null,8300,8300)==BubbleSession.Status.PAUSED,"deadline also applies to missing scene");
  s.reset();check(s.observe(b,1000,1400)==BubbleSession.Status.SEARCHING,"old screenshot does not enter stable state");
  s.observe(b,1500,1500);s.observe(b,1700,1700);
  try{s.sent(b,BubblePolicy.choose(board(4)),1700);throw new AssertionError("other color plan accepted");}catch(IllegalStateException expected){checks++;}
  s.reset();s.observe(b,1000,1000);s.observe(null,1200,1200);
  check(s.observe(b,1150,1200)==BubbleSession.Status.SEARCHING,"invalid scene still advances capture monotonicity");
  check(s.observe(b,1250,1250)==BubbleSession.Status.SEARCHING,"out-of-order capture cannot survive as first stable observation");
  check(s.observe(b,1400,1400)==BubbleSession.Status.READY,"new stable observations recover after missing scene");
  s.sent(b,shot,1400);s.acknowledge(1600);
  BubbleBoard shifted=new BubbleBoard(b.width,b.height,b.cols,b.rows,b.x0,b.y0+6,b.step,b.launcherX,b.launcherY,2,shot.expectedCells());
  s.observe(shifted,1900,1900);s.observe(shifted,2200,2200);check(s.pending()&&s.verified()==0,"world/ceiling translation is not a predicted normal shot");
  s.reset();s.observe(b,3000,3000);s.observe(b,3200,3200);s.sent(b,shot,3200);s.acknowledge(3400);
  BubbleBoard reloaded=after(b,b.cells());s.observe(reloaded,3800,3800);s.observe(reloaded,4100,4100);
  check(s.pending()&&s.verified()==0,"a different loaded color without board impact is not success");
 }
 private static BubbleSession latePending(BubbleBoard b,BubblePolicy.Shot shot){
  BubbleSession s=new BubbleSession(1000,5000);s.observe(b,1000,1000);s.observe(b,1100,1100);s.sent(b,shot,1200);return s;
 }
 private static void lateVerification(){
  BubbleBoard b=board(1);BubblePolicy.Shot shot=BubblePolicy.choose(b);BubbleBoard next=after(b,shot.expectedCells());
  BubbleSession s=latePending(b,shot);s.acknowledge(1250);
  s.observe(next,2100,2100);
  check(s.observe(next,2200,2200)==BubbleSession.Status.PAUSED&&s.awaitingLateVerification()&&!s.finalPaused(),"primary expiry enters bounded visual-only pause");
  check(s.verified()==0&&s.pending()&&s.acknowledged(),"soft expiry preserves one unresolved acknowledged shot");
  s.observe(next,2300,2300);
  check(s.observe(next,2300,2350)==BubbleSession.Status.PAUSED&&s.verified()==0,"duplicate late frame cannot verify");
  s.observe(next,2400,2400);
  check(s.observe(next,2500,2500)==BubbleSession.Status.VERIFIED&&s.verified()==1&&!s.pending()&&!s.awaitingLateVerification(),"two fresh late frames exactly matching prediction verify once");
  check(s.observe(next,2600,2600)==BubbleSession.Status.READY&&s.verified()==1,"late result cannot be counted again");

  s=latePending(b,shot);s.acknowledge(1250);s.observe(null,2200,2200);
  int[] wrong=shot.expectedCells();wrong[2]=wrong[2]==3?4:3;BubbleBoard unrelated=after(b,wrong);
  s.observe(unrelated,2300,2300);
  check(s.observe(unrelated,2400,2400)==BubbleSession.Status.PAUSED&&s.lastWait()==BubbleSession.Reason.CELLS_MISMATCH,"unrelated late stable board remains paused");
  BubbleBoard shifted=new BubbleBoard(b.width,b.height,b.cols,b.rows,b.x0,b.y0+6,b.step,b.launcherX,b.launcherY,2,shot.expectedCells());
  s.observe(shifted,2500,2500);
  check(s.observe(shifted,2600,2600)==BubbleSession.Status.PAUSED&&s.lastWait()==BubbleSession.Reason.GEOMETRY_MISMATCH,"late geometry changes cannot verify");
  s.observe(next,2700,3101);
  check(s.verified()==0&&s.lastWait()==BubbleSession.Reason.STALE,"late stale frame remains rejected");
  s.observe(next,6100,6100);s.acknowledge(6150);
  check(s.observe(next,6200,6200)==BubbleSession.Status.PAUSED&&s.finalPaused()&&!s.awaitingLateVerification(),"absolute final dispatch deadline wins over a matching frame and duplicate ACK");
  String expired=s.report();s.observe(next,6300,6300);s.acknowledge(6400);
  check(s.report().equals(expired)&&s.verified()==0,"final pause is immutable and cannot reopen");

  s=latePending(b,shot);s.observe(next,2100,2100);
  check(s.observe(next,2200,2200)==BubbleSession.Status.PAUSED&&s.finalPaused(),"missing Android ACK has no late recovery window");
  s.acknowledge(2300);s.observe(next,2400,2400);s.observe(next,2500,2500);
  check(!s.acknowledged()&&s.verified()==0&&s.finalPaused(),"late ACK cannot reopen unacknowledged expiry");

  s=latePending(b,shot);s.acknowledge(2200);
  check(!s.acknowledged()&&s.observe(next,2300,2300)==BubbleSession.Status.PAUSED&&s.finalPaused(),"late ACK cannot open recovery when no frame observed primary expiry");

  s=latePending(b,shot);s.acknowledge(1250);s.observe(null,2200,2200);s.reset();
  check(!s.pending()&&!s.paused()&&!s.awaitingLateVerification()&&!s.acknowledged()&&s.verified()==0,"STOP/reset clears late state and prediction");
  s.acknowledge(2300);s.observe(next,2400,2400);s.observe(next,2500,2500);
  check(s.verified()==0&&!s.pending(),"old completion and old prediction cannot verify after reset");
  s=latePending(b,shot);s.acknowledge(1250);s.observe(null,2200,2200);s.invalidateCandidate();
  check(s.finalPaused()&&!s.awaitingLateVerification()&&s.pending(),"context invalidation revokes an active late window without losing the pending shot");
  s.observe(next,2300,2300);s.observe(next,2400,2400);
  check(s.verified()==0&&s.report().contains("CONTEXT_INVALIDATED"),"old expected board cannot resume after context invalidation");
  s=latePending(b,shot);s.acknowledge(1250);s.invalidateCandidate();
  check(s.observe(null,2200,2200)==BubbleSession.Status.PAUSED&&s.finalPaused(),"context invalidation before primary prevents opening late recovery");
  s=latePending(b,shot);s.acknowledge(1250);s.invalidateCandidate();s.observe(next,1600,1600);
  check(s.observe(next,1700,1700)==BubbleSession.Status.VERIFIED,"legacy fresh ordinary verification before primary survives candidate invalidation");
  for(long[] limits:new long[][]{{999,5000},{10001,11000},{2000,1999},{1000,15001},{-1,-1}}){
   try{new BubbleSession(limits[0],limits[1]);throw new AssertionError("invalid response deadlines accepted");}catch(IllegalArgumentException expected){checks++;}
  }
 }
 private static void directShotGeometry(){
  var random=new java.util.Random(730);
  for(int scene=0;scene<24;scene++){
   int[] cells=new int[72];int depth=2+scene%3;
   for(int row=0;row<depth;row++)for(int col=0;col<6-row%2;col++)cells[row*6+col]=1+random.nextInt(4);
   BubbleBoard b=new BubbleBoard(320,640,6,12,32,80,50,160,600,1+random.nextInt(4),cells);
   BubblePolicy.Shot shot=BubblePolicy.chooseDirect(b);check(shot!=null,"direct shot available in normal seeded scene");
   check(b.slot(shot.landing)&&b.color(shot.landing)==0,"ray only attaches to a visible empty slot");
   double travel=Math.hypot(shot.aimX-b.launcherX,shot.aimY-b.launcherY),dx=(shot.aimX-b.launcherX)/travel,dy=(shot.aimY-b.launcherY)/travel;
   boolean clear=true;
   // Independent discrete collision oracle: the moving center must remain a
   // full projectile+target diameter from every occupied center before impact.
   for(double at=0;at<travel-b.step*.05;at+=b.step/20.0){
    double x=b.launcherX+dx*at,y=b.launcherY+dy*at;
    for(int index=0;index<b.size();index++)if(b.color(index)>0&&Math.hypot(x-b.x(index),y-b.y(index))<b.step*.999){clear=false;break;}
    if(!clear)break;
   }
   check(clear,"direct ray cannot pass through a nearer bubble");
   double nearest=Double.POSITIVE_INFINITY;for(int index=0;index<b.size();index++)if(b.color(index)>0)nearest=Math.min(nearest,Math.hypot(shot.aimX-b.x(index),shot.aimY-b.y(index)));
   check(Math.abs(nearest-b.step)<.05||Math.abs(shot.aimY-b.y0)<.05,"aim terminates at first bubble or empty ceiling");
   BubbleBoard expected=after(b,shot.expectedCells());check(expected.ceilingConnected(),"predicted remaining objects stay attached to ceiling");
  }
 }
 private static BubbleSession pending(BubbleBoard b,BubblePolicy.Shot shot){
  BubbleSession s=new BubbleSession();s.observe(b,1000,1000);s.observe(b,1200,1200);s.sent(b,shot,1200);return s;
 }
 private static void diagnostics(){
  BubbleBoard b=board(1);BubblePolicy.Shot shot=BubblePolicy.choose(b);BubbleBoard next=after(b,shot.expectedCells());
  BubbleSession s=new BubbleSession();s.observe(null,1000,1000);
  check(s.report().contains("reason=NULL_SCENE")&&s.report().contains("observed_cells=-1"),"missing scene is diagnosed without a guessed board");
  s.observe(b,1100,1501);
  check(s.report().contains("reason=STALE")&&s.report().contains("frame_age_ms=401")&&s.report().contains("NULL_SCENE:1,STALE:1"),"bounded reason counts preserve a missing scene followed by stale pixels");
  s=pending(b,shot);check(!s.acknowledged(),"dispatch is not Android completion");s.observe(next,1700,1700);
  check(s.report().contains("reason=BEFORE_ACK")&&s.pending()&&s.verified()==0,"pre-ACK visual change is diagnosed without verification");
  s.acknowledge(1750);s.observe(next,1800,1800);s.observe(next,1850,1850);
  check(s.report().contains("reason=UNSTABLE")&&s.report().contains("candidate_age_ms=50"),"a distinct but too-close frame still needs stability");
  s.observe(next,2000,2000);
  check(s.verified()==1&&s.report().contains("reason=VERIFIED")&&s.report().contains("sent_age_ms=800 ack_age_ms=250")&&s.report().contains("observations=6")&&s.report().contains("mismatched_cells=0"),"verified report retains the actual frame/send/ACK ages after clearing pending state");
  s=pending(b,shot);s.acknowledge(1250);s.observe(next,1300,1300);
  check(s.pending()&&s.report().contains("reason=SETTLING"),"post-ACK settling is distinguished from absent ACK");
  BubbleBoard shifted=new BubbleBoard(b.width,b.height,b.cols,b.rows,b.x0,b.y0+6,b.step,b.launcherX,b.launcherY,2,shot.expectedCells());
  s=pending(b,shot);s.acknowledge(1400);s.observe(shifted,1800,1800);s.observe(shifted,2000,2000);
  check(s.pending()&&s.verified()==0&&s.report().contains("reason=GEOMETRY_MISMATCH")&&s.report().contains("mismatched_cells=0"),"same colors at shifted geometry do not verify and report the geometry branch");
  int[] wrong=shot.expectedCells();wrong[2]=wrong[2]==3?4:3;BubbleBoard unrelated=after(b,wrong);
  s=pending(b,shot);s.acknowledge(1400);s.observe(unrelated,1800,1800);s.observe(unrelated,2000,2000);
  check(s.pending()&&s.verified()==0&&s.report().contains("reason=CELLS_MISMATCH")&&s.report().contains("mismatched_cells=1"),"a stable unrelated board reports the exact mismatch count without its colors");
  s.observe(next,8200,8200);String expired=s.report();
  check(s.paused()&&s.pending()&&s.acknowledged()&&s.lastWait()==BubbleSession.Reason.CELLS_MISMATCH&&s.verified()==0&&expired.contains("reason=EXPIRED last_wait=CELLS_MISMATCH")&&expired.contains("sent_age_ms=7000"),"deadline retains the prior rejected branch while refusing even matching pixels");
  s.observe(next,8300,8300);check(s.report().equals(expired),"later paused observations cannot overwrite the evidence that caused the pause");
  s.visionReason("reader\n"+"x".repeat(300));String report=s.report();String detail=report.substring(report.indexOf("vision_reason=")+14);
  check(detail.length()==120&&!detail.contains("\n")&&report.length()<2000,"reader explanation and total metadata stay bounded on one line");
  s.reset();check(s.report().contains("reason=RESET")&&s.report().contains("observations=0")&&s.report().contains("reason_counts=- vision_reason=none"),"reset starts a new bounded diagnostic session");
 }
 private static BufferedImage synthetic(){
  BufferedImage im=new BufferedImage(320,640,BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();g.setColor(new java.awt.Color(COLORS[0]));g.fillRect(0,0,320,640);
  for(int row=0;row<3;row++)for(int col=0;col<6-row%2;col++){
   int x=Math.round(26+(col+row%2*.5f)*48),y=Math.round(80+row*48*.8660254f);
   g.setColor(new java.awt.Color(COLORS[1+(col+row)%4]));g.fillOval(x-23,y-23,46,46);
  }
  for(int[] p:new int[][]{{160,600,1},{90,615,2},{230,615,4}}){g.setColor(new java.awt.Color(COLORS[p[2]]));g.fillOval(p[0]-23,p[1]-23,46,46);}
  g.dispose();return im;
 }
 private static void reversedStaggerFixture(){
  BufferedImage im=new BufferedImage(320,640,BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();
  g.setColor(new java.awt.Color(COLORS[0]));g.fillRect(0,0,320,640);
  // Like the supplied screenshot, the second row begins half a step left of the first.
  for(int row=0;row<3;row++)for(int col=0;col<(row%2==0?5:6);col++){
   int x=50+col*48-(row%2)*24,y=Math.round(80+row*48*.8660254f);
   g.setColor(new java.awt.Color(COLORS[1+(col+row)%4]));g.fillOval(x-23,y-23,46,46);
  }
  g.setColor(new java.awt.Color(COLORS[3]));g.fillOval(137,577,46,46);g.dispose();
  var read=BubbleVision.read(im::getRGB,320,640,0,0,320,640,0,0,0,0,null);
  check(read.usable(),"opposite row staggering must be recognized: "+read.reason());
  BubbleBoard reverse=read.board();check(reverse.shortRowParity==0&&!reverse.slot(reverse.cols-1)&&reverse.slot(2*reverse.cols-1),"short first row and long second row retained");
  check(reverse.neighbor(0,reverse.cols)&&reverse.neighbor(0,reverse.cols+1),"opposite phase adjacency");
  var tracked=BubbleVision.read(im::getRGB,320,640,0,0,320,640,0,0,0,0,reverse);check(tracked.usable()&&tracked.board().same(reverse),"tracked reversed geometry stable");
  int[] simple=new int[6*12];simple[1]=1;simple[2]=1;
  BubbleBoard scene=new BubbleBoard(320,640,6,12,26,80,48,160,600,1,simple,0);
  BubblePolicy.Shot shot=BubblePolicy.choose(scene);check(shot!=null&&scene.slot(shot.landing),"reverse phase shot lands on visible slot");
  BubbleSession session=new BubbleSession();session.observe(scene,1000,1000);session.observe(scene,1200,1200);session.sent(scene,shot,1200);session.acknowledge(1250);
  BubbleBoard result=new BubbleBoard(320,640,6,12,26,80,48,160,600,2,shot.expectedCells(),0);
  session.observe(result,1700,1700);check(session.observe(result,1900,1900)==BubbleSession.Status.VERIFIED,"reverse phase verifies predicted outcome");
  BubbleBoard wrongPhase=new BubbleBoard(320,640,6,12,26,80,48,160,600,2,shot.expectedCells(),1);
  check(!scene.sameGeometry(wrongPhase),"different row phases cannot confirm each other");
  BufferedImage ambiguous=new BufferedImage(320,640,BufferedImage.TYPE_INT_RGB);var brush=ambiguous.createGraphics();
  brush.setColor(new java.awt.Color(COLORS[0]));brush.fillRect(0,0,320,640);
  for(int row:new int[]{0,2})for(int col=0;col<5;col++){
   int x=50+col*48,y=Math.round(80+row*48*.8660254f);brush.setColor(new java.awt.Color(COLORS[1+(col+row)%4]));brush.fillOval(x-23,y-23,46,46);
  }
  brush.setColor(new java.awt.Color(COLORS[3]));brush.fillOval(137,577,46,46);brush.dispose();
  check(!BubbleVision.read(ambiguous::getRGB,320,640,0,0,320,640,0,0,0,0,null).usable(),"one observed row phase cannot infer alternating lattice");
  System.out.println("Reversed stagger fixture: "+read.reason());
 }
 private static void perception(){
  BufferedImage im=synthetic();var r=BubbleVision.read(im::getRGB,320,640,0,0,320,640,0,0,0,0,null);
  check(r.usable(),"ordinary synthetic field: "+r.reason());check(r.board().count()==17&&r.board().loadedColor==1,"reserve and bonus do not become launched bubble");
  check(Math.abs(r.board().step-48)<2&&Math.abs(r.board().x0-26)<3&&Math.abs(r.board().y0-80)<3,"lattice fitted in source pixels");
  var tracked=BubbleVision.read(im::getRGB,320,640,0,0,320,640,0,0,0,0,r.board());check(tracked.usable()&&tracked.board().same(r.board()),"stable geometry can read next frame");
  BufferedImage one=synthetic();var oneBrush=one.createGraphics();oneBrush.setColor(new java.awt.Color(COLORS[0]));oneBrush.fillRect(0,45,320,160);
  oneBrush.setColor(new java.awt.Color(COLORS[1]));oneBrush.fillOval(26-23,80-23,46,46);oneBrush.dispose();
  var last=BubbleVision.read(one::getRGB,320,640,0,0,320,640,0,0,0,0,r.board());
  check(last.usable()&&last.board().count()==1,"tracked geometry reads the last candy from pixels");
  check(!BubbleVision.read(one::getRGB,320,640,0,0,320,640,0,0,0,0,null).usable(),"a single candy cannot invent initial board geometry");
  int[] calls={0};var sampled=BubbleVision.read((x,y)->{calls[0]++;return im.getRGB(x,y);},320,640,0,0,320,640,0,0,0,0,r.board());
  check(sampled.usable()&&calls[0]<12_000,"cached geometry probes bounded cells instead of copying the frame");
  check(!BubbleVision.read((x,y)->0x202020,320,640,0,0,320,640,0,0,0,0,r.board()).usable(),"cached probes never retain pixels from an earlier image");
  BufferedImage offset=new BufferedImage(420,760,BufferedImage.TYPE_INT_RGB);var offsetGraphics=offset.createGraphics();offsetGraphics.drawImage(im,37,51,null);offsetGraphics.dispose();
  var shifted=BubbleVision.read(offset::getRGB,420,760,37,51,357,691,0,0,0,0,r.board());
  check(shifted.usable()&&shifted.board().same(r.board()),"nonzero source ROI offset preserves local bubble coordinates");
  check(!BubbleVision.read(im::getRGB,320,640,0,0,320,640,10,10,40,40,r.board()).usable(),"overlay blocks perception");
  var g=im.createGraphics();g.setColor(new java.awt.Color(0x3040df));g.fillOval(26-23,80-23,46,46);g.dispose();
  check(!BubbleVision.read(im::getRGB,320,640,0,0,320,640,0,0,0,0,r.board()).usable(),"unsupported blue bubble is not empty sky");
  for(int special:new int[]{0x202020,0xffffff}){
   BufferedImage symbol=synthetic();var brush=symbol.createGraphics();brush.setColor(new java.awt.Color(special));brush.fillOval(26-7,80-7,14,14);brush.dispose();
   check(!BubbleVision.read(symbol::getRGB,320,640,0,0,320,640,0,0,0,0,r.board()).usable(),"contrast center symbol is not an ordinary candy ring");
  }
  BufferedImage mixed=synthetic();var brush=mixed.createGraphics();brush.setClip(new java.awt.geom.Ellipse2D.Float(3,57,46,46));brush.setColor(new java.awt.Color(COLORS[1]));brush.fillRect(3,57,23,46);brush.setColor(new java.awt.Color(COLORS[4]));brush.fillRect(26,57,23,46);brush.dispose();
  check(!BubbleVision.read(mixed::getRGB,320,640,0,0,320,640,0,0,0,0,r.board()).usable(),"overlapping mixed-color animation is rejected");
  try{BubbleVision.read((x,y)->0, Integer.MAX_VALUE,640,0,0,Integer.MAX_VALUE,640,0,0,0,0,null);throw new AssertionError("overflow geometry accepted");}catch(IllegalArgumentException expected){checks++;}
  check(!BubbleVision.read((x,y)->0x202020,320,640,0,0,320,640,0,0,0,0,null).usable(),"blank screen cannot authorize a shot");
  BufferedImage emptyGun=synthetic();var reserve=emptyGun.createGraphics();reserve.setColor(new java.awt.Color(COLORS[0]));reserve.fillRect(136,576,48,48);reserve.setColor(new java.awt.Color(COLORS[3]));reserve.fillOval(101,577,46,46);reserve.dispose();
  check(!BubbleVision.read(emptyGun::getRGB,320,640,0,0,320,640,0,0,0,0,null).usable(),"a spare ball near the gun cannot replace the missing loaded ball");
 }
 private static void specialProjectileFailClosed(){
  // A dark/white center is a potential bomb or another unsupported loaded
  // projectile, not a normal candy. Until BulletType has a verified visual
  // contract, neither initial acquisition nor tracked frames may authorize it.
  BufferedImage normal=synthetic();
  BubbleVision.Read known=BubbleVision.read(normal::getRGB,320,640,0,0,320,640,0,0,0,0,null);
  check(known.usable(),"ordinary launcher establishes a valid baseline");
  BubbleBoard stable=known.board();
  for(int marker:new int[]{0x202020,0xffffff,0x3040df}){
   BufferedImage special=synthetic();
   var graphics=special.createGraphics();
   graphics.setColor(new java.awt.Color(marker));
   if(marker==0x3040df)graphics.fillOval(160-23,600-23,46,46);
   else graphics.fillOval(160-8,600-8,16,16);
   graphics.dispose();
   BubbleVision.Read tracked=BubbleVision.read(special::getRGB,320,640,0,0,320,640,0,0,0,0,stable);
   BubbleVision.Read cold=BubbleVision.read(special::getRGB,320,640,0,0,320,640,0,0,0,0,null);
   check(!tracked.usable()&&tracked.board()==null,"special loaded projectile rejects tracked reading: "+Integer.toHexString(marker));
   check(!cold.usable()&&cold.board()==null,"special loaded projectile cannot reuse reserve on cold acquisition: "+Integer.toHexString(marker));
   BubbleSession session=new BubbleSession();
   check(session.observe(stable,1000,1000)==BubbleSession.Status.SEARCHING,"first ordinary frame is not a gesture permit");
   check(session.observe(stable,1200,1200)==BubbleSession.Status.READY,"ordinary baseline stable");
   check(session.observe(tracked.board(),1400,1400)==BubbleSession.Status.SEARCHING
     &&session.observe(tracked.board(),1600,1600)==BubbleSession.Status.SEARCHING
     &&session.verified()==0&&!session.pending(),
     "unsupported loaded projectile revokes READY without predicting success");
   BubbleVision.Read recovered=BubbleVision.read(normal::getRGB,320,640,0,0,320,640,0,0,0,0,stable);
   check(recovered.usable()&&recovered.board().same(stable),"fresh ordinary launcher remains readable after rejected special");
   check(session.observe(recovered.board(),1800,1800)==BubbleSession.Status.SEARCHING
     &&session.observe(recovered.board(),2000,2000)==BubbleSession.Status.READY,
     "two fresh ordinary frames required before another plan can be considered");
  }
 }
 private static void recorded(Path path)throws Exception{
  BufferedImage original=ImageIO.read(path.toFile());if(original==null)throw new IllegalArgumentException("image");
  for(float scale:new float[]{.75f,1f,1.5f}){
   int w=Math.round(original.getWidth()*scale),h=Math.round(original.getHeight()*scale);
   BufferedImage im=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(original,0,0,w,h,null);g.dispose();
   long coldAt=System.nanoTime();var r=BubbleVision.read(im::getRGB,w,h,0,0,w,h,0,0,0,0,null);long coldNs=System.nanoTime()-coldAt;
   check(r.usable(),"recorded screenshot scale="+scale+": "+r.reason());BubbleBoard b=r.board();
   check(b.count()==48&&b.loadedColor==3,"48 manually counted candy bubbles and yellow launcher");
   check(Math.abs(b.x0/scale-35)<5&&Math.abs(b.y0/scale-171)<5&&Math.abs(b.step/scale-56)<3,"recorded geometry matches manual centers");
   check(BubblePolicy.choose(b)!=null,"a direct ordinary shot is possible on recorded board");
   int[][] manual={{3,3,3,3,3,2,3,3,3,3},{4,4,4,4,2,2,1,1,1,0},{4,4,4,4,2,2,2,2,2,4},{1,1,1,1,4,4,4,4,4,0},{2,2,2,2,1,1,1,3,1,2}};
   check(b.cols==10,"recorded row width is ten");
   for(int row=0;row<b.rows;row++)for(int col=0;col<b.cols;col++)check(b.color(row*b.cols+col)==(row<manual.length?manual[row][col]:0),"recorded manually annotated color at "+row+","+col);
   long[] elapsed=new long[8];
   for(int i=0;i<elapsed.length;i++){long at=System.nanoTime();var again=BubbleVision.read(im::getRGB,w,h,0,0,w,h,0,0,0,0,b);elapsed[i]=System.nanoTime()-at;check(again.usable()&&again.board().same(b),"cached source geometry reads identical colors");}
   Arrays.sort(elapsed);
   long policyAt=System.nanoTime();BubblePolicy.Shot shot=BubblePolicy.choose(b);long policyNs=System.nanoTime()-policyAt;
   check(shot!=null&&shot.removed==0&&shot.dropped==0,"recorded yellow shot builds a pair; no imaginary immediate yellow triple");
   System.out.printf(java.util.Locale.ROOT,"Recorded bubble frame scale=%.2f count=%d x0=%.2f y0=%.2f step=%.2f; cloud JVM acquire=%.2fms cached-p50=%.2fms cached-max=%.2fms policy=%.2fms%n",scale,b.count(),b.x0/scale,b.y0/scale,b.step/scale,coldNs/1e6,elapsed[4]/1e6,elapsed[7]/1e6,policyNs/1e6);
  }
 }
 public static void main(String[] args)throws Exception{geometryAndPolicy();singleRemaining();directShotGeometry();session();lateVerification();diagnostics();perception();specialProjectileFailClosed();reversedStaggerFixture();if(args.length>0)recorded(Path.of(args[0]));System.out.println("Bubble board/policy/feedback: "+checks+" assertions PASS; no physical-device claim");}
}
