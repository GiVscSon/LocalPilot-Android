package org.localpilot.android.retro;

import java.util.List;
import java.util.Locale;
import org.localpilot.android.learning.TaskSchema;

/**
 * Hardware, display, controller, and emulator package specifications for classic retro platforms
 * supported by LocalPilot-Android:
 * 1. NES (Nintendo Entertainment System / Famicom / Dendy) — 256x240, 8x8 tiles, D-Pad + B/A.
 * 2. DOS (MS-DOS PC VGA Mode 13h / EGA) — 320x200 (4:3 CRT display), Cursor keys + Ctrl/Alt/Space.
 * 3. SEGA (Mega Drive / Genesis & Master System) — 320x224, 8x8 tiles, D-Pad + A/B/C + Spin Dash.
 */
public final class RetroPlatform {

 public enum Console {
  NES("nes", "NES / Famicom / Dendy", 256, 240, 16, 15, 0.10f, 0.0f,
    new String[]{
      "MOVE_LEFT", "MOVE_RIGHT", "AIM_UP", "CROUCH_DOWN",
      "JUMP_A", "ATTACK_B", "RUN_RIGHT_B", "RUN_JUMP_RIGHT_AB"
    }),
  DOS("dos", "MS-DOS (VGA / EGA)", 320, 200, 20, 12, 0.0f, 0.16f,
    new String[]{
      "STEP_LEFT", "STEP_RIGHT", "JUMP_CLIMB_UP", "CROUCH_DOWN",
      "FIRE_CTRL", "USE_STRAFE_ALT", "LEAP_RIGHT_UP", "ADVANCE_FIRE_RIGHT"
    }),
  SEGA("sega", "SEGA Mega Drive / Genesis", 320, 224, 20, 14, 0.12f, 0.0f,
    new String[]{
      "DPAD_LEFT", "DPAD_RIGHT", "CROUCH_DOWN", "SPECIAL_A",
      "ATTACK_B", "JUMP_C", "SPRINT_JUMP_RIGHT_C", "SPIN_DASH_CHARGE_RELEASE"
    });

  public final String id;
  public final String title;
  public final int nativeWidth;
  public final int nativeHeight;
  public final int gridCols;
  public final int gridRows;
  public final float topHudRatio;
  public final float bottomHudRatio;
  private final String[] actionLabels;

  Console(String id, String title, int nativeWidth, int nativeHeight,
    int gridCols, int gridRows, float topHudRatio, float bottomHudRatio,
    String[] actionLabels) {
   this.id = id;
   this.title = title;
   this.nativeWidth = nativeWidth;
   this.nativeHeight = nativeHeight;
   this.gridCols = gridCols;
   this.gridRows = gridRows;
   this.topHudRatio = topHudRatio;
   this.bottomHudRatio = bottomHudRatio;
   this.actionLabels = actionLabels.clone();
  }

  public int actionCount() { return actionLabels.length; }

  public String actionLabel(int action) {
   if (action < 0 || action >= actionLabels.length) return "WAIT";
   return actionLabels[action];
  }

  public float nativeAspectRatio() {
   return nativeWidth / (float) nativeHeight;
  }
 }

 public enum Archetype {
  NES_PLATFORMER(
    Console.NES,
    TaskSchema.GameId.NES_PLATFORMER,
    "NES · Сайд-скроллер платформер (Mario / Contra / Castlevania)",
    true, false
  ),
  NES_TACTICAL_ARENA(
    Console.NES,
    TaskSchema.GameId.NES_TACTICAL_ARENA,
    "NES · Тактическая арена и лабиринт (Battle City / Bomberman)",
    false, true
  ),
  DOS_PLATFORM_EXPLORER(
    Console.DOS,
    TaskSchema.GameId.DOS_PLATFORM_EXPLORER,
    "DOS · Приключения и точные прыжки (Prince of Persia / Commander Keen / Dave)",
    true, false
  ),
  DOS_CORRIDOR_ACTION(
    Console.DOS,
    TaskSchema.GameId.DOS_CORRIDOR_ACTION,
    "DOS · Коридорный экшен и шутер (Wolfenstein 3D / Doom / Tyrian)",
    false, true
  ),
  SEGA_HIGH_SPEED_ACT(
    Console.SEGA,
    TaskSchema.GameId.SEGA_HIGH_SPEED_ACT,
    "SEGA · Скоростной импульсный платформер (Sonic / Earthworm Jim / Aladdin)",
    true, false
  ),
  SEGA_BRAWLER_RUNGUN(
    Console.SEGA,
    TaskSchema.GameId.SEGA_BRAWLER_RUNGUN,
    "SEGA · Битэмап и ран-н-ган (Streets of Rage / Golden Axe / Gunstar Heroes)",
    true, true
  );

  public final Console console;
  public final TaskSchema.GameId gameId;
  public final String displayName;
  public final boolean sideScrolling;
  public final boolean arenaOrDepthAxis;

  Archetype(Console console, TaskSchema.GameId gameId, String displayName,
    boolean sideScrolling, boolean arenaOrDepthAxis) {
   this.console = console;
   this.gameId = gameId;
   this.displayName = displayName;
   this.sideScrolling = sideScrolling;
   this.arenaOrDepthAxis = arenaOrDepthAxis;
  }

  public static Archetype forGameId(TaskSchema.GameId gameId) {
   if (gameId == null) return null;
   for (Archetype a : values()) {
    if (a.gameId == gameId) return a;
   }
   return null;
  }
 }

