package org.localpilot.android;

import android.app.Activity;
import org.localpilot.shared.HarnessCoordinator;

/** True read-only live workspace over the existing executors, not another
 * emulator, model runtime, gesture dispatcher or mutable training state.
 * Starts are gated; the actual environment still has its own ActionGate/STOP. */
public final class HarnessWorkspaceEngine {
 private HarnessWorkspaceEngine(){}
 public record Live(String nesEpoch,String nesMode,String nesOwner,boolean nesWaiting,
   boolean nesAction,boolean nesBusy,String androidPackage,String androidProfile,
   boolean androidArmed,boolean androidRunning,long androidFrames,long androidVerified,
   String androidStopReason,String androidReadiness,HarnessWorkspacePolicy.Occupancy occupancy){
  public boolean canStartNes(){return occupancy.canStartNes();}
  public boolean canStartAndroid(){return occupancy.canStartAndroid();}
  public String runningLabel(){return occupancy.activeLabel();}
 }
 public static Live inspect(Activity activity){
  HarnessCoordinator nes=HarnessCoordinator.nes();
  HarnessCoordinator.Snapshot n=nes.snapshot();
  boolean nesBusy=nes.hasPending()||nes.actionInFlight();
  PilotService android=PilotService.instance;
  PilotService.HarnessAndroidState a=android==null?null:android.harnessEnvironmentState();
  String selected=activity.getSharedPreferences(MainActivity.PREF,Activity.MODE_PRIVATE)
    .getString("selected_game_package","");
  boolean armed=a!=null&&a.windowArmed(),running=a!=null&&a.capturing();
  boolean androidBusy=armed||running;
  String readiness=android==null?"Accessibility-служба не подключена":
    android.installedGameReadiness(selected);
  return new Live(n.epoch(),n.mode(),n.owner(),n.waiting(),n.actionInFlight(),nesBusy,
    a==null?selected:a.packageName(),a==null?"UNKNOWN":a.profile(),armed,running,
    a==null?0:a.frames(),a==null?-1:a.verifiedResponses(),
    a==null?"Нет подключённой Android-службы":a.lastStopReason(),readiness,
    new HarnessWorkspacePolicy.Occupancy(nesBusy,androidBusy));
 }
}
