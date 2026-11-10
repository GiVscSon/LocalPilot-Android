import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.localpilot.gamelab.GameLabEngine;
import org.localpilot.shared.SimExperience;
import org.localpilot.shared.SimLabPolicy;

/**
 * Headless autonomous GameLab self-play:
 * train with bounded simulator transitions, evaluate random baseline and
 * learned lab-only action preference on identical held-out seeds.
 *
 * This NEVER runs real Android games, executes gestures, touches ModelStore,
 * uses Market ACK, or promotes simulated learning to production.
 */
public final class SimAutoplay {
 private SimAutoplay(){}

 private record Move(float x,float y,float toX,float toY,int durationMs){}
 private static final class Results {
  int moves,changed,blocked,noChange,episodes;
  long totalScore,physicsScore,gestureScore;
  final StringBuilder events=new StringBuilder(
   "game,episode,step,result,score_before,score_after,from_x,from_y,to_x,to_y,tick_score_before,tick_score_after\n");
  void add(GameLabEngine.Game game,int episode,int step,Move move,
           GameLabEngine.Result result,int beforeScore,int afterScore,int tickBefore,int tickAfter){
   physicsScore+=tickAfter-tickBefore;gestureScore+=afterScore-beforeScore;
   moves++;
   switch(result){
    case CHANGED -> changed++;
    case BLOCKED -> blocked++;
    case NO_CHANGE -> noChange++;
   }
   events.append(game.name()).append(',').append(episode).append(',')
     .append(step).append(',').append(result.name()).append(',')
     .append(beforeScore).append(',').append(afterScore).append(',')
     .append(SimExperience.quantize(move.x())).append(',')
     .append(SimExperience.quantize(move.y())).append(',')
     .append(SimExperience.quantize(move.toX())).append(',')
     .append(SimExperience.quantize(move.toY())).append(',')
     .append(tickBefore).append(',').append(tickAfter).append('\n');
  }
  void episodeCompleted(int score){episodes++;totalScore+=score;}
  double changedPct(){return moves==0?0:100.0*changed/moves;}
  double meanScore(){return episodes==0?0:(double)totalScore/episodes;}
  double meanPhysicsScore(){return episodes==0?0:(double)physicsScore/episodes;}
  double meanGestureScore(){return episodes==0?0:(double)gestureScore/episodes;}
 }
 private static float cx(GameLabEngine engine,int col){
  return GameLabEngine.GRID_LEFT+(col+.5f)*(GameLabEngine.GRID_RIGHT-GameLabEngine.GRID_LEFT)/engine.n;
 }
 private static float cy(GameLabEngine engine,int row){
  return GameLabEngine.GRID_TOP+(row+.5f)*(GameLabEngine.GRID_BOTTOM-GameLabEngine.GRID_TOP)/engine.n;
 }
 private static Move move(float x,float y,float xx,float yy){
  return new Move(x,y,xx,yy,140);
 }
 private static List<Move> candidates(GameLabEngine engine){
  List<Move> result=new ArrayList<>();
  switch(engine.game){
   case MATCH,PROTECTED_SWAP -> {
    for(int row=0;row<engine.n;row++)for(int col=0;col<engine.n;col++){
     int from=row*engine.n+col;
     if(engine.protectedCell(from))continue;
     if(col+1<engine.n&&!engine.protectedCell(from+1)){
      result.add(move(cx(engine,col),cy(engine,row),
        cx(engine,col+1),cy(engine,row)));
      result.add(move(cx(engine,col+1),cy(engine,row),
        cx(engine,col),cy(engine,row)));
     }
     if(row+1<engine.n&&!engine.protectedCell(from+engine.n)){
      result.add(move(cx(engine,col),cy(engine,row),
        cx(engine,col),cy(engine,row+1)));
      result.add(move(cx(engine,col),cy(engine,row+1),
        cx(engine,col),cy(engine,row)));
     }
    }
   }
   case BOARD2048 -> {
    result.add(move(.5f,.45f,.3f,.45f));
    result.add(move(.5f,.45f,.7f,.45f));
    result.add(move(.5f,.45f,.5f,.25f));
    result.add(move(.5f,.45f,.5f,.65f));
   }
   case BUBBLE -> {
    for(int c=0;c<engine.n;c++)
     result.add(move(.5f,495f/640f,(c+.5f)/engine.n,96f/640f));
    // Additional aimed rays can hit a side wall before the contact field.
    result.add(move(.5f,495f/640f,.03f,.35f));
    result.add(move(.5f,495f/640f,.97f,.35f));
   }
   case PADDLE -> {
    for(float x:new float[]{.18f,.34f,.5f,.66f,.82f})
     result.add(move(.5f,.76f,x,.76f));
   }
   case HEXON -> {
    for(float x:new float[]{.16f,.5f,.84f})
     result.add(move(.5f,.66f,x,.66f));
   }
   case TRAFFIC,ARI,CAT_TOWER -> {
    result.add(move(.5f,.55f,.29f,.55f));
    result.add(move(.5f,.55f,.71f,.55f));
    result.add(move(.5f,.55f,.5f,.55f));
   }
   case FLAPPY -> result.add(move(.5f,.55f,.5f,.55f));
  }
  return result;
 }
 private static String kind(Move move){
  int dx=SimExperience.quantize(move.toX())-SimExperience.quantize(move.x());
  int dy=SimExperience.quantize(move.toY())-SimExperience.quantize(move.y());
  if(Math.max(Math.abs(dx),Math.abs(dy))<90)return "TAP";
  return Math.abs(dx)>=Math.abs(dy)?(dx<0?"LEFT":"RIGHT"):(dy<0?"UP":"DOWN");
 }
 private static Move decide(GameLabEngine engine,Random rng,SimLabPolicy lab,
                            double exploreProbability,boolean scripted){
  List<Move> possibilities=candidates(engine);
  if(possibilities.isEmpty())return null;
  // A scripted coach is a stronger LAB comparator, never a real-game policy.
  // During training, alternate demonstrations and random exploration.
  if(scripted||(lab==null&&rng.nextDouble()>exploreProbability)){
   List<float[]> inputs=new ArrayList<>();
   for(Move c:possibilities)
    inputs.add(new float[]{c.x(),c.y(),c.toX(),c.toY()});
   int index=GameLabCoach.choose(engine,inputs);
   if(index<0||index>=possibilities.size())
    throw new AssertionError("coach selected invalid action");
   return possibilities.get(index);
  }
  if(lab!=null&&rng.nextDouble()>exploreProbability){
   List<Move> favored=new ArrayList<>();
   for(Move candidate:possibilities){
    String preferred=lab.bestLabAction(engine.game.name(),
      SimExperience.quantize(candidate.x()),SimExperience.quantize(candidate.y()));
    if(!"UNKNOWN".equals(preferred)&&preferred.equals(kind(candidate)))
     favored.add(candidate);
   }
   if(!favored.isEmpty())possibilities=favored;
  }
  return possibilities.get(rng.nextInt(possibilities.size()));
 }
 private static long episodeSeed(long seed,GameLabEngine.Game game,int episode){
  return seed+game.ordinal()*1000003L+episode*9127L;
 }
 private static String seedSchedule(long seed,int episodes){
  StringBuilder json=new StringBuilder("{");
  int gameIndex=0;
  for(GameLabEngine.Game game:GameLabEngine.Game.values()){
   if(gameIndex++>0)json.append(',');
   json.append('"').append(game.name()).append("\":[");
   for(int episode=0;episode<episodes;episode++){
    if(episode>0)json.append(',');
    json.append(episodeSeed(seed,game,episode));
   }
   json.append(']');
  }
  return json.append('}').toString();
 }
 private static Results simulate(GameLabEngine.Game game,int episodes,int steps,
                                 long seed,SimLabPolicy lab,double exploreProbability,
                                 List<SimExperience.Step> replay,boolean scripted){
  Results results=new Results();
  for(int episode=0;episode<episodes;episode++){
   GameLabEngine engine=new GameLabEngine(game,episodeSeed(seed,game,episode));
   Random rng=new Random(episodeSeed(seed+7L,game,episode));
   for(int step=0;step<steps;step++){
    // Physics advances independently. Only gesture-induced state change is
    // used for a transition label, never a passive game tick.
    int tickScoreBefore=engine.score;
    for(int tick=0;tick<3;tick++)engine.tick();
    int tickScoreAfter=engine.score;
    Move candidate=decide(engine,rng,lab,exploreProbability,scripted);
    if(candidate==null)throw new IllegalStateException(
      "no safe GameLab simulator action for "+game);
    int before=engine.fingerprint(),scoreBefore=engine.score;
    GameLabEngine.Result result=engine.gesture(
       candidate.x(),candidate.y(),candidate.toX(),candidate.toY(),
       candidate.durationMs());
    int after=engine.fingerprint(),scoreAfter=engine.score;
    if(("CHANGED".equals(result.name()))!=(before!=after))
     throw new AssertionError("inconsistent simulator change witness");
    results.add(game,episode,step,candidate,result,scoreBefore,scoreAfter,tickScoreBefore,tickScoreAfter);
    if(replay!=null)replay.add(new SimExperience.Step(replay.size()+1,
      engine.game.name(),engine.fault.name(),
      SimExperience.quantize(candidate.x()),SimExperience.quantize(candidate.y()),
      SimExperience.quantize(candidate.toX()),SimExperience.quantize(candidate.toY()),
      candidate.durationMs(),before,after,scoreBefore,scoreAfter,result.name()));
   }
   results.episodeCompleted(engine.score);
  }
  return results;
 }
 private static long number(String text,String name,long low,long high){
  long value=Long.parseLong(text);
  if(value<low||value>high)throw new IllegalArgumentException(name+" outside safe range");
  return value;
 }
 public static void main(String[] args)throws Exception{
  Path output=Path.of("build/reports/sim-autoplay");
  long seed=20261108L;
  int trainEpisodes=8,evalEpisodes=4,steps=10;
  for(int i=0;i<args.length;i++){
   if(i+1>=args.length)throw new IllegalArgumentException("missing value for "+args[i]);
   String arg=args[i],value=args[++i];
   switch(arg){
    case "--out" -> output=Path.of(value);
    case "--seed" -> seed=number(value,arg,0,1000000000000L);
    case "--train-episodes" -> trainEpisodes=(int)number(value,arg,1,32);
    case "--eval-episodes" -> evalEpisodes=(int)number(value,arg,1,32);
    case "--steps" -> steps=(int)number(value,arg,1,80);
    default -> throw new IllegalArgumentException("unknown option "+arg);
   }
  }
  int total=GameLabEngine.Game.values().length;
  if((long)total*trainEpisodes*steps>SimExperience.MAX_ROWS)
   throw new IllegalArgumentException("training replay exceeds bounded experience contract");
  Files.createDirectories(output);
  List<SimExperience.Step> training=new ArrayList<>();
  // Train on synthetic experience only. Do not use evaluation seeds in training.
  for(GameLabEngine.Game game:GameLabEngine.Game.values())
   simulate(game,trainEpisodes,steps,seed,null,.50,training,false);
  String serialized=SimExperience.encode(training);
  List<SimExperience.Step> decoded=SimExperience.decode(serialized);
  if(decoded.size()!=total*trainEpisodes*steps)
   throw new AssertionError("replay missing training actions");
  SimLabPolicy learned=SimLabPolicy.train(decoded);
  StringBuilder baselineCsv=new StringBuilder();
  StringBuilder learnedCsv=new StringBuilder();
  StringBuilder scriptedCsv=new StringBuilder();
  StringBuilder json=new StringBuilder();
  StringBuilder markdown=new StringBuilder(
    "# GameLab automated self-play · simulator only\n\n"+
    "This is offline GameLab, not a Market acceptance test or production neural transfer.\n"+
    "No Android ACK, AccessibilityService or real-game gesture is produced.\n\n"+
    "Measurement: immediate gesture changes only; delayed physics rewards are reported separately, never relabelled as successful input or attributed to earlier actions.\n"+
    "Physics budget: three ticks per action; short episodes can finish before paddle interception or a complete round.\n"+
    "Learned context: start-position zone and coarse direction, without board/object state.\n"+
    "CHANGED is not a goal/win metric. Several scenes omit hazards, goals or action choices.\n"+
    "Independent seeds do not guarantee unseen initial states in deterministic scenes.\n"+
    "See source documentation: `docs/SIMULATOR_MEASUREMENT_LIMITS.ru.md`.\n\n"+
    "| Game | Random change % | Learned change % | Scripted change % | Learned delta pp | Random score | Learned score | Scripted score |\n"+
    "|---|---:|---:|---:|---:|---:|---:|---:|\n");
  json.append("{\n  \"schema\": \"localpilot-gamelab-autoplay-v1\",\n")
    .append("  \"source\": \"SIMULATED_GAME\",\n")
    .append("  \"physics_rules\": \"2048-random-spawn_bubble-ray_paddle-bricks-v1\",\n")
    .append("  \"physics_ticks_per_action\": 3,\n")
    .append("  \"evaluation_score_attribution\": \"EPISODE_TOTAL_SPLIT_PHYSICS_GESTURE\",\n")
    .append("  \"environment_kind\": \"SIMPLIFIED_UI_TEST_SCENES\",\n")
    .append("  \"score_attribution\": \"IMMEDIATE_GESTURE_ONLY\",\n")
    .append("  \"learned_context\": \"START_ZONE_AND_COARSE_DIRECTION\",\n")
    .append("  \"measurement_limits\": [")
    .append("\"CHANGED is not goal completion or verified real-game progress\",")
    .append("\"Delayed tick rewards are not attributed to earlier actions\",")
    .append("\"The learned table does not observe board cells or object state\",")
    .append("\"FLAPPY has one candidate; several scenes omit hazards or terminal goals\",")
    .append("\"Different seeds can retain identical initial states\"],\n")
    .append("  \"real_game_accepted\": false,\n")
    .append("  \"weights_promoted_to_real_games\": false,\n")
    .append("  \"seed\": ").append(seed).append(",\n")
    .append("  \"train_episodes_per_game\": ").append(trainEpisodes).append(",\n")
    .append("  \"evaluation_episodes_per_game\": ").append(evalEpisodes).append(",\n")
    .append("  \"steps_per_episode\": ").append(steps).append(",\n")
    .append("  \"training_events\": ").append(decoded.size()).append(",\n")
    .append("  \"training_seeds\": ").append(seedSchedule(seed,trainEpisodes)).append(",\n")
    .append("  \"evaluation_seeds\": ").append(seedSchedule(seed+987654321L,evalEpisodes)).append(",\n")
    .append("  \"training_exploration_probability\": 0.5,\n")
    .append("  \"games\": [\n");
  int index=0;
  for(GameLabEngine.Game game:GameLabEngine.Game.values()){
   long holdoutSeed=seed+987654321L;
   Results random=simulate(game,evalEpisodes,steps,holdoutSeed,null,1.0,null,false);
   Results policy=simulate(game,evalEpisodes,steps,holdoutSeed,learned,.20,null,false);
   Results coach=simulate(game,evalEpisodes,steps,holdoutSeed,null,0,null,true);
   if(index==0){
    baselineCsv.append(random.events);
    learnedCsv.append(policy.events);
    scriptedCsv.append(coach.events);
   }else{
    baselineCsv.append(random.events.substring(random.events.indexOf("\n")+1));
    learnedCsv.append(policy.events.substring(policy.events.indexOf("\n")+1));
    scriptedCsv.append(coach.events.substring(coach.events.indexOf("\n")+1));
   }
   double delta=policy.changedPct()-random.changedPct();
   if(index++>0)json.append(",\n");
   json.append("    {\"game\": \"").append(game.name()).append("\",")
     .append(" \"training_actions\": ").append(learned.samples(game.name()))
     .append(", \"random_change_rate\": ").append(fmt(random.changedPct()))
     .append(", \"lab_change_rate\": ").append(fmt(policy.changedPct()))
     .append(", \"scripted_change_rate\": ").append(fmt(coach.changedPct()))
     .append(", \"delta_percentage_points\": ").append(fmt(delta))
     .append(", \"random_mean_score\": ").append(fmt(random.meanScore()))
     .append(", \"lab_mean_score\": ").append(fmt(policy.meanScore()))
     .append(", \"scripted_mean_score\": ").append(fmt(coach.meanScore()))
     .append(", \"random_physics_score\": ").append(fmt(random.meanPhysicsScore()))
     .append(", \"lab_physics_score\": ").append(fmt(policy.meanPhysicsScore()))
     .append(", \"scripted_physics_score\": ").append(fmt(coach.meanPhysicsScore()))
     .append(", \"random_gesture_score\": ").append(fmt(random.meanGestureScore()))
     .append(", \"lab_gesture_score\": ").append(fmt(policy.meanGestureScore()))
     .append(", \"scripted_gesture_score\": ").append(fmt(coach.meanGestureScore()))
     .append("}");
   markdown.append("| ").append(game.name()).append(" | ")
     .append(fmt(random.changedPct())).append(" | ")
     .append(fmt(policy.changedPct())).append(" | ")
     .append(fmt(coach.changedPct())).append(" | ")
     .append(fmt(delta)).append(" | ")
     .append(fmt(random.meanScore())).append(" | ")
     .append(fmt(policy.meanScore())).append(" | ")
     .append(fmt(coach.meanScore())).append(" |\n");
  }
  json.append("\n  ]\n}\n");
  markdown.append("\n## Training summary\n\n")
    .append(learned.report()).append("\n\n")
    .append("Training mixes scripted demonstrations with random exploration. ")
    .append("Scripted is NOT a learned model. ")
    .append("All ten scenes were simulated on separate held-out seeds. ")
    .append("A positive delta is measured only for that scene and seed, ")
    .append("not evidence of generalization or success in real Market games.\n");
  Files.writeString(output.resolve("training-experience.lpx"),serialized,StandardCharsets.UTF_8);
  Files.writeString(output.resolve("evaluation-random.csv"),baselineCsv,StandardCharsets.UTF_8);
  Files.writeString(output.resolve("evaluation-lab-policy.csv"),learnedCsv,StandardCharsets.UTF_8);
  Files.writeString(output.resolve("evaluation-scripted.csv"),scriptedCsv,StandardCharsets.UTF_8);
  Files.writeString(output.resolve("report.json"),json,StandardCharsets.UTF_8);
  Files.writeString(output.resolve("report.md"),markdown,StandardCharsets.UTF_8);
  System.out.println("GAMELAB_AUTOPLAY_PASS scenes="+total+" train_events="+decoded.size()+
    " accepted="+learned.accepted()+" changes="+learned.changed()+
    " output="+output);
 }
 private static String fmt(double value){
  return String.format(Locale.ROOT,"%.3f",value);
 }
}
