package org.localpilot.android;

import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Independent hex-distance blast and rendered loaded-bomb fixtures. */
public final class BubbleBombTest {
 private static int checks;
 private static final int W=320,H=640;
 private static final int[] COLORS={0x87ceeb,0xe52c32,0xf08016,0xf0cf20,0x4ab63b};
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static int[] field(){
  int[] cells=new int[72];for(int row=0;row<4;row++)for(int col=0;col<6-row%2;col++)cells[row*6+col]=1+(row*3+col)%4;
  return cells;
 }
 private static BubbleBoard bomb(int[] cells){return new BubbleBoard(W,H,6,12,32,80,50,160,600,0,cells,1,BubbleBoard.Projectile.BOMB);}
 private static int hexDistance(BubbleBoard b,int a,int c){
  int ar=a/b.cols,cr=c/b.cols;
  int aq=a%b.cols-(b.shortRowParity==1?(ar-(ar&1))/2:(ar+(ar&1))/2);
  int cq=c%b.cols-(b.shortRowParity==1?(cr-(cr&1))/2:(cr+(cr&1))/2);
  int dx=aq-cq,dy=ar-cr;return Math.max(Math.abs(dx),Math.max(Math.abs(dy),Math.abs(dx+dy)));
 }
 private static int[] independentResult(BubbleBoard b,int impact){
  int[] result=b.cells();for(int i=0;i<result.length;i++)if(hexDistance(b,i,impact)<=2)result[i]=0;
  boolean[] attached=new boolean[result.length];for(int i=0;i<b.cols;i++)attached[i]=result[i]>0;
  boolean changed=true;while(changed){changed=false;for(int i=0;i<result.length;i++)if(result[i]>0&&!attached[i])
   for(int j=0;j<result.length;j++)if(attached[j]&&hexDistance(b,i,j)==1){attached[i]=true;changed=true;break;}}
  for(int i=0;i<result.length;i++)if(!attached[i])result[i]=0;return result;
 }
 private static void policyAndProof(){
  BubbleBoard board=bomb(field());BubblePolicy.Shot shot=BubblePolicy.choose(board);
  check(shot!=null&&shot.projectile==BubbleBoard.Projectile.BOMB,"A loaded bomb produces a typed plan");
  check(board.color(shot.landing)>0,"Bomb detonates on an occupied impact cell instead of inventing a wildcard cell");
  check(Arrays.equals(independentResult(board,shot.landing),shot.expectedCells()),"Cube-distance radius-two blast plus independently computed ceiling drop agrees");
  int sourceRemoved=0,colorMask=0;for(int i=0;i<board.size();i++)if(board.color(i)>0&&hexDistance(board,i,shot.landing)<=2){sourceRemoved++;colorMask|=1<<board.color(i);}
  check(shot.removed==sourceRemoved&&Integer.bitCount(colorMask)>1,"Bomb removes actual source bubbles of several colors");
  int remaining=0;for(int color:shot.expectedCells())if(color>0)remaining++;
  check(remaining==board.count()-shot.removed-shot.dropped&&remaining<board.count(),"Bomb is consumed, not inserted as a colored bubble");
  int[] expected=shot.expectedCells();BubbleBoard next=new BubbleBoard(W,H,6,12,32,80,50,160,600,2,expected);
  BubbleSession session=new BubbleSession();session.observe(board,1000,1000);session.observe(board,1200,1200);session.sent(board,shot,1200);
  session.observe(next,1800,1800);session.observe(next,2000,2000);
  check(session.verified()==0&&session.verifiedBombs()==0,"Cluster reduction without ACK cannot verify a bomb");
  session.acknowledge(2001);session.observe(next,2200,2200);
  check(session.observe(next,2400,2400)==BubbleSession.Status.VERIFIED&&session.verified()==1&&session.verifiedBombs()==1,"Two stable fresh exact post-ACK results verify one bomb");
  session.observe(next,2600,2600);check(session.verifiedBombs()==1,"Repeated result is not another bomb reward");
  session.reset();session.observe(board,3000,3000);session.observe(board,3200,3200);session.sent(board,shot,3200);session.acknowledge(3400);
  BubbleBoard unchanged=new BubbleBoard(W,H,6,12,32,80,50,160,600,2,board.cells());session.observe(unchanged,3800,3800);session.observe(unchanged,4000,4000);
  check(session.pending()&&session.verifiedBombs()==0&&session.lastWait()==BubbleSession.Reason.CELLS_MISMATCH,"Loaded color change without target destruction is not success");
  int[] wrong=expected.clone();int keep=-1;for(int i=0;i<board.size();i++)if(board.color(i)>0&&expected[i]==0){keep=i;break;}wrong[keep]=board.color(keep);
  BubbleBoard partial=new BubbleBoard(W,H,6,12,32,80,50,160,600,2,wrong);session.observe(partial,4200,4200);session.observe(partial,4400,4400);
  check(session.pending()&&session.verifiedBombs()==0,"Partial or unrelated reduction does not satisfy the exact predicted cluster");
  int[] last=new int[72];last[2]=4;check(BubblePolicy.choose(bomb(last))!=null,"Bomb can target the final bubble regardless of ordinary color");
  try{new BubbleBoard(W,H,6,12,32,80,50,160,600,0,field());throw new AssertionError("legacy wildcard accepted");}catch(IllegalArgumentException expectedError){checks++;}
  try{new BubbleBoard(W,H,6,12,32,80,50,160,600,1,field(),1,BubbleBoard.Projectile.BOMB);throw new AssertionError("colored bomb accepted");}catch(IllegalArgumentException expectedError){checks++;}
 }
 private static BufferedImage image(int[] cells,boolean special,boolean rectangle,float brightness){
  BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);var brush=image.createGraphics();
  brush.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
  brush.setColor(new java.awt.Color(shade(COLORS[0],brightness)));brush.fillRect(0,0,W,H);
  for(int i=0;i<cells.length;i++)if(cells[i]>0){int row=i/6,col=i%6;float cx=32+(col+(row&1)*.5f)*50,cy=80+row*50*.8660254f;
   brush.setColor(new java.awt.Color(shade(COLORS[cells[i]],brightness)));brush.fill(new Ellipse2D.Float(cx-23,cy-23,46,46));}
  brush.dispose();
  for(int y=573;y<=627;y++)for(int x=133;x<=187;x++){
   double dx=x-160,dy=y-600;boolean inside=rectangle?Math.abs(dx)<=23&&Math.abs(dy)<=23:dx*dx+dy*dy<=23*23;
   if(!inside)continue;int color=COLORS[2];
   if(special){double angle=Math.atan2(dy,dx);if(angle<0)angle+=Math.PI*2;int sector=(int)Math.floor((angle+Math.PI/8)/(Math.PI/4))%8;color=COLORS[1+sector%4];if(dx*dx+dy*dy<9)color=0xffffff;}
   image.setRGB(x,y,shade(color,brightness));
  }
  return image;
 }
 private static int shade(int color,float factor){return Math.round((color>>16&255)*factor)<<16|Math.round((color>>8&255)*factor)<<8|Math.round((color&255)*factor);}
 private static void button(BufferedImage image,boolean enabled,float brightness){
  for(int y=566;y<=614;y++)for(int x=246;x<=294;x++){
   int dx=x-270,dy=y-590;if(dx*dx+dy*dy>18*18)continue;
   int color=0x777777;if(enabled){double angle=Math.atan2(dy,dx);if(angle<0)angle+=Math.PI*2;int sector=(int)Math.floor((angle+Math.PI/8)/(Math.PI/4))%8;color=COLORS[1+sector%4];if(dx*dx+dy*dy<9)color=0xffffff;}
   image.setRGB(x,y,shade(color,brightness));
  }
 }
 private static BubbleVision.Read read(BufferedImage image,BubbleBoard previous,BubbleVision.BombCalibration calibration){return BubbleVision.read(image::getRGB,W,H,0,0,W,H,0,0,0,0,previous,true,Float.NaN,calibration);}
 private static void calibratedVision(){
  int[] cells=field();BufferedImage special=image(cells,true,false,1);BubbleBoard tracked=bomb(cells);
  BubbleVision.BombCalibration calibration=BubbleVision.BombCalibration.capture(special::getRGB,W,H,0,0,W,H,160,600,46);
  check(calibration.validFor(W,H)&&!calibration.validFor(W,500),"Bomb calibration is scoped to unchanged ROI aspect");
  String encoded=calibration.encode();BubbleVision.BombCalibration restored=BubbleVision.BombCalibration.decode(encoded);
  check(restored.encode().equals(encoded),"Bomb calibration round-trips separately from legacy palettes and checkpoints");
  var result=read(special,tracked,restored);
  check(result.usable()&&result.board().candidateSpecial()&&result.board().loadedColor==0&&result.board().count()==tracked.count(),"Known gun geometry reads a calibrated loaded bomb without changing board colors");
  check(!read(special,tracked,null).usable(),"Uncalibrated multicolor launcher remains UNKNOWN");
  var initial=read(special,null,restored);
  check(initial.usable()&&initial.board().candidateSpecial(),"A calibrated launcher permits first-frame acquisition from independent ceiling anchors");
  var dim=read(image(cells,true,false,.72f),tracked,restored);
  check(dim.usable()&&dim.board().candidateSpecial(),"Normalized pattern comparison tolerates global brightness changes");
  var ordinary=image(cells,false,false,1);var ordinaryRead=read(ordinary,tracked,restored);
  check(ordinaryRead.usable()&&!ordinaryRead.board().candidateSpecial()&&ordinaryRead.board().loadedColor==2,"Ordinary cannon color is not promoted to a bomb");
  try{BubbleVision.BombCalibration.capture(ordinary::getRGB,W,H,0,0,W,H,160,600,46);throw new AssertionError("normal calibration accepted");}catch(IllegalArgumentException expected){checks++;}
  BufferedImage rectangle=image(cells,true,true,1);
  try{BubbleVision.BombCalibration.capture(rectangle::getRGB,W,H,0,0,W,H,160,600,46);throw new AssertionError("UI rectangle calibration accepted");}catch(IllegalArgumentException expected){checks++;}
  check(!read(rectangle,tracked,restored).usable(),"A rectangular multicolor popup in the gun region does not match a compact bomb");
  var missing=image(cells,false,false,1);for(int y=572;y<=628;y++)for(int x=132;x<=188;x++)missing.setRGB(x,y,COLORS[0]);
  check(!read(missing,tracked,restored).usable(),"Missing launcher is not synthesized by calibration");
  check(!BubbleVision.read(special::getRGB,W,H,0,0,W,H,130,570,190,630,tracked,true,Float.NaN,restored).usable(),"Overlay over the launcher still blocks all bomb input");
  BubbleSession session=new BubbleSession();session.observe(result.board(),1000,1000);
  check(session.observe(ordinaryRead.board(),1200,1200)==BubbleSession.Status.SEARCHING,"Switching projectile kind resets candidate stability");
  for(String invalid:new String[]{"",encoded.replace("bubble-bomb-v1","bubble-bomb-v2"),encoded+",ff",encoded.replaceFirst(",0\\.5,",",NaN,")}){
   try{BubbleVision.BombCalibration.decode(invalid);throw new AssertionError("invalid calibration accepted");}catch(IllegalArgumentException expected){checks++;}
  }
 }
 private static void calibratedBooster(){
  BufferedImage image=image(field(),false,false,1);button(image,true,1);
  BubbleBoosterCalibration calibration=BubbleBoosterCalibration.capture(image::getRGB,W,H,0,0,W,H,252,572,288,608);
  check(calibration.ready(image::getRGB,W,H,0,0,W,H),"Explicit ready-button signature matches its observed viewport");
  check(calibration.centerX(0,W)==270&&calibration.centerY(0,H)==590,"Booster coordinates remain screen-global rather than board-local");
  check(calibration.top(0,H)>500,"The permitted viewport can contain a booster below the board ROI");
  check(!calibration.ready(image::getRGB,W,H,0,0,W,H,250,570,290,610),"An overlay on the booster prevents activation proof");
  String text=calibration.encode();check(BubbleBoosterCalibration.decode(text).encode().equals(text),"Ready-button calibration round-trips in a separate versioned format");
  BufferedImage dim=image(field(),false,false,.72f);button(dim,true,.72f);
  check(calibration.ready(dim::getRGB,W,H,0,0,W,H),"Ready-button proof tolerates global brightness without trusting an uncalibrated orange point");
  BufferedImage disabled=image(field(),false,false,1);button(disabled,false,1);
  check(!calibration.ready(disabled::getRGB,W,H,0,0,W,H),"Greyed-out booster never matches charged state");
  try{BubbleBoosterCalibration.capture(disabled::getRGB,W,H,0,0,W,H,252,572,288,608);throw new AssertionError("disabled calibration accepted");}catch(IllegalArgumentException expected){checks++;}
  BubbleBoosterSession session=new BubbleBoosterSession();
  check(session.observe(true,false,1000,1000)==BubbleBoosterSession.Status.OBSERVING,"One charged-button frame cannot authorize a tap");
  check(session.observe(true,false,1100,1100)==BubbleBoosterSession.Status.READY,"Two distinct stable charged-button frames open preparation");
  long token=session.prepare(1100);check(session.pending()&&session.activationVerified()==0,"Preparation is neither dispatch nor successful loading");
  session.sent(token,1101);session.observe(false,true,1300,1300);
  check(session.pending()&&session.activationVerified()==0,"A bomb image before ACK cannot verify activation");
  session.acknowledge(token,1301);check(session.activationVerified()==0,"Android ACK is not loading proof");
  session.observe(false,true,1450,1450);check(session.activationVerified()==0,"One post-ACK bomb frame is insufficient");
  check(session.observe(false,true,1550,1550)==BubbleBoosterSession.Status.LOADED&&session.activationVerified()==1&&!session.pending(),"Two current calibrated bomb frames verify exactly one activation");
  session.observe(true,false,1800,1800);session.observe(true,false,2000,2000);
  check(session.status()==BubbleBoosterSession.Status.OBSERVING&&session.activationVerified()==1,"A still-visible charged icon cannot trigger an activation loop");
  check(new BubbleSession().verified()==0,"Booster activation never increments a shot-verification counter");
  session.reset();session.observe(true,false,3000,3000);session.observe(true,false,3100,3100);long rejected=session.prepare(3100);session.cancelUnsent(rejected);
  check(!session.pending()&&session.status()==BubbleBoosterSession.Status.OBSERVING,"Only unaccepted reservations can be cancelled");
  check(session.observe(true,false,3100,3100)==BubbleBoosterSession.Status.OBSERVING,"A repeated source frame cannot rearm a rejected preparation");
  session.observe(true,false,3300,3300);session.observe(true,false,3400,3400);long accepted=session.prepare(3400);session.sent(accepted,3401);
  try{session.cancelUnsent(accepted);throw new AssertionError("accepted activation cancelled");}catch(IllegalStateException expected){checks++;}
  session.acknowledge(rejected,3500);check(session.status()==BubbleBoosterSession.Status.WAIT_ACK,"An old reservation's ACK cannot release a newer pending activation");
  session.acknowledge(accepted,3501);session.observe(false,false,3800,3800);
  check(session.pending()&&session.activationVerified()==0,"Button disappearance alone does not prove a loaded bomb");
  check(session.observe(false,true,8400,8400)==BubbleBoosterSession.Status.PAUSED&&session.activationVerified()==0,"Absolute loading deadline wins even over a late bomb image");
  session.reset();session.observe(true,false,9000,9000);session.observe(true,false,9100,9100);long invalidated=session.prepare(9100);session.sent(invalidated,9101);session.acknowledge(invalidated,9200);session.invalidate();
  check(session.observe(false,true,9500,9500)==BubbleBoosterSession.Status.PAUSED&&session.activationVerified()==0,"Context loss cannot auto-resume a pending booster tap");
 }
 public static void main(String[] args){policyAndProof();calibratedVision();calibratedBooster();System.out.println("Calibrated Bubble bomb: "+checks+" assertions PASS; physical-game blast semantics require acceptance");}
}
