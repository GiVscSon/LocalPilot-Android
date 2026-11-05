package org.localpilot.gamelab;
import org.localpilot.shared.HubContract;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * A real, separately installed Android activity used as the Pilot's target.
 * Delivers real MotionEvents and screen pixels from the Android compositor.
 * It is NOT a nested OS, and its simulated success is not Market-game evidence.
 */
public final class TestDeviceActivity extends Activity {
 public static final String BUILD_ID="GameLab-1.0";
 private static final int EXPORT=61;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ArrayList<String> records=new ArrayList<>();
 private GameLabEngine engine;
 private GameSurface surface;
 private TextView status;
 private Spinner games,faults;
 private Button retroLauncher;
 private boolean playing=true;
 private long seed=20261104L;
 private final Runnable clock=new Runnable(){public void run(){
  if(engine!=null&&surface!=null&&playing){
   if(engine.fault!=GameLabEngine.Fault.FROZEN_VIEW)engine.tick();
   surface.invalidate();
  }
  update();handler.postDelayed(this,55);
 }};
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  game(0);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
  GameLabInsets.install(this,root,12);root.setBackgroundColor(HubContract.BACKGROUND);
  TextView title=label("LocalPilot · GameLab",21);
  title.setTextColor(HubContract.ACCENT);
  root.addView(title);
  TextView warning=label(HubContract.Evidence.SIMULATED_GAME.label
    +" • отдельное приложение • не испытание игр Маркета",12);
  warning.setTextColor(HubContract.WARNING);root.addView(warning);
  games=new Spinner(this);
  String[] labels=new String[GameLabEngine.Game.values().length];
  for(int i=0;i<labels.length;i++)labels[i]=GameLabEngine.Game.values()[i].label;
  games.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
  games.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   @Override public void onItemSelected(android.widget.AdapterView<?> v,View selected,int position,long id){
    game(position);if(surface!=null)surface.invalidate();
   }
   @Override public void onNothingSelected(android.widget.AdapterView<?> v){}
  });
  root.addView(games);
  faults=new Spinner(this);
  faults.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
   new String[]{"Нормальная игра","Игнорировать вход","Заморозить экран","Панель закрывает поле"}));
  faults.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   @Override public void onItemSelected(android.widget.AdapterView<?> v,View selected,int position,long id){
    if(engine==null||surface==null)return;
    engine.fault=GameLabEngine.Fault.values()[position];
    surface.freeze(engine.fault==GameLabEngine.Fault.FROZEN_VIEW);
   }
   @Override public void onNothingSelected(android.widget.AdapterView<?> v){}
  });
  root.addView(faults);
  surface=new GameSurface();root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout row=new LinearLayout(this);root.addView(row);
  addButton(row,"Сброс",()->{engine.reset();surface.clearFreeze();faults.setSelection(0);addLog("RESET");});
  addButton(row,"Пауза",()->{playing=!playing;addLog(playing?"RESUME":"PAUSE");});
  addButton(row,"Фокус",()->new AlertDialog.Builder(this).setTitle("Проверка потери фокуса")
    .setMessage("Это реальное Android-диалоговое окно. Проверяй, что LocalPilot остановит ввод и не примет старые кадры.")
    .setPositiveButton("Закрыть",null).show());
  addButton(row,"CSV",()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
      .setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE)
      .putExtra(Intent.EXTRA_TITLE,"localpilot-gamelab-actions.csv"),EXPORT));
  LinearLayout retroRow=new LinearLayout(this);root.addView(retroRow);
  retroLauncher=addButton(retroRow,"Ретро · NES / Денди / DOS",()->startActivity(new Intent(this,RetroLibraryActivity.class)));
  TextView retroStatus=label("NES и Денди: запуск реального ROM. DOS: пока подготовка ядра.",11);
  retroStatus.setTextColor(HubContract.SECONDARY);root.addView(retroStatus);
  String pilotPackage=getIntent().getStringExtra("pilot_package");
  if(HubContract.supportedPilotPackage(pilotPackage))
   addButton(retroRow,"К LocalPilot",()->{
    // Explicit exported launcher avoids Android 11+ package-visibility false
    // negatives. Launching Pilot never grants Accessibility consent.
    try{
     Intent back=new Intent(Intent.ACTION_MAIN)
       .setClassName(pilotPackage,"org.localpilot.android.MainActivity")
       .addCategory(Intent.CATEGORY_LAUNCHER);
     startActivity(back);
    }catch(android.content.ActivityNotFoundException notInstalled){
     addLog("PILOT_LAUNCHER_NOT_FOUND");
    }
   });
  status=label("",12);root.addView(status);
  setContentView(root);
  root.requestApplyInsets();
  addLog("START game="+engine.game.name());
 }
 private Button addButton(LinearLayout parent,String title,Runnable run){
  Button b=new Button(this);b.setText(title);b.setTextSize(11);
  b.setOnClickListener(v->run.run());parent.addView(b,new LinearLayout.LayoutParams(0,-2,1));
  return b;
 }
 private TextView label(String content,int size){
  TextView t=new TextView(this);t.setText(content);t.setTextSize(size);
  t.setTextColor(HubContract.TEXT);return t;
 }
 private void game(int index){
  if(index<0||index>=GameLabEngine.Game.values().length)return;
  engine=new GameLabEngine(GameLabEngine.Game.values()[index],seed+index*101);
  if(surface!=null)surface.clearFreeze();
  addLog("GAME "+engine.game+" pilotProfile="+engine.game.localPilotProfile);
  update();
 }
 private void update(){
  if(status==null||engine==null)return;
  status.setText("Только тестовое поле • профиль "+engine.game.localPilotProfile
   +" • счёт "+engine.score+" • касаний "+engine.actions+" • изменений стенда "+engine.simulatedChanges
   +" • отказов "+engine.rejected+" • режим "+engine.fault);
  // Stable, machine-readable ACCESSIBILITY evidence. This is an actual
  // Android-rendered TextView value updated only after real UI event handling.
  // It is deliberately marked "synthetic", not a Market-game reward.
  status.setContentDescription("gamelab-state-v1 synthetic=true game="+engine.game.name()
    +" touches="+engine.actions+" changes="+engine.simulatedChanges
    +" rejected="+engine.rejected+" profile="+engine.game.localPilotProfile);
  GameLabEvidenceProvider.game(engine,surface,retroLauncher);
 }
 private void addLog(String entry){
  if(records.size()>=512)records.remove(0);
  records.add(SystemClock.elapsedRealtime()+","+entry.replace(',',';'));
 }
 @Override protected void onResume(){super.onResume();handler.post(clock);}
 @Override protected void onPause(){handler.removeCallbacks(clock);GameLabEvidenceProvider.clear("gamelab");super.onPause();}
 @Override protected void onDestroy(){if(surface!=null)surface.release();super.onDestroy();}
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(req!=EXPORT||result!=RESULT_OK||data==null||data.getData()==null)return;
  StringBuilder body=new StringBuilder("uptime_ms,event\n");
  for(String record:records)body.append(record).append('\n');
  try(OutputStream stream=getContentResolver().openOutputStream(data.getData())){
   if(stream==null)throw new IOException("no writer");
   stream.write(body.toString().getBytes(StandardCharsets.UTF_8));
  }catch(IOException failure){
   new AlertDialog.Builder(this).setMessage("Не удалось сохранить журнал").setPositiveButton("OK",null).show();
  }
 }
 private final class GameSurface extends View {
  private final Paint brush=new Paint(3);
  private final RectF rect=new RectF();
  private Bitmap frozen;
  private float downX,downY;private long downAt;
  GameSurface(){super(TestDeviceActivity.this);
   setContentDescription("GameLab synthetic game display; never real Market acceptance");
  }
  void freeze(boolean enabled){
   if(!enabled){clearFreeze();return;}
   if(frozen!=null)return;
   Bitmap b=Bitmap.createBitmap(360,640,Bitmap.Config.ARGB_8888);
   Canvas backing=new Canvas(b);
   render(backing);
   frozen=b;invalidate();
  }
  void clearFreeze(){if(frozen!=null){frozen.recycle();frozen=null;}invalidate();}
  void release(){clearFreeze();}
  @Override protected void onDraw(Canvas c){
   super.onDraw(c);
   if(engine==null)return;
   // A single scale preserves square 2048 cells and the CV/object geometry
   // on both phones and tablets. Never stretch X and Y independently.
   GameLabViewport transform=new GameLabViewport(getWidth(),getHeight());
   c.drawColor(HubContract.BACKGROUND);
   c.save();
   c.translate(transform.offsetX,transform.offsetY);
   c.scale(transform.scale,transform.scale);
   if(frozen!=null)c.drawBitmap(frozen,0,0,brush);
   else render(c);
   c.restore();
  }
  private void color(int argb){brush.setColor(argb);brush.setStyle(Paint.Style.FILL);}
  private void block(Canvas c,float l,float t,float r,float b,int argb,float radius){
   color(argb);c.drawRoundRect(l,t,r,b,radius,radius,brush);
  }
  private void circle(Canvas c,float x,float y,float rad,int argb){
   color(argb);c.drawCircle(x,y,rad,brush);
  }
  private void words(Canvas c,String text,float x,float y,int size,int color){
   color(color);brush.setTextSize(size);c.drawText(text,x,y,brush);
  }
  private void hex(Canvas c,float x,float y,float radius,int color){
   Path path=new Path();
   for(int i=0;i<6;i++){
    double radians=Math.PI/3*i-Math.PI/6;
    float vx=x+(float)Math.cos(radians)*radius;
    float vy=y+(float)Math.sin(radians)*radius;
    if(i==0)path.moveTo(vx,vy);else path.lineTo(vx,vy);
   }
   path.close();color(color);c.drawPath(path,brush);
  }
  private int tint(int index){
   int[] palette={0xff234569,0xff45b5df,0xffed954c,0xff86d184,0xffeb6f91,
     0xffbd86e8,0xffffcf67,0xffb3cad7,0xff303444,0xff22293d};
   return palette[Math.abs(index)%palette.length];
  }
  private void render(Canvas c){
   c.drawColor(0xff111e31);
   block(c,0,0,360,75,0xff172e45,0);
   words(c,engine.game.label,13,32,21,Color.WHITE);
   words(c,"СИМУЛЯЦИЯ · "+engine.game.name(),13,56,12,0xffffbd77);
   // Gameplay always fits the ROI 0..1 x 0.13..0.90.
   block(c,8,86,352,580,0xff1c3349,13);
   switch(engine.game){
    case BUBBLE -> {
     for(int row=0;row<engine.n;row++)for(int col=0;col<engine.n;col++){
      int value=engine.cells[row*engine.n+col];if(value==0)continue;
      circle(c,32+col*42+(row%2)*10,115+row*39,17,tint(value));
      circle(c,26+col*42+(row%2)*10,108+row*39,5,0x88ffffff);
     }
     block(c,155,490,205,545,0xffd4a181,8);circle(c,180,495,18,tint(engine.launches%4+1));
     words(c,"Aim & tap",122,575,15,Color.WHITE);
    }
    case ARI -> {
     for(int i=0;i<8;i++){
      float x=23+(i*79)%250,y=150+i*48-(engine.ticks%35);
      block(c,x,y,x+80,y+11,0xffde9149,5);
     }
     block(c,engine.playerX*360-14,engine.playerY*640-17,
       engine.playerX*360+14,engine.playerY*640+23,0xffffcc51,12);
     circle(c,engine.playerX*360-5,engine.playerY*640-5,2,0xff222222);
     words(c,"wrap-around • tap / swipe",65,560,13,0xffc6d6e5);
    }
    case PADDLE -> {
     for(int i=0;i<24;i++){
      if((i+engine.score)%11==0)continue;
      float x=14+(i%8)*42,y=130+(i/8)*28;
      block(c,x,y,x+39,y+20,tint(i%5+1),3);
     }
     circle(c,engine.ballX*360,engine.ballY*640,11,0xfffa334a);
     block(c,engine.paddleX*360-47,530,engine.paddleX*360+47,546,0xffc8955c,8);
    }
    case TRAFFIC -> {
     block(c,70,90,290,577,0xff747986,3);
     for(int lane=1;lane<=2;lane++)for(int k=0;k<10;k++)
      block(c,70+lane*220f/3-2,92+k*52+engine.ticks%26,
        70+lane*220f/3+2,115+k*52+engine.ticks%26,0xffdbe5e9,1);
     block(c,98,190+engine.ticks%200,145,257+engine.ticks%200,0xff2f3641,8);
     float carX=70+(engine.lane+.5f)*220/3;
     block(c,carX-24,461,carX+24,530,0xffe74e4f,8);
     block(c,carX-17,467,carX+17,484,0xff263d49,3);
     block(c,carX-19,506,carX+19,516,0xffefce61,4);
    }
    case FLAPPY -> {
     float by=engine.playerY*640;
     block(c,290,115,326,200,0xff55ba66,5);
     block(c,290,360,326,545,0xff55ba66,5);
     for(int i=0;i<5;i++)block(c,290+i*4,140,294+i*4,160,0xff428f56,1);
     block(c,13,560,347,580,0xffb17b4c,0);
     circle(c,93,by,21,0xff2e3541);block(c,78,by-21,103,by-12,0xffe94f55,4);
     words(c,"Tap = flap",25,110,14,Color.WHITE);
    }
    case BOARD2048,MATCH,PROTECTED_SWAP -> drawSquareBoard(c);
    case HEXON -> {
     for(int i=0;i<9;i++){
      float x=66+(i%3)*112+(i/3%2)*14,y=150+(i/3)*102;
      hex(c,x,y,34,tint(engine.cells[i]));
     }
     for(int i=0;i<3;i++){
      block(c,19+i*112,474,110+i*112,570,0xff364960,8);
      words(c,""+engine.slots[i]+"/10",40+i*112,523,22,0xffdfe9f7);
     }
    }
    case CAT_TOWER -> {
     for(int i=0;i<7;i++){
      int x=20+(i*55)%230,y=140+i*62;
      block(c,x,y,x+105,y+14,0xffabb0bf,6);
     }
     circle(c,engine.playerX*360,engine.playerY*640,23,0xffefac59);
     hex(c,engine.playerX*360-13,engine.playerY*640-15,9,0xffefac59);
     hex(c,engine.playerX*360+13,engine.playerY*640-15,9,0xffefac59);
     circle(c,engine.playerX*360-8,engine.playerY*640-3,3,0xff162536);
     circle(c,engine.playerX*360+8,engine.playerY*640-3,3,0xff162536);
    }
   }
   if(engine.fault==GameLabEngine.Fault.PANEL_OVERLAP){
    block(c,0,405,300,572,0xd922293b,13);
    words(c,"PANEL COVERS ROI",18,464,17,0xffffaaaa);
   }
   block(c,0,588,360,640,0xff142b41,0);
   words(c,"касания "+engine.actions+"  изменения стенда "+engine.simulatedChanges,10,613,12,0xffa5e8f5);
   words(c,"НЕ СЧИТАЕТСЯ РЕАЛЬНОЙ ПРИЁМКОЙ",10,634,10,HubContract.WARNING);
  }
  private void drawSquareBoard(Canvas c){
   int n=engine.n;float left=GameLabEngine.GRID_LEFT*360f,top=GameLabEngine.GRID_TOP*640f;
    float cell=(GameLabEngine.GRID_RIGHT-GameLabEngine.GRID_LEFT)*360f/n;
   block(c,left-6,top-6,left+cell*n+6,top+cell*n+6,0xff506278,10);
   for(int r=0;r<n;r++)for(int col=0;col<n;col++){
    int v=engine.cells[r*n+col];
    float x=left+col*cell,y=top+r*cell;
    int fill=engine.game==GameLabEngine.Game.BOARD2048
      ?0xff000000|GameLab2048Palette.rgb(v)
      :v>=8?0xff2a2e37:tint(v);
    block(c,x+2,y+2,x+cell-2,y+cell-2,fill,6);
    if(v!=0){
     String label=engine.game==GameLabEngine.Game.BOARD2048?String.valueOf(v):
       v==8?"☠":v==9?"!":"●";
     words(c,label,x+cell*.27f,y+cell*.63f,Math.max(14,(int)(cell*.32f)),Color.WHITE);
    }
   }
  }
  @Override public boolean onTouchEvent(MotionEvent event){
   if(engine==null)return false;
   GameLabViewport geometry=new GameLabViewport(getWidth(),getHeight());
   float x=geometry.xFromScreen(event.getX()),y=geometry.yFromScreen(event.getY());
   if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
    if(!geometry.contains(event.getX(),event.getY()))return false;
    downX=x;downY=y;downAt=SystemClock.elapsedRealtime();return true;
   }
   if(event.getActionMasked()!=MotionEvent.ACTION_UP)return true;
   long elapsed=SystemClock.elapsedRealtime()-downAt;
   if(engine.fault==GameLabEngine.Fault.PANEL_OVERLAP&&y>.63f&&x<.84f){
    addLog("PANEL_BLOCKED,game="+engine.game);return true;
   }
   int old=engine.fingerprint();
   GameLabEngine.Result response=engine.gesture(downX,downY,x,y,elapsed);
   addLog("TOUCH_RECEIVED,game="+engine.game+",result="+response+
    ",changed_state="+(old!=engine.fingerprint())+",fault="+engine.fault);
   invalidate();update();
   return true;
  }
 }
}
