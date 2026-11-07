package org.localpilot.android;

/** Explicit screen geometry; odd rows have one fewer slot and a half-cell X offset. */
public final class HexaConfig {
 public final int rows,cols,left,top,right,bottom;
 public final long activeMask;
 public HexaConfig(int rows,int cols,int left,int top,int right,int bottom){this(rows,cols,left,top,right,bottom,slots(rows,cols));}
 public HexaConfig(int rows,int cols,int left,int top,int right,int bottom,long activeMask){
  if(rows<2||rows>8||cols<2||cols>8||left<0||top<0||right<=left||bottom<=top||right-left<cols*12||bottom-top<rows*12)
   throw new IllegalArgumentException("hexa geometry");
  long slots=slots(rows,cols);
  if(activeMask==0||(activeMask&~slots)!=0)throw new IllegalArgumentException("hexa active slots");
  this.rows=rows;this.cols=cols;this.left=left;this.top=top;this.right=right;this.bottom=bottom;this.activeMask=activeMask;
 }
 private static long slots(int rows,int cols){
  if(rows<2||rows>8||cols<2||cols>8)throw new IllegalArgumentException("hexa dimensions");
  long mask=0;for(int r=0;r<rows;r++)for(int c=0;c<cols-(r&1);c++)mask|=1L<<(r*cols+c);return mask;
 }
 public int size(){return rows*cols;}
 public boolean active(int index){return index>=0&&index<size()&&(activeMask&(1L<<index))!=0;}
 public float cellWidth(){return (right-left)/(float)cols;}
 public float cellHeight(){return (bottom-top)/(float)rows;}
 public float cellCenterX(int row,int col){checkCell(row,col);return left+(col+.5f+(row&1)*.5f)*cellWidth();}
 public float cellCenterY(int row,int col){checkCell(row,col);return top+(row+.5f)*cellHeight();}
 public float slotX(int slot){checkHand(slot);return left+(.2f+.3f*slot)*(right-left);}
 public float slotY(int slot){checkHand(slot);return bottom+.12f*(bottom-top);}
 public boolean neighbors(int a,int b){
  if(!active(a)||!active(b)||a==b)return false;
  int ar=a/cols,br=b/cols,ac=a%cols,bc=b%cols;
  if(ar==br)return Math.abs(ac-bc)==1;
  return Math.abs(ar-br)==1&&Math.abs(2*ac+(ar&1)-(2*bc+(br&1)))==1;
 }
 public long signature(){long h=activeMask;h=mix(h,rows);h=mix(h,cols);h=mix(h,left);h=mix(h,top);h=mix(h,right);return mix(h,bottom);}
 static long mix(long hash,int value){return (hash^(value&0xffffffffL))*0x100000001b3L;}
 private void checkCell(int row,int col){if(row<0||row>=rows||col<0||col>=cols)throw new IllegalArgumentException("hexa cell");}
 private static void checkHand(int slot){if(slot<0||slot>=3)throw new IllegalArgumentException("hexa hand");}
 public String encode(){return "hexa-v1,"+rows+","+cols+","+left+","+top+","+right+","+bottom+","+Long.toUnsignedString(activeMask);}
 public static HexaConfig decode(String value){
  try{String[] p=value.split(",",-1);if(p.length!=8||!p[0].equals("hexa-v1"))throw new IllegalArgumentException();
   return new HexaConfig(Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]),Integer.parseInt(p[4]),Integer.parseInt(p[5]),Integer.parseInt(p[6]),Long.parseUnsignedLong(p[7]));
  }catch(RuntimeException error){throw new IllegalArgumentException("invalid hexa geometry",error);}
 }
}
