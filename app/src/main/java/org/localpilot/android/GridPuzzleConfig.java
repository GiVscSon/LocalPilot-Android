package org.localpilot.android;

/** Persisted grid geometry, relative to one app/game/mode and window aspect. */
public final class GridPuzzleConfig {
 public final int rows,cols,minGroup;public final float left,top,right,bottom,aspect;public final boolean hexOffsetRows;
 public GridPuzzleConfig(int rows,int cols,int minGroup){this(rows,cols,minGroup,false);}
 public GridPuzzleConfig(int rows,int cols,int minGroup,boolean hexOffsetRows){this(rows,cols,minGroup,0,0,0,0,0,hexOffsetRows);}
 private GridPuzzleConfig(int rows,int cols,int minGroup,float left,float top,float right,float bottom,float aspect,boolean hexOffsetRows){
  if(rows<3||rows>10||cols<3||cols>10||minGroup<2||minGroup>10)throw new IllegalArgumentException("grid settings");
  for(float v:new float[]{left,top,right,bottom,aspect})if(!Float.isFinite(v))throw new IllegalArgumentException("grid geometry");
  if(aspect!=0&&(aspect<=0||left<0||top<0||right>1||bottom>1||right<=left||bottom<=top))throw new IllegalArgumentException("grid region");
  this.rows=rows;this.cols=cols;this.minGroup=minGroup;this.left=left;this.top=top;this.right=right;this.bottom=bottom;this.aspect=aspect;this.hexOffsetRows=hexOffsetRows;
 }
 public GridPuzzleConfig region(int x,int y,int width,int height,int windowWidth,int windowHeight){
  if(windowWidth<=0||windowHeight<=0||width<1||height<1)throw new IllegalArgumentException("window");
  return new GridPuzzleConfig(rows,cols,minGroup,x/(float)windowWidth,y/(float)windowHeight,(x+width)/(float)windowWidth,(y+height)/(float)windowHeight,windowWidth/(float)windowHeight,hexOffsetRows);
 }
 public boolean fits(int width,int height){return width>0&&height>0&&aspect>0&&Math.abs(width/(float)height/aspect-1)<.05f;}
 public String encode(){String base=(hexOffsetRows?"v2,":"v1,")+rows+","+cols+","+minGroup+","+left+","+top+","+right+","+bottom+","+aspect;return hexOffsetRows?base+",1":base;}
 public static GridPuzzleConfig decode(String text){try{String[] p=text.split(",",-1);boolean hex=p.length==10&&p[0].equals("v2")&&p[9].equals("1");if(!hex&&(p.length!=9||!p[0].equals("v1")))throw new IllegalArgumentException();return new GridPuzzleConfig(Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]),Float.parseFloat(p[4]),Float.parseFloat(p[5]),Float.parseFloat(p[6]),Float.parseFloat(p[7]),Float.parseFloat(p[8]),hex);}catch(RuntimeException e){throw new IllegalArgumentException("invalid grid settings",e);}}
 /** Horizontal cell position relative to the selected ROI, preserving the ordinary rectangular default. */
 public float cellCenterX(int row,int col,float roiLeft,float cellWidth){if(row<0||row>=rows||col<0||col>=cols||!Float.isFinite(roiLeft)||!Float.isFinite(cellWidth)||cellWidth<=0)throw new IllegalArgumentException("grid sample");return roiLeft+(col+.5f+(hexOffsetRows&&(row&1)==1?.5f:0f))*cellWidth;}

 public static String key(String pkg,String slot,GridPuzzlePolicy.Mode mode){if(pkg==null||pkg.isBlank()||slot==null||slot.isBlank()||mode==null)throw new IllegalArgumentException("grid scope");return pkg+"#"+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(slot.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"#grid-"+mode.name();}
}
