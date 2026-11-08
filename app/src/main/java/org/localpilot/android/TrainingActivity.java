package org.localpilot.android;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.View;
import android.view.MotionEvent;
import android.widget.*;
import org.localpilot.android.rl.*;
import java.io.*;
import java.util.Locale;
import java.util.concurrent.Executors;

/** Native on-device training. Activity disappearance pauses the single application worker. */
public final class TrainingActivity extends Activity {
 private static Runtime runtime;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private TextView status;private Spinner genre,paddleScenario;private View scene;
 private Button train,evaluate,play,export,manualPlay,manualReset,stopButton;
 private Button paddleTrain,paddleDemo,paddleApply;
 private LinearLayout manualControls;private TextView manualHelp;
 private float downX,downY;private int heldAction=-1;
 private final Runnable refresh=new Runnable(){public void run(){
  if(status==null||runtime==null)return;
  status.setText(runtime.message);scene.invalidate();
  boolean idle=!runtime.busy;
  boolean manual=runtime.manual!=null;
  genre.setEnabled(idle);
  train.setEnabled(idle);
  manualPlay.setEnabled(idle);
  evaluate.setEnabled(idle&&runtime.hasTrainedCheckpoint(selectedGenre()));
  play.setEnabled(idle&&runtime.hasTrainedCheckpoint(selectedGenre()));
  export.setEnabled(idle&&runtime.checkpoint!=null&&runtime.checkpoint.isFile());
  stopButton.setEnabled(runtime.busy);
  manualReset.setEnabled(manual);
  manualControls.setVisibility(manual?View.VISIBLE:View.GONE);
  manualHelp.setVisibility(manual?View.VISIBLE:View.GONE);
  paddleScenario.setEnabled(idle);
  paddleTrain.setEnabled(idle);
  paddleDemo.setEnabled(idle);
  paddleApply.setEnabled(idle&&runtime.paddleCandidate!=null&&runtime.paddleCandidate.eligible());
  handler.postDelayed(this,250);
 }};
 private final Runnable manualClock=new Runnable(){public void run(){
  if(runtime!=null&&runtime.manual!=null){
   ArcadeManualController control=runtime.manual;
   runtime.manualAction(heldAction<0?control.neutral():heldAction);
   if(scene!=null)scene.invalidate();
  }
  handler.postDelayed(this,70);
 }};
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
  box.addView(scene,new LinearLayout.LayoutParams(-1,dp(Math.max(220,Math.min(480,getResources().getConfiguration().screenHeightDp-360)))));
  scene.setClickable(true);
  scene.setContentDescription("Учебное поле: включите ручную игру, затем управляйте жестами или кнопками ниже");
  scene.setOnTouchListener((v,event)->{
   ArcadeManualController control=runtime.manual;
   if(control==null)return false; // AI demonstrations are read-only.
   int action=event.getActionMasked();
   float x=event.getX()/Math.max(1f,v.getWidth()),y=event.getY()/Math.max(1f,v.getHeight());
   if(action==MotionEvent.ACTION_DOWN){
    downX=x;downY=y;
    if(control.env.genre==ArcadeEnv.Genre.JETPACK)heldAction=1;
    return true;
   }
   if(action==MotionEvent.ACTION_UP){
    heldAction=-1;
    int selected=control.actionForGesture(downX,downY,x,y);
    if(selected>=0)runtime.manualAction(selected);
    v.performClick();v.invalidate();return true;
   }
   if(action==MotionEvent.ACTION_CANCEL){heldAction=-1;return true;}
   return true;
  });
status=new TextView(this);status.setTextSize(12);ScrollView stats=new ScrollView(this);stats.addView(status);box.addView(stats,new LinearLayout.LayoutParams(-1,dp(120)));

