package org.localpilot.android;
/** Strict known-palette reader. UNKNOWN rather than OCR guesses or partial boards. */
public final class PixelBoardReader {
 /** Explicit observed website skin; never inferred from UNKNOWN pixels. */
 public enum Skin { ORIGINAL(0xcdc1b4), CLASSIC_DARK_EMPTY(0xb8aa9c);
  final int empty;Skin(int rgb){empty=rgb;}
 }
 public interface Pixels {int rgb(int x,int y);}
 public static Board2048 read(Pixels pixels,int width,int height,int left,int top,int right,int bottom){
  return read(pixels,width,height,left,top,right,bottom,Skin.ORIGINAL);
 }
 public static Board2048 read(Pixels pixels,int width,int height,int left,int top,int right,int bottom,Skin skin){
  if(skin==null)throw new IllegalArgumentException("2048 skin required");
  if(left<0||top<0||right>width||bottom>height||right-left<160||bottom-top<160||Math.abs((right-left)-(bottom-top))>Math.min(right-left,bottom-top)*.08)throw new IllegalArgumentException("Выделите квадратное поле 4×4 без заголовка");
  int[] result=new int[16];double[] offsets={.23,.29,.35};for(int r=0;r<4;r++)for(int c=0;c<4;c++){int found=-2;for(double t:offsets){int x=left+(int)((c+t)*(right-left)/4.0),y=top+(int)((r+.24)*(bottom-top)/4.0);int rgb=pixels.rgb(x,y),tile=Board2048.colorTile(rgb);
   if(tile<0&&skin==Skin.CLASSIC_DARK_EMPTY&&near(rgb,skin.empty))tile=0;
   if(tile<0||found!=-2&&tile!=found)throw new IllegalArgumentException("Неизвестный цвет/анимация: клетка "+(r+1)+","+(c+1));found=tile;}result[r*4+c]=found;}Board2048 b=new Board2048(result);if(b.max()==0)throw new IllegalArgumentException("Пустое поле не подтверждено");return b;
 }
 private static boolean near(int a,int b){for(int shift:new int[]{0,8,16})if(Math.abs((a>>shift&255)-(b>>shift&255))>8)return false;return true;}
}
