package org.localpilot.android;

/** Native six-way HexaSort placement. Only recognised hand stacks and proven empty cells are eligible. */
public final class HexaPolicy {
 public static final class Move {
  public final int handSlot,targetRow,targetCol,color,durationMs,matchingNeighbors;
  public final float fromX,fromY,toX,toY;
  public final boolean fillQuest;
  public final long boardSignature,geometrySignature;
  private Move(HexaBoard board,int slot,int target,boolean quest,int neighbors){
   HexaConfig c=board.config;handSlot=slot;targetRow=target/c.cols;targetCol=target%c.cols;color=board.handColor(slot);
   fromX=c.slotX(slot);fromY=c.slotY(slot);toX=c.cellCenterX(targetRow,targetCol);toY=c.cellCenterY(targetRow,targetCol);
   durationMs=420;matchingNeighbors=neighbors;fillQuest=quest;boardSignature=board.signature();geometrySignature=c.signature();
  }
  public int targetIndex(int cols){return targetRow*cols+targetCol;}
 }
 private HexaPolicy(){}
 public static Move choose(HexaBoard board,boolean fillQuest){
  if(board==null||!board.usable())return null;
  int bestSlot=-1,bestTarget=-1,bestNeighbors=-1;float best=-Float.MAX_VALUE;
  for(int s=0;s<3;s++){
   int color=board.handColor(s);if(color<=0)continue;
   for(int i=0;i<board.size();i++)if(board.config.active(i)&&board.colorAt(i)==HexaBoard.EMPTY){
    int count=board.matchingNeighbors(i,color);
    if(fillQuest?count!=0:count==0)continue;
    int adjacent=0;for(int j=0;j<board.size();j++)if(board.config.neighbors(i,j)&&board.colorAt(j)>0)adjacent++;
    float score=fillQuest?-adjacent*10f:count*100f+adjacent;
    score-=i*.001f+s*.00001f;
    if(score>best){best=score;bestSlot=s;bestTarget=i;bestNeighbors=count;}
   }
  }
  return bestSlot<0?null:new Move(board,bestSlot,bestTarget,fillQuest,bestNeighbors);
 }
}
