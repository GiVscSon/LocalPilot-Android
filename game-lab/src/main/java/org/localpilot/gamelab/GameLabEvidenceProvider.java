package org.localpilot.gamelab;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;
import android.os.SystemClock;
import android.view.View;

/**
 * READ-ONLY game-lab diagnostic view for isolated ADB acceptance.
 *
 * This cannot inject actions, train weights, approve input, load ROMs, or
 * manipulate a game. Only the android shell UID or this app can query it.
 * Camera/compositor screenshot + real OS touch are independently required.
 */
public final class GameLabEvidenceProvider extends ContentProvider {
 public static final String AUTHORITY="org.localpilot.gamelab.evidence";
 public static final String PATH="/state";
 public static final Uri URI=Uri.parse("content://"+AUTHORITY+PATH);
 private static final String[] COLUMNS={
  "source","scene","game","touches","changes","rejected","profile",
  "frames","pixel_hash","canvas_left","canvas_top","canvas_right","canvas_bottom",
  "primary_left","primary_top","primary_right","primary_bottom",
  "secondary_left","secondary_top","secondary_right","secondary_bottom","updated_elapsed_ms"
 };
 private record Bounds(int left,int top,int right,int bottom){
  static Bounds of(View view){
   Rect rect=new Rect();
   if(view==null||view.getVisibility()!=View.VISIBLE
     ||!view.isAttachedToWindow()||!view.getGlobalVisibleRect(rect)
     ||rect.width()<=0||rect.height()<=0)return new Bounds(0,0,0,0);
   return new Bounds(rect.left,rect.top,rect.right,rect.bottom);
  }
 }
 private record Evidence(String scene,String game,int touches,int changes,
       int rejected,int profile,int frames,int pixelHash,
       Bounds canvas,Bounds primary,Bounds secondary,long timestampMs){}
 private static volatile Evidence current;
 private static final Bounds EMPTY=new Bounds(0,0,0,0);
 private static void publish(Evidence next){current=next;}
 /** Called only by real Android UI lifecycle and OS-delivered touch listeners. */
 static void game(GameLabEngine engine,View canvas,View retroButton){
  if(engine==null)return;
  publish(new Evidence("gamelab",engine.game.name(),engine.actions,
    engine.simulatedChanges,engine.rejected,engine.game.localPilotProfile,
    0,0,Bounds.of(canvas),Bounds.of(retroButton),EMPTY,
    SystemClock.elapsedRealtime()));
 }
 static void retro(RetroEmulatorCatalog.System system,View launchButton){
  if(system==null)return;
  publish(new Evidence("retro",system.name(),0,0,0,-1,
   0,0,EMPTY,Bounds.of(launchButton),EMPTY,SystemClock.elapsedRealtime()));
 }
 static void nes(boolean dendy,int frames,int pixelHash,View demo,View a){
  publish(new Evidence("nes",dendy?"DENDY":"NES",0,0,0,-1,
    Math.max(0,frames),pixelHash,EMPTY,Bounds.of(demo),Bounds.of(a),
    SystemClock.elapsedRealtime()));
 }
 static void clear(String scene){
  Evidence sample=current;
  if(sample!=null&&sample.scene.equals(scene))current=null;
 }
 @Override public boolean onCreate(){return true;}
 @Override public String getType(Uri uri){
  return URI.equals(uri)?"vnd.android.cursor.item/vnd.localpilot.gamelab.evidence":null;
 }
 @Override public Cursor query(Uri uri,String[] projection,String selection,
      String[] selectionArgs,String sortOrder){
  int uid=Binder.getCallingUid();
  if(uid!=Process.SHELL_UID&&uid!=Process.myUid())
   throw new SecurityException("Only isolated shell can inspect synthetic lab evidence");
  if(!URI.equals(uri)||selection!=null||selectionArgs!=null||sortOrder!=null
    ||projection!=null)throw new IllegalArgumentException("Read-only state URI");
  MatrixCursor result=new MatrixCursor(COLUMNS,1);
  Evidence e=current;
  if(e==null)return result;
  result.addRow(new Object[]{
   "gamelab-only",e.scene,e.game,e.touches,e.changes,e.rejected,e.profile,
   e.frames,e.pixelHash,e.canvas.left,e.canvas.top,e.canvas.right,e.canvas.bottom,
   e.primary.left,e.primary.top,e.primary.right,e.primary.bottom,
   e.secondary.left,e.secondary.top,e.secondary.right,e.secondary.bottom,e.timestampMs
  });
  return result;
 }
 @Override public Uri insert(Uri uri,ContentValues values){
  throw new UnsupportedOperationException("GameLab evidence is read only");
 }
 @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){
  throw new UnsupportedOperationException("GameLab evidence is read only");
 }
 @Override public int delete(Uri uri,String selection,String[] selectionArgs){
  throw new UnsupportedOperationException("GameLab evidence is read only");
 }
}