  TextView manualNote=new TextView(this);
  manualNote.setText("Четыре учебные механики, НЕ копии игр Маркета. Управление работает только в режиме «Играть самому». Ручные ходы не подменяют обучающие награды ИИ.");
  box.addView(manualNote);
  LinearLayout manualRow=new LinearLayout(this);box.addView(manualRow);
  manualPlay=button(manualRow,"Играть самому",()->{
   heldAction=-1;
   if(runtime.startManual(selected())){
    buildManualControls();
    status.setText(runtime.message);
   }
  });
  manualReset=button(manualRow,"Заново",()->{heldAction=-1;runtime.manualReset();scene.invalidate();});
  manualReset.setEnabled(false);
  manualHelp=new TextView(this);
  manualHelp.setText("Удерживай кнопку, чтобы действие повторялось. На поле: касание слева/справа, свайп для смены полосы или прыжка. Пауза / STOP прекращает ввод.");
  box.addView(manualHelp);
  manualControls=new LinearLayout(this);manualControls.setOrientation(LinearLayout.VERTICAL);
  box.addView(manualControls);manualControls.setVisibility(View.GONE);
  manualHelp.setVisibility(View.GONE);
  LinearLayout row=new LinearLayout(this);box.addView(row);
  train=button(row,"Обучать ИИ",()->runtime.start(selected(),0));
  evaluate=button(row,"Оценить модель",()->runtime.start(selected(),1));
  LinearLayout row2=new LinearLayout(this);box.addView(row2);
  play=button(row2,"Смотреть ИИ",()->runtime.start(selected(),2));
  stopButton=button(row2,"STOP",()->{heldAction=-1;runtime.pause();scene.invalidate();});
  export=button(box,"Экспорт обученной модели",()->{
   if(runtime.checkpoint==null||!runtime.checkpoint.isFile()){toast("Нет сохранённой модели");return;}
   startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
      .addCategory(Intent.CATEGORY_OPENABLE)
      .putExtra(Intent.EXTRA_TITLE,"localpilot-"+runtime.current+"-checkpoint.zip"),71);
  });
  // The paddle weight-transfer experiment uses autonomous simulation, not
  // interactive gamepad controls. Keep it separate and collapsed by default.
  LinearLayout experimental=new LinearLayout(this);
  experimental.setOrientation(LinearLayout.VERTICAL);
  experimental.setVisibility(View.GONE);
  button(box,"Карканоид · эксперименты ИИ ▾",()->experimental.setVisibility(
    experimental.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
  box.addView(experimental);
  TextView heading=new TextView(this);
  heading.setText("Физическая демонстрация и перенос модели. Ручной ввод здесь не поддерживается.");
  experimental.addView(heading);
  paddleScenario=new Spinner(this);
  paddleScenario.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
    new String[]{"Классический","Узкая платформа","Быстрый шар","Рикошеты от стен",
      "Пустоты между блоками","Плотные блоки","Задержка управления","Сильный отскок"}));
  experimental.addView(paddleScenario);
  paddleTrain=button(experimental,"Обучить на 8 сценариях",()->runtime.trainPaddle());
  paddleDemo=button(experimental,"Смотреть ИИ Карканоида",()->runtime.demoPaddle(
    PaddleWeightLab.Scenario.values()[paddleScenario.getSelectedItemPosition()]));
  paddleApply=button(experimental,"Перенести проверенные веса",()->{
   PaddleTransfer.Candidate candidate=runtime.paddleCandidate;
   if(candidate==null||!candidate.eligible()){toast("Кандидат не прошёл проверку");return;}
   PilotService pilot=PilotService.instance;
   toast(pilot==null?"Включите службу и выберите настоящий Карканоид":
     pilot.installPaddleTransfer(candidate));
  });

 }
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 private ArcadeEnv.Genre selectedGenre(){
  return ArcadeEnv.Genre.values()[Math.max(0,Math.min(
    ArcadeEnv.Genre.values().length-1,genre.getSelectedItemPosition()))];
 }
 /** Buttons map to actual ArcadeEnv actions, not a cosmetic mock joystick. */
 private void buildManualControls(){
  manualControls.removeAllViews();
  ArcadeManualController control=runtime.manual;
  if(control==null)return;
  String[] actions=control.controls();
  LinearLayout row=null;
  for(int i=0;i<actions.length;i++){
   if(i%3==0){row=new LinearLayout(this);manualControls.addView(row);}
   final int actionIndex=i;
   Button input=button(row,actions[i],()->{
    if(runtime.manual!=null)runtime.manualAction(actionIndex);
    scene.invalidate();
   });
   input.setOnTouchListener((v,event)->{
    int phase=event.getActionMasked();
    if(phase==MotionEvent.ACTION_DOWN){heldAction=actionIndex;return true;}
    if(phase==MotionEvent.ACTION_UP){
     heldAction=-1;v.performClick();return true;
    }
    if(phase==MotionEvent.ACTION_CANCEL){heldAction=-1;return true;}
    return true;
   });
  }
 }
 private ArcadeEnv.Genre selected(){int index=genre.getSelectedItemPosition();getSharedPreferences(MainActivity.PREF,MODE_PRIVATE).edit().putInt("training_genre",index).apply();return ArcadeEnv.Genre.values()[index];}
 private Button button(LinearLayout box,String text,Runnable run){Button b=new Button(this);b.setText(text);b.setTextSize(12);b.setOnClickListener(v->run.run());box.addView(b,box.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2));return b;}
 @Override protected void onResume(){super.onResume();handler.post(refresh);handler.post(manualClock);}
 @Override protected void onStop(){heldAction=-1;runtime.pause();handler.removeCallbacks(refresh);handler.removeCallbacks(manualClock);super.onStop();}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=71||result!=RESULT_OK||data==null)return;final File source=runtime.checkpoint;if(runtime.busy||source==null)return;
  runtime.executor.execute(()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException("output");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);runOnUiThread(()->toast("Checkpoint экспортирован"));}catch(IOException e){runOnUiThread(()->toast("Ошибка экспорта: "+e.getMessage()));}});
 }
 private static final class Runtime {
  final java.util.concurrent.ExecutorService executor=Executors.newSingleThreadExecutor();final File directory;
  volatile boolean busy,running;
  volatile ArcadeManualController manual;
  volatile String message="Выбери учебную механику: «Играть самому» или «Обучать ИИ»";
  boolean hasTrainedCheckpoint(ArcadeEnv.Genre genre){
   if(genre==null)return false;
   if(current==genre&&trainer!=null&&trainer.updates>0)return true;
   File saved=new File(directory,genre+".zip");
   return saved.isFile()&&saved.length()>256;
  }
  synchronized boolean startManual(ArcadeEnv.Genre genre){
   if(busy||genre==null)return false;
   manual=new ArcadeManualController(genre,20261108L);
   busy=true;running=false;paddleScene=null;
   publish(manual.env);
   message="Ручная игра · "+genre+" · касания и кнопки работают; это НЕ обучение ИИ";
   return true;
  }
  synchronized void manualAction(int action){
   ArcadeManualController input=manual;
   if(input==null)return;
   try{
    if(!input.step(action)){
     message="Уровень завершён · нажми «Заново» или STOP";
     return;
    }
    publish(input.env);
    message=String.format(Locale.ROOT,
      "Ручное управление %s · шагов %d · награда симулятора %.2f · прогресс %.1f%s",
      input.env.genre,input.steps(),input.reward(),input.env.progress,
      input.active()?"":" · ФИНИШ: Заново/STOP");
   }catch(RuntimeException rejected){message="Ввод симулятора отклонён: "+rejected.getClass().getSimpleName();}
  }
  synchronized void manualReset(){
   if(manual==null)return;
   manual.reset();publish(manual.env);
   message="Ручная игра сброшена · можешь управлять заново";
  }
  volatile float[] scene;volatile PaddleWeightLab.Scene paddleScene;
  volatile PaddleTransfer.Candidate paddleCandidate;
  volatile File checkpoint;volatile ArcadeEnv.Genre current;DqnTrainer trainer;
  Runtime(File root){directory=new File(root,"arcade-rl");}
  synchronized void start(ArcadeEnv.Genre genre,int mode){if(busy)return;busy=true;running=true;paddleScene=null;message="Загрузка модели…";
   executor.execute(()->{try{
    if(trainer==null||current!=genre){File f=new File(directory,genre+".zip");DqnTrainer loaded;
     loaded=DqnTrainer.loadRecovering(f,genre,20261021);trainer=loaded;checkpoint=f;current=genre;
    }
    if(mode!=0&&trainer.updates<=0){
     message="У модели нет обученных обновлений. Сначала нажми «Обучать ИИ»";return;
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
  synchronized void pause(){running=false;if(manual!=null){manual=null;busy=false;message="STOP · ручное управление выключено";}}
  String stats(){return String.format(Locale.ROOT,"%s · шаги %d · эпизоды %d\nОбновления %d · replay %d · epsilon %.3f\nНаграда %.3f · лучший прогресс %.1f · loss %.4f",current,trainer.steps,trainer.episodes,trainer.updates,trainer.replaySize(),trainer.epsilon(),trainer.lastReturn,trainer.bestProgress,trainer.loss);}
  void publish(ArcadeEnv e){
   float[] s=new float[2+e.objects.size()*5];boolean vertical=e.genre==ArcadeEnv.Genre.JUMPER||e.genre==ArcadeEnv.Genre.PLATFORMER;
   s[0]=e.genre==ArcadeEnv.Genre.JETPACK?70:e.x;s[1]=e.genre==ArcadeEnv.Genre.LANES?550-e.y:640-(e.y-(vertical?e.camera:0));
   int i=2;for(ArcadeEnv.Entity o:e.objects){s[i++]=e.genre==ArcadeEnv.Genre.JETPACK?70+o.x-e.progress:o.x;s[i++]=e.genre==ArcadeEnv.Genre.LANES?550-(o.y-e.progress):640-(o.y-(vertical?e.camera:0));s[i++]=o.used&&o.type==3?0:o.w;s[i++]=o.h;s[i++]=o.type;}scene=s;if(manual==null&&trainer!=null)message=stats();
  }
 }
}
