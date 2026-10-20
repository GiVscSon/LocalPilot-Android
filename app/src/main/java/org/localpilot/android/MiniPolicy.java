package org.localpilot.android;
public final class MiniPolicy {
 public static final int TTT=0,LIGHTS=1;
 public static int size(int kind){if(kind<0||kind>1)throw new IllegalArgumentException();return kind==TTT?3:5;}
 public static int winner(int[] b){int[][] lines={{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};for(int[] l:lines)if(b[l[0]]!=0&&b[l[0]]==b[l[1]]&&b[l[1]]==b[l[2]])return b[l[0]];for(int v:b)if(v==0)return 0;return 3;}
 public static int[] toggle(int[] b,int cell){int[] a=b.clone();int row=cell/5,col=cell%5;for(int i=0;i<25;i++)if(Math.abs(i/5-row)+Math.abs(i%5-col)<=1)a[i]^=1;return a;}
 private static int eval(int[] b,int player,java.util.Map<Integer,Integer> cache){int key=player;for(int v:b)key=key*3+v;Integer saved=cache.get(key);if(saved!=null)return saved;int w=winner(b);if(w!=0)return w==1?10:w==2?-10:0;int best=player==1?-100:100;for(int i=0;i<9;i++)if(b[i]==0){b[i]=player;int v=eval(b,3-player,cache);b[i]=0;best=player==1?Math.max(best,v):Math.min(best,v);}cache.put(key,best);return best;}
 public static int choose(int kind,int[] b){validate(kind,b);if(kind==TTT){if(winner(b)!=0)return -1;int x=0,o=0;for(int v:b){if(v==1)x++;if(v==2)o++;}if(x!=o)return -2;java.util.Map<Integer,Integer> cache=new java.util.HashMap<>();int best=-100,cell=-1;for(int i=0;i<9;i++)if(b[i]==0){int[] a=b.clone();a[i]=1;int value=eval(a,2,cache);if(value>best){best=value;cell=i;}}return cell;}
  int[] rows=new int[25];for(int i=0;i<25;i++){int[] e=new int[25];e[i]=1;int[] effects=toggle(new int[25],i);for(int j=0;j<25;j++)if(effects[j]!=0)rows[j]|=1<<i;}for(int j=0;j<25;j++)rows[j]|=b[j]<<25;
  int rank=0;int[] pivot=new int[25];for(int c=0;c<25;c++){int found=rank;while(found<25&&(rows[found]&(1<<c))==0)found++;if(found==25)continue;int temp=rows[rank];rows[rank]=rows[found];rows[found]=temp;for(int r=0;r<25;r++)if(r!=rank&&(rows[r]&(1<<c))!=0)rows[r]^=rows[rank];pivot[rank++]=c;}
  for(int r=rank;r<25;r++)if((rows[r]&((1<<25)-1))==0&&(rows[r]&(1<<25))!=0)return -2;int solution=0;for(int r=0;r<rank;r++)if((rows[r]&(1<<25))!=0)solution|=1<<pivot[r];return solution==0?-1:Integer.numberOfTrailingZeros(solution);
 }
 public static void validate(int kind,int[] b){int n=size(kind);if(b==null||b.length!=n*n)throw new IllegalArgumentException("Размер поля");for(int v:b)if(v<0||v>(kind==TTT?2:1))throw new IllegalArgumentException("Клетка");}
 public static boolean verifies(int kind,int[] before,int cell,int[] after){validate(kind,before);validate(kind,after);if(cell<0||cell>=before.length)return false;if(kind==LIGHTS)return java.util.Arrays.equals(toggle(before,cell),after);if(before[cell]!=0||after[cell]!=1)return false;int addedO=0;for(int i=0;i<9;i++){if(i==cell)continue;if(before[i]==after[i])continue;if(before[i]!=0||after[i]!=2)return false;addedO++;}int[] own=before.clone();own[cell]=1;return addedO==(winner(own)==0?1:0);}
}
