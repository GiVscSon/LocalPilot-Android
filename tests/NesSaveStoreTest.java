package org.localpilot.shared;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class NesSaveStoreTest {
 static int checks;
 static void yes(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
 interface Work {void run()throws Exception;}
 static void rejects(Work work)throws Exception{checks++;try{work.run();}catch(java.io.IOException expected){return;}throw new AssertionError("Invalid save accepted");}
 public static void main(String[] args)throws Exception{
  Path vault=Files.createTempDirectory("nes-save-contract");
  byte[] rom=new byte[16400];rom[0]=78;rom[1]=69;rom[2]=83;rom[3]=26;
  String state="{\"schema\":\"localpilot-jsnes-save-v1\",\"machine\":{}}";
  NesSaveStore.Save first=new NesSaveStore.Save(rom,state,45,123,false,32,"a".repeat(64));
  yes(NesSaveStore.load(vault)==null,"No invented initial save");
  NesSaveStore.save(vault,first);
  NesSaveStore.Save loaded=NesSaveStore.load(vault);
  yes(loaded.frame()==45&&loaded.verifiedActions()==32&&loaded.policySha256().equals("a".repeat(64)),"Metadata survives process boundary");
  yes(Arrays.equals(rom,loaded.rom())&&state.equals(loaded.coreState()),"Exact ROM and CPU/PPU JSON retained");
  byte[] copy=loaded.rom();copy[0]=0;yes(loaded.rom()[0]==78,"ROM accessor cannot mutate saved identity");
  NesSaveStore.save(vault,new NesSaveStore.Save(rom,state,301,-123,true,48,"b".repeat(64)));
  yes(NesSaveStore.load(vault).frame()==301&&NesSaveStore.load(vault).dendy(),"Atomic checkpoint replaces earlier progress");
  Path file=vault.resolve("console/nes/latest.nss");byte[] good=Files.readAllBytes(file);
  byte[] bad=good.clone();bad[100]^=1;Files.write(file,bad);
  rejects(()->NesSaveStore.load(vault));rejects(()->NesSaveStore.save(vault,first));
  yes(Arrays.equals(bad,Files.readAllBytes(file)),"Corruption preserved instead of silently overwritten");
  Files.write(file,Arrays.copyOf(good,99));rejects(()->NesSaveStore.load(vault));
  Files.write(file,good);
  rejects(()->NesSaveStore.save(vault,new NesSaveStore.Save(rom,state,14,123,false,0,"")));
  rejects(()->NesSaveStore.save(vault,new NesSaveStore.Save(rom,state,45,0,false,0,"")));
  rejects(()->NesSaveStore.save(vault,new NesSaveStore.Save(rom,state,45,123,false,-1,"")));
  rejects(()->NesSaveStore.save(vault,new NesSaveStore.Save(rom,state,45,123,false,0,"foreign")));
  rejects(()->NesSaveStore.save(vault,new NesSaveStore.Save(rom,"{"+"x".repeat(4*1024*1024)+"}",45,123,false,0,"")));
  byte[] foreign=rom.clone();foreign[0]=0;rejects(()->NesSaveStore.save(vault,new NesSaveStore.Save(foreign,state,45,123,false,0,"")));
  Files.delete(file);Files.createSymbolicLink(file,Files.createTempFile("foreign-save",".bin"));rejects(()->NesSaveStore.load(vault));rejects(()->NesSaveStore.save(vault,first));
  Path sparse=Files.createTempDirectory("nes-save-large");NesSaveStore.save(sparse,first);
  try(var f=new java.io.RandomAccessFile(sparse.resolve("console/nes/latest.nss").toFile(),"rw")){f.setLength(NesSaveStore.MAX_FILE+1);}
  rejects(()->NesSaveStore.load(sparse));
  System.out.println("NES_SAVE_STORE_PASS checks="+checks+" atomic=true corruption_preserved=true bounded=true input_permits_persisted=false");
 }
}
