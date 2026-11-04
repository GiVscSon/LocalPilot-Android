package org.localpilot.android;
public class GameViewportTest {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  GameViewport.Box all=new GameViewport.Box(0,0,720,480);
  PixelBoardReader.Pixels scene=(x,y)->x<200||x>=520?0x245888:((y/20+x/20)%2==0?0xf28c00:0xeeee99);
  GameViewport.Box found=GameViewport.detect(scene,all,null);
  check(found.left()==200&&found.right()==520&&found.top()==0&&found.bottom()==480,"pillarboxes cropped");
  PixelBoardReader.Pixels panel=(x,y)->x<190&&y<380?((y/8)%2==0?0xffffff:0x202830):scene.rgb(x,y);
  check(GameViewport.detect(panel,all,new GameViewport.Box(0,0,190,380)).equals(found),"overlay excluded from sidebar sampling");
  check(GameViewport.detect((x,y)->0,all,null).equals(all),"black frame not invented as game");
  check(GameViewport.detect((x,y)->0xffffff,all,null).equals(all),"white frame unchanged");
  check(GameViewport.detect((x,y)->x<200?0x245888:x>=520?0x222222:scene.rgb(x,y),all,null).equals(all),"different edges not assumed letterbox");
  check(GameViewport.detect((x,y)->((x+y)%3==0?0x111111:0xffffff),all,null).equals(all),"textured full screen retained");
  GameViewport.Box window=new GameViewport.Box(100,50,600,450);
  check(GameViewport.selection(window,200,100,450,400).equals(new GameViewport.Box(200,100,450,400)),"portrait rectangle accepted");
  check(GameViewport.selection(window,450,400,200,100).equals(new GameViewport.Box(200,100,450,400)),"reverse drag accepted");
  for(int[] b:new int[][]{{0,0,400,400},{110,60,120,70},{200,100,200,300},{100,50,601,450}}){try{GameViewport.selection(window,b[0],b[1],b[2],b[3]);throw new AssertionError("bad selection accepted");}catch(IllegalArgumentException expected){checks++;}}
  for(int shift:new int[]{0,30,100}){
   GameViewport.Box b=new GameViewport.Box(shift,0,shift+720,480);
   GameViewport.Box c=GameViewport.detect((x,y)->scene.rgb(x-shift,y),b,null);
   check(c.left()==shift+200&&c.right()==shift+520,"window offset preserved");
  }
  System.out.println("Game viewport: "+checks+" assertions PASS");
 }
}
