package org.localpilot.android;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public class CaptureMatrixTest {
 private static final long BASE=1000;
 private static int assertions,scenarios;
 private static final List<String> rows=new ArrayList<>();
 private static void check(boolean ok){assertions++;if(!ok)throw new AssertionError("assertion "+assertions+" after "+scenarios+" scenarios");}
 private record Setup(CaptureStartup state,long current,long old){}
 private static Setup prepare(int stage){
  CaptureStartup s=new CaptureStartup();long old=s.begin(0);s.stop(1);long id=s.begin(BASE);
  if(stage>=2)s.move(id,CaptureStartup.Stage.CONSENT,CaptureStartup.Stage.SERVICE,BASE);
  if(stage>=3)s.move(id,CaptureStartup.Stage.SERVICE,CaptureStartup.Stage.FIRST_FRAME,BASE);
  if(stage>=4)s.move(id,CaptureStartup.Stage.FIRST_FRAME,CaptureStartup.Stage.RUNNING,BASE);
  if(stage==5)s.recognized(id,BASE);
  if(stage==0)s.stop(BASE);
  return new Setup(s,id,old);
 }
 private static void row(String group,String stage,String event,String id){rows.add(group+","+stage+","+event+","+id+",PASS");scenarios++;}
 private static String label(int stage){return new String[]{"STOP","Ожидание разрешения захвата…","2/5 служба","3/5 кадр","4/5 кадры","5/5 игра"}[stage];}
 private static void callbacks(){
  for(int stage=0;stage<6;stage++)for(int event=0;event<5;event++)for(int kind=0;kind<3;kind++){
   Setup p=prepare(stage);CaptureStartup s=p.state;long id=kind==0?p.current:kind==1?p.old:0;
   String before=s.report();boolean expected=kind==0&&(event==0&&stage==1||event==1&&stage==2||event==2&&stage==3||event==3&&stage==4);
   boolean actual=switch(event){
    case 0->s.move(id,CaptureStartup.Stage.CONSENT,CaptureStartup.Stage.SERVICE,BASE+1);
    case 1->s.move(id,CaptureStartup.Stage.SERVICE,CaptureStartup.Stage.FIRST_FRAME,BASE+1);
    case 2->s.move(id,CaptureStartup.Stage.FIRST_FRAME,CaptureStartup.Stage.RUNNING,BASE+1);
    case 3->s.recognized(id,BASE+1);
    default->{s.error(id,"TEST_FAILURE");yield kind==0&&stage!=0;}
   };
   if(event==4)expected=kind==0&&stage!=0;
   check(actual==expected);
   check(s.label().equals(label(expected&&event<4?stage+1:stage)));
   check(!before.equals(s.report())==expected);
   row("callback",label(stage),new String[]{"grant","ready","frame","recognize","error"}[event],new String[]{"current","old","zero"}[kind]);
  }
 }
 private static void stopRetry(){
  for(int stage=0;stage<6;stage++){
   Setup p=prepare(stage);CaptureStartup s=p.state;s.stop(BASE+10);
   check(!s.active(p.current));check(s.label().equals("STOP"));
   check(!s.move(p.current,CaptureStartup.Stage.CONSENT,CaptureStartup.Stage.SERVICE,BASE+11));
   check(!s.recognized(p.current,BASE+11));check(s.timeout(p.current,Long.MAX_VALUE)==null);
   row("lifecycle",label(stage),"stop","current");
   long next=s.begin(BASE+20);check(next!=p.current&&next!=p.old);check(s.label().equals(label(1)));
   String before=s.report();s.error(p.current,"LATE_ERROR");
   check(!s.move(p.current,CaptureStartup.Stage.SERVICE,CaptureStartup.Stage.FIRST_FRAME,BASE+21));
   check(before.equals(s.report()));check(s.active(next));
   row("lifecycle",label(stage),"retry_then_late_callback","old");
  }
 }
 private static void timeouts(){
  for(int stage=0;stage<6;stage++)for(int kind=0;kind<3;kind++){
   long limit=stage==1?120000:stage==2||stage==3?15000:120000;
   for(long delta:new long[]{-1,0,limit-1,limit,limit+1,1000000}){
    Setup p=prepare(stage);long id=kind==0?p.current:kind==1?p.old:0;
    String code=p.state.timeout(id,BASE+delta);
    boolean expected=kind==0&&stage>=1&&stage<=3&&delta>=limit;
    check((code!=null)==expected);
    if(expected)check(code.equals(new String[]{"","CONSENT_TIMEOUT","SERVICE_TIMEOUT","FIRST_FRAME_TIMEOUT"}[stage]));
    row("timeout",label(stage),"elapsed_"+delta,new String[]{"current","old","zero"}[kind]);
   }
  }
 }
 private static void duplicates()throws Exception{
  for(int stage=1;stage<=4;stage++){
   Setup p=prepare(stage);AtomicInteger accepted=new AtomicInteger();
   var pool=Executors.newFixedThreadPool(8);CountDownLatch go=new CountDownLatch(1);List<Future<?>> jobs=new ArrayList<>();final int st=stage;
   try{
    for(int i=0;i<32;i++)jobs.add(pool.submit(()->{try{go.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
     boolean ok=st==4?p.state.recognized(p.current,BASE+1):p.state.move(p.current,CaptureStartup.Stage.values()[st],CaptureStartup.Stage.values()[st+1],BASE+1);
     if(ok)accepted.incrementAndGet();
    }));
    go.countDown();for(Future<?> job:jobs)job.get(5,TimeUnit.SECONDS);
   }finally{pool.shutdownNow();}
   check(accepted.get()==1);check(p.state.label().equals(label(stage+1)));
   row("concurrency",label(stage),"32_duplicate_callbacks","current");
  }
 }
 private static void codes(){
  for(String type:new String[]{null,"","SecurityException","ForegroundServiceStartNotAllowedException","IllegalStateException","RESULT_MISSING","FIRST_FRAME_TIMEOUT","bad value/with punctuation", "X".repeat(300)}){
   String code=CaptureStartup.errorCode(type);check(code.length()<=32);new StageMetrics().failure(StageMetrics.Stage.CAPTURE,code);
   row("diagnostic","any",type==null?"null":type.isEmpty()?"empty":type.substring(0,Math.min(type.length(),45)),"n/a");
  }
 }
 public static void main(String[] args)throws Exception{
  callbacks();stopRetry();timeouts();duplicates();codes();
  Path output=Path.of(args.length>0?args[0]:"/tmp/localpilot-capture-matrix.csv");
  List<String> csv=new ArrayList<>();csv.add("group,stage,event,session,result");csv.addAll(rows);Files.write(output,csv);
  System.out.println("Capture matrix: "+scenarios+" scenarios, "+assertions+" assertions PASS; "+output);
 }
}
