package org.localpilot.android;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
public class ExperienceTest {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
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
  System.out.println("PASS "+checks+" experience persistence/import/limits assertions");
 }
}
