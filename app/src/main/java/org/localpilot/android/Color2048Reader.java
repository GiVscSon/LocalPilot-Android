package org.localpilot.android;

/**
 * Experimental reader for the colorful 4x4 "2048" skin shown in the user's
 * recordings. Known colors cover 2, 4, 8 and 16; higher ranks are learned only
 * from a deterministic merge in a visually validated move.
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

 /** Game-scoped palette. Candidates commit only after two distinct stable frames and full move validation. */
 public static final class Session {
  private final int[] learned=new int[12];
  private int[] pending;private Board2048 pendingBoard,pendingBefore;private int pendingAction,stableReads;
  private long pendingAt=-1;
  public Session(){reset();}
  public void reset(){java.util.Arrays.fill(learned,-1);discard();}
  /** A result/menu invalidates an in-flight merge, but not previously verified colors. */
  public void cancelPending(){discard();}
  private void discard(){pending=null;pendingBoard=pendingBefore=null;pendingAt=-1;stableReads=0;}
  public int learnedCount(){int n=0;for(int i=5;i<=11;i++)if(learned[i]>=0)n++;return n;}
  /** Bounded, versioned palette for the current game/profile key. */
  public String encodeLearned(){StringBuilder out=new StringBuilder("v1");for(int rank=5;rank<=11;rank++){out.append(';');int rgb=learned[rank];if(rgb<0)out.append('-');else{String hex=Integer.toHexString(rgb&0xffffff);for(int n=hex.length();n<6;n++)out.append('0');out.append(hex);}}return out.toString();}
  /** Restore only a complete palette with colors separated from known and each other. */
  public void restoreLearned(String encoded){
   if(encoded==null||encoded.length()>64)throw new IllegalArgumentException("invalid learned palette");
   String[] parts=encoded.split(";",-1);if(parts.length!=8||!parts[0].equals("v1"))throw new IllegalArgumentException("invalid learned palette");
   int[] restored=new int[12];java.util.Arrays.fill(restored,-1);
   for(int n=0;n<7;n++){
    String value=parts[n+1];if(value.equals("-"))continue;
    if(value.length()!=6)throw new IllegalArgumentException("invalid learned color");int rgb=0;
    for(int i=0;i<6;i++){int digit=Character.digit(value.charAt(i),16);if(digit<0)throw new IllegalArgumentException("invalid learned color");rgb=(rgb<<4)|digit;}
    int low=Math.min(rgb>>16&255,Math.min(rgb>>8&255,rgb&255)),high=Math.max(rgb>>16&255,Math.max(rgb>>8&255,rgb&255));
    if(high-low<60||high<70)throw new IllegalArgumentException("invalid learned color");
    for(int[] base:COLORS)if(distance(rgb,(base[0]<<16)|(base[1]<<8)|base[2])<110*110)throw new IllegalArgumentException("learned color conflicts with base palette");
    for(int rank=5;rank<5+n;rank++)if(restored[rank]>=0&&distance(rgb,restored[rank])<110*110)throw new IllegalArgumentException("learned colors conflict");
    restored[5+n]=rgb;
   }
   System.arraycopy(restored,0,learned,0,learned.length);discard();
  }
  public Board2048 read(PixelBoardReader.Pixels pixels,int width,int height,int left,int top,int right,int bottom,
    Board2048 before,int action,long frameAt){
   try{return candidate(pixels,width,height,left,top,right,bottom,before,action,frameAt);}
   catch(RuntimeException error){discard();throw error;}
  }
  private Board2048 candidate(PixelBoardReader.Pixels pixels,int width,int height,int left,int top,int right,int bottom,
    Board2048 before,int action,long frameAt){
   if(pixels==null||left<0||top<0||right>width||bottom>height||right-left<160||bottom-top<160
     ||Math.abs((right-left)-(bottom-top))>Math.min(right-left,bottom-top)*.08||frameAt<0)
    throw new IllegalArgumentException("invalid colour board geometry/time");
   Board2048 expected=before==null?null:Verified2048.beforeSpawn(before,action);
   int[] colors=learned.clone(),values=new int[16];boolean added=false;
   for(int i=0;i<16;i++){
    int[] sample=sample(pixels,left,top,right,bottom,i);int rgb=sample[0],value=known(rgb,colors);
    if(value<0){
     int target=expected==null?0:expected.cells[i];
     if(target<32||target>2048||sample[1]>24)throw new IllegalArgumentException(
      "Неизвестная или анимированная плитка: строка "+(i/4+1)+", столбец "+(i%4+1)
      +". Ожидайте остановки анимации; плитки 32 и выше изучаются после подтверждённого слияния.");
     int rank=Integer.numberOfTrailingZeros(target);
     // Existing values cannot acquire a new identity from an arbitrary later colour.
     for(int old:before.cells)if(old==target)throw new IllegalArgumentException("unknown pre-existing tile");
     if(colors[rank]>=0)throw new IllegalArgumentException("inconsistent merged tile colour");
     for(int[] base:COLORS)if(distance(rgb,(base[0]<<16)|(base[1]<<8)|base[2])<110*110)
      throw new IllegalArgumentException("merged colour conflicts with known palette");
     for(int j=5;j<=11;j++)if(colors[j]>=0&&distance(rgb,colors[j])<110*110)
      throw new IllegalArgumentException("merged colour conflicts with learned palette");
     int low=Math.min(rgb>>16&255,Math.min(rgb>>8&255,rgb&255)),high=Math.max(rgb>>16&255,Math.max(rgb>>8&255,rgb&255));
     if(high-low<60||high<70)throw new IllegalArgumentException("background cannot teach a tile");
     colors[rank]=rgb;value=target;added=true;
    }
    // Newly inferred tiles must be homogeneous at every occurrence in this frame.
    if(value>=32&&learned[Integer.numberOfTrailingZeros(value)]<0&&sample[1]>24)
     throw new IllegalArgumentException("animated merged tile");
    values[i]=value;
   }
   Board2048 board=new Board2048(values);
   if(board.max()==0||before!=null&&!before.verifies(board,action))throw new IllegalArgumentException("colour board transition not verified");
   if(added){
    boolean same=pending!=null&&board.same(pendingBoard)&&before.same(pendingBefore)&&action==pendingAction;
    if(same)for(int i=5;i<=11;i++)if((colors[i]<0)!=(pending[i]<0)||colors[i]>=0&&distance(colors[i],pending[i])>12*12)same=false;
    if(same&&frameAt>pendingAt&&frameAt-pendingAt>=80){stableReads++;}
    else if(!same){stableReads=1;pendingAt=frameAt;}
    pending=colors;pendingBoard=board;pendingBefore=new Board2048(before.cells);pendingAction=action;
   }else discard();
   return board;
  }
  public void confirm(Board2048 stable){
   if(pending==null)return;
   if(stableReads<2||!pendingBoard.same(stable))throw new IllegalStateException("palette requires two stable frames");
   System.arraycopy(pending,0,learned,0,learned.length);discard();
  }
  private static int known(int rgb,int[] palette){
   int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
   int result=-1;try{result=classify(r,g,b,0,0);}catch(IllegalArgumentException ignored){}
   for(int i=5;i<=11;i++)if(palette[i]>=0&&distance(rgb,palette[i])<=24*24){if(result>=0)return -1;result=1<<i;}
   return result;
  }
  private static int distance(int a,int b){int r=(a>>16&255)-(b>>16&255),g=(a>>8&255)-(b>>8&255),v=(a&255)-(b&255);return r*r+g*g+v*v;}
  private static int[] sample(PixelBoardReader.Pixels pixels,int l,int t,int r,int b,int cell){
   int sr=0,sg=0,sb=0;int[] min={255,255,255},max={0,0,0};
   for(int y=0;y<3;y++)for(int x=0;x<3;x++){
    int rgb=pixels.rgb(l+(int)((cell%4+.20+x*.05)*(r-l)/4),t+(int)((cell/4+.18+y*.05)*(b-t)/4));
    int red=rgb>>16&255,green=rgb>>8&255,blue=rgb&255;sr+=red;sg+=green;sb+=blue;
    min[0]=Math.min(min[0],red);max[0]=Math.max(max[0],red);min[1]=Math.min(min[1],green);max[1]=Math.max(max[1],green);min[2]=Math.min(min[2],blue);max[2]=Math.max(max[2],blue);
   }
   return new int[]{(sr/9<<16)|(sg/9<<8)|sb/9,Math.max(max[0]-min[0],Math.max(max[1]-min[1],max[2]-min[2]))};
  }
 }

 private static int classify(int r,int g,int b,int row,int col){
  int low=Math.min(r,Math.min(g,b)),high=Math.max(r,Math.max(g,b));
  // The real Market 2048 glass cells are translucent: at one location
  // the scene behind an empty tile is (189,174,193), elsewhere (253,222,210).
  // Neither passes the old near-white test. Accept only low-chroma, reasonably
  // bright glass; actual orange/pink/rose/blue tiles have a much stronger
  // chromatic component. Never infer a tile from an arbitrary vivid background.
  if(low>=150&&high-low<=90)return 0;

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
