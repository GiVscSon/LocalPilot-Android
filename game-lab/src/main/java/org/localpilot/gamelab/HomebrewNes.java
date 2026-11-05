package org.localpilot.gamelab;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Original public-domain homebrew NROM cartridge generated in memory.
 * Contains ACTUAL 6502 executable code, PPU configuration, palette, CHR
 * sprite tile and controller poll. No commercial game data or BIOS.
 *
 * A/B change backdrop color. D-pad moves the actual NES sprite.
 */
public final class HomebrewNes {
 private HomebrewNes(){}
 public static final int ROM_LENGTH=16+16*1024+8*1024;
 private static final class Patch {
  final String name;final int position;final boolean relative;
  Patch(String n,int p,boolean r){name=n;position=p;relative=r;}
 }
 private static final class Asm {
  final ByteArrayOutputStream code=new ByteArrayOutputStream();
  final Map<String,Integer> labels=new HashMap<>();
  final ArrayList<Patch> patches=new ArrayList<>();
  void op(int... instruction){for(int v:instruction)code.write(v&255);}
  void label(String name){
   if(labels.putIfAbsent(name,code.size())!=null)throw new AssertionError("duplicate "+name);
  }
  void abs(int opcode,int address){op(opcode,address&255,(address>>>8)&255);}
  void imm(int opcode,int value){op(opcode,value);}
  void branch(int opcode,String name){
   op(opcode,0);patches.add(new Patch(name,code.size()-1,true));
  }
  void jump(String name){op(0x4c,0,0);patches.add(new Patch(name,code.size()-2,false));}
  byte[] code(){
   byte[] bytes=code.toByteArray();
   for(Patch p:patches){
    Integer target=labels.get(p.name);
    if(target==null)throw new AssertionError("missing label "+p.name);
    if(p.relative){
     int offset=target-(p.position+1);
     if(offset<-128||offset>127)throw new AssertionError("branch too far "+p.name);
     bytes[p.position]=(byte)offset;
    }else{
     int address=0x8000+target;
     bytes[p.position]=(byte)(address&255);
     bytes[p.position+1]=(byte)(address>>>8);
    }
   }
   return bytes;
  }
 }
 public static byte[] cartridge(){
  Asm a=new Asm();
  // CPU, PPU and controller initialization at $8000.
  a.op(0x78,0xd8);       // SEI; CLD
  a.imm(0xa2,0xff);a.op(0x9a); // LDX #$FF; TXS
  a.imm(0xa9,0);a.abs(0x8d,0x2000);a.abs(0x8d,0x2001);
  a.imm(0xa9,0x0f);a.op(0x85,0); // backdrop color
  a.imm(0xa9,0x80);a.op(0x85,1); // sprite X
  a.imm(0xa9,0x70);a.op(0x85,2); // sprite Y
  a.label("vblank1");a.abs(0x2c,0x2002);a.branch(0x10,"vblank1");
  a.label("vblank2");a.abs(0x2c,0x2002);a.branch(0x10,"vblank2");
  a.imm(0xa9,0x3f);a.abs(0x8d,0x2006);
  a.imm(0xa9,0);a.abs(0x8d,0x2006);
  a.imm(0xa9,0x0f);a.abs(0x8d,0x2007); // universal backdrop
  a.imm(0xa9,0x3f);a.abs(0x8d,0x2006);
  a.imm(0xa9,0x11);a.abs(0x8d,0x2006);
  a.imm(0xa9,0x30);a.abs(0x8d,0x2007); // white sprite foreground
  a.imm(0xa9,0);a.abs(0x8d,0x2003);
  a.imm(0xa2,0);a.imm(0xa9,0xff);
  a.label("hideSprite");a.abs(0x8d,0x2004);a.op(0xca);a.branch(0xd0,"hideSprite");
  a.imm(0xa9,0x1e);a.abs(0x8d,0x2001);
  a.label("frame");
  a.abs(0x2c,0x2002);a.branch(0x10,"frame");
  // Strobe joypad 1. Read A/B/Select/Start/Up/Down/Left/Right serially.
  a.imm(0xa9,1);a.abs(0x8d,0x4016);
  a.imm(0xa9,0);a.abs(0x8d,0x4016);
  a.abs(0xad,0x4016);a.imm(0x29,1);a.branch(0xf0,"noA");
  a.imm(0xa9,0x16);a.op(0x85,0); // A = red backdrop
  a.label("noA");
  a.abs(0xad,0x4016);a.imm(0x29,1);a.branch(0xf0,"noB");
  a.imm(0xa9,0x12);a.op(0x85,0); // B = blue backdrop
  a.label("noB");
  a.abs(0xad,0x4016); // Select
  a.abs(0xad,0x4016); // Start
  a.abs(0xad,0x4016);a.imm(0x29,1);a.branch(0xf0,"noUp");
  a.op(0xc6,2);a.label("noUp"); // DEC $02
  a.abs(0xad,0x4016);a.imm(0x29,1);a.branch(0xf0,"noDown");
  a.op(0xe6,2);a.label("noDown");
  a.abs(0xad,0x4016);a.imm(0x29,1);a.branch(0xf0,"noLeft");
  a.op(0xc6,1);a.label("noLeft");
  a.abs(0xad,0x4016);a.imm(0x29,1);a.branch(0xf0,"noRight");
  a.op(0xe6,1);a.label("noRight");
  // Write palette and first OAM sprite during vblank.
  a.imm(0xa9,0);a.abs(0x8d,0x2001);
  a.abs(0x2c,0x2002);
  a.imm(0xa9,0x3f);a.abs(0x8d,0x2006);
  a.imm(0xa9,0);a.abs(0x8d,0x2006);
  a.op(0xa5,0);a.abs(0x8d,0x2007);
  a.imm(0xa9,0);a.abs(0x8d,0x2003);
  a.op(0xa5,2);a.abs(0x8d,0x2004);
  a.imm(0xa9,1);a.abs(0x8d,0x2004);
  a.imm(0xa9,0);a.abs(0x8d,0x2004);
  a.op(0xa5,1);a.abs(0x8d,0x2004);
  a.imm(0xa9,0x1e);a.abs(0x8d,0x2001);
  a.jump("frame");
  a.label("interrupt");
  int interruptAddress=0x8000+a.code.size();
  a.op(0x40); // RTI: preserve game state on NMI/IRQ, never reset mid-game.
  byte[] code=a.code();
  if(code.length>=16*1024-6)throw new AssertionError("PRG overflow");
  byte[] cartridge=new byte[ROM_LENGTH];
  cartridge[0]='N';cartridge[1]='E';cartridge[2]='S';cartridge[3]=26;
  cartridge[4]=1;cartridge[5]=1; // 16KiB PRG, 8KiB CHR
  System.arraycopy(code,0,cartridge,16,code.length);
  int vector=16+16*1024-6;
  int[] vectors={interruptAddress,0x8000,interruptAddress};
  for(int i=0;i<3;i++){
   int address=vectors[i];
   cartridge[vector+2*i]=(byte)(address&255);
   cartridge[vector+2*i+1]=(byte)(address>>>8);
  }
  // Two-bitplanes of CHR tile 1. 8x8 solid foreground.
  int tile=16+16*1024+16;
  for(int row=0;row<8;row++)cartridge[tile+row]=(byte)255;
  return cartridge;
 }
}
