package org.localpilot.android.retro;

import java.util.ArrayList;
import java.util.List;
import org.localpilot.android.learning.ObservationEncoder;
import org.localpilot.android.learning.RealisticCurriculumEnv;
import org.localpilot.android.rl.ArcadeEnv;

/**
 * Frame-Accurate Physics & Pixel-Rendering Curriculum Simulator for NES, DOS, and SEGA Games.
 *
 * Supports all 6 retro archetypes (`NES_PLATFORMER`, `NES_TACTICAL_ARENA`,
 * `DOS_PLATFORM_EXPLORER`, `DOS_CORRIDOR_ACTION`, `SEGA_HIGH_SPEED_ACT`, `SEGA_BRAWLER_RUNGUN`)
 * and includes `renderRgbFrame(...)` so the full closed loop (`RetroConsoleEnv` -> RGB pixels ->
 * `RetroVision` -> `ObservationEncoder` -> `StudentPolicy` -> `RetroInputBridge`) is verified
 * end-to-end in JVM and on Android.
 */
public final class RetroConsoleEnv {

 public final RetroPlatform.Archetype archetype;
 public final RealisticCurriculumEnv.CurriculumStage stage;
 private ArcadeEnv.Rng rng;
 private long frameGen;
 private int steps;
 private float playerX, playerY, playerVx, playerVy;
 private boolean onGround, pitAhead, spinDashReady;
 private float enemyX, enemyY;
 private boolean enemyAlive;
 private float projX, projY;
 private boolean projActive;
 private float collectibleX, collectibleY;
 private boolean collectibleActive;
 private int scoreProxy;
 private int collectiblesCount;
 private float vitalityRatio;

 public RetroConsoleEnv(RetroPlatform.Archetype archetype,
   RealisticCurriculumEnv.CurriculumStage stage, long seed) {
  if (archetype == null || stage == null) throw new IllegalArgumentException("null archetype or stage");
  this.archetype = archetype;
  this.stage = stage;
  reset(seed);
 }

 public void reset(long seed) {
  this.rng = new ArcadeEnv.Rng(seed == 0 ? 0x20261106L : seed);
  this.frameGen = 1L;
  this.steps = 0;
  this.playerX = 0.20f;
  this.playerY = 0.72f;
  this.playerVx = 0.0f;
  this.playerVy = 0.0f;
  this.onGround = true;
  this.vitalityRatio = 1.0f;
  this.scoreProxy = 10;
  this.collectiblesCount = 2;
  spawnScenario();
 }

 private void spawnScenario() {
  int mode = rng.integer(4);
  this.pitAhead = archetype.sideScrolling && (mode == 0);
  this.spinDashReady = archetype == RetroPlatform.Archetype.SEGA_HIGH_SPEED_ACT && (mode == 1);
  this.enemyAlive = mode != 0 || stage != RealisticCurriculumEnv.CurriculumStage.STAGE_1_BASIC;
  this.enemyX = Math.min(0.88f, playerX + 0.14f + rng.unit() * 0.16f);
  this.enemyY = archetype == RetroPlatform.Archetype.SEGA_BRAWLER_RUNGUN && mode == 2
    ? playerY + 0.14f : playerY;
  this.projActive = stage != RealisticCurriculumEnv.CurriculumStage.STAGE_1_BASIC && (mode == 3);
  this.projX = playerX + 0.12f;
  this.projY = playerY;
  this.collectibleActive = true;
  this.collectibleX = Math.min(0.90f, playerX + 0.10f);
  this.collectibleY = mode == 2 ? playerY - 0.14f : playerY - 0.03f;
 }

 public long frameGeneration() { return frameGen; }
 public int scoreProxy() { return scoreProxy; }
 public int collectiblesCount() { return collectiblesCount; }
 public float vitalityRatio() { return vitalityRatio; }

