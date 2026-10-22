package org.localpilot.android;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Local bounded crash metadata: never exception messages, frames, URLs or credentials. */
public final class CrashDiagnostics {
 private static volatile String phase="STARTUP";
 public static void phase(String value){phase=value;}
 public static void install(Context context){
  final File file=new File(context.getFilesDir(),"last-java-crash.txt");
  final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
   try(FileOutputStream out=new FileOutputStream(file)){
    StringBuilder s=new StringBuilder("LocalPilot crash metadata\ntime=").append(System.currentTimeMillis()).append("\nphase=").append(phase).append("\ntype=").append(error.getClass().getName()).append('\n');
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
  try{android.content.pm.PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),0);s.append("Build=").append(p.versionName).append("/code").append(p.getLongVersionCode()).append('\n');}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
  s.append("Current phase=").append(phase).append('\n');
  File f=new File(context.getFilesDir(),"last-java-crash.txt");
  if(f.isFile())try(InputStream in=new FileInputStream(f)){byte[] buffer=new byte[8192];int n=in.read(buffer);if(n>0)s.append(new String(buffer,0,n,StandardCharsets.UTF_8));}catch(IOException e){s.append("Saved crash report unreadable\n");}
  else s.append("No saved Java crash\n");
  try{
   ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
   for(ApplicationExitInfo exit:manager.getHistoricalProcessExitReasons(context.getPackageName(),0,3))
    s.append("Previous exit: reason=").append(exit.getReason()).append(" status=").append(exit.getStatus()).append(" timestamp=").append(exit.getTimestamp()).append(" importance=").append(exit.getImportance()).append('\n');
  }catch(RuntimeException e){s.append("Exit metadata unavailable\n");}
  s.append("Reason3=low memory;4=Java crash;5=native crash;6=ANR (Android ApplicationExitInfo).\n");
  return s.toString();
 }
}
