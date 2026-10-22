package org.localpilot.android;

/**
 * Experimental reader for the colorful 4x4 "2048" skin shown in the user's
 * recordings. The current calibrated palette covers only 2, 4, 8 and 16;
 * unknown higher tiles fail closed instead of being guessed.
 */
public final class Color2048Reader {
 private static final int[][] COLORS={
   {245,100,0},  // 2: orange
   {249,40,190}, // 4: pink
   {192,42,100}, // 8: rose
   {53,38,140}   // 16: blue
 };
 private static final int[] VALUES={2,4,8,16};

 private Color2048Reader(){}

 public static Board2048 read(PixelBoardReader.Pixels pixels,int width,int height,
   int left,int top,int right,int bottom){
  if(pixels==null||left<0||top<0||right>width||bottom>height
    ||right-left<160||bottom-top<160
    ||Math.abs((right-left)-(bottom-top))>Math.min(right-left,bottom-top)*.08)
   throw new IllegalArgumentException("Выделите квадратное цветное поле 4×4 без заголовка");

  int[] cells=new int[16];
  double[] xOffsets={.23,.29,.35};
  for(int row=0;row<4;row++)for(int col=0;col<4;col++){
   int red=0,green=0,blue=0;
   for(double xOffset:xOffsets){
    int x=left+(int)((col+xOffset)*(right-left)/4.0);
    int y=top+(int)((row+.24)*(bottom-top)/4.0);
    int rgb=pixels.rgb(x,y);
    red+=(rgb>>16)&255;green+=(rgb>>8)&255;blue+=rgb&255;
   }
   int r=red/xOffsets.length,g=green/xOffsets.length,b=blue/xOffsets.length;
   cells[row*4+col]=classify(r,g,b,row,col);
  }
  Board2048 board=new Board2048(cells);
  if(board.max()==0)throw new IllegalArgumentException("Плитки не распознаны");
  return board;
 }

 private static int classify(int r,int g,int b,int row,int col){
  int low=Math.min(r,Math.min(g,b)),high=Math.max(r,Math.max(g,b));
  // Empty cells in this skin are pale, low-saturation glass over the scene.
  if(high-low<=42&&r>=195&&g>=175&&b>=185)return 0;

  int best=-1,bestDistance=Integer.MAX_VALUE,secondDistance=Integer.MAX_VALUE;
  for(int i=0;i<COLORS.length;i++){
   int dr=r-COLORS[i][0],dg=g-COLORS[i][1],db=b-COLORS[i][2];
   int distance=dr*dr+dg*dg+db*db;
   if(distance<bestDistance){secondDistance=bestDistance;bestDistance=distance;best=i;}
   else if(distance<secondDistance)secondDistance=distance;
  }
  // Gradients and texture shift the samples, but the four known colours remain
  // well separated. A close tie or a distant colour is UNKNOWN.
  if(best<0||bestDistance>85*85||secondDistance-bestDistance<18*18)
   throw new IllegalArgumentException("Неизвестная плитка в клетке "+(row+1)+","+(col+1)
     +" · цветной профиль пока распознаёт только 2, 4, 8 и 16");
  return VALUES[best];
 }
}
