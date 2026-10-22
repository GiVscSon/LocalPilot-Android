package org.localpilot.android.web;
import java.util.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;
public class GoalRewardTest {
 private static int checks;
 private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 private static void bad(Runnable r){try{r.run();throw new AssertionError("bad input accepted");}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
 private static float[] f(float n){float[] a=new float[256];Arrays.fill(a,n);return a;}
 private static WebLesson d(){return new WebLesson(WebLesson.gameId("https://example.org/game"),"https://example.org","Goal game");}
 private static WebLesson.Sample s(String session,int t,float state,float x,GoalReward r){return new WebLesson.Sample(session,t,.66f,new WebLesson.Action(WebLesson.Kind.TAP,x,.5f,x,.5f,100),f(state),f(state+.001f),r);}
 public static void main(String[] args)throws Exception{
  check(GoalReward.metric(GoalReward.Source.DISTANCE,10,7,10).value==.3f,"closer positive");
  check(GoalReward.metric(GoalReward.Source.DISTANCE,7,10,10).value==-.3f,"farther negative");
  check(GoalReward.metric(GoalReward.Source.PROGRESS,10,15,10).value==.5f,"score increase positive");
  check(GoalReward.metric(GoalReward.Source.PROGRESS,15,10,10).value==-.5f,"score decline negative");
  check(GoalReward.metric(GoalReward.Source.PROGRESS,10,10,10).value==0,"no change neutral");
  check(GoalReward.metric(GoalReward.Source.PROGRESS,-1e12,1e12,1).value==1,"positive clipped");
  check(GoalReward.metric(GoalReward.Source.DISTANCE,-1e12,1e12,1).value==-1,"negative clipped");
  check(!GoalReward.unknown().known(),"unknown is not success");
  for(float value:new float[]{-1,0,.25f,1})check(GoalReward.decode(GoalReward.manual(value).encode()).value==value,"manual roundtrip");
  check(!GoalReward.decode(GoalReward.unknown().encode()).known(),"unknown roundtrip");
  GoalReward metric=GoalReward.metric(GoalReward.Source.DISTANCE,40,15,100);check(GoalReward.decode(metric.encode()).encode().equals(metric.encode()),"metric evidence roundtrip");
  for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,1.1f,-1.1f})bad(()->GoalReward.manual(value));
  for(double scale:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})bad(()->GoalReward.metric(GoalReward.Source.PROGRESS,1,2,scale));
  bad(()->GoalReward.metric(GoalReward.Source.MANUAL,1,2,1));bad(()->GoalReward.metric(GoalReward.Source.DISTANCE,Double.NaN,2,1));
  bad(()->GoalReward.decode("PROGRESS,1.0,1.0,2.0,100.0"));
  check(GoalReward.parseMetricText("1 234,5")==1234.5,"localized numeric text");check(GoalReward.parseMetricText("-2.5")==-2.5,"signed number");check(GoalReward.parseMetricText("1\u00a0234")==1234,"nbsp");
  for(String t:new String[]{"Score:12 coins:3","NaN","Infinity","1e999","1.2.3","1 2","","x".repeat(129)})bad(()->GoalReward.parseMetricText(t));
  check(GoalReward.observed(GoalReward.Source.DISTANCE,10,7,10,false,100,300,400).value==.3f,"fresh observation earns reward");
  check(!GoalReward.observed(GoalReward.Source.DISTANCE,10,7,10,true,100,300,400).known(),"overlapping input is unknown");
  check(!GoalReward.observed(GoalReward.Source.DISTANCE,10,7,10,false,100,299,400).known(),"too early observation unknown");
  check(!GoalReward.observed(GoalReward.Source.DISTANCE,10,7,10,false,100,300,751).known(),"stale outcome unknown");
  check(!GoalReward.observed(GoalReward.Source.DISTANCE,10,7,10,false,100,500,400).known(),"future observation unknown");
  check(!GoalReward.observed(GoalReward.Source.DISTANCE,Double.NaN,7,10,false,100,300,400).known(),"missing before unknown");
  check(!GoalReward.observed(GoalReward.Source.DISTANCE,10,Double.NaN,10,false,100,300,400).known(),"missing after unknown");
  WebLesson a=d();a.add(s("one",1,.1f,.2f,GoalReward.unknown()));byte[] v2=a.encode();check(new String(v2,StandardCharsets.UTF_8).startsWith("LOCALPILOT-WEB-v2"),"unrated keeps v2");
  WebLesson.Sample original=a.lastSample();a.rate(original,GoalReward.manual(-1));check(a.lastSample().outcome.value==-1,"rating exact sample");bad(()->a.rate(original,GoalReward.manual(1)));
  check(a.predict(f(.1f),.66f)==null,"failure never recommended");a.add(s("one",2,.1f,.8f,GoalReward.manual(1)));check(a.predict(f(.1f),.66f).x==.8f,"success chosen over failed action");
  check(a.outcomeSummary().contains("Удачные 1")&&a.outcomeSummary().contains("ошибки 1"),"separate statistics");
  a.add(s("one",3,.1f,.8f,GoalReward.manual(-1)));check(a.predict(f(.1f),.66f)==null,"negative same action veto");
  byte[] encoded=a.encode();check(new String(encoded,StandardCharsets.UTF_8).startsWith("LOCALPILOT-WEB-v3"),"rated format v3");check(Arrays.equals(WebLesson.decode(encoded).encode(),encoded),"v3 byte roundtrip");
  byte[] corrupt=encoded.clone();corrupt[corrupt.length-20]^=1;try{WebLesson.decode(corrupt);throw new AssertionError();}catch(java.io.IOException expected){checks++;}
  check(Arrays.equals(a.copy().encode(),encoded),"copy preserves rewards");check(Arrays.equals(a.session("one").encode(),encoded),"session preserves rewards");check(Arrays.equals(a.assigned("Goal game").encode(),encoded),"named reassignment preserves rewards");
  WebLesson neutral=d();neutral.add(s("a",1,.1f,.2f,GoalReward.manual(0)));check(neutral.predict(f(.1f),.66f)==null,"neutral not a positive demonstration");
  WebLesson mixed=d();mixed.add(s("a",1,.1f,.2f,GoalReward.unknown()));mixed.add(s("a",2,.7f,.8f,GoalReward.manual(1)));check(mixed.predict(f(.1f),.66f)==null,"unrated excluded once graded mode active");
  WebLesson strength=d();strength.add(s("a",1,.1f,.2f,GoalReward.manual(.1f)));strength.add(s("a",2,.1f,.8f,GoalReward.manual(1)));check(strength.predict(f(.1f),.66f).x==.8f,"larger reward resolves equally close actions");
  WebLesson old=WebLesson.decode(v2),graded=WebLesson.decode(v2);graded.rateLast(GoalReward.manual(1));WebLesson merge=old.merged(graded);check(merge.size()==1&&merge.lastSample().outcome.value==1,"merge upgrades same sample,does not duplicate");check(old.lastSample().outcome.source==GoalReward.Source.UNKNOWN,"merge preserves original");
  WebLesson opposite=WebLesson.decode(v2);opposite.rateLast(GoalReward.manual(-1));bad(()->graded.merged(opposite));
  WebLesson cleared=WebLesson.decode(graded.encode());cleared.rateLast(GoalReward.unknown());check(Arrays.equals(WebLesson.decode(cleared.encode()).encode(),cleared.encode()),"v3 persists after removing rating");
  WebLesson legacy=new WebLesson(WebLesson.gameId("https://example.org/game"),"https://example.org");bad(()->legacy.add(s("a",1,.1f,.2f,GoalReward.manual(1))));check(legacy.size()==0,"invalid add is atomic");
  WebLesson metricData=d();metricData.add(s("a",1,.1f,.2f,metric));check(metricData.hasMetricRewards(),"metric source retained");
  java.io.File file=Files.createTempDirectory("goal-rewards-").resolve("data.lpweb").toFile();old.save(file);graded.save(file);check(WebLesson.load(new java.io.File(file+".bak")).lastSample().outcome.source==GoalReward.Source.UNKNOWN,"backup preserves old unrated data");check(WebLesson.load(file).lastSample().outcome.value==1,"rated saved");
  java.io.File originalArchive=new java.io.File(file+".pre-rewards-v2.bak");byte[] archived=Files.readAllBytes(originalArchive.toPath());check(Arrays.equals(archived,v2),"pre-reward v2 archived byte-exact");graded.rateLast(GoalReward.manual(-1));graded.save(file);check(Arrays.equals(archived,Files.readAllBytes(originalArchive.toPath())),"original archive not overwritten by later ratings");
  WebLesson report=d();for(int i=0;i<6;i++)report.add(s("train",i,.1f,.8f,GoalReward.manual(1)));for(int i=0;i<4;i++)report.add(s("held",100+i,.1f,.2f,GoalReward.manual(-1)));String text=report.trainReport();check(text.contains("повторено известных ошибок 0")&&text.contains("ошибки 4"),"holdout reports negative avoidance,not matching failures");check(text.contains("оцениваемых примеров 0"),"no negative samples in positive coverage denominator");check(!text.contains("Покрытие низкое"),"no false low-coverage warning with no eligible samples");
  System.out.println("Goal reward: "+checks+" assertions PASS");
 }
}
