package org.localpilot.gamelab;

/**
 * Fail-closed iNES / NES 2.0 preflight for the pinned JSNES 2.1.0 bundle.
 * Validates the FULL cartridge, not only its 16-byte header. Passing this
 * check establishes cartridge format compatibility, not gameplay correctness.
 */
public final class NesRomPreflight {
 private NesRomPreflight(){}
 public static final long MAX_BYTES=3L*1024*1024;
 public record Result(boolean runnable,String code,int mapper,boolean nes2){}
 private static Result fail(String code,int mapper,boolean nes2){
  return new Result(false,code,mapper,nes2);
 }
 private static int unsigned(byte value){return value&255;}
 private static boolean supportedMapper(int mapper){
  // Pinned JSNES 2.1.0 implements these mapper IDs. Individual variants
  // may still require a targeted emulator regression before agent use.
  return switch(mapper){
   case 0,1,2,3,4,5,7,9,11,34,38,66,71,79,94,118,119,140,180,240,241 -> true;
   default -> false;
  };
 }
 private static long bankSize(int lsb,int highNibble,int unit){
  if(highNibble!=15)return ((((long)highNibble)<<8)|lsb)*unit;
  // NES 2.0 exponent/multiplier notation (only when upper nibble is 0xF).
  int exponent=lsb>>>2;
  int multiplier=((lsb&3)<<1)+1;
  if(exponent>24)return MAX_BYTES+1;
  return (1L<<exponent)*multiplier;
 }
 public static Result check(byte[] data){
  if(data==null||data.length<16)return fail("ROM_HEADER_TRUNCATED",-1,false);
  if(data.length>MAX_BYTES)return fail("ROM_TOO_LARGE",-1,false);
  if(unsigned(data[0])!='N'||unsigned(data[1])!='E'||
     unsigned(data[2])!='S'||unsigned(data[3])!=26)
   return fail("ROM_BAD_MAGIC",-1,false);
  int flags6=unsigned(data[6]),flags7=unsigned(data[7]);
  int format=flags7&0x0c;
  boolean nes2=format==0x08;
  if(format!=0&&format!=0x08)return fail("ROM_UNKNOWN_HEADER_VARIANT",-1,false);
  int mapper=(flags6>>>4)|(flags7&0xf0);
  if(nes2)mapper|=(unsigned(data[8])&0x0f)<<8;
  if(nes2&&(unsigned(data[8])>>>4)!=0)
   return fail("NES2_SUBMAPPER_UNVERIFIED",mapper,true);
  if(!supportedMapper(mapper))return fail("MAPPER_UNSUPPORTED",mapper,nes2);
  long prg,chr;
  if(nes2){
   int sizeMsb=unsigned(data[9]);
   prg=bankSize(unsigned(data[4]),sizeMsb&15,16384);
   chr=bankSize(unsigned(data[5]),sizeMsb>>>4,8192);
   // NES2 console variants need separate core/extension support.
   if((flags7&3)!=0)return fail("NES2_CONSOLE_UNSUPPORTED",mapper,true);
  }else{
   prg=(long)unsigned(data[4])*16384;
   chr=(long)unsigned(data[5])*8192;
  }
  if(prg==0||prg>MAX_BYTES||chr>MAX_BYTES)
   return fail("ROM_SIZE_UNSUPPORTED",mapper,nes2);
  // JSNES 2.1.0 MMC1 SUROM 512KiB with CHR-RAM can crash on invalid opcode.
  if(mapper==1&&prg>=512L*1024&&chr==0)
   return fail("MAPPER1_SUROM_UNSAFE",mapper,nes2);
  long expected=16+(((flags6&4)!=0)?512:0)+prg+chr;
  if(expected>MAX_BYTES)return fail("ROM_TOO_LARGE",mapper,nes2);
  if(data.length<expected)return fail("ROM_TRUNCATED",mapper,nes2);
  if(data.length>expected)return fail("ROM_TRAILING_DATA_UNVERIFIED",mapper,nes2);
  return new Result(true,"READY",mapper,nes2);
 }
}
