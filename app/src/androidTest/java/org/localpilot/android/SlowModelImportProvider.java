package org.localpilot.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Test-APK only: an incomplete, deliberately invalid GGUF stream. Never a model fixture for inference. */
public final class SlowModelImportProvider extends ContentProvider {
 private volatile CountDownLatch release=new CountDownLatch(1);
 private volatile boolean started;
 @Override public boolean onCreate(){return true;}
 @Override public Bundle call(String method,String arg,Bundle extras){
  if("reset".equals(method)){release.countDown();release=new CountDownLatch(1);started=false;}
  if("release".equals(method))release.countDown();
  Bundle result=new Bundle();result.putBoolean("started",started);return result;
 }
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws java.io.FileNotFoundException {
  if(!"r".equals(mode))throw new java.io.FileNotFoundException("Read only fixture");
  try{
   ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
   CountDownLatch gate=release;
   Thread writer=new Thread(()->{
    try(FileOutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){
     out.write(new byte[]{'G','G','U','F'});out.flush();started=true;
     if(!gate.await(30,TimeUnit.SECONDS))return;
     out.write(new byte[124]);
    }catch(Exception ignored){}
   },"Slow-GGUF-Test-Producer");writer.setDaemon(true);writer.start();return pipe[0];
  }catch(java.io.IOException failure){throw new java.io.FileNotFoundException(failure.toString());}
 }
 @Override public String getType(Uri uri){return "application/octet-stream";}
 @Override public Cursor query(Uri u,String[] p,String s,String[] a,String sort){return null;}
 @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
 @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
