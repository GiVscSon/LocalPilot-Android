import java.nio.file.*;
import java.util.*;
import org.localpilot.shared.GameLabReplay;

class GameLabReplayTest {
 private static void yes(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private static void bad(Runnable r){try{r.run();throw new AssertionError("expected rejection");}
  catch(IllegalArgumentException expected){}}
 public static void main(String[] argv)throws Exception{
  String success=GameLabReplay.row("BOARD2048",4,"NONE",.2f,.8f,.9f,.8f,150,12,14,"CHANGED");
  String noChange=GameLabReplay.row("BOARD2048",4,"NONE",.2f,.8f,.9f,.8f,150,14,14,"NO_CHANGE");
  String blocked=GameLabReplay.row("BOARD2048",4,"PANEL_OVERLAP",.2f,.8f,.9f,.8f,150,14,14,"BLOCKED");
  String replay=GameLabReplay.PREFIX+String.join("\n",success,success,success,success,success,noChange,blocked)+"\n";
  yes(GameLabReplay.parse(replay).size()==7,"all genuine replay samples parsed");
  GameLabReplay.Shadow model=new GameLabReplay.Shadow();
  yes(model.importReplay(replay),"first import");
  yes(!model.importReplay(replay),"deduped identical import");
  yes(model.imports()==1,"no duplicated imports");
  yes(model.total(6,2)==6&&model.changed(6,2)==5,"sim-only counts, blocked fault ignored");
  yes(model.recommend("BOARD2048").startsWith("RIGHT"),"shadow training prior");
  yes(model.report().contains("не управляет играми"),"safe report label");
  Path path=Files.createTempFile("lab-shadow-", ".txt");
  try{
   model.save(path.toFile());
   GameLabReplay.Shadow restored=GameLabReplay.Shadow.load(path.toFile());
   yes(restored.total(6,2)==6&&restored.imports()==1,"persisted shadow");
   yes(!restored.importReplay(replay),"durable dedupe");
  }finally{Files.deleteIfExists(path);}
  bad(()->GameLabReplay.parse(replay.replace("GAMELAB_SIMULATED","REAL_GAME")));
  bad(()->GameLabReplay.parse(replay.replace("BOARD2048,4","BOARD2048,9")));
  bad(()->GameLabReplay.parse(replay.replace("12,14,CHANGED","12,12,CHANGED")));
  bad(()->GameLabReplay.parse(replay.replace(GameLabReplay.MARKER,"LOCALPILOT_REAL_MODEL")));
  bad(()->GameLabReplay.row("BUBBLE",9,"NONE",-.1f,.3f,.5f,.5f,90,1,2,"CHANGED"));
  bad(()->GameLabReplay.parse(GameLabReplay.PREFIX));
  bad(()->GameLabReplay.Shadow.decode("NOT_A_REAL_SHADOW_MODEL"));
  System.out.println("GAMELAB_SHADOW_BRIDGE_PASS");
 }
}
