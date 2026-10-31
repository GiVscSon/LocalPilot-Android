package org.localpilot.android;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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
  if(files.isEmpty()){System.out.println("LOCAL_JPG_REPLAY SKIP: empty directory");return;}
  List<Result> results=new ArrayList<>();int errors=0,unmapped=0;
  for(Path file:files){
   String filename=file.getFileName().toString();Spec spec=manifest.get(filename);
   if(spec==null){
    results.add(new Result(filename,"UNMAPPED","UNVERIFIED","Add filename,profile,left,top,right,bottom to localpilot-frames.csv"));unmapped++;continue;
   }
   try{
    BufferedImage image=ImageIO.read(file.toFile());
    if(image==null)throw new IllegalArgumentException("UNSUPPORTED_IMAGE");
    if(spec.l<0||spec.t<0||spec.r>image.getWidth()||spec.b>image.getHeight()||spec.r<=spec.l||spec.b<=spec.t)
     throw new IllegalArgumentException("INVALID_ROI image="+image.getWidth()+"x"+image.getHeight());
    String detail=diagnose(image,spec);String verdict=detail.startsWith("FAIL ")?"FAIL":"OBSERVED";
    if("FAIL".equals(verdict))errors++;
    results.add(new Result(filename,spec.profile,verdict,detail));
   }catch(Exception failure){errors++;results.add(new Result(filename,spec.profile,"FAIL",failure.getClass().getSimpleName()+": "+failure.getMessage()));}
  }
  System.out.println("LOCAL_JPG_REPLAY files="+files.size()+" mapped="+(files.size()-unmapped)+" unmapped="+unmapped+" failed="+errors);
  System.out.println("FILE | PROFILE | VERDICT | READER DIAGNOSTICS");
  for(Result item:results)System.out.println(item.file+" | "+item.profile+" | "+item.verdict+" | "+item.detail);
  if(unmapped>0||errors>0)throw new AssertionError("LOCAL_JPG_REPLAY_NOT_ACCEPTED: failed="+errors+" unmapped="+unmapped);
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
    String locator;
    try{
     BoardSelection.Candidate board=PixelBoardLocator.find(pixels,image.getWidth(),image.getHeight(),0,0,image.getWidth(),image.getHeight(),s.el,s.et,s.er,s.eb);
     locator="LOCATED["+board.left+","+board.top+","+board.right+","+board.bottom+"]";
    }catch(IllegalArgumentException error){locator="LOCATOR_REJECTED("+error.getMessage()+")";}
    Board2048 board;
    try{board=Color2048Reader.read(pixels,image.getWidth(),image.getHeight(),s.l,s.t,s.r,s.b);}
    catch(IllegalArgumentException colorError){
     try{board=PixelBoardReader.read(pixels,image.getWidth(),image.getHeight(),s.l,s.t,s.r,s.b);}
     catch(IllegalArgumentException oldError){return "FAIL 2048_READ_REJECTED: color="+colorError.getMessage()+" standard="+oldError.getMessage()+" "+locator;}
    }
    int count32=0;for(int n:board.cells)if(n==32)count32++;
    return "board=4x4 max="+board.max()+" dark32_count="+count32+" boardValues="+java.util.Arrays.toString(board.cells)+" "+locator;
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