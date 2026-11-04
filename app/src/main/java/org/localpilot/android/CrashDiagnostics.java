package org.localpilot.android;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Local bounded crash metadata: never exception messages, frames, URLs or credentials. */
public final class CrashDiagnostics {
 private static volatile String phase="STARTUP";
 private static volatile String installedBuild="unknown";
 private static volatile ActivityManager processManager;
 public static String buildLabel(Context context){
  try{android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);return info.versionName+"/code"+info.getLongVersionCode();}
  catch(android.content.pm.PackageManager.NameNotFoundException|RuntimeException ignored){return "unknown";}
 }
 /** Android stores only our bounded version/phase marker, never a screenshot or arbitrary text. */
 public static void phase(String value){
  try{
   byte[] summary=ProcessExitMetadata.encode(installedBuild,value);phase=value;
   ActivityManager manager=processManager;if(manager!=null)manager.setProcessStateSummary(summary);
  }catch(RuntimeException|OutOfMemoryError ignored){/* Metadata must not interrupt the application. */}
 }
 public static void captureStopped(){
  if(phase.startsWith("FRAME_UI_")||phase.equals("SERVICE_INTERRUPTED")||phase.equals("SERVICE_DESTROYED"))return;
  phase("CAPTURE_STOPPED");
 }
 public static void install(Context context){
  installedBuild=buildLabel(context);
  processManager=(ActivityManager)context.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
  phase("STARTUP");
  final File file=new File(context.getFilesDir(),"last-java-crash.txt");
  final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
   try(FileOutputStream out=new FileOutputStream(file)){
    StringBuilder s=new StringBuilder("LocalPilot crash metadata\nBuildAtCrash=").append(installedBuild).append("\ntime=").append(System.currentTimeMillis()).append("\nphase=").append(phase).append("\ntype=").append(error.getClass().getName()).append('\n');
    StackTraceElement[] stack=error.getStackTrace();
    for(int i=0;i<Math.min(stack.length,16);i++)s.append(stack[i].getClassName()).append('.').append(stack[i].getMethodName()).append(':').append(stack[i].getLineNumber()).append('\n');
    out.write(s.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();
   }catch(Throwable ignored){}
   if(previous!=null)previous.uncaughtException(thread,error);
   else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
  });
 }
 public static String report(Context context){
  StringBuilder s=new StringBuilder("LocalPilot crash diagnostics\n");
  s.append("Build=").append(buildLabel(context)).append('\n');
  s.append("Current phase=").append(phase).append('\n');
  s.append("Own accessibility setting=").append(ownServiceState(context)).append(" connected=").append(PilotService.instance!=null).append('\n');
  File f=new File(context.getFilesDir(),"last-java-crash.txt");
  if(f.isFile())try(InputStream in=new FileInputStream(f)){byte[] buffer=new byte[8192];int n=in.read(buffer);if(n>0)s.append(new String(buffer,0,n,StandardCharsets.UTF_8));}catch(IOException e){s.append("Saved crash report unreadable\n");}
  else s.append("No saved Java crash\n");
  try{
   ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
   int count=0;for(ApplicationExitInfo exit:manager.getHistoricalProcessExitReasons(context.getPackageName(),0,3)){
    if(count++>=3)break;
    s.append("Previous exit: reason=").append(exit.getReason()).append('(').append(ProcessExitMetadata.reasonName(exit.getReason())).append(") status=").append(exit.getStatus())
     .append(" timestamp=").append(exit.getTimestamp()).append(" importance=").append(exit.getImportance()).append('(').append(ProcessExitMetadata.importanceName(exit.getImportance())).append(')')
     .append(" pss_kb=").append(Math.max(0,exit.getPss())).append(" rss_kb=").append(Math.max(0,exit.getRss())).append('\n');
    ProcessExitMetadata.Summary summary=ProcessExitMetadata.decode(exit.getProcessStateSummary());
    if(summary==null)s.append("Previous state: unavailable\n");
    else s.append("Previous state: build=").append(summary.build).append(" phase=").append(summary.phase).append('\n');
   }
  }catch(RuntimeException e){s.append("Exit metadata unavailable\n");}
  s.append("Reason13=OTHER (cause unspecified);10=USER_REQUESTED;11=USER_STOPPED;3=LOW_MEMORY;4=CRASH;5=CRASH_NATIVE;6=ANR.\n")
   .append("No saved Java crash does not exclude process termination. RAM at export is not RAM at the previous exit. Zero PSS/RSS means unavailable.\n");
  return s.toString();
 }
 private static String ownServiceState(Context context){
  try{
   android.view.accessibility.AccessibilityManager manager=(android.view.accessibility.AccessibilityManager)context.getSystemService(Context.ACCESSIBILITY_SERVICE);
   if(manager==null)return "UNKNOWN";
   android.content.ComponentName own=new android.content.ComponentName(context,PilotService.class);
   for(android.accessibilityservice.AccessibilityServiceInfo service:manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
    if(own.equals(android.content.ComponentName.unflattenFromString(service.getId())))return "ENABLED";
   return "DISABLED";
  }catch(RuntimeException ignored){return "UNKNOWN";}
 }
 public static String connectionHint(Context context){
  if(PilotService.instance!=null)return "Служба подключена · выберите игру или обучение";
  return switch(ownServiceState(context)){
   case "ENABLED"->"Служба разрешена в Android, но не подключена. Выключите и снова включите LocalPilot в специальных возможностях. Данные игры сохраняются.";
   case "DISABLED"->"Управление отключено. Нажмите «Включить управление» и включите службу LocalPilot. Обучение в симуляторах доступно.";
   default->"Служба не подключена; статус разрешения недоступен. Проверьте LocalPilot в специальных возможностях Android.";
  };
 }
}
