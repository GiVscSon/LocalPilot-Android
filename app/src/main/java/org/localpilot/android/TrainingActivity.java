package org.localpilot.android;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import org.localpilot.android.rl.*;
import org.localpilot.shared.SimExperience;
import org.localpilot.shared.SimLabPolicy;
import java.io.*;
import java.util.Locale;
import java.util.concurrent.Executors;

/** Native on-device training. Activity disappearance pauses the single application worker. */
public final class TrainingActivity extends Activity {
 private static Runtime runtime;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private TextView status,labReport;private Spinner genre,paddleScenario;private View scene;
 private static final int IMPORT_LAB_EXPERIENCE=72;
 private Button train,evaluate,play,export,paddleTrain,paddleDemo,paddleApply,weightSpaceTest;
 private final Runnable refresh=new Runnable(){public void run(){status.setText(runtime.message);scene.invalidate();boolean idle=!runtime.busy;genre.setEnabled(idle);train.setEnabled(idle);evaluate.setEnabled(idle);play.setEnabled(idle);export.setEnabled(idle);
  paddleScenario.setEnabled(idle);paddleTrain.setEnabled(idle);paddleDemo.setEnabled(idle);
  paddleApply.setEnabled(idle&&runtime.paddleCandidate!=null&&runtime.paddleCandidate.eligible());
  weightSpaceTest.setEnabled(idle);handler.postDelayed(this,250);}};
 @Override public void onCreate(Bundle b){super.onCreate(b);if(runtime==null)runtime=new Runtime(getFilesDir());
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(12,8,12,8);ScrollView page=new ScrollView(this);page.setFillViewport(true);page.addView(box);setContentView(page);
  TextView title=new TextView(this);title.setText("Аркады · обучение на Android");title.setTextSize(20);box.addView(title);
  genre=new Spinner(this);genre.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Вертикальный джампер","Вертикальный платформер","Раннер по полосам","Jetpack"}));genre.setSelection(Math.max(0,Math.min(3,getSharedPreferences(MainActivity.PREF,MODE_PRIVATE).getInt("training_genre",0))));box.addView(genre);
  TextView help=new TextView(this);help.setText("Четыре исходные DQN-модели остаются учебными и не переносятся напрямую: их схемы действий отличаются от настоящих игр. Ниже отдельный Карканоид: его маленькая сеть обучается на восьми физических сценариях и может быть вручную перенесена в реальную зрительную модель после проверки.");box.addView(help);
  scene=new View(this){private final Paint p=new Paint(3);@Override public void onDraw(Canvas c){super.onDraw(c);c.drawColor(0xff101d2d);
 PaddleWeightLab.Scene physics=runtime.paddleScene;
 if(physics!=null){
  c.save();float scale=Math.min(getWidth()/360f,getHeight()/640f);
  c.translate((getWidth()-360*scale)/2,(getHeight()-640*scale)/2);c.scale(scale,scale);
  boolean[] alive=physics.alive();
  for(int row=0;row<3;row++)for(int col=0;col<8;col++){
   if(!alive[row*8+col])continue;
   p.setColor(new int[]{0xff41b6e6,0xffee8b56,0xffa9d070}[row]);
   float left=(.025f+col*.119f)*360,top=(.09f+row*.067f)*640;
   c.drawRoundRect(left,top,left+.100f*360,top+.044f*640,3,3,p);
  }
  p.setColor(0xffeee5c5);float paddleX=(float)physics.paddleX()*360;
  float paddleHalfWidth=(float)physics.halfPaddle()*360;
  c.drawRoundRect(paddleX-paddleHalfWidth,(float)PaddleWeightLab.PADDLE_Y*640,
    paddleX+paddleHalfWidth,(float)PaddleWeightLab.PADDLE_Y*640+12,5,5,p);
  p.setColor(0xffff5e6c);c.drawCircle((float)physics.ballX()*360,(float)physics.ballY()*640,
    (float)PaddleWeightLab.BALL_RADIUS*360,p);c.restore();return;
 }
 float[] s=runtime.scene;if(s==null)return;c.save();float scale=Math.min(getWidth()/360f,getHeight()/640f);c.translate((getWidth()-360*scale)/2,(getHeight()-640*scale)/2);c.scale(scale,scale);p.setColor(0xff5bc2ff);c.drawRect(s[0]-10,s[1]-14,s[0]+10,s[1]+14,p);for(int i=2;i+4<s.length;i+=5){p.setColor(s[i+4]==1?0xff62d394:s[i+4]==2?0xffee6677:0xffffd166);c.drawRect(s[i]-s[i+2]/2,s[i+1]-s[i+3]/2,s[i]+s[i+2]/2,s[i+1]+s[i+3]/2,p);}c.restore();}};
  box.addView(scene,new LinearLayout.LayoutParams(-1,dp(Math.max(220,Math.min(480,getResources().getConfiguration().screenHeightDp-360)))));status=new TextView(this);status.setTextSize(12);ScrollView stats=new ScrollView(this);stats.addView(status);box.addView(stats,new LinearLayout.LayoutParams(-1,dp(120)));
  LinearLayout row=new LinearLayout(this);box.addView(row);train=button(row,"Обучать",()->runtime.start(selected(),0));evaluate=button(row,"Оценить ×50",()->runtime.start(selected(),1));
  LinearLayout row2=new LinearLayout(this);box.addView(row2);play=button(row2,"Показать модель",()->runtime.start(selected(),2));button(row2,"Пауза / STOP",()->runtime.pause());
  export=button(box,"Экспорт checkpoint",()->{if(runtime.checkpoint==null||!runtime.checkpoint.isFile()){toast("Сначала обучите и поставьте на паузу");return;}startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"localpilot-"+runtime.current+"-checkpoint.zip"),71);});
  TextView labHeading=new TextView(this);
  labHeading.setText("GameLab → автономное обучение (без реальных жестов)");
  labHeading.setTextSize(18);box.addView(labHeading);
  TextView labHelp=new TextView(this);
  labHelp.setText("В GameLab нажмите «Опыт GameLab» после нескольких ходов. Здесь импортируйте .lpx: обучается отдельная симуляционная политика. Результаты не изменяют веса настоящих игр и не дают разрешения на ввод.");
  box.addView(labHelp);
  button(box,"Импортировать опыт GameLab (.lpx)",()->{
   Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
     .addCategory(Intent.CATEGORY_OPENABLE);
   startActivityForResult(pick,IMPORT_LAB_EXPERIENCE);
  });
  labReport=new TextView(this);labReport.setTextSize(12);box.addView(labReport);
  readSavedLabExperience();
  TextView heading=new TextView(this);heading.setText("Карканоид · перенос в игровую модель");heading.setTextSize(18);box.addView(heading);
  paddleScenario=new Spinner(this);
  paddleScenario.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
    new String[]{"Классический","Узкая платформа","Быстрый шар","Рикошеты от стен",
      "Пустоты между блоками","Плотные блоки","Задержка управления","Сильный отскок"}));
  box.addView(paddleScenario);
  paddleTrain=button(box,"Обучить на восьми физических условиях",()->runtime.trainPaddle());
  paddleDemo=button(box,"Показать физическую игру и обученную модель",()->runtime.demoPaddle(
    PaddleWeightLab.Scenario.values()[paddleScenario.getSelectedItemPosition()]));
  weightSpaceTest=button(box,"Эксперимент: исправить зацикленную стратегию Ари",()->runtime.checkIntervention());
  paddleApply=button(box,"Перенести веса в настоящий Карканоид",()->{
   PaddleTransfer.Candidate candidate=runtime.paddleCandidate;
   if(candidate==null||!candidate.eligible()){toast("Сначала обучите и пройдите отдельную проверку");return;}
   PilotService pilot=PilotService.instance;
   toast(pilot==null?"Включите службу LocalPilot и выберите настоящий Карканоид":
     pilot.installPaddleTransfer(candidate));
  });
 }
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 private ArcadeEnv.Genre selected(){int index=genre.getSelectedItemPosition();getSharedPreferences(MainActivity.PREF,MODE_PRIVATE).edit().putInt("training_genre",index).apply();return ArcadeEnv.Genre.values()[index];}
 private Button button(LinearLayout box,String text,Runnable run){Button b=new Button(this);b.setText(text);b.setTextSize(12);b.setOnClickListener(v->run.run());box.addView(b,box.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2));return b;}
 @Override protected void onStop(){runtime.pause();handler.removeCallbacks(refresh);super.onStop();}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(req==IMPORT_LAB_EXPERIENCE){
   if(result==RESULT_OK&&data!=null&&data.getData()!=null)
    importLabExperience(data);
   return;
  }
  if(req!=71||result!=RESULT_OK||data==null)return;final File source=runtime.checkpoint;if(runtime.busy||source==null)return;
  runtime.executor.execute(()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException("output");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);runOnUiThread(()->toast("Checkpoint экспортирован"));}catch(IOException e){runOnUiThread(()->toast("Ошибка экспорта: "+e.getMessage()));}});
 }
 private static byte[] readBoundedReplay(InputStream in)throws IOException{
  ByteArrayOutputStream buffer=new ByteArrayOutputStream();
  byte[] data=new byte[8192];int amount;
  while((amount=in.read(data))!=-1){
   if(buffer.size()+amount>SimExperience.MAX_BYTES)
    throw new IOException("simulator replay exceeds limit");
   buffer.write(data,0,amount);
  }
  return buffer.toByteArray();
 }
 @Override protected void onResume(){
  super.onResume();
  handler.post(refresh);
  // Embedded GameLab writes lab-only episodes when its screen closes.
  if(labReport!=null)readSavedLabExperience();
 }
 private File labReplayFile(){return new File(getFilesDir(),"gamelab-sim-experience-v1.lpx");}
 private void readSavedLabExperience(){
  File saved=labReplayFile();
  if(!saved.isFile()){
   labReport.setText("Опыт GameLab ещё не импортирован. Симуляционная модель не обучена.");return;
  }
  try(FileInputStream in=new FileInputStream(saved)){
   byte[] bytes=readBoundedReplay(in);
   if(bytes.length>SimExperience.MAX_BYTES)throw new IOException("bounded replay exceeded");
   SimLabPolicy policy=SimLabPolicy.train(SimExperience.decode(
     new String(bytes,java.nio.charset.StandardCharsets.UTF_8)));
   labReport.setText(policy.report());
  }catch(IOException|IllegalArgumentException invalid){
   labReport.setText("Сохранённый лабораторный опыт повреждён или несовместим. Реальные модели не затронуты.");
  }
 }
 private void importLabExperience(Intent data){
  try(InputStream in=getContentResolver().openInputStream(data.getData())){
   if(in==null)throw new IOException("no SAF stream");
   byte[] bytes=readBoundedReplay(in);
   if(bytes.length>SimExperience.MAX_BYTES)throw new IOException("replay too large");
   SimLabPolicy policy=SimLabPolicy.train(SimExperience.decode(
     new String(bytes,java.nio.charset.StandardCharsets.UTF_8)));
   if(policy.accepted()==0)throw new IllegalArgumentException("no normal simulator actions");
   File saved=labReplayFile();
   File temporary=File.createTempFile("gamelab-import-",".tmp",getFilesDir());
   try{
    try(FileOutputStream out=new FileOutputStream(temporary)){
     out.write(bytes);out.getFD().sync();
    }
    java.nio.file.Files.move(temporary.toPath(),saved.toPath(),
      java.nio.file.StandardCopyOption.ATOMIC_MOVE,
      java.nio.file.StandardCopyOption.REPLACE_EXISTING);
   }finally{temporary.delete();}
   labReport.setText("Импорт завершён. Отдельная учебная политика обновлена.\n"+policy.report());
  }catch(IOException|IllegalArgumentException invalid){
   labReport.setText("Импорт GameLab отклонён: неверный формат, размеры или контрольная сумма. Реальные модели не изменены.");
  }
 }
 private static final class Runtime {
  final java.util.concurrent.ExecutorService executor=Executors.newSingleThreadExecutor();final File directory;
  volatile boolean busy,running;volatile String message="Выберите жанр и нажмите «Обучать»";
  volatile float[] scene;volatile PaddleWeightLab.Scene paddleScene;
  volatile PaddleTransfer.Candidate paddleCandidate;
  volatile File checkpoint;volatile ArcadeEnv.Genre current;DqnTrainer trainer;
  Runtime(File root){directory=new File(root,"arcade-rl");}
  synchronized void start(ArcadeEnv.Genre genre,int mode){if(busy)return;busy=true;running=true;paddleScene=null;message="Загрузка модели…";
   executor.execute(()->{try{
    if(trainer==null||current!=genre){File f=new File(directory,genre+".zip");DqnTrainer loaded;
     loaded=DqnTrainer.loadRecovering(f,genre,20261021);trainer=loaded;checkpoint=f;current=genre;
    }
    publish(trainer.env);
    if(mode==1){message="Оценка модели · 50 эпизодов…";message=trainer.evaluate(50,900000,()->!running);new File(directory,"evaluation").mkdirs();File report=new File(directory,"evaluation/"+genre+".txt");try(FileOutputStream out=new FileOutputStream(report)){out.write(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}return;}
    ArcadeEnv demo=mode==2?new ArcadeEnv(genre):null;if(demo!=null)demo.reset(700000);
    long shown=0,saved=System.nanoTime();
    while(running){
     if(mode==0){for(int i=0;i<4&&running;i++)trainer.step();}
     else {if(demo.terminated||demo.truncated)demo.reset(700000+demo.steps);demo.step(DqnTrainer.argmax(trainer.online.predict(demo.observe())));}
     long now=System.nanoTime();if(now-shown>100_000_000L){shown=now;publish(mode==0?trainer.env:demo);}
     if(mode==0&&now-saved>30_000_000_000L){trainer.save(checkpoint);saved=System.nanoTime();}
     if(mode==2)Thread.sleep(66);else Thread.sleep(1);
    }
    if(mode==0){trainer.save(checkpoint);message="Пауза · прогресс сохранён\n"+stats();}else message="Просмотр остановлен\n"+stats();
   }catch(Exception e){message="Остановлено: "+e.getClass().getSimpleName()+" · "+e.getMessage();}
   finally{running=false;busy=false;}});
  }
  /** The targeted intervention is a lab-only policy test, not an Ari gesture. */
  synchronized void checkIntervention(){
   if(busy)return;
   busy=true;running=true;paddleScene=null;
   message="Программирую начальные веса и проверяю точечное исправление...";
   executor.execute(()->{
    try{
     StringBuilder result=new StringBuilder("Эксперимент weight-space programming\n");
     for(long seed:new long[]{20261104L,177777L,20261201L}){
      if(!running)throw new java.util.concurrent.CancellationException("STOP");
      WeightSpaceSkills.Comparison check=WeightSpaceSkills.evaluateRetreat(seed,400);
      result.append("seed=").append(seed).append(" исправлено=")
        .append(check.plateauRecovered()).append(" регрессий=")
        .append(check.regressions()).append(" годен для эксперимента=")
        .append(check.passes()).append('\n');
     }
     result.append("Изменён один вес. Реальным Прыгуном Ари эта лаборатория НЕ управляет.");
     message=result.toString();
    }catch(java.util.concurrent.CancellationException cancelled){message="Эксперимент отменён кнопкой STOP";}
    catch(Exception error){message="Эксперимент не прошёл: "+error.getMessage();}
    finally{running=false;busy=false;}
   });
  }
  synchronized void trainPaddle(){
   if(busy)return;
   busy=true;running=true;paddleCandidate=null;
   message="Карканоид: обучение на 8 сценариях, затем отдельная проверка...";
   executor.execute(()->{
    try{
     PaddleTransfer.Candidate candidate=PaddleTransfer.trainCandidate(()->!running);
     if(!running)throw new java.util.concurrent.CancellationException();
     paddleCandidate=candidate;
     message=candidate.report()+"\nРезультаты симулятора не подтверждают поведение настоящей игры.";
    }catch(java.util.concurrent.CancellationException interrupted){
     message="Обучение Карканоида отменено · веса не перенесены";
    }catch(Exception error){message="Карканоид: "+error.getClass().getSimpleName()+": "+error.getMessage();}
    finally{running=false;busy=false;}
   });
  }
  synchronized void demoPaddle(PaddleWeightLab.Scenario scenario){
   if(busy)return;
   busy=true;running=true;scene=null;
   message="Сцена Карканоида: "+scenario;
   executor.execute(()->{
    try{
     PaddleWeightLab.Control weights=paddleCandidate!=null&&paddleCandidate.eligible()
       ?PaddleWeightLab.Weights.followBall().patchToAngledImpact(paddleCandidate.offset())
       :PaddleWeightLab.Weights.followBall();
     PaddleWeightLab.Episode result=PaddleWeightLab.play(weights,20261104L,scenario,frame->{
      if(!running)throw new java.util.concurrent.CancellationException();
      paddleScene=frame;
      try{Thread.sleep(42);}catch(InterruptedException e){
       Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException();
      }
     });
     message="Карканоид · "+scenario+" · блоков "+result.bricks()+
       " · отбиваний "+result.catches()+" · пропущено "+result.missed()+" · только симуляция";
    }catch(java.util.concurrent.CancellationException ignored){message="Показ Карканоида остановлен";}
    catch(Exception error){message="Симулятор Карканоида: "+error.getMessage();}
    finally{running=false;busy=false;}
   });
  }
  void pause(){running=false;}
  String stats(){return String.format(Locale.ROOT,"%s · шаги %d · эпизоды %d\nОбновления %d · replay %d · epsilon %.3f\nНаграда %.3f · лучший прогресс %.1f · loss %.4f",current,trainer.steps,trainer.episodes,trainer.updates,trainer.replaySize(),trainer.epsilon(),trainer.lastReturn,trainer.bestProgress,trainer.loss);}
  void publish(ArcadeEnv e){
   float[] s=new float[2+e.objects.size()*5];boolean vertical=e.genre==ArcadeEnv.Genre.JUMPER||e.genre==ArcadeEnv.Genre.PLATFORMER;
   s[0]=e.genre==ArcadeEnv.Genre.JETPACK?70:e.x;s[1]=e.genre==ArcadeEnv.Genre.LANES?550-e.y:640-(e.y-(vertical?e.camera:0));
   int i=2;for(ArcadeEnv.Entity o:e.objects){s[i++]=e.genre==ArcadeEnv.Genre.JETPACK?70+o.x-e.progress:o.x;s[i++]=e.genre==ArcadeEnv.Genre.LANES?550-(o.y-e.progress):640-(o.y-(vertical?e.camera:0));s[i++]=o.used&&o.type==3?0:o.w;s[i++]=o.h;s[i++]=o.type;}scene=s;message=stats();
  }
 }
}
