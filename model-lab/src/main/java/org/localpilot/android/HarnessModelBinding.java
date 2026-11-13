package org.localpilot.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;

/** One persisted model assignment shared by the Harness panel and ModelLab.
 * An assignment is not evidence of inference, GPU offload or model fitness.
 */
public final class HarnessModelBinding {
 private static final String PREF="localpilot-harness-binding-v1";
 private static final String KEY_ID="modelId", KEY_SHA="modelSha";
 public record Assignment(String id,String sha){}
 private HarnessModelBinding(){}
 public static Assignment read(Context context){
  SharedPreferences prefs=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
  String id=prefs.getString(KEY_ID,""),sha=prefs.getString(KEY_SHA,"");
  if(id==null||sha==null||!id.matches("[A-Za-z0-9:_.-]{1,96}")||
      !sha.matches("[a-f0-9]{64}"))return null;
  return new Assignment(id,sha);
 }
 public static ModelLabStore.Entry resolve(Context context,List<ModelLabStore.Entry> entries){
  Assignment assignment=read(context);
  if(assignment==null||entries==null)return null;
  for(ModelLabStore.Entry e:entries)
   if(e.id.equals(assignment.id)&&e.modelSha.equals(assignment.sha))return e;
  return null; // changed/removed weights must never silently receive an old binding
 }
 public static void assign(Context context,ModelLabStore.Entry e){
  if(e==null||e.id==null||!e.id.matches("[A-Za-z0-9:_.-]{1,96}")||
      e.modelSha==null||!e.modelSha.matches("[a-f0-9]{64}"))
   throw new IllegalArgumentException("Unverified model identity");
  boolean ok=context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
    .putString(KEY_ID,e.id).putString(KEY_SHA,e.modelSha).commit();
  if(!ok)throw new IllegalStateException("Cannot commit NES model assignment");
 }
 /** Bind only the already checksum-validated student for a user-approved trial. */
 public static void assignStudent(Context context,String id,String sha){
  if(id==null||!id.matches("student:[0-9a-f]{16}")||
     sha==null||!sha.matches("[a-f0-9]{64}")||
     !id.equals("student:"+sha.substring(0,16)))
   throw new IllegalArgumentException("Student identity must match its real weights");
  if(!context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
       .putString(KEY_ID,id).putString(KEY_SHA,sha).commit())
   throw new IllegalStateException("Cannot save student sandbox binding");
 }
 public static void clear(Context context){
  if(!context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
      .remove(KEY_ID).remove(KEY_SHA).commit())
   throw new IllegalStateException("Cannot revoke NES model assignment");
 }
}
