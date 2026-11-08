package org.localpilot.android.learning;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.localpilot.android.Board2048;
import org.localpilot.android.PixelBoardReader;
public final class ModelLibraryTest {
 private static int checks;
 private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 private static void rejects(Checked action)throws Exception{try{action.run();throw new AssertionError("expected rejection");}catch(IOException|IllegalArgumentException expected){checks++;}}
 private interface Checked{void run()throws Exception;}
 private static byte[] rewriteManifest(byte[] checkpoint,java.util.function.UnaryOperator<String> edit)throws Exception{
  Map<String,byte[]> files=new LinkedHashMap<>();
  try(var zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(checkpoint))){java.util.zip.ZipEntry e;
   while((e=zip.getNextEntry())!=null)files.put(e.getName(),zip.readAllBytes());
  }
  files.put("manifest.json",edit.apply(new String(files.get("manifest.json"),java.nio.charset.StandardCharsets.UTF_8)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");
  String weights=java.util.HexFormat.of().formatHex(hash.digest(files.get("weights.bin")));
  String manifest=java.util.HexFormat.of().formatHex(hash.digest(files.get("manifest.json")));
  files.put("SHA256SUMS",(weights+"  weights.bin\n"+manifest+"  manifest.json\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  try(var zip=new java.util.zip.ZipOutputStream(out)){
   for(var entry:files.entrySet()){zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}
  }return out.toByteArray();
 }
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("lp-library-");LabModelStore store=new LabModelStore(root.toFile());
  for(TaskSchema.GameId g:TaskSchema.GameId.values()){
   store.ensureBundled(g,name->Files.newInputStream(Path.of("app/src/main/assets",name)));
   check(store.versions(g).size()==1,"bundled version for "+g);check(store.loadSelected(g).gameId==g,"typed model");
  }
  TaskSchema.GameId game=TaskSchema.GameId.GAME_2048;String initial=store.selected(game);
  ByteArrayOutputStream exported=new ByteArrayOutputStream();store.exportVersion(game,initial,exported);
  check(store.importVersion(game,new ByteArrayInputStream(exported.toByteArray())).equals(initial),"deduplicate same bytes");
  rejects(()->store.importVersion(game,new ByteArrayInputStream(new byte[]{1,2,3})));
  check(store.selected(game).equals(initial),"invalid import preserves selection");
  rejects(()->store.select(game,"../../not-a-version"));
  byte[] wrongGame=rewriteManifest(exported.toByteArray(),m->m.replace("\"game_id\":\"game_2048\"","\"game_id\":\"hexon\""));
  rejects(()->store.importVersion(game,new ByteArrayInputStream(wrongGame)));
  byte[] duplicate=rewriteManifest(exported.toByteArray(),m->m.substring(0,m.length()-1)+",\"actions\":4}");
  rejects(()->store.importVersion(game,new ByteArrayInputStream(duplicate)));

  TaskSchema.GameId wrong=TaskSchema.GameId.HEXON;
  rejects(()->store.importVersion(wrong,new ByteArrayInputStream(exported.toByteArray())));
  Path other=Files.createTempFile("lp-model-other-",".zip");
  ModelCheckpoint.save(other.toFile(),TaskSchema.forGame(game),new StudentPolicy(game,93,true));
  String next;try(InputStream in=Files.newInputStream(other)){next=store.importVersion(game,in);}
  check(!initial.equals(next)&&store.versions(game).size()==2,"immutable second version");
  store.select(game,initial);check(store.selected(game).equals(initial),"explicit rollback");
  check(!Files.exists(root.resolve("adaptive-engine-v1")),"LAB store never overwrites real models");
  byte[] old=Files.readAllBytes(root.resolve("lab-model-versions-v1").resolve(game.id).resolve(initial+".zip"));
  store.train(game,20261108);check(Arrays.equals(old,Files.readAllBytes(root.resolve("lab-model-versions-v1").resolve(game.id).resolve(initial+".zip"))),"training preserves old version");
  Board2048 board=new Board2048(new int[]{2,2,0,0,4,0,0,0,8,0,0,0,16,0,0,0});
  TaskSchema schema=TaskSchema.forGame(game);
  ShadowEvaluation.Observation observation=new ShadowEvaluation.Observation(schema,ShadowEvaluation.Source.SAVED_REAL_FRAME,
    1,ObservationEncoder.encode2048(board),board.choose());
  ShadowEvaluation.Review review=ShadowEvaluation.review(observation,schema,store.loadSelected(game));
  check(review.legal()&&!review.mayDispatch()&&!review.mayPromote(),"shadow never authorizes input/transfer");
  rejects(()->ShadowEvaluation.review(observation,TaskSchema.forGame(wrong),store.loadSelected(wrong)));
  PixelBoardReader.Pixels dark=(x,y)->x<100&&y<100?0xeee4da:0xb8aa9c;
  rejects(()->PixelBoardReader.read(dark,400,400,0,0,400,400));
  check(PixelBoardReader.read(dark,400,400,0,0,400,400,PixelBoardReader.Skin.CLASSIC_DARK_EMPTY).cells[0]==2,"explicit observed skin");
  rejects(()->PixelBoardReader.read((x,y)->x<100&&y<100?0xeee4da:0x998877,400,400,0,0,400,400,PixelBoardReader.Skin.CLASSIC_DARK_EMPTY));
  System.out.println("MODEL_LIBRARY_PASS checks="+checks+" versions/import/export/rollback/training/shadow");
 }
}
