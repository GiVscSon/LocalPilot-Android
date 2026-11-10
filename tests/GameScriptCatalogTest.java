package org.localpilot.android;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
/** No Android classes. A script cannot mint gestures or training reward. */
public final class GameScriptCatalogTest {
 private static int checks;
 private static void check(boolean truth,String reason){checks++;if(!truth)throw new AssertionError(reason);}
 private static void rejects(String script,String reason){
  try{GameScriptCatalog.parse(new StringReader(script));throw new AssertionError(reason);}
  catch(IllegalArgumentException expected){checks++;}
  catch(IOException unexpected){throw new AssertionError(unexpected);}
 }
 public static void main(String[] args)throws Exception{
  String raw=Files.readString(Path.of("app/src/main/assets/localpilot-game-script.lps"),StandardCharsets.UTF_8);
  GameScriptCatalog catalog=GameScriptCatalog.parse(new StringReader(raw));
  check(catalog.routeCount()==11,"all supported real profile routes required");
  check(catalog.modelCount()==16,"all sixteen simulator models required");
  check(catalog.route(8)==null,"unsupported profile cannot dispatch");
  check(catalog.route(11).controller()==GameScriptCatalog.Controller.GRID_PROTECTED,
    "Nashchupay protected swap cannot be changed to TRACE");
  check(catalog.route(9).controller()==GameScriptCatalog.Controller.BUBBLE,
    "Bubble routes to normal launcher/board detector");
  check(catalog.route(4).controller()==GameScriptCatalog.Controller.BOARD2048,
    "2048 script routes to existing verified board policy");
  check(catalog.route(6).softStalls()==2&&catalog.route(4).softStalls()==2&&catalog.route(9).softStalls()==0,
    "scripts allow only bounded grid recovery, never blind Bubble retry");
  check(catalog.model("hexon").controller()==GameScriptCatalog.Controller.SIM_ONLY,
    "unverified Hexon real controller stays simulator-only");
  check(catalog.model("nes_platformer").training()==GameScriptCatalog.Evidence.SIM_ONLY,
    "retro simulated training cannot become real game reward");
  check(catalog.model("shipuchka").training()==GameScriptCatalog.Evidence.SIM_ONLY,
    "simulated Bubble cannot become verified real shot");
  rejects(raw.replace("route|11|GRID_PROTECTED","route|11|GRID_TRACE"),
    "script cannot rewrite protected skull mechanic");
  rejects(raw.replace("route|6|GRID_SWAP|VISUAL_REAL|2","route|6|GRID_SWAP|VISUAL_REAL|3"),
    "script cannot enable unbounded soft recovery");
  rejects(raw.replace("model|shipuchka|BUBBLE|SIM_ONLY","model|shipuchka|BUBBLE|VISUAL_REAL"),
    "simulation must never mint visual real reward");
  rejects(raw.replace("model|hexon|SIM_ONLY|SIM_ONLY","model|hexon|ARI|SIM_ONLY")+
    "tap|0|100|100\n","script cannot directly express tap coordinates");
  rejects(raw.replace("route|9|BUBBLE","route|9|PADDLE"),
    "script cannot route bubble to another controller");
  rejects(raw.replace("model|kotobashnya|SIM_ONLY|SIM_ONLY",""),
    "missing model source must fail closed");
  System.out.println("Scripted 16-model catalog, safe controller routing & provenance: "+checks+" PASS");
 }
}
