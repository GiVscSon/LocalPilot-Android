package org.localpilot.gamelab;

/**
 * Exact known, non-learned classic 2048 colors shared by the GameLab renderer.
 * Production's PixelBoardReader must read these actual RGB values without
 * guessing tile ranks. This is a separate synthetic training skin, NOT a
 * substitute for the observed Yandex Market glass palette.
 */
public final class GameLab2048Palette {
 public static final int MAX_TILE=2048;
 private static final int[] RGB={
  0xcdc1b4, // empty
  0xeee4da, // 2
  0xede0c8, // 4
  0xf2b179, // 8
  0xf59563, // 16
  0xf67c5f, // 32
  0xf65e3b, // 64
  0xedcf72, // 128
  0xedcc61, // 256
  0xedc850, // 512
  0xedc53f, // 1024
  0xedc22e  // 2048
 };
 private GameLab2048Palette(){}
 public static int rgb(int value){
  if(value<0||value>MAX_TILE||
    value!=0&&(value<2||(value&(value-1))!=0))
   throw new IllegalArgumentException("unsupported 2048 tile "+value);
  return RGB[value==0?0:Integer.numberOfTrailingZeros(value)];
 }
 public static int[] knownRgb(){return RGB.clone();}
}
