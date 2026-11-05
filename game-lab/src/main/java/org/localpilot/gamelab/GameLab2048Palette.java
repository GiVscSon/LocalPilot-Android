package org.localpilot.gamelab;

/**
 * Exact paint used by LocalPilot's strict visible 2048 reader. The test game
 * does not supply its hidden tile values to PilotService.
 */
public final class GameLab2048Palette {
 private GameLab2048Palette(){}
 private static final int[] RGB={
   0xcdc1b4,0xeee4da,0xede0c8,0xf2b179,0xf59563,0xf67c5f,
   0xf65e3b,0xedcf72,0xedcc61,0xedc850,0xedc53f,0xedc22e
 };
 public static int paint(int tile){
  if(tile==0)return 0xff000000|RGB[0];
  if(tile<2||tile>2048||(tile&(tile-1))!=0)
   throw new IllegalArgumentException("Unsupported 2048 tile for strict visual reader");
  int index=Integer.numberOfTrailingZeros(tile);
  return 0xff000000|RGB[index];
 }
 public static int[] colors(){return RGB.clone();}
}
