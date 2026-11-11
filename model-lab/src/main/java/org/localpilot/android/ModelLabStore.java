package org.localpilot.android;

import android.content.Context;
import android.net.Uri;
import android.os.StatFs;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A separate app's private, SHA-256 verified store for preset and user-imported GGUF.
 * There is NO model-to-gesture connection and no access to LocalPilot storage.
 * Unknown architectures are importable but must be load-tested in llama.cpp.
 */
public final class ModelLabStore {
 public static final long MAX_IMPORT_BYTES=3_500_000_000L;
 private static final long SPACE_RESERVE=128L*1024*1024;
 private static final int MAX_IMPORTS=32;
 private static final String MNN_NAME="MNN-Pocket-0.3B-Instruct-Q4_1.gguf";
 private final Context app;
 private final File directory,registry;
 private final ArrayList<Entry> entries=new ArrayList<>();
 private final Map<String,Boolean> verified=new HashMap<>();

 public static final class Entry {
  public final String id,label,modelName,modelSha,projectorName,projectorSha;
  public final long modelBytes,projectorBytes;
  public final boolean bundled;
  Entry(String id,String label,String modelName,long modelBytes,String modelSha,
        String projectorName,long projectorBytes,String projectorSha,boolean bundled){
   this.id=id;this.label=label;this.modelName=modelName;
   this.modelBytes=modelBytes;this.modelSha=modelSha;
   this.projectorName=projectorName;this.projectorBytes=projectorBytes;
   this.projectorSha=projectorSha;this.bundled=bundled;
  }
  public boolean vision(){return projectorName!=null&&!projectorName.isBlank();}
  public String description(){
   return (bundled?"Встроенная · ":"Импорт · ")+label+" · "+(modelBytes/1_000_000)+" МБ"+
    (vision()?" · со зрением":"");
  }
  Entry withProjector(String name,long bytes,String sha){
   return new Entry(id,label,modelName,modelBytes,modelSha,name,bytes,sha,bundled);
  }
 }

