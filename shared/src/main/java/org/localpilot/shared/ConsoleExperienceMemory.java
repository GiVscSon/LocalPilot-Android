package org.localpilot.shared;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One shared, bounded NES history for GameLab, ModelLab and the Console Lab UI.
 * This is contextual feedback, NOT a gradient update, score, or proof of reward.
 * A failed integrity check is surfaced to callers instead of silently training
 * on potentially damaged or forged episodes.
 */
public final class ConsoleExperienceMemory {
 private ConsoleExperienceMemory(){}
 public static final String FILE="console-ai-experience-v2.log";
 private static final String PREVIOUS="console-ai-experience-v2.previous.log";
 private static final String SCHEMA="CONSOLE_AI_EXPERIENCE_V2";
 private static final long MAX_BYTES=131072;
 private static final int MAX_CONTEXT=6;
 private static final int MAX_LINE=350;

 public record Episode(String modelId,String modelSha,String button,int beforeFrame,
    int afterFrame,int beforeHash,int afterHash,String status){}

 private static boolean id(String value) {
  return value!=null&&value.matches("[A-Za-z0-9:_.-]{1,96}");
 }
 private static boolean sha(String value){
  return value!=null&&value.matches("[0-9a-f]{64}");
 }
 private static String digest(String input){
  try{
   byte[] hash=MessageDigest.getInstance("SHA-256").digest(
     input.getBytes(StandardCharsets.UTF_8));
   StringBuilder out=new StringBuilder(64);
   for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));
   return out.toString();
  }catch(java.security.NoSuchAlgorithmException impossible){
   throw new IllegalStateException(impossible);
  }
 }
 private static int number(String value){
  try{
   int result=Integer.parseInt(value);
   if(result<0)throw new NumberFormatException("negative frame");
   return result;
  }catch(NumberFormatException invalid){
   throw new IllegalArgumentException("Invalid NES frame counter",invalid);
  }
 }
 private static int hash(String value){
  try{
   long parsed=Long.parseUnsignedLong(value);
   if(parsed==0||parsed>0xffffffffL)throw new NumberFormatException("invalid fingerprint");
   return (int)parsed;
  }catch(NumberFormatException invalid){
   throw new IllegalArgumentException("Invalid NES fingerprint",invalid);
  }
 }
 private static void validate(Episode e){
  if(e==null||!id(e.modelId)||!sha(e.modelSha)||
    !ConsoleAgentContract.playable(e.button))
   throw new IllegalArgumentException("Invalid NES model or controller action");
  NesTrial proof=NesTrial.create("HOMEBREW",e.button,e.beforeFrame,e.afterFrame,
    e.beforeHash,e.afterHash);
  if(!proof.status.equals(e.status))
   throw new IllegalArgumentException("Inconsistent NES visual observation");
 }
 private static String payload(Episode e){
  return SCHEMA+"|HOMEBREW|"+e.modelId+"|"+e.modelSha+"|"+e.button+"|"+
    e.beforeFrame+"|"+e.afterFrame+"|"+Integer.toUnsignedString(e.beforeHash)+"|"+
    Integer.toUnsignedString(e.afterHash)+"|"+e.status+"|UNKNOWN";
 }
 private static Episode decode(String line){
  if(line==null||line.isEmpty()||line.length()>MAX_LINE)
   throw new IllegalArgumentException("Invalid NES episode size");
  String[] parts=line.split("\\|",-1);
  if(parts.length!=12||!SCHEMA.equals(parts[0])||!"HOMEBREW".equals(parts[1])||
    !"UNKNOWN".equals(parts[10])||!sha(parts[11]))
   throw new IllegalArgumentException("Unknown or incomplete NES episode");
  int at=line.lastIndexOf('|');
  if(!MessageDigest.isEqual(
    digest(line.substring(0,at)).getBytes(StandardCharsets.US_ASCII),
    parts[11].getBytes(StandardCharsets.US_ASCII)))
   throw new IllegalArgumentException("NES episode checksum mismatch");
  Episode e=new Episode(parts[2],parts[3],parts[4],number(parts[5]),
    number(parts[6]),hash(parts[7]),hash(parts[8]),parts[9]);
  validate(e);
  return e;
 }
 /**
  * Writes only already-verified real NES trials. The old JSONL format remains
  * untouched for backward diagnostics; new observations use this shared schema.
  */
 public static synchronized void append(Path directory,String modelId,String modelSha,
                            NesTrial trial)throws IOException{
  if(trial==null||!"HOMEBREW".equals(trial.scenario))
   throw new IllegalArgumentException("Only real verified homebrew is accepted");
  Episode row=new Episode(modelId,modelSha,trial.button,trial.frameBefore,
    trial.frameAfter,trial.hashBefore,trial.hashAfter,trial.status);
  validate(row);
  String body=payload(row);
  byte[] encoded=(body+"|"+digest(body)+"\n").getBytes(StandardCharsets.UTF_8);
  if(encoded.length>MAX_LINE)
   throw new IllegalArgumentException("NES episode exceeds safe record limit");
  Files.createDirectories(directory);
  read(directory); // Validate both generations before append or rotation.
  Path journal=directory.resolve(FILE);
  if(Files.isSymbolicLink(journal))
   throw new IOException("Unsafe NES journal path");
  long current=Files.isRegularFile(journal)?Files.size(journal):0;
  if(current>MAX_BYTES)throw new IOException("NES experience journal exceeds safe limit");
  if(current+encoded.length>MAX_BYTES){
   Files.move(journal,directory.resolve(PREVIOUS),StandardCopyOption.REPLACE_EXISTING);
  }
  try(FileOutputStream out=new FileOutputStream(journal.toFile(),true)){
   out.write(encoded);
   out.getFD().sync();
  }
 }
 private static void load(Path file,List<Episode> target)throws IOException{
  if(!Files.exists(file))return;
  if(!Files.isRegularFile(file)||Files.isSymbolicLink(file)||Files.size(file)>MAX_BYTES)
   throw new IOException("NES episode file is unsafe or oversized");
  List<String> lines=Files.readAllLines(file,StandardCharsets.UTF_8);
  for(String line:lines){
   try{target.add(decode(line));}
   catch(IllegalArgumentException invalid){
    throw new IOException("NES episode integrity failed: "+invalid.getMessage(),invalid);
   }
  }
 }
 public static List<Episode> read(Path directory)throws IOException{
  List<Episode> all=new ArrayList<>();
  load(directory.resolve(PREVIOUS),all);
  load(directory.resolve(FILE),all);
  return List.copyOf(all);
 }
 /** A non-reward experience signal consumed by the actual next GGUF proposal. */
 public static String context(Path directory,String modelId,String modelSha)throws IOException{
  if(!id(modelId)||!sha(modelSha))
   throw new IllegalArgumentException("Invalid model identity for NES context");
  List<Episode> all=read(directory);
  List<Episode> matched=new ArrayList<>();
  for(Episode e:all)if(e.modelId.equals(modelId)&&e.modelSha.equals(modelSha))
   matched.add(e);
  StringBuilder prompt=new StringBuilder(
    "Prior VERIFIED observations by this exact GGUF; a changed image does NOT "+
    "prove a reward or skill. Use only as exploratory memory: ");
  if(matched.isEmpty())return prompt.append("none.").toString();
  int begin=Math.max(0,matched.size()-MAX_CONTEXT);
  for(int i=begin;i<matched.size();i++){
   Episode e=matched.get(i);
   prompt.append(e.button).append(':').append(e.status).append(';');
  }
  return prompt.toString();
 }
 public static String summary(Path directory)throws IOException{
  List<Episode> all=read(directory);
  int changed=0,unchanged=0;
  for(Episode e:all){
   if("PIXELS_DIFFER".equals(e.status))changed++;
   else unchanged++;
  }
  return "NES · проверено ходов "+all.size()+", новый рисунок "+changed+
    ", без визуального изменения "+unchanged+
    ". Награда не установлена; веса GGUF не обучались.";
 }
 /** Additional reward-bearing records in the SAME bounded ConsoleExperienceMemory
  * namespace. Existing v2 unknown-reward logs remain fully backward compatible.
  * No generic ROM can supply this exact cartridge-specific CPU task state.
  */
 public static final String GOAL_FILE="console-homebrew-goals-v1.log";
 private static final String GOAL_SCHEMA="CONSOLE_HOME_BREW_GOAL_V2";
 private static final String LEGACY_GOAL_SCHEMA="CONSOLE_HOME_BREW_GOAL_V1";
 private static final int GOAL_RECORD_LIMIT=500;
 public record GoalEpisode(String epoch,String modelId,String modelSha,String button,
   int frameBefore,int frameAfter,int hashBefore,int hashAfter,String visualStatus,
   int beforeX,int beforeY,int afterX,int afterY,int reward,boolean reached){}

 private static int parseByte(String s){
  int v=number(s);
  if(v>255)throw new IllegalArgumentException("Invalid zero-page byte");
  return v;
 }
 private static GoalEpisode requireGoal(String epoch,String modelId,String modelSha,
     NesTrial trial,int bx,int by,int ax,int ay){
  if(epoch==null||!epoch.matches("[1-9][0-9]{0,18}")||!id(modelId)||!sha(modelSha)||
      trial==null||!"HOMEBREW".equals(trial.scenario))
   throw new IllegalArgumentException("Untrusted goal task/ROM identity");
  HomebrewGoalReward.Result result=HomebrewGoalReward.verify(trial.button,
    trial.frameBefore,trial.frameAfter,bx,by,ax,ay);
  return new GoalEpisode(epoch,modelId,modelSha,trial.button,trial.frameBefore,
    trial.frameAfter,trial.hashBefore,trial.hashAfter,trial.status,
    bx,by,ax,ay,result.reward(),result.reached());
 }
 public static synchronized void appendGoal(Path directory,String epoch,String modelId,
   String modelSha,NesTrial proof,int bx,int by,int ax,int ay)throws IOException{
  GoalEpisode t=requireGoal(epoch,modelId,modelSha,proof,bx,by,ax,ay);
  String payload=GOAL_SCHEMA+"|"+HomebrewGoalReward.SOURCE+"|"+t.epoch+"|"+
    t.modelId+"|"+t.modelSha+"|"+t.button+"|"+t.frameBefore+"|"+t.frameAfter+"|"+
    Integer.toUnsignedString(t.hashBefore)+"|"+Integer.toUnsignedString(t.hashAfter)+
    "|"+t.visualStatus+"|"+t.beforeX+"|"+t.beforeY+"|"+t.afterX+"|"+t.afterY+
    "|"+t.reward+"|"+(t.reached?"1":"0");
  byte[] entry=(payload+"|"+digest(payload)+"\n").getBytes(StandardCharsets.UTF_8);
  if(entry.length>GOAL_RECORD_LIMIT)throw new IllegalArgumentException("Oversized task row");
  Files.createDirectories(directory);
  readGoals(directory); // Preserve corrupt evidence; never append a trusted row to it.
  Path file=directory.resolve(GOAL_FILE);
  if(Files.isSymbolicLink(file)||Files.exists(file)&&!Files.isRegularFile(file))
   throw new IOException("Unsafe task journal");
  long size=Files.exists(file)?Files.size(file):0;
  if(size+entry.length>MAX_BYTES)
   throw new IOException("Goal journal full: export before further learning");
  try(FileOutputStream out=new FileOutputStream(file.toFile(),true)){
   out.write(entry);out.getFD().sync();
  }
 }
 private static GoalEpisode parseGoal(String line){
  if(line==null||line.isEmpty()||line.length()>GOAL_RECORD_LIMIT)
   throw new IllegalArgumentException("Bad goal record length");
  String[] p=line.split("\\|",-1);
  if(p.length!=18||!(GOAL_SCHEMA.equals(p[0])||LEGACY_GOAL_SCHEMA.equals(p[0]))||
     !HomebrewGoalReward.SOURCE.equals(p[1])||!sha(p[4])||!sha(p[17]))
   throw new IllegalArgumentException("Unknown goal task schema/checksum");
  int suffix=line.lastIndexOf('|');
  if(!MessageDigest.isEqual(digest(line.substring(0,suffix)).getBytes(StandardCharsets.US_ASCII),
       p[17].getBytes(StandardCharsets.US_ASCII)))
   throw new IllegalArgumentException("Goal evidence checksum changed");
  if(LEGACY_GOAL_SCHEMA.equals(p[0])&&
      (long)number(p[7])-number(p[6])!=16)
   throw new IllegalArgumentException("Legacy task duration changed");
  NesTrial proof=NesTrial.create("HOMEBREW",p[5],number(p[6]),number(p[7]),
    hash(p[8]),hash(p[9]));
  if(!proof.status.equals(p[10]))
   throw new IllegalArgumentException("Visual verification changed");
  GoalEpisode row=requireGoal(p[2],p[3],p[4],proof,
    parseByte(p[11]),parseByte(p[12]),parseByte(p[13]),parseByte(p[14]));
  if(row.reward!=Integer.parseInt(p[15])||row.reached!=("1".equals(p[16])))
   throw new IllegalArgumentException("Task reward is not reproducible from ROM memory");
  return row;
 }
 public static List<GoalEpisode> readGoals(Path directory)throws IOException{
  Path path=directory.resolve(GOAL_FILE);
  if(!Files.exists(path))return List.of();
  if(Files.isSymbolicLink(path)||!Files.isRegularFile(path)||Files.size(path)>MAX_BYTES)
   throw new IOException("Invalid homebrew goal journal");
  List<GoalEpisode> rows=new ArrayList<>();
  for(String line:Files.readAllLines(path,StandardCharsets.UTF_8)){
   try{rows.add(parseGoal(line));}
   catch(IllegalArgumentException invalid){
    throw new IOException("Goal evidence rejected: "+invalid.getMessage(),invalid);
   }
  }
  return List.copyOf(rows);
 }
 public static String goalSummary(Path directory)throws IOException{
  List<GoalEpisode> rows=readGoals(directory);
  int reached=0,positive=0;
  for(GoalEpisode e:rows){
   if(e.reached)reached++;
   if(e.reward>0)positive++;
  }
  return "Homebrew task "+HomebrewGoalReward.TASK+": "+rows.size()+
    " проверенных CPU-переходов, "+positive+" приблизили к цели, "+
    reached+" достигли цели. Награда относится ТОЛЬКО к встроенной ROM.";
 }


 /**
  * A distinct bounded journal in the SAME NES experience store. Hashes detect
  * accidental corruption, not malicious forgery. The task is rightward motion
  * of the exact built-in homebrew cartridge (CPU RAM zero-page $0001).
  *
  * No record here has StudentPolicy obs64/oldLogProb; PPO remains BLOCKED.
  */
 public static final String HOMEBREW_GOAL_FILE="console-ai-homebrew-goal-v1.log";
 private static final String RIGHT_PROGRESS_SCHEMA="LOCALPILOT_HOMEBREW_GOAL_V1";
 private static final String GOAL_TASK="builtin-homebrew-right-progress-v1";
 public record HomebrewGoal(
   String epoch,String romSha,String modelId,String modelSha,String button,
   int frameBefore,int frameAfter,int xBefore,int xAfter,float reward
 ) {
  public boolean ppoEligible(){return false;}
 }
 private static void checkGoal(HomebrewGoal g){
  if(g==null||g.epoch()==null||!g.epoch().matches("[1-9][0-9]{0,18}")||
    !sha(g.romSha())||!id(g.modelId())||!sha(g.modelSha())||
    !ConsoleAgentContract.playable(g.button())||
    g.frameBefore()<15||g.frameAfter()<=g.frameBefore()||
    (long)g.frameAfter()-(long)g.frameBefore()!=16L||
    g.xBefore()<0||g.xBefore()>255||g.xAfter()<0||g.xAfter()>255||
    !Float.isFinite(g.reward()))
   throw new IllegalArgumentException("Invalid homebrew goal evidence");
  int delta=g.xAfter()-g.xBefore();
  if(Math.abs(delta)>16||
    "RIGHT".equals(g.button())&&delta<0||
    "LEFT".equals(g.button())&&delta>0||
    !("RIGHT".equals(g.button())||"LEFT".equals(g.button()))&&delta!=0||
    Float.floatToIntBits(g.reward())!=Float.floatToIntBits(delta/16.0f))
   throw new IllegalArgumentException("Impossible ROM-RAM task reward");
 }
 private static String goalPayload(HomebrewGoal g,String previous){
  checkGoal(g);
  return RIGHT_PROGRESS_SCHEMA+"|"+g.epoch()+"|"+g.romSha()+"|"+g.modelId()+"|"+
    g.modelSha()+"|"+g.button()+"|"+g.frameBefore()+"|"+g.frameAfter()+"|"+
    g.xBefore()+"|"+g.xAfter()+"|"+
    String.format(Locale.ROOT,"%08x",Float.floatToIntBits(g.reward()))+
    "|"+GOAL_TASK+"|PPO_BLOCKED|"+previous;
 }
 private static List<HomebrewGoal> loadGoals(Path file)throws IOException{
  if(!Files.exists(file))return List.of();
  if(!Files.isRegularFile(file)||Files.isSymbolicLink(file)||Files.size(file)>MAX_BYTES)
   throw new IOException("Unsafe or oversized homebrew goal journal");
  List<String> lines=Files.readAllLines(file,StandardCharsets.UTF_8);
  if(lines.size()>256)throw new IOException("Homebrew goal journal row quota");
  List<HomebrewGoal> parsed=new ArrayList<>();
  String previous="0";
  for(String line:lines){
   String[] fields=line.split("\\|",-1);
   if(fields.length!=15||!RIGHT_PROGRESS_SCHEMA.equals(fields[0])||
     !GOAL_TASK.equals(fields[11])||!"PPO_BLOCKED".equals(fields[12])||
     !previous.equals(fields[13])||!sha(fields[14]))
    throw new IOException("Invalid homebrew goal schema/previous hash");
   String payload=line.substring(0,line.lastIndexOf('|'));
   if(!digest(payload).equals(fields[14]))throw new IOException("Broken goal SHA chain");
   try{
    if(!fields[10].matches("[0-9a-f]{8}"))
     throw new IllegalArgumentException("Invalid float32 bits");
    HomebrewGoal record=new HomebrewGoal(
      fields[1],fields[2],fields[3],fields[4],fields[5],
      Integer.parseInt(fields[6]),Integer.parseInt(fields[7]),
      Integer.parseInt(fields[8]),Integer.parseInt(fields[9]),
      Float.intBitsToFloat((int)Long.parseUnsignedLong(fields[10],16)));
    checkGoal(record);
    parsed.add(record);
   }catch(IllegalArgumentException error){
    throw new IOException("Invalid goal evidence record",error);
   }
   previous=fields[14];
  }
  return List.copyOf(parsed);
 }
 public static List<HomebrewGoal> readHomebrewGoals(Path directory)throws IOException{
  if(directory==null||Files.isSymbolicLink(directory))
   throw new IOException("Invalid NES goal folder");
  return loadGoals(directory.resolve(HOMEBREW_GOAL_FILE));
 }
 /**
  * Called only AFTER HomebrewRamReward and HarnessCoordinator both approve a
  * genuine fixed-ROM step. Every write is fsync'ed and atomically replaced.
  */
 public static synchronized void appendHomebrewGoal(Path directory,HomebrewGoal goal)
      throws IOException{
  try{checkGoal(goal);}catch(IllegalArgumentException invalid){
   throw new IOException("Unverified RAM goal",invalid);
  }
  if(directory==null||Files.isSymbolicLink(directory))
   throw new IOException("Invalid NES goal folder");
  Files.createDirectories(directory);
  Path journal=directory.resolve(HOMEBREW_GOAL_FILE);
  List<HomebrewGoal> prior=loadGoals(journal);
  if(prior.size()>=256)throw new IOException("RAM goal journal row quota");
  String previous="0";
  StringBuilder text=new StringBuilder();
  for(HomebrewGoal old:prior){
   String payload=goalPayload(old,previous);
   previous=digest(payload);
   text.append(payload).append('|').append(previous).append('\n');
  }
  String payload=goalPayload(goal,previous);
  text.append(payload).append('|').append(digest(payload)).append('\n');
  byte[] bytes=text.toString().getBytes(StandardCharsets.UTF_8);
  if(bytes.length>MAX_BYTES)throw new IOException("RAM goal journal byte quota");
  Path temp=Files.createTempFile(directory,"nes-goal-",".pending");
  try{
   try(FileOutputStream output=new FileOutputStream(temp.toFile())){
    output.write(bytes);output.getFD().sync();
   }
   if(loadGoals(temp).size()!=prior.size()+1)
    throw new IOException("RAM goal write check failed");
   Files.move(temp,journal,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  }finally{Files.deleteIfExists(temp);}
 }
}
