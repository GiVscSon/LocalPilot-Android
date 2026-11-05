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
  ok(nes.core.equals("jsnes")&&nes.coreOptionKey().equals("jsnes_compatibility_mode"),"NES executes with bundled JSNES");
  ok(nes.fceummRegion.equals("NTSC")&&dendy.fceummRegion.equals("Dendy"),
    "Dendy timing is a distinct core mode");
  ok(nes.expectedFps==60&&dendy.expectedFps==50,"region clock distinction");
  ok(!dos.core.equals(nes.core)&&dos.coreOptionKey().isEmpty(),"DOSBox has separate core");
  ok(dos.nativeLibraryName().equals("libdosbox_pure_libretro_android.so"),
    "right DOS native core");
  ok(nes.nativeLibraryName().isEmpty()&&nes.runnableInGameLab()&&dendy.runnableInGameLab(),
    "working NES runtime is not a missing native libretro core");
  ok(!dos.runnableInGameLab(),"DOS remains explicitly blocked before native core integration");
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
  ok(!RetroEmulatorCatalog.inspect(dendy,"cart.unif",unif).recognized(),
    "UNIF is not claimed supported by JSNES");
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
  byte[] homebrew=HomebrewNes.cartridge();
  ok(homebrew.length==HomebrewNes.ROM_LENGTH,"full executable NROM cartridge");
  ok(RetroEmulatorCatalog.inspect(nes,"homebrew.nes",homebrew).recognized(),
    "homebrew ROM contains a valid iNES header");
  ok(homebrew[4]==1&&homebrew[5]==1,"one PRG and one CHR bank");
  // Guard the first real 6502 opcodes: STA $00 uses 85 00 (TWO bytes).
  // A third 00 would trigger BRK and crash on actual CPU/PPU execution.
  int[] bootstrap={0x78,0xd8,0xa2,0xff,0x9a,0xa9,0x00,
    0x8d,0x00,0x20,0x8d,0x01,0x20,0xa9,0x0f,
    0x85,0x00,0xa9,0x80,0x85,0x01,0xa9,0x70,0x85,0x02,
    0x2c,0x02,0x20};
  for(int i=0;i<bootstrap.length;i++)ok((homebrew[16+i]&255)==bootstrap[i],
    "decoded 6502 bootstrap byte "+i);
  int vector=16+16384-6;
  ok((homebrew[vector+2]&255)==0&&(homebrew[vector+3]&255)==128,
    "homebrew reset and interrupts enter $8000");
  int spriteTile=16+16384+16;
  ok((homebrew[spriteTile]&255)==255,"actual CHR 8x8 moving sprite exists");
  System.out.println("RETRO_CATALOG_PASS checks="+checks);
 }
}
