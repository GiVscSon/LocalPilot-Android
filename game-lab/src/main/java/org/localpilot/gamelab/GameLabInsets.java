package org.localpilot.gamelab;

import android.app.Activity;
import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;

/**
 * Android 15+ edge-to-edge: a view's global visible bounds do NOT by
 * themselves prove that the system navigation region can receive touches.
 * Reserve system bar and display-cutout insets for all GameLab controls.
 *
 * API min 30, no androidx dependency and no accessibility permission changes.
 */
public final class GameLabInsets {
 private GameLabInsets(){}
 public static void install(Activity activity, View content, int baseDp){
  if(activity==null||content==null||baseDp<0)throw new IllegalArgumentException("insets");
  activity.getWindow().setDecorFitsSystemWindows(false);
  int dp=Math.round(baseDp*activity.getResources().getDisplayMetrics().density);
  content.setOnApplyWindowInsetsListener((view,insets)->{
   Insets safe=insets.getInsets(WindowInsets.Type.systemBars()
        |WindowInsets.Type.displayCutout());
   int left=Math.max(safe.left,dp),top=Math.max(safe.top,dp);
   int right=Math.max(safe.right,dp),bottom=Math.max(safe.bottom,dp);
   if(view.getPaddingLeft()!=left||view.getPaddingTop()!=top||
      view.getPaddingRight()!=right||view.getPaddingBottom()!=bottom)
    view.setPadding(left,top,right,bottom);
   return insets;
  });
 }
}
