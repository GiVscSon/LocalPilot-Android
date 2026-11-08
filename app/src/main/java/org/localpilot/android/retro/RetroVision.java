package org.localpilot.android.retro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.localpilot.android.BoundedPixels;
import org.localpilot.android.PixelBoardReader;

/**
 * Deterministic Pixel-Art & Tile Geometry Vision Engine for NES, DOS, and SEGA Android Emulators.
 *
 * Capabilities:
 * 1. Separates the active CRT/emulator viewport (`256x240`, `320x200`, `320x224`) from black
 *    letterbox/pillarbox borders and on-screen virtual touch gamepad controls;
 * 2. Calibrates virtual D-Pad and Action Button screen coordinates (`VirtualPadLayout`);
 * 3. Strips and reads platform-specific HUD strips (NES top status bar, DOS bottom VGA status bar,
 *    SEGA top-left SCORE/TIME/RINGS overlay) for independent post-ACK visual verification;
 * 4. Extracts tile-grid geometry (ground, pits, spikes, destructible bricks, Eagle base) and
 *    foreground sprites (player, enemies, projectiles, rings/coins/keys) and computes the 8-action
 *    legality mask and candidate utility features.
 */
public final class RetroVision {

 public enum TileKind {
  EMPTY_SKY,
  SOLID_TERRAIN,
  HAZARD_PIT_OR_SPIKE,
  DESTRUCTIBLE_BRICK,
  COLLECTIBLE_RING_COIN,
  OBJECTIVE_BASE_OR_EXIT
 }

 public enum SpriteRole {
  PLAYER,
  ENEMY_PATROL,
  PROJECTILE_THREAT,
  COLLECTIBLE_ITEM,
  BOSS_OR_HEAVY
 }

 public record ViewportBounds(int left, int top, int right, int bottom, int playTop, int playBottom) {
  public int width() { return right - left; }
  public int height() { return bottom - top; }
  public int playHeight() { return playBottom - playTop; }
  public boolean valid() { return width() >= 120 && height() >= 100 && playHeight() >= 72; }
 }

 public static final class VirtualPadLayout {
  public final int screenWidth, screenHeight;
  public final int dpadLeftX, dpadLeftY;
  public final int dpadRightX, dpadRightY;
  public final int dpadUpX, dpadUpY;
  public final int dpadDownX, dpadDownY;
  public final int buttonPrimaryX, buttonPrimaryY;     // NES A / DOS Space / SEGA C (Jump)
  public final int buttonSecondaryX, buttonSecondaryY; // NES B / DOS Ctrl / SEGA B (Attack/Run)
  public final int buttonTertiaryX, buttonTertiaryY;   // DOS Alt / SEGA A (Special/Strafe)

  public VirtualPadLayout(int screenWidth, int screenHeight,
    int dpadLeftX, int dpadLeftY, int dpadRightX, int dpadRightY,
    int dpadUpX, int dpadUpY, int dpadDownX, int dpadDownY,
    int buttonPrimaryX, int buttonPrimaryY,
    int buttonSecondaryX, int buttonSecondaryY,
    int buttonTertiaryX, int buttonTertiaryY) {
   this.screenWidth = screenWidth;
   this.screenHeight = screenHeight;
   this.dpadLeftX = dpadLeftX;
   this.dpadLeftY = dpadLeftY;
   this.dpadRightX = dpadRightX;
   this.dpadRightY = dpadRightY;
   this.dpadUpX = dpadUpX;
   this.dpadUpY = dpadUpY;
   this.dpadDownX = dpadDownX;
   this.dpadDownY = dpadDownY;
   this.buttonPrimaryX = buttonPrimaryX;
   this.buttonPrimaryY = buttonPrimaryY;
   this.buttonSecondaryX = buttonSecondaryX;
   this.buttonSecondaryY = buttonSecondaryY;
   this.buttonTertiaryX = buttonTertiaryX;
   this.buttonTertiaryY = buttonTertiaryY;
  }

