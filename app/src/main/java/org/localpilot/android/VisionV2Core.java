package org.localpilot.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Vision V2: bounded, exposure-tolerant temporal pixel evidence shared by
 * real MediaProjection and Accessibility screenshots.
 *
 * No game labels are invented. This is the primary frame-validity and
 * motion-evidence layer, not a drop-in replacement for semantic detectors.
 * Motion by itself is never post-gesture visual confirmation or training reward.
 * No Android dependencies, network, weight download, or gesture authority.
 */
public final class VisionV2Core {
 public interface Pixels { int rgb(int x,int y); }
 public record Region(int left,int top,int right,int bottom) {
  public Region {
   if(left<0||top<0||right<=left||bottom<=top)
    throw new IllegalArgumentException("Invalid pixel region");
  }
  public boolean contains(int x,int y){
   return x>=left&&x<right&&y>=top&&y<bottom;
  }
  public int width(){return right-left;}
  public int height(){return bottom-top;}
 }
 public record Blob(float left,float top,float right,float bottom,int cells) {
  public Blob {
   if(!Float.isFinite(left)||!Float.isFinite(top)||!Float.isFinite(right)
    ||!Float.isFinite(bottom)||left<0||top<0||right>1||bottom>1
    ||right<=left||bottom<=top||cells<=0)
    throw new IllegalArgumentException("Invalid motion blob");
  }
 }
 public enum Status { PRIMING, READY, GEOMETRY_RESET, GAP_RESET, REPLAY_REJECTED, INSUFFICIENT_PIXELS }
 public record Frame(long timestampMs,Status status,int validCells,int changedCells,
                     float activeRatio,float changedRatio,int luminanceDrift,
                     List<Blob> movingObjects) {
  public Frame { movingObjects=List.copyOf(movingObjects); }
  /** Ready means TWO independent fresh aligned frames, not gameplay success. */
  public boolean ready(){return status==Status.READY;}
  /** Only local foreground differences. Never use this alone as a reward. */
  public boolean hasLocalMotion(){return ready()&&movingObjects.size()>0;}
  public String diagnostics(){
   return "vision-v2="+status+" valid="+validCells+" changed="+changedCells
     +" blobs="+movingObjects.size()+" drift="+luminanceDrift;
  }
 }
 private static final int SW=48,SH=64,SIZE=SW*SH;
 private static final int MIN_VALID=SIZE*65/100;
 private static final int MAX_GAP_MS=850;
 private int[] previousGray,previousRedDiff,previousBlueDiff;
 private boolean[] previousValid;
 private long previousTimestamp=-1,previousEpoch=-1;
 private int previousStream=-1;
 private Region previousRoi,previousExclude;
 private Frame last;
 public synchronized void reset(){
  previousGray=previousRedDiff=previousBlueDiff=null;previousValid=null;
  previousTimestamp=previousEpoch=-1;previousStream=-1;
  previousRoi=previousExclude=null;last=null;
 }
 public synchronized Frame last(){return last;}
 public synchronized Frame observe(Pixels source,int width,int height,
    Region roi,Region excluded,long timestampMs,long epoch,int stream){
  if(source==null||roi==null||width<1||height<1||width>16384||height>16384
    ||roi.right>width||roi.bottom>height||excluded!=null
    &&(excluded.right>width||excluded.bottom>height)
    ||timestampMs<=0||epoch<0||stream<0)
   throw new IllegalArgumentException("Vision V2 frame bounds");
  if(previousTimestamp>=0&&previousEpoch==epoch&&previousStream==stream
     &&roi.equals(previousRoi)&&same(excluded,previousExclude)
     &&timestampMs<=previousTimestamp){
   last=new Frame(timestampMs,Status.REPLAY_REJECTED,0,0,0,0,0,List.of());
   return last;
  }
  final int[] gray=new int[SIZE],redDiff=new int[SIZE],blueDiff=new int[SIZE];
  final boolean[] valid=new boolean[SIZE];
  int n=0;
  for(int y=0;y<SH;y++)for(int x=0;x<SW;x++){
   int px=roi.left+((2*x+1)*roi.width())/(2*SW);
   int py=roi.top+((2*y+1)*roi.height())/(2*SH);
   if(excluded!=null&&excluded.contains(px,py))continue;
   int color=source.rgb(px,py);
   int r=color>>>16&255,g=color>>>8&255,b=color&255;
   int at=y*SW+x;
   // Integer YCbCr-like channels: global exposure changes largely cancel.
   gray[at]=(77*r+150*g+29*b)>>8;
   redDiff[at]=r-g;blueDiff[at]=b-g;
   valid[at]=true;n++;
  }
  float active=n/(float)SIZE;
  boolean aligned=previousGray!=null&&previousEpoch==epoch
     &&previousStream==stream&&roi.equals(previousRoi)
     &&same(excluded,previousExclude);
  Status state=aligned?Status.READY:Status.GEOMETRY_RESET;
  if(previousGray==null)state=Status.PRIMING;
  else if(aligned&&timestampMs-previousTimestamp>MAX_GAP_MS)
   state=Status.GAP_RESET;
  if(n<MIN_VALID)state=Status.INSUFFICIENT_PIXELS;
  int changed=0,drift=0;List<Blob> moving=List.of();
  if(state==Status.READY){
   int[] differences=new int[SIZE];int common=0;
   for(int i=0;i<SIZE;i++)if(valid[i]&&previousValid[i]){
    differences[common++]=gray[i]-previousGray[i];
   }
   if(common<MIN_VALID){state=Status.INSUFFICIENT_PIXELS;}
   else{
    Arrays.sort(differences,0,common);
    drift=differences[common/2]; // robust global exposure estimate
    boolean[] foreground=new boolean[SIZE];
    for(int i=0;i<SIZE;i++){
     if(!valid[i]||!previousValid[i])continue;
     int brightness=Math.abs(gray[i]-previousGray[i]-drift);
     int chroma=Math.abs(redDiff[i]-previousRedDiff[i])
       +Math.abs(blueDiff[i]-previousBlueDiff[i]);
     if(brightness>=26||chroma>=55){foreground[i]=true;changed++;}
    }
    moving=components(foreground);
   }
  }
  // Even rejected/unaligned samples cannot authorize input. Prime only the
  // fresh capture for the next independent comparison.
  previousGray=gray;previousRedDiff=redDiff;previousBlueDiff=blueDiff;
  previousValid=valid;previousTimestamp=timestampMs;
  previousEpoch=epoch;previousStream=stream;
  previousRoi=roi;previousExclude=excluded;
  last=new Frame(timestampMs,state,n,changed,active,
   n==0?0:changed/(float)n,drift,moving);
  return last;
 }
 private static boolean same(Region a,Region b){
  return a==null?b==null:a.equals(b);
 }
 private static List<Blob> components(boolean[] foreground){
  boolean[] visited=new boolean[SIZE];
  int[] queue=new int[SIZE];
  List<Blob> found=new ArrayList<>();
  for(int start=0;start<SIZE;start++){
   if(!foreground[start]||visited[start])continue;
   int head=0,tail=1,minX=start%SW,maxX=minX,minY=start/SW,maxY=minY;
   queue[0]=start;visited[start]=true;
   while(head<tail){
    int current=queue[head++],x=current%SW,y=current/SW;
    minX=Math.min(minX,x);maxX=Math.max(maxX,x);
    minY=Math.min(minY,y);maxY=Math.max(maxY,y);
    for(int yy=Math.max(0,y-1);yy<=Math.min(SH-1,y+1);yy++)
     for(int xx=Math.max(0,x-1);xx<=Math.min(SW-1,x+1);xx++){
      int index=yy*SW+xx;
      if(!foreground[index]||visited[index])continue;
      visited[index]=true;queue[tail++]=index;
     }
   }
   // Avoid registering isolated compression/pixel noise as a moving object.
   if(tail>=4&&maxX>minX&&maxY>minY)
    found.add(new Blob(minX/(float)SW,minY/(float)SH,
       (maxX+1f)/SW,(maxY+1f)/SH,tail));
  }
  found.sort((a,b)->Integer.compare(b.cells(),a.cells()));
  return List.copyOf(found.subList(0,Math.min(12,found.size())));
 }
}
