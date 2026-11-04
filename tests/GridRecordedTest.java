package org.localpilot.android;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Optional private replay: GridVerify <octopus-6x6.jpg> <match-7x7.jpg>.
 * Images stay outside source/release archives; annotations describe token identity only. */
public final class GridRecordedTest {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  if(args.length==0)return;
  if(args.length!=2)throw new IllegalArgumentException("two recorded grid images required");
  int[] octopus={1,2,3,4,5,6, 6,3,2,6,7,4, 0,2,5,8,0,6, 4,2,4,8,3,5, 7,5,5,3,5,4, 6,1,2,0,8,8};
  int[] match={1,2,3,2,2,3,3, 2,2,1,3,2,2,3, 1,4,3,2,1,4,4, 1,2,1,4,3,3,4, 3,4,2,3,3,2,1, 1,3,3,1,2,2,3, 2,4,2,2,3,1,4};
  replay(Path.of(args[0]),6,42,581,534,1068,octopus,true);
  replay(Path.of(args[1]),7,18,372,558,913,match,false);
  System.out.println("Recorded grid replay: "+checks+" assertions PASS; two private screenshots at three scales, no gestures dispatched");
 }
 private static void replay(Path file,int n,int left,int top,int right,int bottom,int[] expected,boolean skulls)throws Exception{
  BufferedImage original=ImageIO.read(file.toFile());
  if(original==null||original.getWidth()!=576||original.getHeight()!=1280)throw new IllegalArgumentException("unexpected recorded fixture geometry");
  for(float scale:new float[]{.75f,1,1.5f}){
   BufferedImage im=new BufferedImage(Math.round(original.getWidth()*scale),Math.round(original.getHeight()*scale),BufferedImage.TYPE_INT_RGB);
   var g=im.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(original,0,0,im.getWidth(),im.getHeight(),null);g.dispose();
   int l=Math.round(left*scale),t=Math.round(top*scale),r=Math.round(right*scale),b=Math.round(bottom*scale);
   var seen=GridPuzzleVision.detect(im::getRGB,l,t,r,b,n,n,0,0,0,0,1000,null,skulls);
   check(seen.usable()&&seen.complete(),"recorded board usable at "+n+"x"+n+" scale "+scale);
   int[] actual=seen.cells();
   for(int i=0;i<expected.length;i++){
    if(expected[i]==0)check(actual[i]==0&&seen.specialCellsMask().get(i),"recorded skull protected at "+i);
    else check(actual[i]>0&&!seen.specialCellsMask().get(i),"ordinary token recognized at "+i);
    for(int j=0;j<i;j++)if(actual[i]>0&&actual[i]==actual[j])check(expected[i]==expected[j],"different objects must never become the same token");
   }
   var next=GridPuzzleVision.detect(im::getRGB,l,t,r,b,n,n,0,0,0,0,1100,seen.palette(),skulls);
   GridPuzzleSession session=new GridPuzzleSession();session.observe(n,n,actual,seen.specialCellsMask(),1000,1000);
   check(next.usable()&&session.observe(n,n,next.cells(),next.specialCellsMask(),1100,1100)==GridPuzzleSession.Status.READY,"private screenshot remains stable with persisted palette");
   var d=GridPuzzlePolicy.choose(new GridPuzzle(n,n,actual),GridPuzzlePolicy.Mode.SWAP_MATCH3,3,0,0,seen.specialCellsMask());
   check(d!=null,"recorded board has a safe immediate match");
   int from=d.fromRow*n+d.fromCol,to=d.toRow*n+d.toCol;int[] moved=expected.clone();
   check(moved[from]>0&&moved[to]>0,"action endpoints avoid all skulls");
   int v=moved[from];moved[from]=moved[to];moved[to]=v;
   for(int i:d.clearedCells())check(moved[i]>0&&line(moved,n,i),"proposed clear agrees with manually annotated objects");
   var hidden=GridPuzzleVision.detect(im::getRGB,l,t,r,b,n,n,l,t,l+(r-l)/n,t+(b-t)/n,1200,seen.palette(),skulls);
   check(!hidden.usable(),"panel occlusion still blocks recorded board");
  }
 }
 private static boolean line(int[] values,int n,int cell){
  for(int axis=0;axis<2;axis++){
   int dr=axis==0?0:1,dc=axis==0?1:0,count=1;
   for(int sign:new int[]{-1,1})for(int step=1;;step++){
    int r=cell/n+dr*step*sign,c=cell%n+dc*step*sign;
    if(r<0||r>=n||c<0||c>=n||values[r*n+c]!=values[cell])break;count++;
   }
   if(count>=3)return true;
  }
  return false;
 }
}