  public static VirtualPadLayout standardForScreen(int screenW, int screenH, ViewportBounds vp) {
   int padCenterY = vp != null && vp.bottom < screenH - 70
     ? (vp.bottom + screenH) / 2
     : Math.round(screenH * 0.82f);
   int dpadCenterX = Math.round(screenW * 0.18f);
   int step = Math.max(24, Math.round(Math.min(screenW, screenH) * 0.065f));
   int btnBaseX = Math.round(screenW * 0.82f);
   return new VirtualPadLayout(
     screenW, screenH,
     Math.max(12, dpadCenterX - step), padCenterY,
     Math.min(screenW - 12, dpadCenterX + step), padCenterY,
     dpadCenterX, Math.max(12, padCenterY - step),
     dpadCenterX, Math.min(screenH - 12, padCenterY + step),
     Math.min(screenW - 12, btnBaseX + step / 2), padCenterY - step / 3,
     Math.max(12, btnBaseX - step / 2), padCenterY + step / 4,
     Math.max(12, btnBaseX - step), padCenterY + step / 2
   );
  }
 }

 public record SpriteBox(SpriteRole role, float x, float y, float width, float height, float vx, float vy) {}

 public static final class RetroScene {
  public final RetroPlatform.Archetype archetype;
  public final long frameGeneration;
  public final ViewportBounds viewport;
  public final VirtualPadLayout padLayout;
  public final float playerX, playerY;
  public final float playerVx, playerVy;
  public final boolean onGround;
  public final boolean pitAhead;
  public final boolean hazardCollision;
  public final boolean spinDashOrSpecialReady;
  public final float nearestEnemyDx, nearestEnemyDy;
  public final float nearestProjectileTtc;
  public final float nearestCollectibleDx, nearestCollectibleDy;
  public final int activeEnemyCount;
  public final int hudScoreProxy;
  public final int hudCollectiblesProxy;
  public final float hudVitalityRatio;
  public final float confidence;
  public final List<SpriteBox> sprites;
  public final float[][] candidateFeatures; // [8][6]
  public final boolean[] legalMask;         // [8]

  public RetroScene(RetroPlatform.Archetype archetype, long frameGeneration,
    ViewportBounds viewport, VirtualPadLayout padLayout,
    float playerX, float playerY, float playerVx, float playerVy,
    boolean onGround, boolean pitAhead, boolean hazardCollision, boolean spinDashOrSpecialReady,
    float nearestEnemyDx, float nearestEnemyDy, float nearestProjectileTtc,
    float nearestCollectibleDx, float nearestCollectibleDy,
    int activeEnemyCount, int hudScoreProxy, int hudCollectiblesProxy, float hudVitalityRatio,
    float confidence, List<SpriteBox> sprites,
    float[][] candidateFeatures, boolean[] legalMask) {
   this.archetype = archetype;
   this.frameGeneration = frameGeneration;
   this.viewport = viewport;
   this.padLayout = padLayout;
   this.playerX = playerX;
   this.playerY = playerY;
   this.playerVx = playerVx;
   this.playerVy = playerVy;
   this.onGround = onGround;
   this.pitAhead = pitAhead;
   this.hazardCollision = hazardCollision;
   this.spinDashOrSpecialReady = spinDashOrSpecialReady;
   this.nearestEnemyDx = nearestEnemyDx;
   this.nearestEnemyDy = nearestEnemyDy;
   this.nearestProjectileTtc = nearestProjectileTtc;
   this.nearestCollectibleDx = nearestCollectibleDx;
   this.nearestCollectibleDy = nearestCollectibleDy;
   this.activeEnemyCount = activeEnemyCount;
   this.hudScoreProxy = hudScoreProxy;
   this.hudCollectiblesProxy = hudCollectiblesProxy;
   this.hudVitalityRatio = hudVitalityRatio;
   this.confidence = confidence;
   this.sprites = List.copyOf(sprites);
   this.candidateFeatures = new float[8][6];
   for (int a = 0; a < 8; a++) {
    System.arraycopy(candidateFeatures[a], 0, this.candidateFeatures[a], 0, 6);
   }
   this.legalMask = legalMask.clone();
  }

  public boolean usable() {
   if (viewport == null || !viewport.valid() || confidence < 0.45f) return false;
   for (boolean b : legalMask) if (b) return true;
   return false;
  }

  public String summary() {
   return String.format(Locale.ROOT,
     "RETRO_SCENE[%s gen=%d pos=(%.2f,%.2f) vel=(%.2f,%.2f) enemy=%d score=%d coll=%d vit=%.2f conf=%.2f]",
     archetype.name(), frameGeneration, playerX, playerY, playerVx, playerVy,
     activeEnemyCount, hudScoreProxy, hudCollectiblesProxy, hudVitalityRatio, confidence);
  }
 }

