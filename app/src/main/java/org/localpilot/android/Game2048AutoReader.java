package org.localpilot.android;

/** A fail-closed board reader: a known Market glass 2048 board is not the legacy palette. */
public final class Game2048AutoReader {
 private Game2048AutoReader(){}
 public record Result(Board2048 board,boolean colored,boolean switched) {}
 /**
  * Switch only during the first read of a specifically selected 2048 game.
  * The fallback itself must fully classify 16 cells, and PilotService still
  * needs two independent stable reads before dispatch. A foreign color or
  * mid-gesture transition is never admitted as a board.
  */
 public static Result read(PixelBoardReader.Pixels pixels,int width,int height,
   int left,int top,int right,int bottom,Color2048Reader.Session colorSession,
   boolean colored,boolean firstReadMarketFallback,Board2048 before,
   int action,long frameAt){
  if(pixels==null||colorSession==null)throw new IllegalArgumentException("board reader");
  if(colored)
   return new Result(colorSession.read(pixels,width,height,left,top,right,bottom,before,action,frameAt),true,false);
  try{
   return new Result(PixelBoardReader.read(pixels,width,height,left,top,right,bottom),false,false);
  }catch(IllegalArgumentException legacyFailure){
   if(!firstReadMarketFallback||before!=null)throw legacyFailure;
   try{
    // No guessed rank, synthetic tile, or softening of UNKNOWN is allowed.
    Board2048 glass=colorSession.read(pixels,width,height,left,top,right,bottom,
      null,action,frameAt);
    return new Result(glass,true,true);
   }catch(IllegalArgumentException glassFailure){
    // Keep the original strict reader's rejection; both classifiers failed.
    throw legacyFailure;
   }
  }
 }
}
