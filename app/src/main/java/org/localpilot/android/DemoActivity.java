package org.localpilot.android;
import android.app.*;import android.os.*;import android.view.*;import android.widget.*;
public class DemoActivity extends Activity {
 private int score=0;private FrameLayout field;private TextView label;private Button target;
 @Override public void onCreate(Bundle b){super.onCreate(b);field=new FrameLayout(this);setContentView(field);label=new TextView(this);label.setTextSize(22);label.setText("Счёт: 0\nНажмите «Начать проверку ×10»");label.setContentDescription("LOCALPILOT_DEMO_SCORE:0");field.addView(label);target=new Button(this);target.setText("ЦЕЛЬ");target.setContentDescription("LOCALPILOT_DEMO_TARGET");target.setOnClickListener(v->{score++;label.setText("Счёт: "+score);label.setContentDescription("LOCALPILOT_DEMO_SCORE:"+score);move();});field.addView(target,new FrameLayout.LayoutParams(170,100));
  Button start=new Button(this);start.setText("Начать проверку ×10");start.setContentDescription("LOCALPILOT_DEMO_START");
  start.setOnClickListener(v->{if(PilotService.instance==null)Toast.makeText(this,"Включите службу LocalPilot в специальных возможностях",Toast.LENGTH_LONG).show();else PilotService.instance.startTouchCheck();});
  FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);params.setMargins(24,0,24,24);field.addView(start,params);field.post(this::move);}
 private void move(){int w=field.getWidth(),h=field.getHeight();target.setX(Math.max(0,(w-190)*((score*37%100)/100f)));target.setY(Math.max(150,(h-300)*(.35f+(score*17%50)/100f)));}
}