 private RetroVision() {}

 /**
  * Locates the active retro console CRT viewport inside a phone/tablet frame, excluding black
  * pillarbox/letterbox bars and bottom touch-controller panels.
  */
 public static ViewportBounds locateViewport(PixelBoardReader.Pixels pixels, int width, int height,
   RetroPlatform.Console console) {
  if (pixels == null || width < 160 || height < 120 || console == null) {
   return new ViewportBounds(0, 0, Math.max(1, width), Math.max(1, height), 0, Math.max(1, height));
  }
  int minX = width, maxX = 0, minY = height, maxY = 0;
  int stepX = Math.max(2, width / 64);
  int stepY = Math.max(2, height / 64);
  // Scan upper 78% of portrait screen or central 88% of landscape screen to avoid bottom virtual pad
  int scanBottom = height > width ? Math.round(height * 0.72f) : Math.round(height * 0.92f);
  for (int y = 4; y < scanBottom; y += stepY) {
   for (int x = 4; x < width - 4; x += stepX) {
    int rgb = pixels.rgb(x, y);
    int r = (rgb >> 16) & 0xFF;
    int g = (rgb >> 8) & 0xFF;
    int b = rgb & 0xFF;
    // Non-black / non-letterbox pixel (also ignore dark bezel #08080c)
    if (r + g + b > 42) {
     if (x < minX) minX = x;
     if (x > maxX) maxX = x;
     if (y < minY) minY = y;
     if (y > maxY) maxY = y;
    }
   }
  }
  if (maxX - minX < 120 || maxY - minY < 100) {
   minX = 0;
   maxX = width;
   minY = 0;
   maxY = height > width ? Math.round(height * 0.68f) : height;
  }
  int vpH = maxY - minY;
  int playTop = minY + Math.round(vpH * console.topHudRatio);
  int playBottom = maxY - Math.round(vpH * console.bottomHudRatio);
  return new ViewportBounds(minX, minY, maxX, maxY, playTop, playBottom);
 }

 /**
  * Analyzes an RGB frame from an Android emulator or rendered `RetroConsoleEnv` and extracts
  * the structured `RetroScene` (HUD, player, enemies, hazards, collectibles, and 8-action utilities).
  */
 public static RetroScene analyzeFrame(int[] argb, int width, int height, long generation,
   RetroPlatform.Archetype archetype, RetroScene previousScene) {
  BoundedPixels bounded = new BoundedPixels(argb, width, height, 0, 0, width, height, generation);
  return analyzeFrame(bounded, width, height, generation, archetype, previousScene);
 }

