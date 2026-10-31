package org.localpilot.android;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** Read-only offline replay. Each screenshot gets an explicit game profile and ROI. No Android gestures. */
public final class LocalJpgReplay {
 private LocalJpgReplay(){}
 private record Spec(String profile,int l,int t,int r,int b,int el,int et,int er,int eb){}
 private record Result(String file,String profile,String verdict,String detail){}
 public static void main(String[] args) throws Exception {
  if(args.length!=1)throw new IllegalArgumentException("JPG directory required");
  Path dir=Path.of(args[0]);
  Map<String,Spec> manifest=readManifest(dir.resolve("localpilot-frames.csv"));
  List<Path> files;
  try(var listing=Files.list(dir)){
   files=listing.filter(Files::isRegularFile).filter(f->f.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png)"))
    .sorted().toList();
  }
  if(files.isEmpty()&&manifest.isEmpty()){System.out.println("LOCAL_JPG_REPLAY SKIP: empty directory and manifest");return;}
  List<Result> results=new ArrayList<>();int errors=0,unmapped=0,missing=0;
  Set<String> actualFiles=new HashSet<>();
  for(Path file:files){
   String filename=file.getFileName().toString();actualFiles.add(filename);Spec spec=manifest.get(filename);
   if(spec==null){
    results.add(new Result(filename,"UNMAPPED","UNVERIFIED","Add filename,profile,left,top,right,bottom to localpilot-frames.csv"));unmapped++;continue;
   }
   try{
    if(Files.size(file)>32L*1024*1024)throw new IllegalArgumentException("IMAGE_FILE_TOO_LARGE");
    BufferedImage image=ImageIO.read(file.toFile());
    if(image==null)throw new IllegalArgumentException("UNSUPPORTED_IMAGE");
    if(spec.l<0||spec.t<0||spec.r>image.getWidth()||spec.b>image.getHeight()||spec.r<=spec.l||spec.b<=spec.t)
     throw new IllegalArgumentException("INVALID_ROI image="+image.getWidth()+"x"+image.getHeight());
    boolean noExclusion=spec.el==0&&spec.et==0&&spec.er==0&&spec.eb==0;
    if(!noExclusion&&(spec.el<0||spec.et<0||spec.er<=spec.el||spec.eb<=spec.et
        ||spec.er>image.getWidth()||spec.eb>image.getHeight()))
     throw new IllegalArgumentException("INVALID_EXCLUDED_ROI image="+image.getWidth()+"x"+image.getHeight());
    String detail=diagnose(image,spec);String verdict=detail.startsWith("FAIL ")?"FAIL":"OBSERVED";
    if("FAIL".equals(verdict))errors++;
    results.add(new Result(filename,spec.profile,verdict,detail));
   }catch(Exception failure){errors++;results.add(new Result(filename,spec.profile,"FAIL",failure.getClass().getSimpleName()+": "+failure.getMessage()));}
  }
  // A manifest listing ten frames must never PASS when only one frame is available.
  for(String expected:new TreeSet<>(manifest.keySet()))if(!actualFiles.contains(expected)){
   missing++;errors++;
   results.add(new Result(expected,manifest.get(expected).profile,"FAIL","MISSING_IMAGE: manifest lists a frame not present in this directory"));
  }
  System.out.println("LOCAL_JPG_REPLAY files="+files.size()+" declared="+manifest.size()+" mapped="+(files.size()-unmapped)+" unmapped="+unmapped+" missing="+missing+" failed="+errors);
  System.out.println("FILE | PROFILE | VERDICT | READER DIAGNOSTICS");
  for(Result item:results)System.out.println(item.file+" | "+item.profile+" | "+item.verdict+" | "+item.detail);
  if(unmapped>0||errors>0)throw new AssertionError("LOCAL_JPG_REPLAY_NOT_ACCEPTED: failed="+errors+" unmapped="+unmapped+" missing="+missing);
 }
 private static Map<String,Spec> readManifest(Path path) throws IOException {
  Map<String,Spec> result=new HashMap<>();if(!Files.exists(path))return result;
  int lineNumber=0;
  for(String line:Files.readAllLines(path)){
   lineNumber++;String raw=line.trim();if(raw.isEmpty()||raw.startsWith("#")||raw.startsWith("filename,"))continue;
   String[] fields=raw.split(",",-1);
   if(fields.length!=6&&fields.length!=10)throw new IllegalArgumentException("BAD_MANIFEST line="+lineNumber+" expected 6 or 10 columns");
   int[] nums=new int[fields.length-2];
   for(int i=2;i<fields.length;i++)nums[i-2]=Integer.parseInt(fields[i].trim());
   String name=fields[0].trim(),profile=fields[1].trim().toUpperCase(Locale.ROOT);
   if(name.isEmpty()||!name.equals(Path.of(name).getFileName().toString()))throw new IllegalArgumentException("BAD_FILENAME line="+lineNumber);
   if(!List.of("BUBBLE","GRID6","GRID7","2048","FLAPPY","TRAFFIC","PADDLE").contains(profile))
    throw new IllegalArgumentException("BAD_PROFILE line="+lineNumber+": "+profile);
   Spec spec=new Spec(profile,nums[0],nums[1],nums[2],nums[3],fields.length==10?nums[4]:0,fields.length==10?nums[5]:0,fields.length==10?nums[6]:0,fields.length==10?nums[7]:0);
   if(result.putIfAbsent(name,spec)!=null)throw new IllegalArgumentException("DUPLICATE_FILE line="+lineNumber);
  }
  return result;
 }
 /** Diagnostic only: output actual pixels, never assign UNKNOWN cells a value. */
 private static String scan2048Cell(BufferedImage image,Spec spec){
  // R2C4 for a 4x4 board. ROI [54,825,638,1409] gives center (565,1044).
  int w=spec.r-spec.l,h=spec.b-spec.t;
  int cx=spec.l+(int)Math.round(w*3.5/4.0),cy=spec.t+(int)Math.round(h*1.5/4.0);
  double scale=Math.min(w,h)/584.0;
  int inner=Math.max(4,(int)Math.round(24*scale));
  int outer=Math.max(inner+2,(int)Math.round(48*scale));
  int stride=Math.max(1,(int)Math.round(2*scale));
  int minLum=255,maxLum=0;long red=0,green=0,blue=0,count=0;
  for(int y=cy-outer;y<=cy+outer;y+=stride)for(int x=cx-outer;x<=cx+outer;x+=stride){
   if(x<spec.l||x>=spec.r||y<spec.t||y>=spec.b)continue;
   int dx=x-cx,dy=y-cy,d2=dx*dx+dy*dy;
   if(d2<inner*inner||d2>outer*outer)continue;
   int rgb=image.getRGB(x,y),r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
   int lum=(r*77+g*150+b*29)>>8;
   minLum=Math.min(minLum,lum);maxLum=Math.max(maxLum,lum);
   red+=r;green+=g;blue+=b;count++;
  }
  if(count==0)return "DIAG_2048_CELL_R2C4 samples=0";
  int rAvg=(int)(red/count),gAvg=(int)(green/count),bAvg=(int)(blue/count);
  float[] ringHsv=java.awt.Color.RGBtoHSB(rAvg,gAvg,bAvg,null);
  int center=image.getRGB(cx,cy),cr=center>>16&255,cg=center>>8&255,cb=center&255;
  float[] centerHsv=java.awt.Color.RGBtoHSB(cr,cg,cb,null);
  return String.format(Locale.ROOT,
   "DIAG_2048_CELL_R2C4 centerXY=(%d,%d) ring_avg=#%02X%02X%02X ringHSV=(%.1f,%.3f,%.3f) lumSpread=%d..%d center=#%02X%02X%02X centerHSV=(%.1f,%.3f,%.3f) samples=%d",
   cx,cy,rAvg,gAvg,bAvg,ringHsv[0]*360f,ringHsv[1],ringHsv[2],minLum,maxLum,
   cr,cg,cb,centerHsv[0]*360f,centerHsv[1],centerHsv[2],count);
 }
 private static String diagnose(BufferedImage image,Spec s){
  PixelBoardReader.Pixels pixels=image::getRGB;
  switch(s.profile){
   case "BUBBLE":{
    BubbleVision.Read seen=BubbleVision.read(image::getRGB,image.getWidth(),image.getHeight(),s.l,s.t,s.r,s.b,s.el,s.et,s.er,s.eb,null);
    if(!seen.usable())return "FAIL BUBBLE_REJECTED: "+seen.reason();
    BubbleBoard b=seen.board();
    return "balls="+b.count()+" shortRowParity="+b.shortRowParity+" disconnected="+(b.count()-b.connected(b.cells()))+
      " ceilingConnected="+b.ceilingConnected()+" reason="+seen.reason()+" HUD_CHECK=requires labelled overlay bounds";
   }
   case "GRID6":case "GRID7":{
    int size=s.profile.equals("GRID6")?6:7;boolean skulls=size==6;
    GridPuzzleVision.Observation obs=GridPuzzleVision.detect(image::getRGB,s.l,s.t,s.r,s.b,size,size,s.el,s.et,s.er,s.eb,1000,null,skulls);
    int observed=size*size-obs.unknownIndices().length;
    List<String> reasons=new ArrayList<>();
    for(int row=0;row<size;row++)for(int col=0;col<size;col++){
     GridPuzzleVision.CellReason reason=obs.reason(row,col);
     if(reason!=GridPuzzleVision.CellReason.KNOWN)reasons.add("R"+(row+1)+"C"+(col+1)+":"+reason);
    }
    return "observedCells="+observed+"/"+(size*size)+" complete="+obs.complete()+" usable="+obs.usable()+
       " partialUsable="+obs.partialUsable()+" unknowns="+obs.unknownReasonCounts()+" cellReasons="+String.join(";",reasons);
   }
   case "2048":{
    String cellProbe=scan2048Cell(image,s);
    String locator;
    try{
     BoardSelection.Candidate board=PixelBoardLocator.find(pixels,image.getWidth(),image.getHeight(),0,0,image.getWidth(),image.getHeight(),s.el,s.et,s.er,s.eb);
     locator="LOCATED["+board.left+","+board.top+","+board.right+","+board.bottom+"]";
    }catch(IllegalArgumentException error){locator="LOCATOR_REJECTED("+error.getMessage()+")";}
    Board2048 board;
    try{board=Color2048Reader.read(pixels,image.getWidth(),image.getHeight(),s.l,s.t,s.r,s.b);}
    catch(IllegalArgumentException colorError){
     try{board=PixelBoardReader.read(pixels,image.getWidth(),image.getHeight(),s.l,s.t,s.r,s.b);}
     catch(IllegalArgumentException oldError){return "FAIL 2048_READ_REJECTED: color="+colorError.getMessage()+" standard="+oldError.getMessage()+" "+locator+" "+cellProbe;}
    }
    int count32=0;for(int n:board.cells)if(n==32)count32++;
    return "board=4x4 max="+board.max()+" dark32_count="+count32+" boardValues="+java.util.Arrays.toString(board.cells)+" "+locator+" "+cellProbe;
   }
   case "FLAPPY":case "PADDLE":case "TRAFFIC":{
    ArcadeProfile profile=ArcadeProfile.valueOf(s.profile);
    ArcadeObservation found=ArcadeVision.detect(profile,pixels,image.getWidth(),image.getHeight(),s.l,s.t,s.r,s.b,s.el,s.et,s.er,s.eb,1000,null);
    StringBuilder detail=new StringBuilder("usable="+found.usable()+" confidence="+String.format(Locale.ROOT,"%.2f",found.confidence));
    if(profile==ArcadeProfile.FLAPPY)detail.append(" counterY_px=").append(s.t+Math.round(found.bottom*(s.b-s.t)));
    detail.append(" acceptedObstacles=");for(ArcadeObservation.Box o:found.obstacles)detail.append(String.format(Locale.ROOT,"(%.2f,%.2f)-(%.2f,%.2f) ",o.left,o.top,o.right,o.bottom));
    if(profile==ArcadeProfile.FLAPPY)detail.append(" belowCounterRejected=not_exposed_by_ArcadeVision");
    return detail.toString();
   }
   default:throw new IllegalArgumentException("UNKNOWN_PROFILE "+s.profile);
  }
 }
}