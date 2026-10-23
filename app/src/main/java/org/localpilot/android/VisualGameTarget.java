package org.localpilot.android;

/** App identity rules; recognition of a compatible scene is checked separately. */
public final class VisualGameTarget {
 private VisualGameTarget(){}
 public static boolean allowed(String pkg,String own){
  if(pkg==null||pkg.equals(own)||!pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))return false;
  String p=pkg.toLowerCase(java.util.Locale.ROOT);
  return !p.endsWith(".settings")&&!p.endsWith(".systemui")&&!p.contains("permissioncontroller")
    &&!p.contains("packageinstaller")&&!p.contains(".launcher")&&!p.equals("com.google.android.apps.nexuslauncher")
    &&!p.equals("com.mi.android.globallauncher")&&!p.equals("com.miui.home");
 }
 public static boolean legacyMarket(String pkg){return "ru.beru.android".equals(pkg)||"ru.yandex.market".equals(pkg)||"com.yandex.market".equals(pkg);}
 /** Package + named slot + profile. A browser can host several independent games. */
 public static String key(String pkg,String slot,ArcadeProfile profile){
  if(pkg==null||!pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")||slot==null||slot.length()>80||slot.trim().isEmpty()||profile==null)throw new IllegalArgumentException("game identity");
  String encoded=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(slot.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  return pkg+"#"+encoded+"#"+profile.name();
 }
 public static String modelSlot(String slot,int controls,String vision){
  if(controls==0&&vision==null)return slot;
  try{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest((slot+"|"+controls+"|"+vision).getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder key=new StringBuilder("model-");for(byte b:bytes)key.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return key.toString();}
  catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
}
