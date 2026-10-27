package org.localpilot.android.web;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Explicit demonstrations from an owned WebView; incompatible with simulator checkpoints. */
public final class WebLesson {
 public static final int FEATURES=256,MAX_ROWS=1000,MAX_BYTES=8*1024*1024;
 private static final String HEADER="LOCALPILOT-WEB-v1\nrgb8x8-delta8x8-v1\n",HEADER_V2="LOCALPILOT-WEB-v2\nrgb8x8-delta8x8-v1\n";
 private static final String HEADER_V3="LOCALPILOT-WEB-v3\nrgb8x8-delta8x8-v1\n";
 private boolean rewardFormat;
 public enum Kind { WAIT,TAP,DRAG,HOLD }
 public static final class Action {
  public final Kind kind;public final float x,y,toX,toY;public final int duration;
  public Action(Kind k,float x,float y,float tx,float ty,int ms){
   if(k==null||!unit(x)||!unit(y)||!unit(tx)||!unit(ty)||ms<30||ms>2000)throw new IllegalArgumentException("gesture");
   kind=k;this.x=x;this.y=y;toX=tx;toY=ty;duration=ms;
  }
  public String label(){return kind+" "+Math.round(x*100)+"%/"+Math.round(y*100)+"% → "+Math.round(toX*100)+"%/"+Math.round(toY*100)+"% · "+duration+"ms";}
  private String group(){return kind==Kind.WAIT?"WAIT":kind==Kind.DRAG?"DRAG_"+(toX-x>.05f?"RIGHT":toX-x<-.05f?"LEFT":toY-y>0?"DOWN":"UP"):kind+"_"+(x<.4f?"LEFT":x>.6f?"RIGHT":"CENTER");}
 }
 public static final class Sample {
  public final String session;public final long time;public final float aspect;public final Action action;
  private final float[] before,after;public final GoalReward outcome;
  public Sample(String session,long time,float aspect,Action action,float[] before,float[] after){this(session,time,aspect,action,before,after,GoalReward.unknown());}
  public Sample(String session,long time,float aspect,Action action,float[] before,float[] after,GoalReward outcome){
   if(session==null||!session.matches("[a-zA-Z0-9_-]{1,64}")||time<0||!Float.isFinite(aspect)||aspect<.2f||aspect>5||action==null)throw new IllegalArgumentException("sample");
   validateFeatures(before);validateFeatures(after);this.session=session;this.time=time;this.aspect=aspect;this.action=action;this.before=before.clone();this.after=after.clone();this.outcome=Objects.requireNonNull(outcome);
  }
 }
 public final String game,origin,profileId,profileName;
 private final ArrayList<Sample> samples=new ArrayList<>();
 /** v1 data has no trustworthy game identity and stays unassigned until explicitly reviewed. */
 public WebLesson(String game,String origin){this(game,origin,"",true);}
 public WebLesson(String game,String origin,String name){this(game,origin,normalizeProfile(name),false);}
 private WebLesson(String game,String origin,String name,boolean legacy){
  if(game==null||!game.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("game ID");
  URI u=https(origin);if(u.getRawQuery()!=null||u.getRawFragment()!=null||!u.getPath().isEmpty())throw new IllegalArgumentException("origin");
  this.game=game;this.origin=origin;profileName=name;profileId=legacy?"":profileKey(name);
 }
 public boolean identified(){return !profileId.isEmpty();}
 public static String normalizeProfile(String name){
  if(name==null)throw new IllegalArgumentException("profile name");
  for(int i=0;i<name.length();i++)if(Character.isISOControl(name.charAt(i))||Character.getType(name.charAt(i))==Character.FORMAT||Character.getType(name.charAt(i))==Character.LINE_SEPARATOR||Character.getType(name.charAt(i))==Character.PARAGRAPH_SEPARATOR||name.charAt(i)=='|')throw new IllegalArgumentException("profile name");
  String normalized=java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFKC).trim().replaceAll(" +"," ");
  if(normalized.isEmpty()||normalized.length()>64)throw new IllegalArgumentException("profile length");return normalized;
 }
 public static String profileKey(String name){return sha(normalizeProfile(name).toLowerCase(Locale.ROOT));}
 private WebLesson emptyLike(){WebLesson d=identified()?new WebLesson(game,origin,profileName):new WebLesson(game,origin);d.rewardFormat=rewardFormat;return d;}
 public synchronized Map<String,Integer> sessions(){LinkedHashMap<String,Integer> out=new LinkedHashMap<>();for(Sample s:samples)out.put(s.session,out.getOrDefault(s.session,0)+1);return Collections.unmodifiableMap(out);}
 public synchronized WebLesson session(String id){WebLesson out=emptyLike();for(Sample s:samples)if(s.session.equals(id))out.samples.add(s);if(out.samples.isEmpty())throw new IllegalArgumentException("unknown session");return out;}
 /** Assign only after the user has established that all selected rows belong to this game. */
 public synchronized WebLesson assigned(String name){
  WebLesson out=new WebLesson(game,origin,name);if(identified()&&!profileId.equals(out.profileId))throw new IllegalArgumentException("different named profile");out.samples.addAll(samples);out.rewardFormat=rewardFormat;return out;
 }
 public synchronized WebLesson assignedTo(String game,String origin,String name){
  if(identified()||!this.origin.equals(origin))throw new IllegalArgumentException("Only reviewed legacy rows on the same origin may be assigned");
  WebLesson out=new WebLesson(game,origin,name);out.samples.addAll(samples);out.rewardFormat=rewardFormat;return out;
 }
 public static URI https(String value){
  if(value==null)throw new IllegalArgumentException("HTTPS address");
  try{URI u=new URI(value);if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||u.getPort()==0||u.getPort()<-1)throw new IllegalArgumentException("Use HTTPS without embedded credentials");return u;}
  catch(java.net.URISyntaxException e){throw new IllegalArgumentException("Invalid HTTPS address",e);}
 }
 public static String gameId(String url){URI u=https(url);String s=u.toString();int hash=s.indexOf('#');if(hash>=0)s=s.substring(0,hash);return sha(s);}
 public static String origin(String url){URI u=https(url);return "https://"+(u.getHost().contains(":")?"["+u.getHost()+"]":u.getHost())+(u.getPort()==-1?"":":"+u.getPort());}
 public synchronized int size(){return samples.size();}
 public synchronized void add(Sample sample){if(samples.size()>=MAX_ROWS)throw new IllegalStateException("1000 examples reached; export or choose a new game");Objects.requireNonNull(sample);if(sample.outcome.known()&&!identified())throw new IllegalStateException("Assign a profile before rating");samples.add(sample);if(sample.outcome.known())rewardFormat=true;}
 public synchronized WebLesson copy(){WebLesson d=emptyLike();d.samples.addAll(samples);return d;}
 public synchronized WebLesson merged(WebLesson other){
  if(!identified()||!other.identified()||!game.equals(other.game)||!origin.equals(other.origin)||!profileId.equals(other.profileId))throw new IllegalArgumentException("Examples belong to another game/profile or are unassigned");
  WebLesson d=copy();d.rewardFormat|=other.rewardFormat;Map<String,Integer> seen=new HashMap<>();for(int i=0;i<d.samples.size();i++)seen.put(baseRow(d.samples.get(i)),i);
  for(Sample s:other.copy().samples){String key=baseRow(s);Integer at=seen.get(key);if(at==null){seen.put(key,d.samples.size());d.add(s);}else{Sample old=d.samples.get(at);if(old.outcome.known()&&s.outcome.known()&&!old.outcome.encode().equals(s.outcome.encode()))throw new IllegalArgumentException("Conflicting outcome labels");if(!old.outcome.known()&&s.outcome.known())d.samples.set(at,s);}}return d;
 }
 public synchronized void rateLast(GoalReward outcome){if(samples.isEmpty())throw new IllegalStateException("No sample");rate(samples.get(samples.size()-1),outcome);}
 public synchronized void rate(Sample expected,GoalReward outcome){if(!identified())throw new IllegalStateException("No named sample");int i=samples.indexOf(expected);if(i<0)throw new IllegalStateException("Sample changed");Sample s=samples.get(i);samples.set(i,new Sample(s.session,s.time,s.aspect,s.action,s.before,s.after,outcome));rewardFormat=true;}
 public synchronized boolean hasMetricRewards(){return samples.stream().anyMatch(s->s.outcome.source==GoalReward.Source.PROGRESS||s.outcome.source==GoalReward.Source.DISTANCE);}
 public synchronized Sample lastSample(){return samples.isEmpty()?null:samples.get(samples.size()-1);}
 public synchronized String outcomeSummary(){int good=0,bad=0,neutral=0,unknown=0;for(Sample s:samples)if(!s.outcome.known())unknown++;else if(s.outcome.value>0)good++;else if(s.outcome.value<0)bad++;else neutral++;return "Удачные "+good+" · ошибки "+bad+" · нейтральные "+neutral+" · без оценки "+unknown;}
 /** Count ONLY outcome-rated, unambiguous directional swipes of this 2048 profile.
  * Layout-dependent pixels, coordinates, unlabeled samples and other profiles
  * are never transferred into the real 2048 controller.
  * Indexes: LEFT,UP,RIGHT,DOWN successes; then the four failures.
  */
 public synchronized int[] graded2048Directions(){
  if(!identified()||!"2048".equalsIgnoreCase(profileName))throw new IllegalArgumentException("not an identified 2048 profile");
  int[] counts=new int[8];
  for(Sample s:samples){
   if(s.action.kind!=Kind.DRAG||!s.outcome.known()||s.outcome.value==0)continue;
   float dx=s.action.toX-s.action.x,dy=s.action.toY-s.action.y;
   float horizontal=Math.abs(dx),vertical=Math.abs(dy),main=Math.max(horizontal,vertical),minor=Math.min(horizontal,vertical);
   if(main<.15f||main<minor*1.7f)continue;
   int direction=horizontal>vertical?(dx<0?0:2):(dy<0?1:3);
   counts[direction+(s.outcome.value>0?0:4)]++;
  }
  return counts;
 }

 private static boolean unit(float f){return Float.isFinite(f)&&f>=0&&f<=1;}
 private static void validateFeatures(float[] f){if(f==null||f.length!=FEATURES)throw new IllegalArgumentException("feature shape");for(float v:f)if(!Float.isFinite(v)||v< -1||v>1)throw new IllegalArgumentException("feature range");}
 public static String sha(String s){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));char[] hex="0123456789abcdef".toCharArray(),out=new char[b.length*2];for(int i=0;i<b.length;i++){out[2*i]=hex[(b[i]&255)>>>4];out[2*i+1]=hex[b[i]&15];}return new String(out);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static String vector(float[] f){StringBuilder s=new StringBuilder();for(float v:f){if(s.length()>0)s.append(',');s.append(Float.toString(v));}return s.toString();}
 private static float[] parseVector(String value){String[] p=value.split(",",-1);if(p.length!=FEATURES)throw new IllegalArgumentException("features");float[] a=new float[FEATURES];for(int i=0;i<a.length;i++)a[i]=Float.parseFloat(p[i]);validateFeatures(a);return a;}
 private static String baseRow(Sample s){Action a=s.action;return s.session+"|"+s.time+"|"+s.aspect+"|"+a.kind+"|"+a.x+"|"+a.y+"|"+a.toX+"|"+a.toY+"|"+a.duration+"|"+vector(s.before)+"|"+vector(s.after);}
 private String row(Sample s){return baseRow(s)+(rewardFormat?"|"+s.outcome.encode():"");}
 public synchronized byte[] encode(){
  StringBuilder out=new StringBuilder(identified()?(rewardFormat?HEADER_V3:HEADER_V2):HEADER).append(game).append('\n').append(origin).append('\n');if(identified())out.append(profileId).append('\n').append(profileName).append('\n');String previous=sha(out.toString());
  for(Sample s:samples){String body=row(s)+"|"+previous;previous=sha(body);out.append(body).append('|').append(previous).append('\n');}
  byte[] b=out.toString().getBytes(StandardCharsets.UTF_8);if(b.length>MAX_BYTES)throw new IllegalStateException("Dataset size limit");return b;
 }
 public static byte[] bounded(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){if(b.size()+n>MAX_BYTES)throw new IOException("Dataset exceeds 8 MiB");b.write(block,0,n);}return b.toByteArray();}
 public static WebLesson decode(byte[] bytes)throws IOException{
  if(bytes.length==0||bytes.length>MAX_BYTES)throw new IOException("Empty or oversized dataset");String text=new String(bytes,StandardCharsets.UTF_8);
  boolean v3=text.startsWith(HEADER_V3),v2=v3||text.startsWith(HEADER_V2);String header=v3?HEADER_V3:v2?HEADER_V2:HEADER;
  if(!Arrays.equals(bytes,text.getBytes(StandardCharsets.UTF_8))||!text.startsWith(header)||!text.endsWith("\n"))throw new IOException("Not LOCALPILOT-WEB-v1/v2/v3; checkpoints, audit and videos are different formats");
  String[] lines=text.substring(header.length(),text.length()-1).split("\n",-1);int metadata=v2?4:2;
  try{
   if(lines.length<metadata||lines.length>MAX_ROWS+metadata)throw new IllegalArgumentException("rows");
   WebLesson d=v2?new WebLesson(lines[0],lines[1],lines[3]):new WebLesson(lines[0],lines[1]);
   d.rewardFormat=v3;
   if(v2&&(!d.profileId.equals(lines[2])||!d.profileName.equals(lines[3])))throw new IllegalArgumentException("profile identity");
   String previous=sha(header+String.join("\n",Arrays.copyOf(lines,metadata))+"\n");
   for(int i=metadata;i<lines.length;i++){
    String[] p=lines[i].split("\\|",-1);int prev=v3?12:11,hash=prev+1;if(p.length!=hash+1||!p[prev].equals(previous)||!sha(lines[i].substring(0,lines[i].lastIndexOf('|'))).equals(p[hash]))throw new IllegalArgumentException("Checksum mismatch");
    Action a=new Action(Kind.valueOf(p[3]),Float.parseFloat(p[4]),Float.parseFloat(p[5]),Float.parseFloat(p[6]),Float.parseFloat(p[7]),Integer.parseInt(p[8]));
    d.add(new Sample(p[0],Long.parseLong(p[1]),Float.parseFloat(p[2]),a,parseVector(p[9]),parseVector(p[10]),v3?GoalReward.decode(p[11]):GoalReward.unknown()));previous=p[hash];
   }return d;
  }catch(IllegalArgumentException|IllegalStateException e){throw new IOException("Invalid web examples",e);}
 }
 public void save(File file)throws IOException{
  if(rewardFormat&&file.isFile()){
   byte[] previous;try(InputStream in=new FileInputStream(file)){previous=bounded(in);}
   if(new String(previous,StandardCharsets.UTF_8).startsWith(HEADER_V2)){
    decode(previous);File archive=new File(file+".pre-rewards-v2.bak");if(!archive.exists())Files.copy(file.toPath(),archive.toPath());
   }
  }
  byte[] b=encode();File parent=file.getAbsoluteFile().getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Dataset directory");File temp=File.createTempFile("web-", ".pending",parent);
  try{try(FileOutputStream out=new FileOutputStream(temp)){out.write(b);out.getFD().sync();}decode(Files.readAllBytes(temp.toPath()));
   if(file.exists()){File backup=new File(file+".bak.pending");Files.copy(file.toPath(),backup.toPath(),StandardCopyOption.REPLACE_EXISTING);Files.move(backup.toPath(),new File(file+".bak").toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
   Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
  }finally{Files.deleteIfExists(temp.toPath());}
 }
 public static WebLesson load(File f)throws IOException{try(InputStream in=new FileInputStream(f)){return decode(bounded(in));}}
 public static WebLesson loadRecovering(File file)throws IOException{
  if(!file.exists())return load(new File(file+".bak"));
  try{return load(file);}catch(IOException failure){
   File backup=new File(file+".bak");if(!backup.isFile())throw failure;
   WebLesson restored;try{restored=load(backup);}catch(IOException error){failure.addSuppressed(error);throw failure;}
   File corrupt=new File(file+".corrupt-"+System.currentTimeMillis());
   Files.move(file.toPath(),corrupt.toPath(),StandardCopyOption.ATOMIC_MOVE);
   restored.save(file);return restored;
  }
 }
 private static float distance(float[] a,float[] b){float sum=0;for(int i=0;i<a.length;i++)sum+=Math.abs(a[i]-b[i]);return sum/a.length;}
 public synchronized Action predict(float[] features,float aspect){validateFeatures(features);return identified()?predict(samples,features,aspect):null;}
 private static Action predict(List<Sample> data,float[] features,float aspect){
  boolean graded=data.stream().anyMatch(s->s.outcome.known());
  Sample best=null;float nearest=Float.POSITIVE_INFINITY,rank=Float.POSITIVE_INFINITY;
  for(Sample s:data){if(graded&&(!s.outcome.known()||s.outcome.value<=0))continue;if(Math.abs(s.aspect-aspect)>s.aspect*.05f)continue;float d=distance(s.before,features);if(d>.10f)continue;float candidate=d-(graded?.01f*s.outcome.value:0);if(candidate<rank){rank=candidate;nearest=d;best=s;}}
  if(best==null||nearest>.10f)return null;
  for(Sample s:data)if(s.outcome.known()&&s.outcome.value<0&&s.action.group().equals(best.action.group())&&Math.abs(s.aspect-aspect)<=s.aspect*.05f&&distance(s.before,features)<nearest+.008f)return null;
  for(Sample s:data)if((!graded||s.outcome.known()&&s.outcome.value>0)&&Math.abs(s.aspect-aspect)<=s.aspect*.05f&&!s.action.group().equals(best.action.group())&&(!graded||Math.abs(s.outcome.value-best.outcome.value)<=.1f)&&distance(s.before,features)<nearest+.004f)return null;
  return best.action;
 }
 public synchronized String trainReport(){
  if(!identified())throw new IllegalStateException("Assign reviewed data to a game profile first");
  if(samples.size()<10)throw new IllegalStateException("Record or import at least 10 examples");
  String held=samples.get(samples.size()-1).session;List<Sample> train=new ArrayList<>(),test=new ArrayList<>();
  for(Sample s:samples)(s.session.equals(held)?test:train).add(s);
  if(train.size()<5||test.size()<3)return outcomeSummary()+". Профиль «"+profileName+"». Примеры подготовлены: "+samples.size()+". Для независимой оценки запишите ещё одну сессию. Данных для оценки пока недостаточно.";
  boolean graded=samples.stream().anyMatch(s->s.outcome.known());int matched=0,covered=0,evaluated=0,badRecommended=0;
  for(Sample s:test){Action a=predict(train,s.before,s.aspect);if(graded&&s.outcome.known()&&s.outcome.value<0){if(a!=null&&a.group().equals(s.action.group()))badRecommended++;continue;}if(graded&&(!s.outcome.known()||s.outcome.value<=0))continue;evaluated++;if(a!=null){covered++;if(a.group().equals(s.action.group())&&(a.kind==Kind.WAIT||Math.abs(a.x-s.action.x)<.15f&&Math.abs(a.y-s.action.y)<.15f))matched++;}}
  String coverage=String.format(Locale.ROOT,"%.1f",evaluated==0?0:100.0*covered/evaluated);
  return outcomeSummary()+". "+(samples.stream().anyMatch(s->s.outcome.known())?"Обучение использует удачные примеры; ошибки запрещают похожие действия. ":"Оценок нет: это повторение демонстраций без знания результата. ")+"Профиль «"+profileName+"». Модель по образцам: "+samples.size()+". Отдельная последняя сессия: "+test.size()+" примеров; оцениваемых примеров "+evaluated+"; повторено известных ошибок "+badRecommended+"; выдано рекомендаций "+covered+", совпало "+matched+". Покрытие "+coverage+"%. "+(evaluated>0&&covered*4<evaluated?"Покрытие низкое: нужны примеры этой игры. ":"")+"Это совпадение с демонстрациями, не подтверждение побед в игре.";
 }
}
