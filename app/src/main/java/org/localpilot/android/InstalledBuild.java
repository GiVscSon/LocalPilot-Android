package org.localpilot.android;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Bounded metadata from the packaged APK, never inferred from versionCode alone. */
public final class InstalledBuild {
 private static volatile String revision, fingerprint;
 private InstalledBuild(){}
 private static synchronized void read(Context context){
  if(revision!=null)return;
  revision=fingerprint="unverified";
  try(InputStream in=context.getAssets().open("localpilot-build.json")){
   ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[512];int n;
   while((n=in.read(bytes))!=-1){if(out.size()+n>4096)throw new IOException("provenance size");out.write(bytes,0,n);}
   JSONObject data=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
   String sha=data.getString("git_sha"),source=data.getString("source_sha256");
   if(!"localpilot-build-provenance-v1".equals(data.getString("schema"))
      ||!sha.matches("[a-f0-9]{40}")||!source.matches("[a-f0-9]{64}"))throw new IOException("provenance schema");
   revision=sha;fingerprint=source;
  }catch(Exception ignored){/* Report missing proof, never invent a revision. */}
 }
 public static String revision(Context context){read(context);return revision;}
 public static String fingerprint(Context context){read(context);return fingerprint;}
 public static String shortRevision(Context context){String sha=revision(context);return sha.length()==40?sha.substring(0,12):sha;}
}
