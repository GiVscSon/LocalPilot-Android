package org.localpilot.android;

/** A reusable top-colour observation. UNKNOWN never means an empty playable slot. */
public final class HexaBoard {
 public static final int UNKNOWN=-1,EMPTY=0,BLOCKED=-2;
 public final HexaConfig config;
 private final int[] cells=new int[64],hand=new int[3];
 HexaBoard(HexaConfig config){if(config==null)throw new IllegalArgumentException("hexa config");this.config=config;clear();}
 public HexaBoard(HexaConfig config,int[] cells,int[] hand){this(config);set(cells,hand);}
 void clear(){for(int i=0;i<64;i++)cells[i]=config.active(i)?UNKNOWN:BLOCKED;for(int i=0;i<3;i++)hand[i]=UNKNOWN;}
 void cell(int index,int value){cells[index]=value;}
 void hand(int index,int value){hand[index]=value;}
 private void set(int[] values,int[] hands){
  if(values==null||values.length!=config.size()||hands==null||hands.length!=3)throw new IllegalArgumentException("hexa observation");
  for(int i=0;i<config.size();i++){if(values[i]<BLOCKED||values[i]>16||!config.active(i)&&values[i]!=BLOCKED||config.active(i)&&values[i]==BLOCKED)throw new IllegalArgumentException("hexa cell value");cells[i]=values[i];}
  for(int i=0;i<3;i++){if(hands[i]<UNKNOWN||hands[i]>16)throw new IllegalArgumentException("hexa hand colour");hand[i]=hands[i];}
 }
 public int size(){return config.size();}
 public int colorAt(int index){if(index<0||index>=size())throw new IllegalArgumentException("hexa cell");return cells[index];}
 public int colorAt(int row,int col){if(row<0||row>=config.rows||col<0||col>=config.cols)throw new IllegalArgumentException("hexa cell");return cells[row*config.cols+col];}
 public int handColor(int slot){if(slot<0||slot>=3)throw new IllegalArgumentException("hexa hand");return hand[slot];}
 public boolean usable(){for(int i=0;i<size();i++)if(config.active(i)&&cells[i]<EMPTY)return false;return true;}
 public int occupied(){int n=0;for(int i=0;i<size();i++)if(cells[i]>0)n++;return n;}
 public int empty(){int n=0;for(int i=0;i<size();i++)if(config.active(i)&&cells[i]==EMPTY)n++;return n;}
 public int matchingNeighbors(int index,int color){int n=0;for(int i=0;i<size();i++)if(cells[i]==color&&config.neighbors(index,i))n++;return n;}
 public long signature(){long h=config.signature();for(int i=0;i<size();i++)h=HexaConfig.mix(h,cells[i]);for(int i=0;i<3;i++)h=HexaConfig.mix(h,hand[i]);return h;}
 public void copyCells(int[] out){if(out==null||out.length<size())throw new IllegalArgumentException("hexa copy");System.arraycopy(cells,0,out,0,size());}
 public void copyHand(int[] out){if(out==null||out.length<3)throw new IllegalArgumentException("hexa copy");System.arraycopy(hand,0,out,0,3);}
}
