package org.localpilot.android;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
public class ExperienceTest {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message+" (check "+checks+")");}
 interface Failure{void run()throws Exception;}static void refuses(Failure f)throws Exception{boolean fail=false;try{f.run();}catch(IOException e){fail=true;}check(fail);}
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("experience-");File file=dir.resolve("data.log").toFile();ExperienceStore store=new ExperienceStore(file);check(ExperienceStore.validate(store.snapshot())==0);
  int[] before=new int[25],after=MiniPolicy.toggle(before,12);store.append("lights",before,12,after);byte[] one=store.snapshot();check(ExperienceStore.validate(one)==1);check(Arrays.equals(one,new ExperienceStore(file).snapshot()));
  store.append("ttt",new int[9],0,new int[]{1,2,0,0,0,0,0,0,0});check(ExperienceStore.validate(store.snapshot())==2);
  int[] b=new int[16];b[0]=2;b[1]=2;int[] a=new Board2048(b).move(0).board.cells.clone();a[1]=2;store.append("2048",b,0,a);byte[] three=store.snapshot();check(ExperienceStore.validate(three)==3);
  refuses(()->store.append("lights",before,12,before));check(Arrays.equals(three,store.snapshot()));
  refuses(()->store.restoreEmpty(one));check(Arrays.equals(three,store.snapshot()));
  ExperienceStore restored=new ExperienceStore(dir.resolve("restore.log").toFile());check(restored.restoreEmpty(three)==3);check(Arrays.equals(three,restored.snapshot()));
  byte[] truncated=Arrays.copyOf(three,three.length-1);refuses(()->ExperienceStore.validate(truncated));
  byte[] changed=three.clone();changed[changed.length-3]^=1;refuses(()->ExperienceStore.validate(changed));
  byte[] blank=(new String(one,StandardCharsets.UTF_8)+"\n").getBytes(StandardCharsets.UTF_8);refuses(()->ExperienceStore.validate(blank));
  File corrupt=dir.resolve("corrupt.log").toFile();Files.write(corrupt.toPath(),truncated);refuses(()->new ExperienceStore(corrupt));check(Arrays.equals(truncated,Files.readAllBytes(corrupt.toPath())));
  ExperienceStore empty=new ExperienceStore(dir.resolve("empty.log").toFile());refuses(()->empty.restoreEmpty(changed));check(ExperienceStore.validate(empty.snapshot())==0);
  refuses(()->ExperienceStore.bounded(new ByteArrayInputStream(new byte[ExperienceStore.MAX+1])));
  for(int i=3;i<ExperienceStore.MAX_ROWS;i++)store.append("lights",before,12,after);check(ExperienceStore.validate(store.snapshot())==256);byte[] full=store.snapshot();refuses(()->store.append("lights",before,12,after));check(Arrays.equals(full,store.snapshot()));
  ExperienceStore stale=new ExperienceStore(dir.resolve("stale.log").toFile());ExperienceStore other=new ExperienceStore(dir.resolve("stale.log").toFile());stale.append("lights",before,12,after);other.append("lights",before,12,after);check(ExperienceStore.validate(stale.snapshot())==2);
  byte[] beforeInvalid=empty.snapshot();refuses(()->empty.append("ttt",new int[]{1,0,0,0,0,0,0,0,0},1,new int[]{1,1,2,0,0,0,0,0,0}));check(Arrays.equals(beforeInvalid,empty.snapshot()));
  refuses(()->empty.restoreEmpty("wrong header\n".getBytes(StandardCharsets.UTF_8)));check(Arrays.equals(beforeInvalid,empty.snapshot()));
  // Grid records are accepted only after a separate visual CHANGED gate
  // (enforced by PilotService), and structurally validated on import.
  ExperienceStore gridStore=new ExperienceStore(dir.resolve("verified-grids.log").toFile());
  for(int side:new int[]{6,7}){
   int total=side*side;int[] gridBefore=new int[total];
   for(int i=0;i<total;i++)gridBefore[i]=100+i;
   gridBefore[0]=1;gridBefore[1]=2;gridBefore[2]=1;gridBefore[side+1]=1;
   int from=1,to=side+1,code=from*total+to;
   int[] gridAfter=gridBefore.clone();gridAfter[0]=90;gridAfter[1]=91;
   String profile="grid"+side+"-swap";
   gridStore.append(profile,gridBefore,code,gridAfter);
   check(ExperienceStore.validate(gridStore.snapshot())==side-5);
   int[] oneFrame=gridBefore.clone();oneFrame[0]=90;
   refuses(()->gridStore.append(profile,gridBefore,code,oneFrame));
   refuses(()->gridStore.append(profile,gridBefore,0,gridAfter));
  }
  check(ExperienceStore.validate(new ExperienceStore(dir.resolve("verified-grids.log").toFile()).snapshot())==2);
  // Real-device failure signature: an entire 6x6 board changes token
  // namespace (before 8..14; after 1..7), yet the old structural verifier
  // could see a legal match. Never use this as post-ACK learning evidence.
  int[] suspectedBefore={8,9,0,10,9,10,11,8,8,12,-1,13,8,9,12,14,9,10,9,14,8,14,13,12,9,-1,4,4,10,9,10,11,11,9,4,12};
  int[] suspectedAfter={4,6,0,-1,2,4,5,1,3,-1,2,5,-1,5,4,5,-1,4,-1,3,1,2,2,7,2,-1,-1,3,5,2,2,6,6,2,4,7};
  check(!ExperienceStore.sharedGridPaletteIdentity(suspectedBefore,suspectedAfter),
    "suspected global palette relabel cannot count as a verified game response");
  int[] stableAfter=suspectedBefore.clone();stableAfter[0]=12;stableAfter[7]=11;stableAfter[14]=8;
  check(ExperienceStore.sharedGridPaletteIdentity(suspectedBefore,stableAfter),
    "ordinary local changes retain enough shared palette IDs");
  check(!ExperienceStore.sharedGridPaletteIdentity(suspectedBefore,null),
    "missing response is never valid evidence");
  // Keep v1 historical files readable: do not retroactively invalidate
  // a user's existing archive or make ExperienceStore fail at startup.
  gridStore.append("grid6-swap",suspectedBefore,6,suspectedAfter);
  check(ExperienceStore.validate(gridStore.snapshot())==3,
    "existing v1 export remains importable while live learning is quarantined");

  System.out.println("PASS "+checks+" experience persistence/import/limits assertions");
 }
}