 public ModelLabStore(Context context){
  app=context.getApplicationContext();
  directory=new File(app.getNoBackupFilesDir(),"modellab-models");
  if(!directory.isDirectory()&&!directory.mkdirs())
   throw new IllegalStateException("Нет приватного каталога моделей");
  registry=new File(directory,"registry.json");
  try{
   if(BuildConfig.PRELOADED_MODELS)loadIncluded();
   loadImported();
  }catch(Exception error){
   throw new IllegalStateException("Не удалось проверить каталог моделей: "+error.getMessage(),error);
  }
 }
 public synchronized List<Entry> list(){return new ArrayList<>(entries);}
 public synchronized Entry find(String id){
  for(Entry e:entries)if(e.id.equals(id))return e;
  return null;
 }
 private static boolean simpleName(String name){
  return name!=null&&name.matches("[A-Za-z0-9_.-]{1,150}")&&
    !name.contains("..")&&name.endsWith(".gguf");
 }
 private static String hash(byte[] input){
  StringBuilder out=new StringBuilder(input.length*2);
  for(byte b:input){out.append(Character.forDigit((b>>>4)&15,16));
                    out.append(Character.forDigit(b&15,16));}
  return out.toString();
 }
 private static String fileSha(File file)throws Exception{
  MessageDigest sha=MessageDigest.getInstance("SHA-256");
  try(InputStream in=new FileInputStream(file)){
   byte[] buf=new byte[131072];int n;
   while((n=in.read(buf))!=-1)sha.update(buf,0,n);
  }
  return hash(sha.digest());
 }
 private static String display(String raw){
  String value=raw==null?"Imported model":raw.replaceAll("[\\p{Cntrl}]","").trim();
  return value.length()>90?value.substring(0,90):value;
 }
 private static JSONObject json(String value)throws Exception{
  return new JSONObject(value);
 }
 private static String bounded(InputStream stream,int max)throws Exception{
  try(java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
   byte[] buf=new byte[2048];int n;
   while((n=stream.read(buf))!=-1){
    if(out.size()+n>max)throw new IllegalArgumentException("Слишком большой манифест");
    out.write(buf,0,n);
   }
   return out.toString(StandardCharsets.UTF_8);
  }
 }
 private void loadIncluded()throws Exception{
  entries.add(new Entry("builtin:pocket","MNN-Pocket 0.3B",
    MNN_NAME,BuildConfig.POCKET_BYTES,BuildConfig.POCKET_SHA256,null,0,null,true));
  String manifest;
  try(InputStream in=app.getAssets().open("model-catalog.json")){
   manifest=bounded(in,20000);
  }
  JSONObject root=json(manifest);
  if(!"localpilot-extra-models-v1".equals(root.getString("schema")))
   throw new IllegalArgumentException("Неверный манифест встроенных моделей");
  JSONArray rows=root.getJSONArray("files");
  Map<String,JSONObject> items=new HashMap<>();
  for(int i=0;i<rows.length();i++){
   JSONObject item=rows.getJSONObject(i);
   String filename=item.getString("filename");
   if(!simpleName(filename)||items.put(filename,item)!=null)
    throw new IllegalArgumentException("Плохое имя или дубликат GGUF");
  }
  addIncluded(items,"qwen35","Qwen3.5-0.8B","Qwen3.5-0.8B-Q4_0.gguf",null);
  addIncluded(items,"lfm350","LFM2.5-350M","LFM2.5-350M-Q4_0.gguf",null);
  addIncluded(items,"lfmvl450","LFM2.5-VL-450M · vision",
   "LFM2.5-VL-450M-Q4_0.gguf","mmproj-LFM2.5-VL-450m-Q8_0.gguf");
 }
 private void addIncluded(Map<String,JSONObject> items,String id,String label,
                          String name,String projector)throws Exception{
  JSONObject one=items.get(name);
  JSONObject two=projector==null?null:items.get(projector);
  if(one==null||(projector!=null&&two==null))
   throw new IllegalArgumentException("Отсутствует файл предзагруженной модели "+name);
  entries.add(new Entry("builtin:"+id,label,name,one.getLong("bytes"),
   one.getString("sha256"),projector,
   two==null?0:two.getLong("bytes"),two==null?null:two.getString("sha256"),true));
 }
 private void loadImported()throws Exception{
  if(!registry.isFile())return;
  if(registry.length()>128000)throw new IllegalArgumentException("Слишком большой реестр");
  String text;
  try(InputStream in=new FileInputStream(registry)){text=bounded(in,128000);}
  JSONArray rows=new JSONArray(text);
  if(rows.length()>MAX_IMPORTS)throw new IllegalArgumentException("Слишком много моделей");
  for(int i=0;i<rows.length();i++){
   JSONObject o=rows.getJSONObject(i);
   String name=o.getString("modelName");
   String proj=o.optString("projectorName","");
   if(!simpleName(name)||(!proj.isEmpty()&&!simpleName(proj)))
    throw new IllegalArgumentException("Некорректное имя импортированной модели");
   String sha=o.getString("modelSha"),psha=o.optString("projectorSha","");
   if(!sha.matches("[0-9a-f]{64}")||
     (!proj.isEmpty()&&!psha.matches("[0-9a-f]{64}")))
    throw new IllegalArgumentException("Неверный SHA256 в реестре");
   long size=o.getLong("modelBytes"),projSize=o.optLong("projectorBytes",0);
   if(size<4||size>MAX_IMPORT_BYTES||projSize<0||projSize>MAX_IMPORT_BYTES)
    throw new IllegalArgumentException("Неверный размер импортированной модели");
   String id=o.getString("id");
   if(!id.matches("import:[0-9a-f]{16}"))throw new IllegalArgumentException("Неверный ID модели");
   entries.add(new Entry(id,display(o.optString("label","GGUF")),name,
    size,sha,proj.isEmpty()?null:proj,projSize,psha,false));
  }
 }
 private void persist()throws Exception{
  JSONArray rows=new JSONArray();
  for(Entry e:entries)if(!e.bundled){
   JSONObject o=new JSONObject();
   o.put("id",e.id);o.put("label",e.label);o.put("modelName",e.modelName);
   o.put("modelBytes",e.modelBytes);o.put("modelSha",e.modelSha);
   o.put("projectorName",e.projectorName==null?"":e.projectorName);
   o.put("projectorBytes",e.projectorBytes);
   o.put("projectorSha",e.projectorSha==null?"":e.projectorSha);
   rows.put(o);
  }
  File part=new File(directory,"registry.json.partial");
  try(FileOutputStream out=new FileOutputStream(part)){
   out.write(rows.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();
  }
  if(registry.exists()&&!registry.delete())throw new IllegalStateException("Cannot update registry");
  if(!part.renameTo(registry))throw new IllegalStateException("Cannot save registry");
 }
 private static final class ImportedFile {
  final String filename,sha;
  final long bytes;
  ImportedFile(String filename,String sha,long bytes){
   this.filename=filename;this.sha=sha;this.bytes=bytes;
  }
 }
 private ImportedFile copy(InputStream input,String expectedSha)throws Exception{
  if(input==null)throw new IllegalArgumentException("Файл не открыт");
  if(expectedSha!=null&&!expectedSha.isBlank()&&!expectedSha.matches("(?i)[0-9a-f]{64}"))
   throw new IllegalArgumentException("SHA256 должен состоять из 64 шестнадцатеричных знаков");
  File temp=File.createTempFile("import-",".partial",directory);
  long copied=0;
  MessageDigest digest=MessageDigest.getInstance("SHA-256");
  try(InputStream in=input;FileOutputStream out=new FileOutputStream(temp)){
   byte[] buf=new byte[131072];int n;
   StatFs fs=new StatFs(directory.getAbsolutePath());
   if(fs.getAvailableBytes()<SPACE_RESERVE)
    throw new IllegalStateException("Недостаточно памяти для импорта модели");
   while((n=in.read(buf))!=-1){
    copied+=n;
    if(copied>MAX_IMPORT_BYTES)throw new IllegalArgumentException("GGUF превышает лимит 3,5 ГБ");
    if((copied% (16*1024*1024))<n &&fs.getAvailableBytes()<SPACE_RESERVE)
     throw new IllegalStateException("Закончилось свободное место");
    out.write(buf,0,n);digest.update(buf,0,n);
   }
   out.getFD().sync();
  }catch(Exception failure){temp.delete();throw failure;}
  try{
   if(copied<128)throw new IllegalArgumentException("Файл GGUF слишком мал");
   try(InputStream in=new FileInputStream(temp)){
    byte[] magic=new byte[4];
    if(in.read(magic)!=4||magic[0]!='G'||magic[1]!='G'||magic[2]!='U'||magic[3]!='F')
     throw new IllegalArgumentException("Поддерживаются только файлы GGUF");
   }
   String sha=hash(digest.digest());
   if(expectedSha!=null&&!expectedSha.isBlank()&&!sha.equalsIgnoreCase(expectedSha))
    throw new SecurityException("Контрольная сумма модели не совпадает");
   String finalName="import-"+sha+".gguf";
   File dest=new File(directory,finalName);
   if(dest.isFile()){
    if(dest.length()!=copied||!fileSha(dest).equals(sha))
     throw new SecurityException("Внутренний файл с тем же SHA повреждён");
    temp.delete();
   }else if(!temp.renameTo(dest))throw new IllegalStateException("Не удалось сохранить GGUF");
   verified.put(finalName,true);
   return new ImportedFile(finalName,sha,copied);
  }finally{temp.delete();}
 }
 public synchronized Entry importFile(Uri uri,String label,String expectedSha,
                                       String attachProjectorTo)throws Exception{
  if(uri==null||!"content".equals(uri.getScheme()))
   throw new SecurityException("Только Android Content Picker");
  if(attachProjectorTo==null&&entries.size()>MAX_IMPORTS+4)
   throw new IllegalStateException("Слишком много импортированных GGUF");
  InputStream stream=app.getContentResolver().openInputStream(uri);
  return register(copy(stream,expectedSha),label,attachProjectorTo);
 }
 public synchronized Entry download(String link,String label,String expectedSha,
                                    String attachProjectorTo)throws Exception{
  if(attachProjectorTo==null&&entries.size()>MAX_IMPORTS+4)
   throw new IllegalStateException("Слишком много импортированных GGUF");
  HttpURLConnection connection=ModelLabNetworkPolicy.open(link);
  try{
   if(connection.getContentLengthLong()>MAX_IMPORT_BYTES)
    throw new IllegalArgumentException("Удалённый GGUF больше лимита 3,5 ГБ");
   return register(copy(connection.getInputStream(),expectedSha),label,attachProjectorTo);
  }finally{connection.disconnect();}
 }
 private Entry register(ImportedFile file,String label,String projectorFor)throws Exception{
  if(projectorFor!=null){
   Entry found=find(projectorFor);
   if(found==null||found.bundled)throw new IllegalArgumentException(
     "Файл mmproj можно привязать только к импортированной модели");
   Entry next=found.withProjector(file.filename,file.bytes,file.sha);
   int index=entries.indexOf(found);
   entries.set(index,next);persist();return next;
  }
  String id="import:"+file.sha.substring(0,16);
  Entry existing=find(id);
  if(existing!=null)return existing;
  Entry next=new Entry(id,display(label),file.filename,file.bytes,file.sha,
      null,0,null,false);
  entries.add(next);persist();return next;
 }
 private File ensure(String name,long bytes,String sha,boolean bundled)throws Exception{
  if(!simpleName(name)||sha==null||!sha.matches("[0-9a-f]{64}")||
      bytes<128||bytes>MAX_IMPORT_BYTES)
   throw new SecurityException("Некорректный файл или контрольная сумма GGUF");
  File target=new File(directory,name);
  if(target.isFile()&&target.length()==bytes){
   if(Boolean.TRUE.equals(verified.get(name))||fileSha(target).equals(sha)){
    verified.put(name,true);return target;
   }
   if(!bundled)throw new SecurityException("Импортированный файл изменился");
  }
  if(!bundled)throw new IllegalArgumentException("Импортированный GGUF отсутствует");
  StatFs stats=new StatFs(directory.getAbsolutePath());
  if(stats.getAvailableBytes()<bytes+SPACE_RESERVE)
   throw new IllegalStateException("Мало места для распаковки предустановленного GGUF");
  File temp=File.createTempFile("bundled-",".partial",directory);
  try(InputStream in=app.getAssets().open("models/"+name);
      FileOutputStream out=new FileOutputStream(temp)){
   MessageDigest digest=MessageDigest.getInstance("SHA-256");
   byte[] buf=new byte[131072];int n;long total=0;
   while((n=in.read(buf))!=-1){
    total+=n;
    if(total>bytes)throw new SecurityException("Неверный размер GGUF внутри APK");
    out.write(buf,0,n);digest.update(buf,0,n);
   }
   out.getFD().sync();
   if(total!=bytes||!hash(digest.digest()).equals(sha))
    throw new SecurityException("Неверный SHA-256 встроенной модели");
  }catch(Exception failure){temp.delete();throw failure;}
  if(target.exists()&&!target.delete()){temp.delete();throw new IllegalStateException("Cannot replace model");}
  if(!temp.renameTo(target)){temp.delete();throw new IllegalStateException("Cannot commit model");}
  verified.put(name,true);return target;
 }
 public synchronized File modelFile(Entry e)throws Exception{
  Entry present=find(e.id);
  if(present==null)throw new IllegalStateException("Эта модель удалена");
  return ensure(present.modelName,present.modelBytes,present.modelSha,present.bundled);
 }
 public synchronized File projectorFile(Entry e)throws Exception{
  Entry present=find(e.id);
  if(present==null||!present.vision())throw new IllegalArgumentException("Не назначен mmproj GGUF");
  return ensure(present.projectorName,present.projectorBytes,
    present.projectorSha,present.bundled);
 }
 public synchronized void remove(Entry model)throws Exception{
  Entry current=find(model.id);
  if(current==null||current.bundled)throw new IllegalArgumentException("Встроенную модель нельзя удалить");
  entries.remove(current);
  persist();
  // Imported files are addressed by SHA. Do not delete a blob used by another model.
  for(String filename:new String[]{current.modelName,current.projectorName}){
   if(filename==null)continue;
   boolean stillUsed=false;
   for(Entry other:entries)
    if(filename.equals(other.modelName)||filename.equals(other.projectorName)){
     stillUsed=true;break;
    }
   if(!stillUsed)new File(directory,filename).delete();
  }
 }
}
