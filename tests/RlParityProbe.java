package org.localpilot.android.rl;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Portable float32 trace of the EXACT simulator sources used by Android.
 *
 * The fixed seeds and actions are input vectors, not a reference reward model.
 * This probe never issues an Android gesture and never imports Android APIs.
 * The Python/JPype parity gate independently assembles the same wire bytes.
 */
public final class RlParityProbe {
 public static final int STEPS=64;
 public static final long SEED=20261028L;

 private RlParityProbe(){}

 public static byte[] trace(ArcadeEnv.Genre genre)throws IOException {
  if(genre==null)throw new IllegalArgumentException("genre");
  ArcadeEnv env=new ArcadeEnv(genre);
  env.reset(SEED+genre.ordinal());
  ByteArrayOutputStream raw=new ByteArrayOutputStream();
  try(DataOutputStream out=new DataOutputStream(raw)){
   for(int i=0;i<STEPS;i++){
    if(env.terminated||env.truncated)env.reset(SEED+1000L+i+genre.ordinal()*100L);
    int action=(i*7+3)%env.actions().length;
    for(float v:env.observe())out.writeInt(Float.floatToRawIntBits(v));
    ArcadeEnv.Step step=env.step(action);
    out.writeInt(action);
    out.writeInt(Float.floatToRawIntBits(step.reward));
    out.writeInt(Float.floatToRawIntBits(step.progress));
    out.writeBoolean(step.terminated);
    out.writeBoolean(step.truncated);
    out.writeInt(step.durationFrames);
    for(float v:step.observation)out.writeInt(Float.floatToRawIntBits(v));
   }
  }
  return raw.toByteArray();
 }

 public static String sha256(byte[] bytes) {
  try {
   byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
   StringBuilder result=new StringBuilder(64);
   for(byte b:digest)result.append(String.format(Locale.ROOT,"%02x",b&255));
   return result.toString();
  }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }

 public static void main(String[] args)throws Exception {
  if(args.length!=0)throw new IllegalArgumentException("no arguments expected");
  for(ArcadeEnv.Genre genre:ArcadeEnv.Genre.values())
   System.out.println(genre.name()+" "+sha256(trace(genre)));
 }
}
