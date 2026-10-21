package org.localpilot.android;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import org.localpilot.android.rl.*;
import java.io.*;
import java.util.Locale;
import java.util.concurrent.Executors;

/** Native on-device training. Activity disappearance pauses the single application worker. */
public final class TrainingActivity extends Activity {
 private static Runtime runtime;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private TextView status;private Spinner genre;private View scene;private Button train,evaluate,play,export;
 private final Runnable refresh=new Runnable(){public void run(){status.setText(runtime.message);scene.invalidate();boolean idle=!runtime.busy;genre.setEnabled(idle);train.setEnabled(idle);evaluate.setEnabled(idle);play.setEnabled(idle);export.setEnabled(idle);handler.postDelayed(this,250);}};
 @Override public void onCreate(Bundle b){super.onCreate(b);if(runtime==null)runtime=new Runtime(getFilesDir());
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(12,8,12,8);ScrollView page=new ScrollView(this);page.setFillViewport(true);page.addView(box);setContentView(page);
  TextView title=new TextView(this);title.setText("Аркады · обучение на Android");title.setTextSize(20);box.addView(title);
  genre=new Spinner(this);genre.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Вертикальный джампер","Вертикальный платформер","Раннер по полосам","Jetpack"}));genre.setSelection(Math.max(0,Math.min(3,getSharedPreferences(MainActivity.PREF,MODE_PRIVATE).getInt("training_genre",0))));box.addView(genre);
  TextView help=new TextView(this);help.setText("Собственные симуляторы · Double DQN · координаты объектов. Это ещё не управление установленным Doodle Jump. При уходе в фон — пауза и checkpoint.");box.addView(help);
  scene=new View(this){private final Paint p=new Paint(3);@Override public void onDraw(Canvas c){super.onDraw(c);c.drawColor(0xff101d2d);float[] s=runtime.scene;if(s==null)return;c.save();float scale=Math.min(getWidth()/360f,getHeight()/640f);c.translate((getWidth()-360*scale)/2,(getHeight()-640*scale)/2);c.scale(scale,scale);p.setColor(0xff5bc2ff);c.drawRect(s[0]-10,s[1]-14,s[0]+10,s[1]+14,p);for(int i=2;i+4<s.length;i+=5){p.setColor(s[i+4]==1?0xff62d394:s[i+4]==2?0xffee6677:0xffffd166);c.drawRect(s[i]-s[i+2]/2,s[i+1]-s[i+3]/2,s[i]+s[i+2]/2,s[i+1]+s[i+3]/2,p);}c.restore();}};
  box.addView(scene,new LinearLayout.LayoutParams(-1,dp(Math.max(220,Math.min(480,getResources().getConfiguration().screenHeightDp-360)))));status=new TextView(this);status.setTextSize(12);ScrollView stats=new ScrollView(this);stats.addView(status);box.addView(stats,new LinearLayout.LayoutParams(-1,dp(120)));
  LinearLayout row=new LinearLayout(this);box.addView(row);train=button(row,"Обучать",()->runtime.start(selected(),0));evaluate=button(row,"Оценить ×50",()->runtime.start(selected(),1));
  LinearLayout row2=new LinearLayout(this);box.addView(row2);play=button(row2,"Показать модель",()->runtime.start(selected(),2));button(row2,"Пауза / STOP",()->runtime.pause());
  export=button(box,"Экспорт checkpoint",()->{if(runtime.checkpoint==null||!runtime.checkpoint.isFile()){toast("Сначала обучите и поставьте на паузу");return;}startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"localpilot-"+runtime.current+"-checkpoint.zip"),71);});
 }
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 private ArcadeEnv.Genre selected(){int index=genre.getSelectedItemPosition();getSharedPreferences(MainActivity.PREF,MODE_PRIVATE).edit().putInt("training_genre",index).apply();return ArcadeEnv.Genre.values()[index];}
 private Button button(LinearLayout box,String text,Runnable run){Button b=new Button(this);b.setText(text);b.setTextSize(12);b.setOnClickListener(v->run.run());box.addView(b,box.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2));return b;}
 @Override protected void onResume(){super.onResume();handler.post(refresh);}
 @Override protected void onStop(){runtime.pause();handler.removeCallbacks(refresh);super.onStop();}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=71||result!=RESULT_OK||data==null)return;final File source=runtime.checkpoint;if(runtime.busy||source==null)return;
  runtime.executor.execute(()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException("output");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);runOnUiThread(()->toast("Checkpoint экспортирован"));}catch(IOException e){runOnUiThread(()->toast("Ошибка экспорта: "+e.getMessage()));}});
 }
 private static final class Runtime {
  final java.util.concurrent.ExecutorService executor=Executors.newSingleThreadExecutor();final File directory;
  volatile boolean busy,running;volatile String message="Выберите жанр и нажмите «Обучать»";volatile float[] scene;volatile File checkpoint;volatile ArcadeEnv.Genre current;DqnTrainer trainer;
  Runtime(File root){directory=new File(root,"arcade-rl");}
  synchronized void start(ArcadeEnv.Genre genre,int mode){if(busy)return;busy=true;running=true;message="Загрузка модели…";
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
  void pause(){running=false;}
  String stats(){return String.format(Locale.ROOT,"%s · шаги %d · эпизоды %d\nОбновления %d · replay %d · epsilon %.3f\nНаграда %.3f · лучший прогресс %.1f · loss %.4f",current,trainer.steps,trainer.episodes,trainer.updates,trainer.replaySize(),trainer.epsilon(),trainer.lastReturn,trainer.bestProgress,trainer.loss);}
  void publish(ArcadeEnv e){
   float[] s=new float[2+e.objects.size()*5];boolean vertical=e.genre==ArcadeEnv.Genre.JUMPER||e.genre==ArcadeEnv.Genre.PLATFORMER;
   s[0]=e.genre==ArcadeEnv.Genre.JETPACK?70:e.x;s[1]=e.genre==ArcadeEnv.Genre.LANES?550-e.y:640-(e.y-(vertical?e.camera:0));
   int i=2;for(ArcadeEnv.Entity o:e.objects){s[i++]=e.genre==ArcadeEnv.Genre.JETPACK?70+o.x-e.progress:o.x;s[i++]=e.genre==ArcadeEnv.Genre.LANES?550-(o.y-e.progress):640-(o.y-(vertical?e.camera:0));s[i++]=o.used&&o.type==3?0:o.w;s[i++]=o.h;s[i++]=o.type;}scene=s;message=stats();
  }
 }
}