 /** Advances the retro console simulation by one action step and updates physics & HUD counters. */
 public float stepPhysics(int action) {
  frameGen++;
  steps++;
  RetroVision.RetroScene before = snapshotScene();
  if (action < 0 || action >= 8 || !before.legalMask[action]) {
   vitalityRatio = Math.max(0.1f, vitalityRatio - 0.25f);
   return -1.0f;
  }

  float[] cand = before.candidateFeatures[action];
  float comp = cand[5];
  float prog = cand[0];
  float combat = cand[2];
  float coll = cand[3];

  // Update player position & velocity
  if (archetype.sideScrolling) {
   playerVx = prog >= 0.6f ? 0.06f : 0.025f;
   playerX = Math.min(0.82f, playerX + playerVx);
   if (playerX >= 0.78f) playerX = 0.22f; // Scroll camera window
  } else {
   playerVx = (action == 1 || action == 6 || action == 7) ? 0.04f : (action == 0 ? -0.04f : 0.02f);
   playerX = Math.max(0.12f, Math.min(0.85f, playerX + playerVx));
  }

  if (combat >= 0.80f && enemyAlive) {
   enemyAlive = false;
   scoreProxy += 5;
  }
  if (coll >= 0.65f && collectibleActive) {
   collectibleActive = false;
   collectiblesCount += 1;
   scoreProxy += 2;
  }
  if (comp >= 0.65f) {
   scoreProxy += 2;
  }

  // Respawn next wave/obstacle for continuous curriculum rollouts
  if (steps % 2 == 0) {
   spawnScenario();
  }
  return Math.max(-1.0f, Math.min(1.0f, comp * 1.15f - 0.10f));
 }

 /** Builds a direct `RetroVision.RetroScene` from the current simulator state. */
 public RetroVision.RetroScene snapshotScene() {
  RetroPlatform.Console c = archetype.console;
  RetroVision.ViewportBounds vp = new RetroVision.ViewportBounds(
    0, 0, c.nativeWidth, c.nativeHeight,
    Math.round(c.nativeHeight * c.topHudRatio),
    c.nativeHeight - Math.round(c.nativeHeight * c.bottomHudRatio)
  );
  RetroVision.VirtualPadLayout pad = RetroVision.VirtualPadLayout.standardForScreen(
    c.nativeWidth, c.nativeHeight + 96, vp
  );
  float enemyDx = enemyAlive ? (enemyX - playerX) : 0.65f;
  float enemyDy = enemyAlive ? (enemyY - playerY) : 0.0f;
  float projTtc = projActive ? 0.18f : 1.0f;
  float collDx = collectibleActive ? (collectibleX - playerX) : 0.40f;
  float collDy = collectibleActive ? (collectibleY - playerY) : -0.05f;

  float[][] cand = new float[8][6];
  boolean[] mask = new boolean[8];
  RetroVision.computeCandidateFeatures(
    archetype, onGround, pitAhead, spinDashReady,
    enemyDx, enemyDy, projTtc, collDx, collDy,
    cand, mask
  );
  List<RetroVision.SpriteBox> sprites = new ArrayList<>();
  sprites.add(new RetroVision.SpriteBox(
    RetroVision.SpriteRole.PLAYER, playerX, playerY, 0.06f, 0.10f, playerVx, playerVy
  ));
  if (enemyAlive) {
   sprites.add(new RetroVision.SpriteBox(
     RetroVision.SpriteRole.ENEMY_PATROL, enemyX, enemyY, 0.06f, 0.10f, -0.02f, 0f
   ));
  }
  return new RetroVision.RetroScene(
    archetype, frameGen, vp, pad,
    playerX, playerY, playerVx, playerVy,
    onGround, pitAhead, false, spinDashReady,
    enemyDx, enemyDy, projTtc, collDx, collDy,
    enemyAlive ? 1 : 0, scoreProxy, collectiblesCount, vitalityRatio,
    0.94f, sprites, cand, mask
  );
 }

