package org.localpilot.android.web;
import java.io.*;

/** New per-profile paths never overwrite URL-only legacy examples. */
public final class WebLessonStore {
 private final File root;
 public WebLessonStore(File root){this.root=root;}
 private static String game(String game){if(game==null||!game.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("game ID");return game;}
 public File namedFile(String game,String name){return new File(new File(root,"web-lessons-v2/"+game(game)),WebLesson.profileKey(name)+".lpweb");}
 public WebLesson loadNamed(String game,String origin,String name)throws IOException{
  File file=namedFile(game,name);WebLesson expected=new WebLesson(game,origin,name);
  WebLesson loaded=(file.isFile()||new File(file+".bak").isFile())?WebLesson.loadRecovering(file):expected;
  if(!loaded.identified()||!loaded.game.equals(expected.game)||!loaded.origin.equals(expected.origin)||!loaded.profileId.equals(expected.profileId))throw new IOException("Stored profile identity mismatch");return loaded;
 }
 public void saveNamed(WebLesson data)throws IOException{if(!data.identified())throw new IOException("Unassigned data must stay in legacy archive");data.save(namedFile(data.game,data.profileName));}
 public File legacyFile(String game){return new File(root,"web-lessons/"+game(game)+".lpweb");}
 public WebLesson loadLegacy(String game)throws IOException{
  File f=legacyFile(game);try{return WebLesson.load(f);}catch(IOException failure){try{return WebLesson.load(new File(f+".bak"));}catch(IOException backup){failure.addSuppressed(backup);throw failure;}}
 }
 public java.util.List<File> archiveFiles(){
  File[] files=new File(root,"web-legacy-imports").listFiles(f->f.isFile()&&f.getName().matches("[0-9a-f]{64}-[0-9a-f]{64}\\.lpweb"));
  if(files==null)return java.util.List.of();java.util.ArrayList<File> out=new java.util.ArrayList<>(java.util.Arrays.asList(files));
  out.sort(java.util.Comparator.comparingLong(File::lastModified).reversed());return java.util.Collections.unmodifiableList(out);
 }
 public File archiveLegacy(WebLesson data)throws IOException{
  if(data.identified())throw new IOException("Named dataset is not legacy");
  byte[] encoded=data.encode();String digest=WebLesson.sha(new String(encoded,java.nio.charset.StandardCharsets.UTF_8));
  File f=new File(root,"web-legacy-imports/"+data.game+"-"+digest+".lpweb");
  if(f.exists()){WebLesson old=WebLesson.load(f);if(!java.util.Arrays.equals(old.encode(),encoded))throw new IOException("Legacy archive collision");}
  else data.save(f);
  return f;
 }
}
