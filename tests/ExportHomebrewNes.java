import java.nio.file.Files;
import java.nio.file.Path;
import org.localpilot.gamelab.HomebrewNes;

/** Make a legal playable cartridge for an actual jsnes CPU/PPU smoke test. */
class ExportHomebrewNes {
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("Output ROM path required");
  Files.write(Path.of(args[0]),HomebrewNes.cartridge());
  System.out.println("NES_HOME_BREW_ROM_OK");
 }
}
