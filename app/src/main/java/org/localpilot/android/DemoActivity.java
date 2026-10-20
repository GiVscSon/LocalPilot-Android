package org.localpilot.android;
import android.app.*;import android.os.*;import android.view.*;import android.widget.*;
public class DemoActivity extends Activity {
 private int score=0;private FrameLayout field;private TextView label;private Button target;
 @Override public void onCreate(Bundle b){super.onCreate(b);field=new FrameLayout(this);setContentView(field);label=new TextView(this);label.setTextSize(22);label.setText("Счёт: 0\nПривяжите панель, затем Демо ×10");field.addView(label);target=new Button(this);target.setText("ЦЕЛЬ");target.setContentDescription("LOCALPILOT_DEMO_TARGET");target.setOnClickListener(v->{score++;label.setText("Счёт: "+score);move();});field.addView(target,new FrameLayout.LayoutParams(170,100));field.post(this::move);}
 private void move(){int w=field.getWidth(),h=field.getHeight();target.setX(Math.max(0,(w-190)*((score*37%100)/100f)));target.setY(Math.max(150,(h-300)*(.35f+(score*17%50)/100f)));}
}