 public record EmulatorTarget(
   Console console,
   Archetype defaultArchetype,
   String packageName,
   String emulatorName,
   boolean hasOnScreenGamepad
 ) {}

 private static final List<EmulatorTarget> KNOWN_EMULATORS = List.of(
   new EmulatorTarget(Console.NES, Archetype.NES_PLATFORMER, "org.localpilot.gamelab", "LocalPilot GameLab NES", true),
   new EmulatorTarget(Console.NES, Archetype.NES_PLATFORMER, "com.explusalpha.NesEmu", "NES.emu", true),
   new EmulatorTarget(Console.NES, Archetype.NES_PLATFORMER, "ru.nostalgiaemulators.nes1", "Nostalgia.NES", true),
   new EmulatorTarget(Console.NES, Archetype.NES_TACTICAL_ARENA, "com.nes.emulator.fc", "FC/NES Emulator", true),
   new EmulatorTarget(Console.DOS, Archetype.DOS_PLATFORM_EXPLORER, "bruenor.magicbox", "Magic DosBox", true),
   new EmulatorTarget(Console.DOS, Archetype.DOS_PLATFORM_EXPLORER, "bruenor.magicbox.free", "Magic DosBox Free", true),
   new EmulatorTarget(Console.DOS, Archetype.DOS_CORRIDOR_ACTION, "com.fishstix.dosbox", "AnDosBox / DosBox Turbo", true),
   new EmulatorTarget(Console.DOS, Archetype.DOS_PLATFORM_EXPLORER, "org.dosbox.dosbox", "DOSBox Android", true),
   new EmulatorTarget(Console.SEGA, Archetype.SEGA_HIGH_SPEED_ACT, "com.explusalpha.MdEmu", "MD.emu (Genesis/Mega Drive)", true),
   new EmulatorTarget(Console.SEGA, Archetype.SEGA_HIGH_SPEED_ACT, "uk.org.tiz.mdemu", "GenPlusDroid", true),
   new EmulatorTarget(Console.SEGA, Archetype.SEGA_BRAWLER_RUNGUN, "com.swordfish.lemuroid", "Lemuroid (Libretro)", true),
   new EmulatorTarget(Console.SEGA, Archetype.SEGA_HIGH_SPEED_ACT, "com.retroarch", "RetroArch", true),
   new EmulatorTarget(Console.SEGA, Archetype.SEGA_HIGH_SPEED_ACT, "com.retroarch.aarch64", "RetroArch 64-bit", true)
 );

 private RetroPlatform() {}

 public static List<EmulatorTarget> knownEmulators() {
  return KNOWN_EMULATORS;
 }

 /**
  * Identifies the retro console and archetype from an Android package name and optional window/game label.
  * Returns null if neither package nor label matches a retro console target.
  */
 public static EmulatorTarget detectTarget(String packageName, String gameOrWindowLabel) {
  String pkg = packageName == null ? "" : packageName.trim();
  String label = gameOrWindowLabel == null ? "" : gameOrWindowLabel.trim().toLowerCase(Locale.ROOT);

  // 1. Check explicit label hints first (handles multi-core RetroArch, Lemuroid, WebView JSNES / js-dos / EmulatorJS)
  if (label.contains("sega") || label.contains("genesis") || label.contains("mega drive")
    || label.contains("sonic") || label.contains("streets of rage") || label.contains("golden axe")
    || label.contains("gunstar") || label.contains("brawler")) {
   Archetype arch = (label.contains("rage") || label.contains("axe") || label.contains("gunstar")
     || label.contains("brawler") || label.contains("битэмап"))
     ? Archetype.SEGA_BRAWLER_RUNGUN : Archetype.SEGA_HIGH_SPEED_ACT;
   return new EmulatorTarget(Console.SEGA, arch, pkg.isEmpty() ? "com.explusalpha.MdEmu" : pkg, "SEGA Target", true);
  }
  if (label.contains("dos") || label.contains("vga") || label.contains("prince")
    || label.contains("keen") || label.contains("dave") || label.contains("wolf")
    || label.contains("doom") || label.contains("tyrian")) {
   Archetype arch = (label.contains("wolf") || label.contains("doom") || label.contains("tyrian")
     || label.contains("corridor") || label.contains("коридор") || label.contains("шутер"))
     ? Archetype.DOS_CORRIDOR_ACTION : Archetype.DOS_PLATFORM_EXPLORER;
   return new EmulatorTarget(Console.DOS, arch, pkg.isEmpty() ? "bruenor.magicbox" : pkg, "DOSBox Target", true);
  }
  if (label.contains("nes") || label.contains("dendy") || label.contains("famicom")
    || label.contains("нэс") || label.contains("денди") || label.contains("mario")
    || label.contains("contra") || label.contains("battle city") || label.contains("bomberman")
    || label.contains("танки")) {
   Archetype arch = (label.contains("battle") || label.contains("tank") || label.contains("танки")
     || label.contains("bomber") || label.contains("arena") || label.contains("арена"))
     ? Archetype.NES_TACTICAL_ARENA : Archetype.NES_PLATFORMER;
   return new EmulatorTarget(Console.NES, arch, pkg.isEmpty() ? "org.localpilot.gamelab" : pkg, "NES Target", true);
  }

  // 2. Fallback to exact package match
  for (EmulatorTarget t : KNOWN_EMULATORS) {
   if (t.packageName.equalsIgnoreCase(pkg)) return t;
  }
  return null;
 }
}
