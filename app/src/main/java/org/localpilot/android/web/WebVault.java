package org.localpilot.android.web;
import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

public final class WebVault {
 private static final String ALIAS="localpilot-web-teacher-v1";
 private static SecretKey key()throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
  KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
  g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
  return g.generateKey();
 }
 public static void save(Context context,String token)throws Exception{
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
  byte[] encrypted=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
  String data=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP);
  if(!context.getSharedPreferences("web-teacher-vault",0).edit().putString("secret",data).commit())throw new java.io.IOException("Key storage failed");
 }
 public static String load(Context context)throws Exception{
  String text=context.getSharedPreferences("web-teacher-vault",0).getString("secret",null);if(text==null)return "";
  String[] p=text.split(":",-1);if(p.length!=2)throw new java.io.IOException("Stored key format");
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));
  return new String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
 }
 public static void clear(Context context){context.getSharedPreferences("web-teacher-vault",0).edit().remove("secret").apply();}
}
