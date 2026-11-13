import java.util.Arrays;
import org.localpilot.gamelab.HomebrewNes;
import org.localpilot.gamelab.NesRomPreflight;
import org.localpilot.gamelab.RetroEmulatorCatalog;

/** Self-contained, real-javac host regression for all ROM admission gates. */
public final class NesRomPreflightTest {
 private static int checks;
 private static void result(byte[] rom,String want){
  NesRomPreflight.Result r=NesRomPreflight.check(rom);
  if(!want.equals(r.code()))throw new AssertionError(want+" != "+r.code());
  checks++;
 }
 private static void expect(boolean good,String description){
  if(!good)throw new AssertionError(description);
  checks++;
 }
 public static void main(String[] args){
  byte[] homebrew=HomebrewNes.cartridge();
  result(homebrew,"READY");
  expect(NesRomPreflight.check(homebrew).mapper()==0,"NROM mapper");
  expect(!NesRomPreflight.check(homebrew).nes2(),"iNES header");

  byte[] nes2=homebrew.clone();
  nes2[7]=8;
  result(nes2,"READY");
  expect(NesRomPreflight.check(nes2).nes2(),"NES2 detected");

  byte[] submapper=nes2.clone();submapper[8]=0x10;
  result(submapper,"NES2_SUBMAPPER_UNVERIFIED");
  byte[] console=nes2.clone();console[7]=9;
  result(console,"NES2_CONSOLE_UNSUPPORTED");

  byte[] mapper=homebrew.clone();mapper[6]=(byte)0xf0;
  result(mapper,"MAPPER_UNSUPPORTED");
  byte[] surom=new byte[16+512*1024];
  System.arraycopy(homebrew,0,surom,0,16);
  surom[4]=32;surom[5]=0;surom[6]=0x10;
  result(surom,"MAPPER1_SUROM_UNSAFE");

  byte[] trainer=homebrew.clone();trainer[6]=4;
  result(trainer,"ROM_TRUNCATED");
  result(Arrays.copyOf(homebrew,homebrew.length-1),"ROM_TRUNCATED");
  result(Arrays.copyOf(homebrew,homebrew.length+1),"ROM_TRAILING_DATA_UNVERIFIED");
  result(Arrays.copyOf(homebrew,16),"ROM_TRUNCATED");

  byte[] zero=homebrew.clone();zero[4]=0;
  result(zero,"ROM_SIZE_UNSUPPORTED");
  byte[] unknown=homebrew.clone();unknown[7]=0x0c;
  result(unknown,"ROM_UNKNOWN_HEADER_VARIANT");
  byte[] badMagic=homebrew.clone();badMagic[0]=0;
  result(badMagic,"ROM_BAD_MAGIC");

  var dos=RetroEmulatorCatalog.preflight(RetroEmulatorCatalog.System.DOS,homebrew);
  expect(!dos.runnable()&&"UNAVAILABLE_LOCALLY".equals(dos.code()),"DOS unavailable");
  expect(!RetroEmulatorCatalog.System.DOS.runnableInGameLab(),"DOS cannot run locally");
  expect(RetroEmulatorCatalog.System.DOS.nativeLibraryName().isEmpty(),"No phantom DOS .so");
  expect("UNAVAILABLE_LOCALLY".equals(RetroEmulatorCatalog.System.DOS.availabilityCode()),
    "DOS availability explicit");

  var nes=RetroEmulatorCatalog.preflight(RetroEmulatorCatalog.System.NES,homebrew);
  expect(nes.runnable()&&"READY".equals(nes.code()),"NES cartridge admitted");
  System.out.println("NES_PREFLIGHT_PASS checks="+checks);
 }
}
