package org.localpilot.shared;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Import-only SIMULATED GameLab replay bridge. Deliberately cannot write
 * RealGameNeuralCritic, grant InputPermit or score Market acceptance.
 * The learned action prior is for shadow comparison until a separately
 * verified sim-to-real calibration is implemented.
 */
public final class GameLabReplay {
 private GameLabReplay(){}
 public static final String MARKER="#localpilot-gamelab-replay-v1,synthetic-only";
 public static final String COLUMNS="source,game,profile,fault,x0,y0,x1,y1,duration_ms,before_hash,after_hash,result";
 public static final String PREFIX=MARKER+"\n"+COLUMNS+"\n";
 private static final int MAX_BYTES=256*1024,MAX_ROWS=512;
 public static final String[] GAMES={"BUBBLE","ARI","MATCH","PADDLE","TRAFFIC",
   "FLAPPY","BOARD2048","HEXON","PROTECTED_SWAP","CAT_TOWER"};
 private static final int[] PROFILES={9,0,6,1,2,3,4,-1,11,-1};
 public static final String[] ACTIONS={"TAP","LEFT","RIGHT","UP","DOWN"};
 private static final String[] RESULTS={"CHANGED","NO_CHANGE","BLOCKED"};
 private static final String[] FAULTS={"NONE","INPUT_IGNORED","FROZEN_VIEW","PANEL_OVERLAP"};
 public record Sample(int game,int profile,int fault,int x0,int y0,int x1,int y1,
    int duration,int before,int after,int result){
  public boolean eligible(){return fault==0 && result!=2;}
  public int action(){
   int dx=x1-x0,dy=y1-y0;
   if(Math.max(Math.abs(dx),Math.abs(dy))<50)return 0;
   return Math.abs(dx)>=Math.abs(dy)?(dx<0?1:2):(dy<0?3:4);
  }
 }
 private static int index(String name,String[] values){
  for(int i=0;i<values.length;i++)if(values[i].equals(name))return i;
  throw new IllegalArgumentException("unknown replay token");
 }
 private static int integer(String token,int min,int max){
  if(!token.matches("-?[0-9]{1,11}"))throw new IllegalArgumentException("non-integer replay");
  long number=Long.parseLong(token);
  if(number<min||number>max)throw new IllegalArgumentException("replay bounds");
  return (int)number;
 }
 private static int signed(String token){
  return integer(token,Integer.MIN_VALUE,Integer.MAX_VALUE);
 }
 public static String row(String game,int profile,String fault,
    float x0,float y0,float x1,float y1,long duration,
    int before,int after,String result){
  int g=index(game,GAMES),f=index(fault,FAULTS),r=index(result,RESULTS);
  if(profile!=PROFILES[g]||duration<0||duration>20000
      ||!Float.isFinite(x0)||!Float.isFinite(y0)
      ||!Float.isFinite(x1)||!Float.isFinite(y1)
      ||x0<0||x0>1||y0<0||y0>1||x1<0||x1>1||y1<0||y1>1
      ||(r==0)!=(before!=after))throw new IllegalArgumentException("invalid synthetic event");
  return "GAMELAB_SIMULATED,"+game+","+profile+","+fault+","+
    Math.round(x0*1000)+","+Math.round(y0*1000)+","+
    Math.round(x1*1000)+","+Math.round(y1*1000)+","+
    duration+","+before+","+after+","+result;
 }
 public static Sample parseRow(String line){
  String[] p=line.split(",",-1);
  if(p.length!=12||!"GAMELAB_SIMULATED".equals(p[0]))
   throw new IllegalArgumentException("not a synthetic GameLab replay");
  int g=index(p[1],GAMES),profile=integer(p[2],-1,100);
  int fault=index(p[3],FAULTS),result=index(p[11],RESULTS);
  if(profile!=PROFILES[g])throw new IllegalArgumentException("game/profile mismatch");
  Sample s=new Sample(g,profile,fault,
    integer(p[4],0,1000),integer(p[5],0,1000),
    integer(p[6],0,1000),integer(p[7],0,1000),
    integer(p[8],0,20000),signed(p[9]),signed(p[10]),result);
  if((result==0)!=(s.before()!=s.after()))
   throw new IllegalArgumentException("outcome/hash mismatch");
  return s;
 }
 public static List<Sample> parse(String body){
  if(body==null||body.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)
   throw new IllegalArgumentException("replay too large");
  String[] lines=body.replace("\r\n","\n").split("\n",-1);
  if(lines.length<3||!MARKER.equals(lines[0])||!COLUMNS.equals(lines[1]))
   throw new IllegalArgumentException("replay schema/version mismatch");
  List<Sample> samples=new ArrayList<>();
  for(int i=2;i<lines.length;i++){
   if(i==lines.length-1&&lines[i].isEmpty())continue;
   if(lines[i].isEmpty()||samples.size()>=MAX_ROWS)
    throw new IllegalArgumentException("empty/oversized replay");
   samples.add(parseRow(lines[i]));
  }
  if(samples.isEmpty())throw new IllegalArgumentException("empty replay");
  return samples;
 }
 public static final class Shadow {
  private static final String MODEL="LP_GAMELAB_SHADOW_V1";
  private final int[][] total=new int[GAMES.length][ACTIONS.length];
  private final int[][] changed=new int[GAMES.length][ACTIONS.length];
  private final Set<String> imported=new TreeSet<>();
  public int imports(){return imported.size();}
  public int total(int game,int action){return total[game][action];}
  public int changed(int game,int action){return changed[game][action];}
  public String recommend(String game){
   int g=index(game,GAMES),best=-1;double highest=-1;
   for(int a=0;a<ACTIONS.length;a++){
    if(total[g][a]<5)continue;
    double rate=(changed[g][a]+1.0)/(total[g][a]+2.0);
    if(rate>highest){highest=rate;best=a;}
   }
   return best<0?"недостаточно данных (только симуляция)":
     ACTIONS[best]+" ("+changed[g][best]+"/"+total[g][best]+
     " изменений; подсказка, не команда)";
  }
  public boolean importReplay(String body){
   List<Sample> samples=parse(body); // full validation before any mutation
   String digest=digest(body.replace("\r\n","\n"));
   if(imported.contains(digest))return false;
   if(imported.size()>=256)throw new IllegalArgumentException("too many replay imports");
   for(Sample s:samples)if(s.eligible()){
    int a=s.action(),g=s.game();
    if(total[g][a]>=1_000_000)throw new IllegalArgumentException("shadow counter limit");
   }
   for(Sample s:samples)if(s.eligible()){
    int a=s.action(),g=s.game();total[g][a]++;
    if(s.result()==0)changed[g][a]++;
   }
   imported.add(digest);
   return true;
  }
  public String report(){
   StringBuilder out=new StringBuilder("GameLab · учебный shadow-prior (не управляет играми)\n");
   out.append("Импортировано журналов: ").append(imports()).append("\n");
   for(int g=0;g<GAMES.length;g++){
    int n=0,yes=0;for(int a=0;a<ACTIONS.length;a++){n+=total[g][a];yes+=changed[g][a];}
    if(n>0)out.append(GAMES[g]).append(": ").append(yes).append("/")
       .append(n).append(" изменений, ").append(recommend(GAMES[g])).append("\n");
   }
   return out.toString();
  }
  public String encode(){
   StringBuilder b=new StringBuilder(MODEL).append("\n");
   for(String value:imported)b.append("I,").append(value).append("\n");
   for(int g=0;g<GAMES.length;g++)for(int a=0;a<ACTIONS.length;a++)
    if(total[g][a]!=0)b.append("C,").append(g).append(",").append(a)
      .append(",").append(total[g][a]).append(",").append(changed[g][a]).append("\n");
   return b.toString();
  }
  public static Shadow decode(String input){
   if(input==null||input.length()>100000||!input.startsWith(MODEL+"\n"))
    throw new IllegalArgumentException("shadow model schema");
   Shadow s=new Shadow();
   String[] rows=input.split("\n");
   for(int i=1;i<rows.length;i++){
    String[] cols=rows[i].split(",",-1);
    if(cols[0].equals("I")&&cols.length==2&&cols[1].matches("[0-9a-f]{64}")){
     if(!s.imported.add(cols[1]))throw new IllegalArgumentException("duplicate import");
    }else if(cols[0].equals("C")&&cols.length==5){
     int g=integer(cols[1],0,GAMES.length-1),a=integer(cols[2],0,ACTIONS.length-1);
     if(s.total[g][a]!=0)throw new IllegalArgumentException("duplicate shadow bin");
     int t=integer(cols[3],1,1_000_000),c=integer(cols[4],0,t);
     s.total[g][a]=t;s.changed[g][a]=c;
    }else throw new IllegalArgumentException("invalid shadow row");
   }
   if(s.imported.size()>256)throw new IllegalArgumentException("too many imports");
   return s;
  }
  public static Shadow load(File file)throws IOException{
   if(!file.isFile())return new Shadow();
   return decode(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
  }
  public void save(File file)throws IOException{
   Path path=file.toPath(),parent=path.getParent();
   if(parent==null)throw new IOException("shadow file has no parent");
   Files.createDirectories(parent);
   Path temp=Files.createTempFile(parent,"gamelab-shadow-", ".pending");
   try{
    Files.write(temp,encode().getBytes(StandardCharsets.UTF_8));
    try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
    catch(AtomicMoveNotSupportedException e){
     Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);
    }
   }finally{Files.deleteIfExists(temp);}
  }
 }
 private static String digest(String body){
  try{
   byte[] raw=MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
   StringBuilder b=new StringBuilder();
   for(byte x:raw)b.append(String.format(Locale.ROOT,"%02x",x&255));
   return b.toString();
  }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
}
