package org.localpilot.android;

/** Three sampled object colours and normalized anchors inside the game ROI. */
public final class CustomVisionConfig {
 public final int[] colors;public final float[] x,y;
 public CustomVisionConfig(int[] colors,float[] x,float[] y){
  if(colors==null||x==null||y==null||colors.length!=3||x.length!=3||y.length!=3)throw new IllegalArgumentException("three object samples required");
  this.colors=colors.clone();this.x=x.clone();this.y=y.clone();
  for(int i=0;i<3;i++)if(colors[i]<0||colors[i]>0xffffff||!Float.isFinite(x[i])||!Float.isFinite(y[i])||x[i]<0||x[i]>1||y[i]<0||y[i]>1)throw new IllegalArgumentException("sample outside game area");
 }
 public String encode(){StringBuilder b=new StringBuilder("v1");for(int i=0;i<3;i++)b.append(',').append(colors[i]).append(',').append(x[i]).append(',').append(y[i]);return b.toString();}
 public static CustomVisionConfig decode(String value){try{String[] p=value.split(",",-1);if(p.length!=10||!p[0].equals("v1"))throw new IllegalArgumentException();int[] c=new int[3];float[] x=new float[3],y=new float[3];for(int i=0;i<3;i++){c[i]=Integer.parseInt(p[1+i*3]);x[i]=Float.parseFloat(p[2+i*3]);y[i]=Float.parseFloat(p[3+i*3]);}return new CustomVisionConfig(c,x,y);}catch(RuntimeException e){throw new IllegalArgumentException("invalid visual configuration",e);}}
}
