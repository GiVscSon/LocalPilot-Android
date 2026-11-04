package org.localpilot.android;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Read-only locator for standard-palette 2048 with a connected bbada0 grid background.
 * This is not OCR or general game recognition. Unknown skins, obscured/narrow gaps,
 * multiple fields and exhausted work budgets fail closed. A fresh field read and user
 * confirmation are still required before a caller sends any gesture.
 */
public final class PixelBoardLocator {
 private static final int BACKGROUND=0xbbada0;
 private static final int TOLERANCE=8;
 private static final long MAX_IMAGE_PIXELS=16_000_000L;
 private static final int MAX_SAMPLES=500_000;
 private static final int MAX_PIXEL_READS=4_000_000;
 private static final int MAX_CANDIDATES=64;

 /** Multi-frame search state; unknown/menu frames retain calibration and never authorize input. */
 public static final class Stability {
  private long lastAt=-1,signature;private BoardSelection.Candidate prior;private int missing,good;
  public void reset(){lastAt=-1;prior=null;missing=good=0;}
  public int missingFrames(){return missing;}
  public boolean observe(BoardSelection.Candidate found,long at,long pixels){
   if(at<0||at<=lastAt)return false;lastAt=at;
   if(found!=null&&(found.profile==null||found.left<0||found.top<0||found.right<=found.left||found.bottom<=found.top))found=null;
   if(found==null){missing=Math.min(1000000,missing+1);good=0;prior=null;signature=pixels;return false;}
   boolean same=prior!=null&&prior.profile==found.profile&&prior.left==found.left&&prior.top==found.top&&prior.right==found.right&&prior.bottom==found.bottom&&signature==pixels;
   missing=0;good=same?Math.min(2,good+1):1;prior=found;signature=pixels;return good==2;
  }
 }
 public static long sceneSignature(PixelBoardReader.Pixels pixels,int l,int t,int r,int b,int el,int et,int er,int eb){
  if(pixels==null||l<0||t<0||r<=l||b<=t)throw new IllegalArgumentException("signature geometry");
  long hash=0xcbf29ce484222325L;
  for(int y=0;y<16;y++)for(int x=0;x<16;x++){
   int px=l+(int)((x+.5)*(r-l)/16),py=t+(int)((y+.5)*(b-t)/16);if(px>=el&&px<er&&py>=et&&py<eb)continue;
   int rgb=pixels.rgb(px,py)&0xf0f0f0;hash=(hash^rgb)*0x100000001b3L;
  }
  return hash;
 }

 private PixelBoardLocator(){}

