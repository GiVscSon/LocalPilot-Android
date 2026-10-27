package org.localpilot.android;

import org.localpilot.android.web.WebLesson;

/**
 * Opt-in, bounded transfer of manually/metric-rated Web 2048 demonstrations
 * to the native/external 2048 move RANKER. Never returns a gesture or changes
 * legality, binding, ROI or post-ACK verification.
 *
 * Only directions are transferable between separately rendered 2048 games:
 * raw WebView pixels, touch positions and sample identities never cross over.
 */
public final class Web2048Transfer {
 private static final int DIRECTIONS=4,MAX_SAMPLES=1000;
 private final String sourceGame;
 private final int[] positive=new int[DIRECTIONS],negative=new int[DIRECTIONS];
 private Web2048Transfer(String source,int[] counts){
  if(source==null||!source.matches("[0-9a-f]{64}")||counts==null||counts.length!=8)throw new IllegalArgumentException("source");
  sourceGame=source;
  int total=0;
  for(int i=0;i<DIRECTIONS;i++){
   if(counts[i]<0||counts[i+4]<0)throw new IllegalArgumentException("negative count");
   positive[i]=counts[i];negative[i]=counts[i+4];
   total+=counts[i]+counts[i+4];
  }
  if(total<10||total>MAX_SAMPLES||positives()<5)throw new IllegalArgumentException("not enough rated 2048 actions");
 }
 public static Web2048Transfer fromLesson(WebLesson lesson){
  if(lesson==null||!lesson.identified()||!"2048".equalsIgnoreCase(lesson.profileName))
   throw new IllegalArgumentException("only identified 2048 lessons are transferable");
  // No inference from screenshots or ungraded demonstrations.
  return new Web2048Transfer(lesson.game,lesson.graded2048Directions());
 }
 private int positives(){int n=0;for(int x:positive)n+=x;return n;}
 public int evidence(){int n=0;for(int i=0;i<DIRECTIONS;i++)n+=positive[i]+negative[i];return n;}
 public String sourceGame(){return sourceGame;}
 /** Tiny preference only among actions which the ordinary 2048 policy permits. */
 public double[] bonus(){
  double[] out=new double[DIRECTIONS];
  for(int i=0;i<DIRECTIONS;i++){
   int n=positive[i]+negative[i];
   if(n<3)continue;
   double confidence=(positive[i]+2.0)/(n+4.0);
   out[i]=Math.max(-.4,Math.min(.4,(confidence-.5)*.8*Math.min(1.0,n/6.0)));
  }
  return out;
 }
 /** No raw features or touch locations are persisted in the transfer. */
 public String encode(){
  StringBuilder b=new StringBuilder("v1;").append(sourceGame);
  for(int i=0;i<DIRECTIONS;i++)b.append(';').append(positive[i]).append(',').append(negative[i]);
  return b.toString();
 }
 public static Web2048Transfer decode(String saved){
  if(saved==null||saved.length()>180)throw new IllegalArgumentException("transfer encoding");
  String[] parts=saved.split(";",-1);
  if(parts.length!=6||!"v1".equals(parts[0]))throw new IllegalArgumentException("transfer version");
  int[] counts=new int[8];
  try{
   for(int i=0;i<DIRECTIONS;i++){
    String[] p=parts[i+2].split(",",-1);
    if(p.length!=2)throw new IllegalArgumentException("transfer pair");
    counts[i]=Integer.parseInt(p[0]);counts[i+4]=Integer.parseInt(p[1]);
   }
  }catch(NumberFormatException e){throw new IllegalArgumentException("transfer counts",e);}
  return new Web2048Transfer(parts[1],counts);
 }
}
