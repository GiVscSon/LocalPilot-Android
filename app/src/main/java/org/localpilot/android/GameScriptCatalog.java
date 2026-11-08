package org.localpilot.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Scripted *routing* and learning provenance, not an alternate gesture issuer.
 * Every action is still proposed by the existing visual policies and gated by
 * ActionGate, InputPermit, STOP, TTL, ROI and independent visual witnesses.
 *
 * LPS1 is a strictly parsed, non-executable, versioned script language.
 * It cannot express raw gesture coordinates, grant permission or mint reward.
 */
public final class GameScriptCatalog {
 public enum Controller {
  ARI,PADDLE,TRAFFIC,FLAPPY,BOARD2048,GRID_SWAP,GRID_GROUP,
  BUBBLE,GRID_TRACE,GRID_PROTECTED,SIM_ONLY;
  public boolean grid(){return this==GRID_SWAP||this==GRID_GROUP||this==GRID_TRACE||this==GRID_PROTECTED;}
 }
 public enum Evidence { VISUAL_REAL, SIM_ONLY }
 public record Route(int profile,Controller controller,Evidence training,int softStalls,int minimumFrameMs){
  public Route {
   if(expected(profile)!=controller||training!=Evidence.VISUAL_REAL
      ||softStalls<0||softStalls>2||minimumFrameMs<80||minimumFrameMs>1200)
    throw new IllegalArgumentException("unsafe or incompatible real-game script");
   if(!controller.grid()&&softStalls!=0)
    throw new IllegalArgumentException("no soft-recovery adapter for this controller");
  }
 }
 public record Model(String id,Controller controller,Evidence training){
  public Model {
   if(id==null||!id.matches("[a-z0-9_]{3,48}")
      ||controller==null||training!=Evidence.SIM_ONLY)
    throw new IllegalArgumentException("model scripts are simulation-only");
  }
 }
 private static final int[] ROUTES={0,1,2,3,4,5,6,7,9,10,11};
 private static final Set<String> MODELS=Set.of(
   "shipuchka","prygun_ari","match_world","karkanoid","market_rush",
   "goshan","game_2048","hexon","nashchupay_khod","kotobashnya",
   "nes_platformer","nes_tactical_arena","dos_platform_explorer",
   "dos_corridor_action","sega_high_speed_act","sega_brawler_rungun");
 private final Map<Integer,Route> routes;
 private final Map<String,Model> models;
 private GameScriptCatalog(Map<Integer,Route> routes,Map<String,Model> models){
  this.routes=Collections.unmodifiableMap(new HashMap<>(routes));
  this.models=Collections.unmodifiableMap(new HashMap<>(models));
 }
 public Route route(int profile){return routes.get(profile);}
 public Model model(String id){return models.get(id);}
 public int routeCount(){return routes.size();}
 public int modelCount(){return models.size();}
 /** Strict match against the app's built-in gesture semantics. A script cannot
  * redirect the protected skull swap to TRACE_LINE or map 2048 to free taps. */
 private static Controller expected(int profile){
  return switch(profile){
   case 0->Controller.ARI;
   case 1->Controller.PADDLE;
   case 2->Controller.TRAFFIC;
   case 3->Controller.FLAPPY;
   case 4,5->Controller.BOARD2048;
   case 6->Controller.GRID_SWAP;
   case 7->Controller.GRID_GROUP;
   case 9->Controller.BUBBLE;
   case 10->Controller.GRID_TRACE;
   case 11->Controller.GRID_PROTECTED;
   default->Controller.SIM_ONLY;
  };
 }
 private static int number(String value) {
  if(value==null||!value.matches("[0-9]{1,4}"))throw new IllegalArgumentException("script integer");
  return Integer.parseInt(value);
 }
 public static GameScriptCatalog parse(Reader reader)throws IOException{
  if(reader==null)throw new IllegalArgumentException("missing script");
  BufferedReader in=new BufferedReader(reader);String first=in.readLine();
  if(!"LPS1".equals(first))throw new IllegalArgumentException("script version");
  Map<Integer,Route> routes=new HashMap<>();
  Map<String,Model> models=new HashMap<>();
  String line;int count=0;
  while((line=in.readLine())!=null){
   if(++count>80||line.length()>200)throw new IllegalArgumentException("script size");
   if(line.isBlank()||line.startsWith("#"))continue;
   String[] cells=line.split("\\|",-1);
   if(cells.length==6&&"route".equals(cells[0])){
    int profile=number(cells[1]);
    Controller controller=Controller.valueOf(cells[2]);
    Evidence training=Evidence.valueOf(cells[3]);
    int stalls=number(cells[4]),frameMs=number(cells[5]);
    if(routes.putIfAbsent(profile,new Route(profile,controller,training,stalls,frameMs))!=null)
     throw new IllegalArgumentException("duplicate real route");
   }else if(cells.length==4&&"model".equals(cells[0])){
    Controller controller=Controller.valueOf(cells[2]);
    Evidence training=Evidence.valueOf(cells[3]);
    Model model=new Model(cells[1],controller,training);
    if(!MODELS.contains(model.id())||models.putIfAbsent(model.id(),model)!=null)
     throw new IllegalArgumentException("unexpected or duplicate simulator script");
   }else throw new IllegalArgumentException("unknown script instruction");
  }
  if(routes.size()!=ROUTES.length||models.size()!=MODELS.size())
   throw new IllegalArgumentException("missing script routes or simulator models");
  for(int key:ROUTES)if(!routes.containsKey(key))
   throw new IllegalArgumentException("missing real-game controller route");
  for(String name:MODELS)if(!models.containsKey(name))
   throw new IllegalArgumentException("missing simulator provenance");
  return new GameScriptCatalog(routes,models);
 }
}