 public static BoardSelection.Candidate find(PixelBoardReader.Pixels pixels,
   int imageWidth,int imageHeight,int windowLeft,int windowTop,int windowRight,
   int windowBottom,int overlayLeft,int overlayTop,int overlayRight,int overlayBottom){
  if(pixels==null||imageWidth<=0||imageHeight<=0)
   throw failure("AUTO_BOARD_GEOMETRY","Не подтверждён размер снимка");
  if((long)imageWidth*imageHeight>MAX_IMAGE_PIXELS)
   throw failure("AUTO_BOARD_LIMIT","Снимок превышает предел автоматического поиска");
  if(windowLeft<0||windowTop<0||windowRight>imageWidth||windowBottom>imageHeight
    ||windowRight<=windowLeft||windowBottom<=windowTop)
   throw failure("AUTO_BOARD_GEOMETRY","Активное окно выходит за снимок");
  int width=windowRight-windowLeft,height=windowBottom-windowTop;
  int step=Math.max(1,(int)Math.ceil(Math.sqrt((long)width*height/(double)MAX_SAMPLES)));
  while(ceil(width,step)*ceil(height,step)>MAX_SAMPLES)step++;
  int columns=(int)ceil(width,step),rows=(int)ceil(height,step);
  byte[] mask=new byte[columns*rows];
  int[] queue=new int[mask.length];
  Reader reader=new Reader(pixels);
  for(int y=0;y<rows;y++)for(int x=0;x<columns;x++){
   int px=windowLeft+x*step,py=windowTop+y*step;
   if(!inside(px,py,overlayLeft,overlayTop,overlayRight,overlayBottom)
      &&background(reader.rgb(px,py)))mask[y*columns+x]=1;
  }
  List<BoardSelection.Candidate> valid=new ArrayList<>();
  int candidates=0;
  for(int start=0;start<mask.length;start++){
   if((start&1023)==0)reader.checkCancelled();
   if(mask[start]!=1)continue;
   int head=0,tail=1,minX=start%columns,maxX=minX,minY=start/columns,maxY=minY;
   queue[0]=start;mask[start]=2;
   while(head<tail){
    if((head&1023)==0)reader.checkCancelled();
    int at=queue[head++],x=at%columns,y=at/columns;
    minX=Math.min(minX,x);maxX=Math.max(maxX,x);
    minY=Math.min(minY,y);maxY=Math.max(maxY,y);
    if(x>0)tail=enqueue(mask,queue,tail,at,at-1,columns,step,windowLeft,windowTop,reader);
    if(x+1<columns)tail=enqueue(mask,queue,tail,at,at+1,columns,step,windowLeft,windowTop,reader);
    if(y>0)tail=enqueue(mask,queue,tail,at,at-columns,columns,step,windowLeft,windowTop,reader);
    if(y+1<rows)tail=enqueue(mask,queue,tail,at,at+columns,columns,step,windowLeft,windowTop,reader);
   }
   int left=windowLeft+minX*step,top=windowTop+minY*step;
   int right=windowLeft+maxX*step+1,bottom=windowTop+maxY*step+1;
   int roughWidth=right-left,roughHeight=bottom-top;
   if(roughWidth+2*step<160||roughHeight+2*step<160)continue;
   if(Math.abs(roughWidth-roughHeight)*100L
      >Math.min(roughWidth,roughHeight)*12L+step*400L)continue;
   if(++candidates>MAX_CANDIDATES)
    throw failure("AUTO_BOARD_LIMIT","Слишком много возможных полей; уточните окно");
   int[] box=refine(reader,queue,tail,columns,step,minX,minY,maxX,maxY,
     windowLeft,windowTop,windowRight,windowBottom);
   BoardSelection.Candidate candidate=new BoardSelection.Candidate(
     BoardSelection.MARKER_2048,box[0],box[1],box[2],box[3]);
   try{
    BoardSelection.choose(List.of(candidate),windowLeft,windowTop,windowRight,windowBottom);
   }catch(BoardSelection.SelectionFailure rejected){
    if(!"AUTO_BOARD_GEOMETRY".equals(rejected.code))throw rejected;
    continue;
   }
   if(intersects(candidate.left,candidate.top,candidate.right,candidate.bottom,
      overlayLeft,overlayTop,overlayRight,overlayBottom))continue;
   if(!outerFrame(reader,candidate)||!regularGrid(reader,candidate))continue;
   // Catch only a parser rejection, not an unexpected exception from the pixel provider.
   try{
    PixelBoardReader.read(reader::rgb,imageWidth,imageHeight,candidate.left,candidate.top,
      candidate.right,candidate.bottom);
   }catch(IllegalArgumentException rejected){
    if(rejected instanceof BoardSelection.SelectionFailure||reader.providerFailure==rejected)
     throw rejected;
    continue;
   }
   valid.add(candidate);
  }
  return BoardSelection.choose(valid,windowLeft,windowTop,windowRight,windowBottom);
 }

 private static long ceil(int value,int divisor){return ((long)value+divisor-1)/divisor;}

 private static int enqueue(byte[] mask,int[] queue,int tail,int from,int at,int columns,
   int step,int originX,int originY,Reader reader){
  if(mask[at]!=1)return tail;
  int x=originX+(from%columns)*step,y=originY+(from/columns)*step;
  int toX=originX+(at%columns)*step,toY=originY+(at/columns)*step;
  int dx=Integer.compare(toX,x),dy=Integer.compare(toY,y);
  for(int i=1;i<step;i++)if(!background(reader.rgb(x+dx*i,y+dy*i)))return tail;
  mask[at]=2;queue[tail++]=at;
  return tail;
 }

