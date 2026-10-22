package org.localpilot.android.web;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
public final class WebProfilesTest{
 private static int checks;
 private interface Work{void run()throws Exception;}
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static void invalid(Work work,String why)throws Exception{try{work.run();throw new AssertionError(why);}catch(IllegalArgumentException|IllegalStateException|IOException expected){checks++;}}
 private static float[] state(){float[] f=new float[256];Arrays.fill(f,.1f);return f;}
 private static WebLesson.Sample row(String session,long time,float x){return new WebLesson.Sample(session,time,.65f,new WebLesson.Action(WebLesson.Kind.TAP,x,.5f,x,.5f,100),state(),state());}
 public static void main(String[] args)throws Exception{
  String game=WebLesson.gameId("https://market.yandex.ru/");String origin="https://market.yandex.ru";
  WebLesson a=new WebLesson(game,origin,"Карканоид"),b=new WebLesson(game,origin,"Гошан");a.add(row("first",1,.2f));b.add(row("first",1,.8f));
  check(!a.profileId.equals(b.profileId),"same URL has independent game IDs");
  check(a.predict(state(),.65f).x==.2f&&b.predict(state(),.65f).x==.8f,"opposite actions stay in their own model");
  invalid(()->a.merged(b),"different games cannot merge");
  check(a.copy().profileId.equals(a.profileId)&&a.copy().size()==1,"copy retains game identity");
  byte[] encoded=a.encode();check(new String(encoded,StandardCharsets.UTF_8).startsWith("LOCALPILOT-WEB-v2\n"),"named format v2");
  WebLesson round=WebLesson.decode(encoded);check(round.profileName.equals("Карканоид")&&round.profileId.equals(a.profileId),"profile metadata roundtrip");
  check(Arrays.equals(encoded,round.encode()),"v2 byte roundtrip");check(a.merged(round).size()==1,"same profile deduplicates");
  byte[] changed=encoded.clone();changed[90]^=1;invalid(()->WebLesson.decode(changed),"metadata tampering rejected");
  check(WebLesson.profileKey("  КАРКАНОИД ").equals(a.profileId),"case/outer whitespace normalized");
  check(WebLesson.profileKey("Game  A").equals(WebLesson.profileKey("Game A")),"space normalization");
  check(WebLesson.profileKey("Ｇａｍｅ").equals(WebLesson.profileKey("game")),"Unicode compatibility normalization");
  for(String bad:new String[]{""," ","a\nb","a\rb","a|b","a\u202eb","a\u2028b","x".repeat(65)})invalid(()->new WebLesson(game,origin,bad),"invalid profile name");
  invalid(()->WebLesson.profileKey(null),"null profile");invalid(()->WebLesson.https(null),"null URL");
  WebLesson legacy=new WebLesson(game,origin);legacy.add(row("one",1,.2f));legacy.add(row("one",2,.2f));legacy.add(row("two",3,.8f));
  byte[] old=legacy.encode();check(new String(old,StandardCharsets.UTF_8).startsWith("LOCALPILOT-WEB-v1\n"),"legacy format preserved");
  WebLesson oldRound=WebLesson.decode(old);check(!oldRound.identified()&&oldRound.size()==3,"legacy imports as unassigned");
  check(Arrays.equals(old,oldRound.encode()),"legacy byte roundtrip");
  check(oldRound.predict(state(),.65f)==null,"unassigned data cannot play");invalid(legacy::trainReport,"unassigned data cannot train");
  invalid(()->a.merged(legacy),"no automatic legacy merge");
  check(new ArrayList<>(legacy.sessions().values()).equals(List.of(2,1)),"original session boundaries retained");
  try{legacy.sessions().put("other",5);throw new AssertionError("mutable sessions");}catch(UnsupportedOperationException expected){checks++;}
  WebLesson one=legacy.session("one");check(one.size()==2&&!one.identified(),"selecting a session does not invent a game label");
  WebLesson assigned=one.assigned("Карканоид");check(assigned.identified()&&assigned.profileId.equals(a.profileId)&&assigned.size()==2,"explicit assignment of reviewed rows");
  check(!legacy.identified()&&legacy.size()==3,"original mixed data unchanged");invalid(()->legacy.session("missing"),"unknown session");
  invalid(()->a.assigned("Гошан"),"named dataset cannot be relabeled as other game");
  check(legacy.session("one").assignedTo(WebLesson.gameId("https://market.yandex.ru/new"),origin,"Карканоид").size()==2,"explicit legacy assignment on same site after URL changed");
  invalid(()->legacy.assignedTo(game,"https://other.example","Карканоид"),"legacy cannot migrate to unrelated origin");
  invalid(()->a.assignedTo(game,origin,"Гошан"),"named data cannot take legacy reassignment path");
  Path root=Files.createTempDirectory("localpilot-web-profiles-");WebLessonStore store=new WebLessonStore(root.toFile());
  File oldFile=store.legacyFile(game);legacy.save(oldFile);byte[] preserved=Files.readAllBytes(oldFile.toPath());
  check(store.loadNamed(game,origin,"Карканоид").size()==0,"new game does not inherit old mixed examples");
  check(Arrays.equals(preserved,Files.readAllBytes(oldFile.toPath())),"loading profile preserves original bytes");
  store.saveNamed(a);store.saveNamed(b);
  check(!store.namedFile(game,"Карканоид").equals(store.namedFile(game,"Гошан")),"separate paths on same URL");
  check(store.loadNamed(game,origin,"Карканоид").predict(state(),.65f).x==.2f,"first file contains first policy only");
  check(store.loadNamed(game,origin,"Гошан").predict(state(),.65f).x==.8f,"second file contains second policy only");
  check(Arrays.equals(preserved,Files.readAllBytes(oldFile.toPath())),"named saves leave legacy untouched");
  File archive=store.archiveLegacy(legacy);byte[] archived=Files.readAllBytes(archive.toPath());check(WebLesson.load(archive).size()==3,"archive retains all old sessions");
  check(store.archiveLegacy(legacy).equals(archive)&&Arrays.equals(archived,Files.readAllBytes(archive.toPath())),"archive is idempotent");
  check(store.archiveFiles().size()==1&&store.archiveFiles().get(0).equals(archive),"imported legacy archive remains browsable");
  invalid(()->store.saveNamed(legacy),"unassigned save prohibited in profile store");invalid(()->store.archiveLegacy(a),"named data not silently archived as legacy");
  store.saveNamed(a.merged(assigned));check(store.loadNamed(game,origin,"Карканоид").size()==3,"reviewed session is copied and deduplicated into profile");
  check(WebLesson.load(oldFile).size()==3&&!WebLesson.load(oldFile).identified(),"legacy still unassigned after copy");
  Files.writeString(oldFile.toPath(),"corrupt");legacy.save(new File(oldFile+".bak"));
  check(store.loadLegacy(game).size()==3&&Files.readString(oldFile.toPath()).equals("corrupt"),"legacy backup read does not rewrite original");
  b.save(store.namedFile(game,"Карканоид"));invalid(()->store.loadNamed(game,origin,"Карканоид"),"wrong profile metadata in expected path rejected");
  check(store.namedFile(game,"../../Other").toPath().normalize().startsWith(root),"profile labels never become paths");
  WebLesson evaluated=new WebLesson(game,origin,"Проверка");for(int i=0;i<6;i++)evaluated.add(row("train",i,.2f));for(int i=0;i<4;i++)evaluated.add(row("test",100+i,.2f));
  check(evaluated.trainReport().contains("Покрытие 100.0%")&&evaluated.trainReport().contains("Проверка"),"coverage and selected game reported");
  System.out.println("Web profiles: "+checks+" assertions PASS");
 }
}
