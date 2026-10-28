package org.localpilot.android.rl;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;import org.localpilot.android.*;
public final class RlTest {
 static int checks;static void ok(boolean condition){checks++;if(!condition)throw new AssertionError("check "+checks);}
 static void rejected(Runnable r){try{r.run();throw new AssertionError("accepted invalid input");}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
 public static void main(String[] args)throws Exception{
  // Independent Java invocations can be compared byte-for-byte with the
  // Python/JPype replay gate. Raw float32 bits include signed zero.
  for(ArcadeEnv.Genre genre:ArcadeEnv.Genre.values()){
   byte[] trace=RlParityProbe.trace(genre);
   ok(trace.length==RlParityProbe.STEPS*(ArcadeEnv.OBS*8+18));
   ok(Arrays.equals(trace,RlParityProbe.trace(genre)));
   ok(RlParityProbe.sha256(trace).matches("[0-9a-f]{64}"));
  }
  for(ArcadeEnv.Genre g:ArcadeEnv.Genre.values()){
   ArcadeEnv a=new ArcadeEnv(g),b=new ArcadeEnv(g);a.reset(123);b.reset(123);ArcadeEnv.Rng rng=new ArcadeEnv.Rng(7);
   for(int i=0;i<2500;i++){
    if(a.terminated||a.truncated){ok(a.terminated==b.terminated&&a.truncated==b.truncated);a.reset(i);b.reset(i);}
    int action=rng.integer(a.actions().length);ArcadeEnv.Step x=a.step(action),y=b.step(action);ok(Arrays.equals(x.observation,y.observation));ok(x.reward==y.reward&&x.progress==y.progress);ok(!(x.terminated&&x.truncated));for(float f:x.observation)ok(Float.isFinite(f)&&Math.abs(f)<=1);
   }
   rejected(()->a.step(-1));rejected(()->a.step(a.actions().length));
   ByteArrayOutputStream state=new ByteArrayOutputStream();a.write(new DataOutputStream(state));b.read(new DataInputStream(new ByteArrayInputStream(state.toByteArray())));ok(Arrays.equals(a.observe(),b.observe()));
  }
  ArcadeEnv j=new ArcadeEnv(ArcadeEnv.Genre.JUMPER);j.reset(1);j.step(2);j.y=400;j.progress=500;j.camera=100;j.vy=0;j.grounded=false;ok(j.step(2).reward==0); // camera movement is not new height
  ArcadeEnv p=new ArcadeEnv(ArcadeEnv.Genre.PLATFORMER);p.objects.clear();p.objects.add(new ArcadeEnv.Entity(0,1,180,0,100,12));p.y=0;p.x=260;p.grounded=true;p.step(3);ok(!p.grounded&&p.y<0);
  ArcadeEnv lane=new ArcadeEnv(ArcadeEnv.Genre.LANES);lane.objects.clear();lane.objects.add(new ArcadeEnv.Entity(1,2,180,8,55,40));ok(lane.step(4).terminated);
  lane.reset(1);lane.objects.clear();lane.objects.add(new ArcadeEnv.Entity(1,2,180,80,55,40));lane.step(2);for(int i=0;i<13&&!lane.terminated;i++)lane.step(4);ok(!lane.terminated&&lane.progress>110);
  ArcadeEnv jet=new ArcadeEnv(ArcadeEnv.Genre.JETPACK);jet.objects.clear();jet.y=320;jet.vy=0;jet.step(1);ok(jet.y>320);jet.step(0);ok(jet.vy<0);
  ArcadeEnv cut=new ArcadeEnv(ArcadeEnv.Genre.JETPACK);cut.objects.clear();cut.steps=1999;ArcadeEnv.Step ended=cut.step(0);ok(ended.truncated&&!ended.terminated);rejected(()->cut.step(0));
  ok(DqnTrainer.bootstrap(2,true,99)==2);ok(Math.abs(DqnTrainer.bootstrap(2,false,3)-4.97)<1e-5);
  DqnTrainer.Net net=new DqnTrainer.Net(3,new ArcadeEnv.Rng(55));float[] input=new float[64];Arrays.fill(input,.31f);float[] gradient=new float[net.w.length];net.gradient(input,1,.2f,gradient);
  for(int i=0;i<net.w.length;i+=101){float old=net.w[i],eps=.001f;net.w[i]=old+eps;float hi=huber(net.predict(input)[1]-.2f);net.w[i]=old-eps;float lo=huber(net.predict(input)[1]-.2f);net.w[i]=old;ok(Math.abs((hi-lo)/(2*eps)-gradient[i])<.004);}
  DqnTrainer fit=new DqnTrainer(ArcadeEnv.Genre.JUMPER,31);for(int i=0;i<32;i++)fit.remember(input,1,1,input,true);float before=Math.abs(fit.online.predict(input)[1]-1);for(int i=0;i<120;i++)fit.learnBatch();ok(Math.abs(fit.online.predict(input)[1]-1)<before*.1);
  DqnTrainer trainer=new DqnTrainer(ArcadeEnv.Genre.LANES,3);for(int i=0;i<1500;i++)trainer.step();ok(trainer.updates>0&&trainer.replaySize()==1500);File folder=Files.createTempDirectory("pilot-rl-").toFile(),checkpoint=new File(folder,"model.zip");trainer.save(checkpoint);DqnTrainer loaded=DqnTrainer.load(checkpoint,ArcadeEnv.Genre.LANES);ok(Arrays.equals(trainer.online.w,loaded.online.w));ok(Arrays.equals(trainer.online.m,loaded.online.m));
  for(int i=0;i<100;i++){trainer.step();loaded.step();}ok(trainer.steps==loaded.steps&&trainer.rng.state==loaded.rng.state);ok(Arrays.equals(trainer.online.w,loaded.online.w));ok(Arrays.equals(trainer.env.observe(),loaded.env.observe()));trainer.save(checkpoint);ok(new File(checkpoint+".bak").isFile());
  DqnTrainer priorModel=DqnTrainer.load(new File(checkpoint+".bak"),ArcadeEnv.Genre.LANES);Files.writeString(checkpoint.toPath(),"interrupted write");DqnTrainer recovered=DqnTrainer.loadRecovering(checkpoint,ArcadeEnv.Genre.LANES,999);ok(Arrays.equals(priorModel.online.w,recovered.online.w));Files.delete(checkpoint.toPath());recovered=DqnTrainer.loadRecovering(checkpoint,ArcadeEnv.Genre.LANES,999);ok(Arrays.equals(priorModel.online.w,recovered.online.w));ok(checkpoint.isFile());
  try{DqnTrainer.load(checkpoint,ArcadeEnv.Genre.JETPACK);throw new AssertionError("wrong genre accepted");}catch(IOException expected){checks++;}
  byte[] weights=Files.readAllBytes(checkpoint.toPath());long step=trainer.steps,rngState=trainer.rng.state;float[] w=trainer.online.w.clone();String evaluation=trainer.evaluate(2,700000,()->false);ok(evaluation.contains("bootstrap CI"));ok(trainer.steps==step&&trainer.rng.state==rngState&&Arrays.equals(w,trainer.online.w));ok(trainer.evaluate(2,1,()->true).contains("отменена"));
  File bad=new File(folder,"bad.zip");try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(bad))){z.putNextEntry(new ZipEntry("state.bin"));z.write(weights);z.closeEntry();}try{DqnTrainer.load(bad,ArcadeEnv.Genre.LANES);throw new AssertionError();}catch(IOException expected){checks++;}
  InputPermit permit=new InputPermit();permit.grantSession(2,10);for(int i=0;i<10000;i++)ok(permit.consume(2,10+i*1000L));ok(!permit.allows(3,20));ok(!permit.allows(2,9));permit.revoke();ok(!permit.allows(2,20));
  File legacy=new File(folder,"legacy.log");SafeJournal old=new SafeJournal(legacy);old.append(SafeJournal.Event.STOP,1,0,1,0);byte[] prior=Files.readAllBytes(legacy.toPath());File segments=new File(folder,"segments");RotatingJournal journal=new RotatingJournal(segments,legacy,2048);long last=0;for(int i=0;i<160;i++){last=journal.nextRequest();journal.append(SafeJournal.Event.PROPOSED,1,last,i,0);journal.append(SafeJournal.Event.ANDROID_COMPLETED,1,last,i,0);}ok(Arrays.equals(prior,Files.readAllBytes(legacy.toPath())));ok(segments.listFiles(f->f.getName().startsWith("segment-")).length==8);RotatingJournal reopened=new RotatingJournal(segments,legacy,2048);ok(reopened.nextRequest()>last);try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(reopened.snapshot()))){int n=0;while(z.getNextEntry()!=null){z.readAllBytes();n++;}ok(n==10);}
  System.out.println("PASS "+checks+" RL/environment/checkpoint/session/journal assertions");
 }
 static float huber(float e){return Math.abs(e)<1?.5f*e*e:Math.abs(e)-.5f;}
}
