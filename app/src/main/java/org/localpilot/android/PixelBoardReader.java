package org.localpilot.android;
/** Strict known-palette reader. UNKNOWN rather than OCR guesses or partial boards. */
public final class PixelBoardReader {
 public interface Pixels {int rgb(int x,int y);}
 public static Board2048 read(Pixels pixels,int width,int height,int left,int top,int right,int bottom){
  if(left<0||top<0||right>width||bottom>height||right-left<160||bottom-top<160||Math.abs((right-left)-(bottom-top))>Math.min(right-left,bottom-top)*.08)throw new IllegalArgumentException("Выделите квадратное поле 4×4 без заголовка");
  int[] result=new int[16];double[] offsets={.23,.29,.35};for(int r=0;r<4;r++)for(int c=0;c<4;c++){int found=-2;for(double t:offsets){int x=left+(int)((c+t)*(right-left)/4.0),y=top+(int)((r+.24)*(bottom-top)/4.0);int tile=Board2048.colorTile(pixels.rgb(x,y));if(tile<0||found!=-2&&tile!=found)throw new IllegalArgumentException("Неизвестный цвет/анимация: клетка "+(r+1)+","+(c+1));found=tile;}result[r*4+c]=found;}Board2048 b=new Board2048(result);if(b.max()==0)throw new IllegalArgumentException("Пустое поле не подтверждено");return b;
 }
}
