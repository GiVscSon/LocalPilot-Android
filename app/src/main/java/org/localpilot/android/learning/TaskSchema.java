package org.localpilot.android.learning;

import java.util.ArrayList;
import java.util.List;

/**
 * Formal task typing, provenance tags, and schema versioning for all 16 target games
 * in LocalPilot Adaptive Learning Engine:
 * - 10 Yandex Market games (`SHIPUCHKA` .. `KOTOBASHNYA`);
 * - 6 Retro Console games across NES, DOS, and SEGA (`NES_PLATFORMER`, `NES_TACTICAL_ARENA`,
 *   `DOS_PLATFORM_EXPLORER`, `DOS_CORRIDOR_ACTION`, `SEGA_HIGH_SPEED_ACT`, `SEGA_BRAWLER_RUNGUN`).
 * Prevents cross-game, cross-platform, or cross-mechanic contamination.
 */
public final class TaskSchema {
 public static final int OBS_DIM = 64;

 public enum Provenance {
  /** Synthetic curriculum environment transition. */
  SIM,
  /** Real-game observation recorded without dispatching input or modifying the game. */
  SHADOW,
  /** Real-game transition with authorized dispatch, Android ACK, and independent post-ACK visual proof. */
  REAL
 }

 public enum TelemetryEvent {
  MODEL_USED,
  MODEL_FALLBACK,
  MODEL_UPDATE_ACCEPTED,
  MODEL_UPDATE_REJECTED
 }

 public enum RewardStatus {
  MEASURED,
  UNKNOWN
 }

 public enum PlatformFamily {
  YANDEX_MARKET,
  NES,
  DOS,
  SEGA
 }

 public enum GameId {
  // 10 Yandex Market Mini-Games
  SHIPUCHKA("shipuchka", "Шипучка", "bubble-hex-v2", "obs64-bubble-v1", "candidate-shot-8-v1", "clear-drop-v1", 8, false, PlatformFamily.YANDEX_MARKET),
  ARI_JUMPER("ari_jumper", "Прыгун Ари", "jumper-wrap-v2", "obs64-jumper-v1", "left-right-wait-3-v1", "height-landing-v1", 3, true, PlatformFamily.YANDEX_MARKET),
  MATCH_WORLD("match_world", "Мэтч Ворлд", "swap-match3-7x7-v2", "obs64-grid7x7-v1", "candidate-swap-8-v1", "match-goal-v1", 8, false, PlatformFamily.YANDEX_MARKET),
  KARKANOID("karkanoid", "Карканоид", "paddle-descent-v2", "obs64-paddle-v1", "paddle-5-v1", "intercept-block-v1", 5, true, PlatformFamily.YANDEX_MARKET),
  MARKET_RUSH("market_rush", "Маркет Раш", "lanes-3to4-v2", "obs64-traffic-v1", "lanes-3-v1", "survival-fuel-v1", 3, true, PlatformFamily.YANDEX_MARKET),
  GOSHAN_FLAPPY("goshan_flappy", "Гошан, вперёд!", "flappy-impulse-v2", "obs64-flappy-v1", "wait-flap-2-v1", "gap-passage-v1", 2, true, PlatformFamily.YANDEX_MARKET),
  GAME_2048("game_2048", "2048", "merge-4x4-v2", "obs64-2048-v1", "lurd-4-v1", "log-merge-v1", 4, false, PlatformFamily.YANDEX_MARKET),
  HEXON("hexon", "Гексон", "hexa-sort-10-v1", "obs64-hexon-v1", "candidate-hex-8-v1", "merge-clear10-v1", 8, false, PlatformFamily.YANDEX_MARKET),
  NASHCHUPAY_KHOD("nashchupay_khod", "Нащупай ход", "protected-skull-swap-6x6-v2", "obs64-grid6x6-skull-v1", "candidate-swap-8-v1", "safe-clear-skull-v1", 8, false, PlatformFamily.YANDEX_MARKET),
  KOTOBASHNYA("kotobashnya", "Котобашня", "tower-climb-stomp-slab-v1", "obs64-tower-v1", "tower-6-v1", "climb-stomp-slab-v1", 6, true, PlatformFamily.YANDEX_MARKET),