 /** Refine only along connected rays from actual component samples, never across a gap. */
 private static int[] refine(Reader reader,int[] component,int count,int columns,int step,
   int minX,int minY,int maxX,int maxY,int windowLeft,int windowTop,int windowRight,
   int windowBottom){
  int[] out={windowLeft+minX*step,windowTop+minY*step,
    windowLeft+maxX*step+1,windowTop+maxY*step+1};
  for(int i=0;i<count;i++){
   int gx=component[i]%columns,gy=component[i]/columns;
   int x=windowLeft+gx*step,y=windowTop+gy*step;
   if(gx==minX)for(int p=x-1;p>=windowLeft&&p>=x-step+1;p--){
    if(!background(reader.rgb(p,y)))break;out[0]=Math.min(out[0],p);
   }
   if(gx==maxX)for(int p=x+1;p<windowRight&&p<=x+step-1;p++){
    if(!background(reader.rgb(p,y)))break;out[2]=Math.max(out[2],p+1);
   }
   if(gy==minY)for(int p=y-1;p>=windowTop&&p>=y-step+1;p--){
    if(!background(reader.rgb(x,p)))break;out[1]=Math.min(out[1],p);
   }
   if(gy==maxY)for(int p=y+1;p<windowBottom&&p<=y+step-1;p++){
    if(!background(reader.rgb(x,p)))break;out[3]=Math.max(out[3],p+1);
   }
  }
  return out;
 }

 /** A clipped last row can still parse; require its external border at all tile centres. */
 private static boolean outerFrame(Reader reader,BoardSelection.Candidate board){
  for(int cell=0;cell<4;cell++){
   int x=board.left+(int)((long)(board.right-board.left)*(cell*2+1)/8);
   int y=board.top+(int)((long)(board.bottom-board.top)*(cell*2+1)/8);
   if(!background(reader.rgb(x,board.top))||!background(reader.rgb(x,board.bottom-1))
      ||!background(reader.rgb(board.left,y))||!background(reader.rgb(board.right-1,y)))
    return false;
  }
  return true;
 }

 /** Six independent grid separators prevent a palette-coloured badge passing as a board. */
 private static boolean regularGrid(Reader reader,BoardSelection.Candidate board){
  for(int axis=0;axis<2;axis++)for(int divider=1;divider<4;divider++){
   int origin=axis==0?board.left:board.top;
   int extent=axis==0?board.right-board.left:board.bottom-board.top;
   int center=origin+(int)((long)extent*divider/4),radius=Math.max(2,extent/40);
   boolean found=false;
   for(int coordinate=center-radius;coordinate<=center+radius&&!found;coordinate++){
    boolean stripe=true;
    for(int cell=0;cell<4&&stripe;cell++)for(int fraction:new int[]{15,50,85}){
     int alongOrigin=axis==0?board.top:board.left;
     int alongExtent=axis==0?board.bottom-board.top:board.right-board.left;
     int along=alongOrigin+(int)((long)alongExtent*(cell*100+fraction)/400);
     if(!background(reader.rgb(axis==0?coordinate:along,axis==0?along:coordinate))){
      stripe=false;break;
     }
    }
    found=stripe;
   }
   if(!found)return false;
  }
  return true;
 }

 private static boolean background(int rgb){
  return Math.max(Math.abs(((rgb>>16)&255)-((BACKGROUND>>16)&255)),
    Math.max(Math.abs(((rgb>>8)&255)-((BACKGROUND>>8)&255)),
      Math.abs((rgb&255)-(BACKGROUND&255))))<=TOLERANCE;
 }

 private static boolean inside(int x,int y,int left,int top,int right,int bottom){
  return right>left&&bottom>top&&x>=left&&x<right&&y>=top&&y<bottom;
 }

 private static boolean intersects(int left,int top,int right,int bottom,
   int otherLeft,int otherTop,int otherRight,int otherBottom){
  return otherRight>otherLeft&&otherBottom>otherTop&&left<otherRight&&right>otherLeft
    &&top<otherBottom&&bottom>otherTop;
 }

 private static BoardSelection.SelectionFailure failure(String code,String message){
  return new BoardSelection.SelectionFailure(code,message);
 }

 private static final class Reader {
  private final PixelBoardReader.Pixels pixels;
  private int reads;
  private RuntimeException providerFailure;
  Reader(PixelBoardReader.Pixels pixels){this.pixels=pixels;}
  int rgb(int x,int y){
   if(++reads>MAX_PIXEL_READS)
    throw failure("AUTO_BOARD_LIMIT","Превышен предел обработки снимка");
   if((reads&1023)==0)checkCancelled();
   try{return pixels.rgb(x,y);}
   catch(RuntimeException error){providerFailure=error;throw error;}
  }
  void checkCancelled(){
   if(Thread.currentThread().isInterrupted())throw new CancellationException("board search cancelled");
  }
 }
}