 public static RetroScene analyzeFrame(PixelBoardReader.Pixels pixels, int width, int height,
   long generation, RetroPlatform.Archetype archetype, RetroScene previousScene) {
  RetroPlatform.Console console = archetype.console;
  ViewportBounds vp = locateViewport(pixels, width, height, console);
  VirtualPadLayout pad = VirtualPadLayout.standardForScreen(width, height, vp);

  // 1. Read HUD strip for score/collectible/vitality proxies
  int hudBrightPixels = 0;
  int hudGoldPixels = 0;
  int hudVitalPixels = 0;
  int hudY0 = console == RetroPlatform.Console.DOS ? vp.playBottom : vp.top;
  int hudY1 = console == RetroPlatform.Console.DOS ? vp.bottom : Math.max(vp.top + 8, vp.playTop);
  for (int y = hudY0; y < hudY1; y += 2) {
   for (int x = vp.left; x < vp.right; x += 2) {
    int rgb = pixels.rgb(x, y);
    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
    if (r > 200 && g > 200 && b > 200) hudBrightPixels++;
    if (r > 210 && g > 170 && b < 80) hudGoldPixels++;
    if (r > 190 && g < 90 && b < 90) hudVitalPixels++;
   }
  }
  int hudScoreProxy = hudBrightPixels / 3;
  int hudCollectiblesProxy = hudGoldPixels / 2;
  float hudVitalityRatio = Math.max(0.15f, Math.min(1.0f, hudVitalPixels / 24.0f));

  // 2. Scan active playfield for player sprite, enemies, projectiles, collectibles, and pits
  float playerSumX = 0f, playerSumY = 0f;
  int playerCount = 0;
  float enemySumX = 0f, enemySumY = 0f;
  int enemyPixelCount = 0;
  float projSumX = 0f, projSumY = 0f;
  int projPixelCount = 0;
  float collSumX = 0f, collSumY = 0f;
  int collPixelCount = 0;
  int pitColumns = 0;

  int pw = Math.max(1, vp.width());
  int ph = Math.max(1, vp.playHeight());

  for (int y = vp.playTop; y < vp.playBottom; y += 2) {
   float ny = (y - vp.playTop) / (float) ph;
   for (int x = vp.left; x < vp.right; x += 2) {
    float nx = (x - vp.left) / (float) pw;
    int rgb = pixels.rgb(x, y);
    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

    // Player signature: cyan/blue/bright-green/sonic-blue hero palette (#20b8f0 / #3068f8 / #50e878)
    if ((b > 185 && g > 110 && r < 95) || (g > 205 && b > 100 && r < 95)) {
     playerSumX += nx;
     playerSumY += ny;
     playerCount++;
    } else if (r > 195 && g < 85 && b < 95) {
     // Enemy / hazard patrol signature (crimson/red/magenta #dc283c)
     enemySumX += nx;
     enemySumY += ny;
     enemyPixelCount++;
    } else if (r > 235 && g > 120 && g < 185 && b < 50) {
     // Projectile / fireball / laser signature (#f58c14)
     projSumX += nx;
     projSumY += ny;
     projPixelCount++;
    } else if (r > 225 && g > 200 && b < 75 && ny < 0.82f) {
     // Collectible coin / ring / key / ammo signature (#f8dc28)
     collSumX += nx;
     collSumY += ny;
     collPixelCount++;
    }
   }
  }

  float playerX = playerCount > 0 ? playerSumX / playerCount : 0.22f;
  float playerY = playerCount > 0 ? playerSumY / playerCount : 0.72f;
  float playerVx = previousScene != null ? (playerX - previousScene.playerX) : 0.0f;
  float playerVy = previousScene != null ? (playerY - previousScene.playerY) : 0.0f;

  // Check ground and pit ahead in bottom 14% of playfield
  int groundY = vp.playTop + Math.min(ph - 2, Math.round(ph * 0.88f));
  int aheadXStart = vp.left + Math.min(pw - 4, Math.round((playerX + 0.04f) * pw));
  int aheadXEnd = vp.left + Math.min(pw - 2, Math.round((playerX + 0.16f) * pw));
  for (int x = aheadXStart; x < aheadXEnd; x += 3) {
   int rgb = pixels.rgb(x, groundY);
   int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
   if (r < 35 && g < 35 && b < 45) pitColumns++;
  }
  boolean pitAhead = archetype.sideScrolling && pitColumns >= 2;
  boolean onGround = playerY >= 0.62f;

  List<SpriteBox> sprites = new ArrayList<>();
  sprites.add(new SpriteBox(SpriteRole.PLAYER, playerX, playerY, 0.06f, 0.10f, playerVx, playerVy));

  float nearestEnemyDx = 0.65f, nearestEnemyDy = 0.0f;
  int activeEnemyCount = 0;
  if (enemyPixelCount >= 3) {
   float ex = enemySumX / enemyPixelCount;
   float ey = enemySumY / enemyPixelCount;
   nearestEnemyDx = ex - playerX;
   nearestEnemyDy = ey - playerY;
   activeEnemyCount = Math.max(1, enemyPixelCount / 18);
   sprites.add(new SpriteBox(SpriteRole.ENEMY_PATROL, ex, ey, 0.06f, 0.10f, -0.02f, 0f));
  }

  float nearestProjTtc = 1.0f;
  if (projPixelCount >= 2) {
   float px = projSumX / projPixelCount;
   float py = projSumY / projPixelCount;
   nearestProjTtc = Math.max(0.05f, Math.min(1.0f, Math.abs(px - playerX) * 1.8f));
   sprites.add(new SpriteBox(SpriteRole.PROJECTILE_THREAT, px, py, 0.03f, 0.03f, -0.05f, 0f));
  }

  float nearestCollDx = 0.35f, nearestCollDy = -0.10f;
  if (collPixelCount >= 2) {
   float cx = collSumX / collPixelCount;
   float cy = collSumY / collPixelCount;
   nearestCollDx = cx - playerX;
   nearestCollDy = cy - playerY;
   sprites.add(new SpriteBox(SpriteRole.COLLECTIBLE_ITEM, cx, cy, 0.04f, 0.05f, 0f, 0f));
  }

  boolean hazardCollision = (activeEnemyCount > 0 && Math.hypot(nearestEnemyDx, nearestEnemyDy) < 0.045f)
    || (projPixelCount >= 2 && nearestProjTtc < 0.08f);
  boolean spinDashOrSpecialReady = onGround && Math.abs(playerVx) < 0.02f;

  // 3. Compute 8-action candidate features and safety legality mask
  boolean[] legalMask = new boolean[8];
  float[][] cand = new float[8][6];
  computeCandidateFeatures(archetype, onGround, pitAhead, spinDashOrSpecialReady,
    nearestEnemyDx, nearestEnemyDy, nearestProjTtc, nearestCollDx, nearestCollDy,
    cand, legalMask);

  float conf = playerCount > 0 ? 0.92f : 0.65f;
  return new RetroScene(
    archetype, generation, vp, pad,
    playerX, playerY, playerVx, playerVy,
    onGround, pitAhead, hazardCollision, spinDashOrSpecialReady,
    nearestEnemyDx, nearestEnemyDy, nearestProjTtc,
    nearestCollDx, nearestCollDy,
    activeEnemyCount, hudScoreProxy, hudCollectiblesProxy, hudVitalityRatio,
    conf, sprites, cand, legalMask
  );
 }

