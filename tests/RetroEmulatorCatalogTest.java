package org.localpilot.gamelab;

import java.io.ByteArrayInputStream;

/** Tests machine identities, cartridge headers and zero fake-emulation claims. */
public final class RetroEmulatorCatalogTest {
 private static int checks;
 private static void ok(boolean v,String note){checks++;if(!v)throw new AssertionError(note);}
 public static void main(String[] args)throws Exception{
  var nes=RetroEmulatorCatalog.System.NES;
  var dendy=RetroEmulatorCatalog.System.DENDY;
  var dos=RetroEmulatorCatalog.System.DOS;
  ok(nes.core.equals(dendy.core),"one hardware core for NES and Dendy");
  ok(nes.coreOptionKey().equals("fceumm_region"),"region option key matches FCEUmm");
  ok(nes.fceummRegion.equals("NTSC")&&dendy.fceummRegion.equals("Dendy"),
    "Dendy timing is a distinct core mode");
  ok(nes.expectedFps==60&&dendy.expectedFps==50,"region clock distinction");
  ok(!dos.core.equals(nes.core)&&dos.coreOptionKey().isEmpty(),"DOSBox has separate core");
  ok(dos.nativeLibraryName().equals("libdosbox_pure_libretro_android.so"),
    "right DOS native core");
  ok(nes.nativeLibraryName().equals("libfceumm_libretro_android.so"),
    "right NES native core");
  byte[] cartridge=new byte[16];
  cartridge[0]='N';cartridge[1]='E';cartridge[2]='S';cartridge[3]=26;
  ok(RetroEmulatorCatalog.inspect(nes,"game.nes",cartridge).recognized(),
    "real iNES magic");
  ok(RetroEmulatorCatalog.inspect(dendy,"game.NES",cartridge).recognized(),
    "Dendy and NES use same iNES cartridge");
  ok(!RetroEmulatorCatalog.inspect(dos,"game.nes",cartridge).recognized(),
    "NES games are not DOS executables");
  ok(!RetroEmulatorCatalog.inspect(nes,"game.nes",new byte[16]).recognized(),
    "fake NES image rejected");
  byte[] unif={'U','N','I','F',0,0,0,0};
  ok(RetroEmulatorCatalog.inspect(dendy,"cart.unif",unif).recognized(),
    "UNIF cartridge supported");
  byte[] zip={'P','K',3,4,0,0,0,0};
  ok(RetroEmulatorCatalog.inspect(dos,"doom.zip",zip).recognized(),
    "DOS software archive recognized");
  ok(!RetroEmulatorCatalog.inspect(dos,"doom.zip",new byte[16]).recognized(),
    "invalid DOS archive rejected");
  ok(RetroEmulatorCatalog.inspect(dos,"GAME.EXE",new byte[]{'M','Z'}).recognized(),
    "DOS EXE magic");
  ok(!RetroEmulatorCatalog.inspect(dos,"GAME.EXE",new byte[]{'Z','M'}).recognized(),
    "incorrect EXE magic rejected");
  ok(RetroEmulatorCatalog.inspect(dos,"GAME.COM",new byte[]{1,2,3,4}).recognized(),
    "DOS COM binary admitted for actual core validation");
  ok(RetroEmulatorCatalog.inspect(nes,"cart.nes",
      new ByteArrayInputStream(cartridge)).recognized(),"stream ROM preflight");
  ok(!RetroEmulatorCatalog.accepts(nes,"malware.exe"),"wrong extension rejected");
  try{RetroEmulatorCatalog.accepts(nes,"../cart.nes");throw new AssertionError("path traversal");}
  catch(IllegalArgumentException expected){checks++;}
  ok(RetroEmulatorCatalog.System.values().length==3,"exactly requested machines");
  System.out.println("RETRO_CATALOG_PASS checks="+checks);
 }
}