  // 6 Retro Console Archetypes (NES, DOS, SEGA)
  NES_PLATFORMER("nes_platformer", "NES · Платформер (Mario / Contra)", "nes-8bit-scroll-v1", "obs64-retro-nes-scroll-v1", "nes-pad-8-v1", "progress-score-lives-v1", 8, true, PlatformFamily.NES),
  NES_TACTICAL_ARENA("nes_tactical_arena", "NES · Танки и лабиринты (Battle City / Bomberman)", "nes-8bit-arena-v1", "obs64-retro-nes-arena-v1", "nes-pad-8-v1", "base-defend-clear-v1", 8, true, PlatformFamily.NES),
  DOS_PLATFORM_EXPLORER("dos_platform_explorer", "DOS · Приключения и ловушки (Prince of Persia / Keen)", "dos-vga-platform-v1", "obs64-retro-dos-platform-v1", "dos-keypad-8-v1", "ledge-trap-duel-v1", 8, true, PlatformFamily.DOS),
  DOS_CORRIDOR_ACTION("dos_corridor_action", "DOS · Коридорный экшен (Wolf3D / Doom / Tyrian)", "dos-vga-corridor-v1", "obs64-retro-dos-corridor-v1", "dos-keypad-8-v1", "aim-strafe-survive-v1", 8, true, PlatformFamily.DOS),
  SEGA_HIGH_SPEED_ACT("sega_high_speed_act", "SEGA · Скоростной платформер (Sonic / Earthworm Jim)", "sega-16bit-momentum-v1", "obs64-retro-sega-momentum-v1", "sega-pad-8-v1", "rings-momentum-loop-v1", 8, true, PlatformFamily.SEGA),
  SEGA_BRAWLER_RUNGUN("sega_brawler_rungun", "SEGA · Битэмап и ран-н-ган (Streets of Rage / Gunstar)", "sega-16bit-brawler-v1", "obs64-retro-sega-brawler-v1", "sega-pad-8-v1", "depth-combo-crowd-v1", 8, true, PlatformFamily.SEGA);

  public final String id, title, mechanicsVersion, observationSchema, actionSchema, rewardVersion;
  public final int actionCount;
  public final boolean continuousArcade;
  public final PlatformFamily platformFamily;

  GameId(String id, String title, String mechanicsVersion, String observationSchema,
    String actionSchema, String rewardVersion, int actionCount, boolean continuousArcade,
    PlatformFamily platformFamily) {
   this.id = id;
   this.title = title;
   this.mechanicsVersion = mechanicsVersion;
   this.observationSchema = observationSchema;
   this.actionSchema = actionSchema;
   this.rewardVersion = rewardVersion;
   this.actionCount = actionCount;
   this.continuousArcade = continuousArcade;
   this.platformFamily = platformFamily;
  }

  public boolean isRetroConsole() {
   return platformFamily != PlatformFamily.YANDEX_MARKET;
  }

  public static List<GameId> marketGames() {
   List<GameId> out = new ArrayList<>(10);
   for (GameId g : values()) if (!g.isRetroConsole()) out.add(g);
   return out;
  }

  public static List<GameId> retroGames() {
   List<GameId> out = new ArrayList<>(6);
   for (GameId g : values()) if (g.isRetroConsole()) out.add(g);
   return out;
  }
 }

 public final GameId gameId;
 public final String packageScope;
 public final String slotScope;
 public final int rows, cols;

 public TaskSchema(GameId gameId, String packageScope, String slotScope, int rows, int cols) {
  if (gameId == null || packageScope == null || packageScope.isBlank()
    || slotScope == null || slotScope.isBlank() || rows < 1 || cols < 1)
   throw new IllegalArgumentException("invalid task schema");
  this.gameId = gameId;
  this.packageScope = packageScope.trim();
  this.slotScope = slotScope.trim();
  this.rows = rows;
  this.cols = cols;
 }

 public static TaskSchema forGame(GameId gameId) {
  int r = switch (gameId) {
   case MATCH_WORLD -> 7;
   case NASHCHUPAY_KHOD -> 6;
   case GAME_2048 -> 4;
   case HEXON -> 5;
   case SHIPUCHKA -> 12;
   case NES_PLATFORMER, NES_TACTICAL_ARENA -> 15;
   case DOS_PLATFORM_EXPLORER, DOS_CORRIDOR_ACTION -> 12;
   case SEGA_HIGH_SPEED_ACT, SEGA_BRAWLER_RUNGUN -> 14;
   default -> 1;
  };
  int c = switch (gameId) {
   case MATCH_WORLD -> 7;
   case NASHCHUPAY_KHOD, SHIPUCHKA -> 6;
   case GAME_2048 -> 4;
   case HEXON -> 5;
   case NES_PLATFORMER, NES_TACTICAL_ARENA -> 16;
   case DOS_PLATFORM_EXPLORER, DOS_CORRIDOR_ACTION, SEGA_HIGH_SPEED_ACT, SEGA_BRAWLER_RUNGUN -> 20;
   default -> 1;
  };
  String pkg = switch (gameId.platformFamily) {
   case YANDEX_MARKET -> "ru.beru.android";
   case NES -> "org.localpilot.gamelab";
   case DOS -> "bruenor.magicbox";
   case SEGA -> "com.explusalpha.MdEmu";
  };
  return new TaskSchema(gameId, pkg, "default", r, c);
 }

 public boolean compatibleWith(TaskSchema other) {
  return other != null && gameId == other.gameId
    && gameId.mechanicsVersion.equals(other.gameId.mechanicsVersion)
    && gameId.observationSchema.equals(other.gameId.observationSchema)
    && gameId.actionSchema.equals(other.gameId.actionSchema)
    && gameId.rewardVersion.equals(other.gameId.rewardVersion)
    && packageScope.equals(other.packageScope)
    && slotScope.equals(other.slotScope)
    && rows == other.rows && cols == other.cols;
 }

 public String scopeKey() {
  return gameId.id + "|" + gameId.mechanicsVersion + "|" + packageScope + "|" + slotScope + "|" + rows + "x" + cols;
 }
}