 /**
  * Computes domain-grounded candidate features `[8][6]` and safety `legalMask[8]` for any retro
  * scene state across NES, DOS, and SEGA archetypes.
  */
 public static void computeCandidateFeatures(
   RetroPlatform.Archetype archetype,
   boolean onGround, boolean pitAhead, boolean spinDashOrSpecialReady,
   float nearestEnemyDx, float nearestEnemyDy, float nearestProjTtc,
   float nearestCollectibleDx, float nearestCollectibleDy,
   float[][] cand, boolean[] legalMask) {
  for (int a = 0; a < 8; a++) legalMask[a] = true;

  boolean enemyCloseAhead = nearestEnemyDx > 0f && nearestEnemyDx < 0.22f && Math.abs(nearestEnemyDy) < 0.18f;
  boolean projectileIncoming = nearestProjTtc < 0.35f;

  switch (archetype) {
   case NES_PLATFORMER -> {
    // 0=LEFT, 1=RIGHT, 2=UP, 3=DOWN, 4=JUMP_A, 5=ATTACK_B, 6=RUN_RIGHT_B, 7=RUN_JUMP_RIGHT_AB
    if (pitAhead) {
     // Walking right without jumping into a pit is masked out as unsafe!
     legalMask[1] = false;
     legalMask[6] = false;
    }
    if (!onGround) {
     legalMask[4] = false; // Cannot initiate new jump mid-air
    }
    for (int a = 0; a < 8; a++) {
     if (!legalMask[a]) continue;
     float prog = (a == 1 || a == 6 || a == 7) ? (pitAhead ? 0.95f : 0.78f) : (a == 4 ? 0.45f : 0.15f);
     float safe = 0.70f;
     if (pitAhead && a == 7) safe = 0.96f;
     else if (projectileIncoming && (a == 3 || a == 4)) safe = 0.92f;
     else if (enemyCloseAhead && (a == 1 || a == 6)) safe = 0.20f;
     float combat = (enemyCloseAhead && (a == 5 || a == 4 || a == 7)) ? 0.90f : 0.20f;
     float coll = (nearestCollectibleDx > 0 && (a == 1 || a == 6 || a == 7)) ? 0.75f : 0.25f;
     float syn = (pitAhead && a == 7) ? 0.98f : ((enemyCloseAhead && a == 5) ? 0.88f : (a == 6 && !pitAhead && !enemyCloseAhead ? 0.85f : 0.30f));
     float comp = clamp01(prog * 0.22f + safe * 0.28f + combat * 0.18f + coll * 0.12f + syn * 0.20f);
     cand[a] = new float[]{prog, safe, combat, coll, syn, comp};
    }
   }
   case NES_TACTICAL_ARENA -> {
    // Battle City / Bomberman: 0=LEFT, 1=RIGHT, 2=UP, 3=DOWN, 4=PLACE_BOMB_A, 5=FIRE_CANNON_B, 6=ADVANCE_FIRE, 7=EVADE_FIRE
    if (projectileIncoming) {
     legalMask[4] = false; // Do not stand still planting when shell is incoming
    }
    boolean alignedX = Math.abs(nearestEnemyDx) < 0.10f;
    boolean alignedY = Math.abs(nearestEnemyDy) < 0.10f;
    boolean lineOfFire = alignedX || alignedY;
    for (int a = 0; a < 8; a++) {
     if (!legalMask[a]) continue;
     float prog = (a == 2 || a == 6) ? 0.65f : 0.40f;
     float safe = projectileIncoming ? ((a == 0 || a == 1 || a == 7) ? 0.95f : 0.25f) : 0.78f;
     float combat = (lineOfFire && (a == 5 || a == 6 || a == 7)) ? 0.94f : (a == 4 && enemyCloseAhead ? 0.82f : 0.25f);
     float coll = (a == 5 || a == 7) ? 0.70f : 0.35f;
     float syn = (lineOfFire && a == 5) ? 0.95f : (projectileIncoming && a == 7 ? 0.92f : 0.35f);
     float comp = clamp01(prog * 0.12f + safe * 0.30f + combat * 0.28f + coll * 0.10f + syn * 0.20f);
     cand[a] = new float[]{prog, safe, combat, coll, syn, comp};
    }
   }
   case DOS_PLATFORM_EXPLORER -> {
    // Prince of Persia / Keen: 0=STEP_LEFT, 1=STEP_RIGHT, 2=JUMP_CLIMB_UP, 3=CROUCH_DOWN,
    // 4=FIRE_SWORD_CTRL, 5=CAREFUL_STEP_ALT, 6=LEAP_RIGHT_UP, 7=ADVANCE_STRIKE_RIGHT
    if (pitAhead) {
     legalMask[1] = false; // Normal walk falls into spike pit / chasm
    }
    for (int a = 0; a < 8; a++) {
     if (!legalMask[a]) continue;
     float prog = (a == 6 && pitAhead) ? 0.94f : ((a == 1 || a == 5 || a == 7) ? 0.72f : 0.30f);
     float safe = pitAhead ? (a == 6 ? 0.96f : (a == 5 ? 0.82f : 0.35f))
       : (enemyCloseAhead ? ((a == 4 || a == 7) ? 0.90f : 0.30f) : 0.80f);
     float combat = enemyCloseAhead && (a == 4 || a == 7) ? 0.95f : 0.20f;
     float coll = (nearestCollectibleDy < -0.08f && a == 2) ? 0.90f : 0.45f;
     float syn = (pitAhead && a == 6) ? 0.96f : (enemyCloseAhead && a == 4 ? 0.92f : (!pitAhead && !enemyCloseAhead && a == 1 ? 0.84f : 0.35f));
     float comp = clamp01(prog * 0.18f + safe * 0.32f + combat * 0.20f + coll * 0.12f + syn * 0.18f);
     cand[a] = new float[]{prog, safe, combat, coll, syn, comp};
    }
   }
   case DOS_CORRIDOR_ACTION -> {
    // Wolf3D / Doom / Tyrian: 0=TURN_LEFT, 1=TURN_RIGHT, 2=MOVE_FORWARD, 3=BACKPEDAL,
    // 4=FIRE_CTRL, 5=STRAFE_ALT, 6=STRAFE_DODGE_RIGHT, 7=ADVANCE_FIRE
    boolean crosshairLocked = Math.abs(nearestEnemyDx) <= 0.12f;
    if (projectileIncoming) {
     legalMask[2] = false; // Do not walk straight into incoming plasma/rockets
    }
    for (int a = 0; a < 8; a++) {
     if (!legalMask[a]) continue;
     float prog = (a == 2 || a == 7) ? 0.75f : 0.40f;
     float safe = projectileIncoming ? ((a == 5 || a == 6 || a == 3) ? 0.95f : 0.25f) : 0.80f;
     float combat = crosshairLocked ? ((a == 4 || a == 7) ? 0.96f : 0.30f)
       : ((nearestEnemyDx < -0.12f && a == 0) || (nearestEnemyDx > 0.12f && a == 1) ? 0.82f : 0.25f);
     float coll = (a == 2 || a == 5) ? 0.65f : 0.40f;
     float syn = (crosshairLocked && !projectileIncoming && a == 4) ? 0.96f
       : (projectileIncoming && a == 6 ? 0.94f : 0.35f);
     float comp = clamp01(prog * 0.14f + safe * 0.28f + combat * 0.30f + coll * 0.08f + syn * 0.20f);
     cand[a] = new float[]{prog, safe, combat, coll, syn, comp};
    }
   }
   case SEGA_HIGH_SPEED_ACT -> {
    // Sonic / Earthworm Jim: 0=DPAD_LEFT, 1=DPAD_RIGHT, 2=CROUCH_ROLL, 3=SPECIAL_SHIELD_A,
    // 4=ATTACK_WHIP_B, 5=JUMP_SPIN_C, 6=SPRINT_JUMP_RIGHT_C, 7=SPIN_DASH_CHARGE_RELEASE
    if (pitAhead) {
     legalMask[1] = false;
    }
    if (!spinDashOrSpecialReady) {
     legalMask[7] = false; // Spin dash requires grounded low-speed charge state
    }
    for (int a = 0; a < 8; a++) {
     if (!legalMask[a]) continue;
     float prog = (a == 6 || a == 7 || a == 1) ? 0.92f : (a == 5 ? 0.60f : 0.20f);
     float safe = (pitAhead || enemyCloseAhead) ? ((a == 6 || a == 5) ? 0.95f : 0.25f) : 0.85f;
     float combat = enemyCloseAhead && (a == 5 || a == 6 || a == 4) ? 0.92f : 0.30f;
     float coll = (a == 6 || a == 1 || a == 7) ? 0.88f : 0.35f;
     float syn = (spinDashOrSpecialReady && !pitAhead && a == 7) ? 0.97f
       : ((pitAhead || enemyCloseAhead) && a == 6 ? 0.95f : (!pitAhead && !enemyCloseAhead && a == 1 ? 0.88f : 0.35f));
     float comp = clamp01(prog * 0.22f + safe * 0.24f + combat * 0.16f + coll * 0.16f + syn * 0.22f);
     cand[a] = new float[]{prog, safe, combat, coll, syn, comp};
    }
   }
   case SEGA_BRAWLER_RUNGUN -> {
    // Streets of Rage / Golden Axe / Gunstar: 0=LEFT, 1=RIGHT, 2=DEPTH_ALIGN, 3=SPECIAL_CROWD_A,
    // 4=MELEE_COMBO_B, 5=JUMP_EVADE_C, 6=JUMP_KICK_RIGHT_C, 7=DASH_BLITZ_ATTACK
    boolean depthAligned = Math.abs(nearestEnemyDy) <= 0.08f;
    boolean surrounded = projectileIncoming && enemyCloseAhead;
    for (int a = 0; a < 8; a++) {
     if (!legalMask[a]) continue;
     float prog = (a == 1 || a == 6 || a == 7) ? 0.76f : 0.42f;
     float safe = surrounded ? ((a == 3 || a == 5) ? 0.96f : 0.25f)
       : (depthAligned ? 0.84f : (a == 2 ? 0.88f : 0.50f));
     float combat = surrounded && a == 3 ? 0.97f
       : (depthAligned && enemyCloseAhead && (a == 4 || a == 7) ? 0.94f
       : (!depthAligned && a == 2 ? 0.85f : 0.30f));
     float coll = (a == 4 || a == 1) ? 0.65f : 0.35f;
     float syn = surrounded && a == 3 ? 0.97f
       : (depthAligned && enemyCloseAhead && a == 4 ? 0.95f
       : (!depthAligned && a == 2 ? 0.90f : (a == 6 ? 0.82f : 0.35f)));
     float comp = clamp01(prog * 0.14f + safe * 0.26f + combat * 0.30f + coll * 0.08f + syn * 0.22f);
     cand[a] = new float[]{prog, safe, combat, coll, syn, comp};
    }
   }
  }
 }

 private static float clamp01(float v) {
  return Math.max(0f, Math.min(1f, v));
 }
}