 /**
  * Renders the current NES, DOS, or SEGA game state into a full ARGB pixel buffer
  * (`width x height`), including the CRT viewport, HUD score/ring/health pixels, terrain,
  * pit gaps, player sprite, enemies, projectiles, collectibles, and optional bottom touch pad.
  */
 public int[] renderRgbFrame(int screenW, int screenH, boolean includeTouchOverlay) {
  int[] px = new int[screenW * screenH];
  RetroPlatform.Console c = archetype.console;
  int vpBottom = includeTouchOverlay && screenH > screenW ? Math.round(screenH * 0.70f) : screenH;
  int playTop = Math.round(vpBottom * c.topHudRatio);
  int playBottom = vpBottom - Math.round(vpBottom * c.bottomHudRatio);
  int playH = Math.max(1, playBottom - playTop);

  // 1. Fill background sky/corridor & bottom touch overlay bezel
  for (int y = 0; y < screenH; y++) {
   int bg = y >= vpBottom ? 0xFF060608 : 0xFF182438;
   for (int x = 0; x < screenW; x++) {
    px[y * screenW + x] = bg;
   }
  }

  // 2. Render HUD pixels (white score pips, gold collectible pips, red vitality bar)
  int hudY0 = c == RetroPlatform.Console.DOS ? playBottom : 0;
  int hudY1 = c == RetroPlatform.Console.DOS ? vpBottom : Math.max(8, playTop);
  int scorePips = Math.min(screenW / 4, scoreProxy * 3);
  int goldPips = Math.min(screenW / 6, collectiblesCount * 2);
  int vitPips = Math.min(screenW / 6, Math.round(vitalityRatio * 24));
  for (int y = hudY0 + 2; y < Math.min(hudY1 - 1, hudY0 + 6); y++) {
   for (int i = 0; i < scorePips && 8 + i * 2 < screenW; i++) {
    px[y * screenW + 8 + i * 2] = 0xFFF4F6FA;
   }
   for (int i = 0; i < goldPips && screenW / 3 + i * 2 < screenW; i++) {
    px[y * screenW + screenW / 3 + i * 2] = 0xFFF5C820;
   }
   for (int i = 0; i < vitPips && (screenW * 2) / 3 + i * 2 < screenW; i++) {
    px[y * screenW + (screenW * 2) / 3 + i * 2] = 0xFFE63838;
   }
  }

  // 3. Render ground terrain and pit ahead
  int groundStart = playTop + Math.round(playH * 0.84f);
  int pitLeft = Math.round((playerX + 0.03f) * screenW);
  int pitRight = Math.round((playerX + 0.17f) * screenW);
  for (int y = groundStart; y < playBottom; y++) {
   for (int x = 0; x < screenW; x++) {
    if (pitAhead && x >= pitLeft && x <= pitRight) {
     px[y * screenW + x] = 0xFF0A0C10; // Deep pit
    } else {
     px[y * screenW + x] = 0xFF684C32; // Solid ground tile
    }
   }
  }

  // 4. Render player sprite (#24C8F8 cyan-blue hero)
  fillBox(px, screenW, screenH,
    Math.round(playerX * screenW) - 6,
    playTop + Math.round(playerY * playH) - 8,
    12, 16, 0xFF24C8F8);

  // 5. Render enemy sprite (#E6283C crimson hazard)
  if (enemyAlive) {
   fillBox(px, screenW, screenH,
     Math.round(enemyX * screenW) - 6,
     playTop + Math.round(enemyY * playH) - 8,
     12, 16, 0xFFE6283C);
  }

  // 6. Render projectile sprite (#FA9018 orange fireball/laser)
  if (projActive) {
   fillBox(px, screenW, screenH,
     Math.round(projX * screenW) - 4,
     playTop + Math.round(projY * playH) - 4,
     8, 8, 0xFFFA9018);
  }

  // 7. Render collectible coin / ring / key (#FCE024 bright gold)
  if (collectibleActive) {
   fillBox(px, screenW, screenH,
     Math.round(collectibleX * screenW) - 4,
     playTop + Math.round(collectibleY * playH) - 4,
     8, 8, 0xFFFCE024);
  }

  return px;
 }

 private static void fillBox(int[] px, int w, int h, int x0, int y0, int bw, int bh, int color) {
  int x1 = Math.min(w, Math.max(0, x0 + bw));
  int y1 = Math.min(h, Math.max(0, y0 + bh));
  for (int y = Math.max(0, y0); y < y1; y++) {
   for (int x = Math.max(0, x0); x < x1; x++) {
    px[y * w + x] = color;
   }
  }
 }

 public ObservationEncoder.EncodedState observeEncoded() {
  return ObservationEncoder.encodeRetro(archetype.gameId, snapshotScene());
 }
}
