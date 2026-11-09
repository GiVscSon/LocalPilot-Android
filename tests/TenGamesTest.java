package org.localpilot.android;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.BitSet;
import java.util.List;

/**
 * Comprehensive verification of:
 * 1. P1 StopContext causal tracking & WindowFocus.BoundsRebinder (1220x2712 phone & tablet rebind)
 *    & PanelDocking.chooseAdaptive (micro-STOP fallback on near-fullscreen ROI);
 * 2. P2 GridPuzzleVision patterned-token fix for RING_NO_MATCH / NO_SAMPLE & HINT_HAND_OCCLUSION;
 * 3. P2 BubbleVision.readRecovering single-pass geometric re-acquisition;
 * 4. P5 Karkanoid descending blocks detection & urgent block threat prioritization;
 * 5. P5 Game #8 (Гексон / Hexa Sort) vision, 6-neighbor stack merge >=10, policy, and post-ACK session;
 * 6. P5 Game #10 (Котобашня / Cat Tower) vision, physics-verified stomp vs side hazard, slab lock, and feedback.
 */
public final class TenGamesTest {
 private static int checks;
 private static void check(boolean ok, String msg) {
  checks++;
  if (!ok) throw new AssertionError(msg);
 }

 public static void main(String[] args) {
  testStopContextAndBoundsRebinder();
  testAdaptivePanelDockingMicroStop();
  testGridPatternedTokensAndHintHand();
  testBubbleGeometricReacquisition();
  testKarkanoidDescendingBlocksPriority();
  testHexonAdapterEndToEnd();
  testKotobashnyaAdapterEndToEnd();
  System.out.println("Ten games & core pipeline fixes: " + checks + " assertions PASS");
 }

 private static void testStopContextAndBoundsRebinder() {
  StopContext sc = new StopContext();
  DisplayGeometry dg = new DisplayGeometry(1220, 2712, 0, 480);
  sc.recordBoundsChange(new StopContext.BoundsTransition(
    7L, 1500L, "ru.beru.android", "TARGET_BOUNDS_CHANGED",
    12, 12, 0, 0, 1220, 2712, 0, 120, 1220, 2600, dg, false));
  sc.recordStop("TARGET_BOUNDS_CHANGED", 1510L, 7L, 8L, true, true,
    "Нащупай ход · обмен", "ru.beru.android", 0, 0, 1220, 2712);
  check(sc.historicalLastStop().equals("TARGET_BOUNDS_CHANGED"), "historical stop saved");
  // Subsequent export stop must NOT overwrite historicalLastStop!
  sc.recordStop("Экспорт примеров: STOP", 2000L, 8L, 9L, false, false,
    "Нащупай ход · обмен", "ru.beru.android", 0, 0, 1220, 2712);
  check(sc.immediateStopReason().equals("Экспорт примеров: STOP"), "immediate reason reflects export");
  check(sc.historicalLastStop().equals("TARGET_BOUNDS_CHANGED"), "export does not overwrite historical stop");
  check(sc.report().contains("BOUNDS_EVENT: epoch=7") && sc.report().contains("1220x2712"),
    "bounds transition retains both geometries and display info");

  WindowFocus.BoundsRebinder rebinder = new WindowFocus.BoundsRebinder();
  check(!rebinder.observeCandidate("ru.beru.android", 14, 0, 100, 1220, 2612, 1000L),
    "first bounds observation starts candidate only");
  check(!rebinder.observeCandidate("ru.beru.android", 14, 0, 100, 1220, 2612, 1100L),
    "100ms is below 160ms stability threshold");
  check(rebinder.observeCandidate("ru.beru.android", 14, 0, 100, 1220, 2612, 1170L),
    "two matching observations across >=160ms authorize safe rebind");

  // Proportional ROI remap from 1220x2712 to scaled window
  int[] remapped = WindowFocus.BoundsRebinder.remapRoi(
    0, 0, 1220, 2712,
    0, 0, 610, 1356,
    103, 1241, 1099, 2262);
  check(remapped != null && Math.abs(remapped[0] - 52) <= 1 && Math.abs(remapped[2] - 550) <= 1,
    "manual ROI proportionally remapped across uniform scale change");
  check(WindowFocus.BoundsRebinder.remapRoi(0, 0, 1220, 2712, 0, 0, 1220, 1356, 103, 1241, 1099, 2262) == null,
    "non-uniform aspect distortion (>8%) fails closed");
 }

 private static void testAdaptivePanelDockingMicroStop() {
  PanelDocking.Area safe = new PanelDocking.Area(0, 0, 1080, 2400);
  // Near-fullscreen ROI leaving only 60px top gutter (too small for 160x100 compact panel, fits 72x48 micro-STOP)
  PanelDocking.Area roi = new PanelDocking.Area(0, 60, 1080, 2400);
  PanelDocking.Area compact = new PanelDocking.Area(0, 80, 320, 180);
  PanelDocking.Area micro = new PanelDocking.Area(0, 80, 120, 128);
  check(PanelDocking.choose(safe, compact, roi, 8) == null, "standard compact panel cannot fit 60px gutter");
  PanelDocking.DockPlan plan = PanelDocking.chooseAdaptive(safe, compact, micro, roi, 8);
  check(plan.usable() && plan.microStopMode && !plan.placement.intersects(roi) && safe.contains(plan.placement),
    "adaptive docking places micro-STOP pill in 60px top gutter outside ROI");
  PanelDocking.DockPlan impossible = PanelDocking.chooseAdaptive(safe, compact, micro, safe, 8);
  check(!impossible.usable() && "PANEL_COVERS_ROI".equals(impossible.reason),
    "100% fullscreen ROI with zero gutter still fails closed");
 }

 private static void testGridPatternedTokensAndHintHand() {
  // 6x6 board where tokens have bright center emblems (which previously caused RING_NO_MATCH)
  int[] colors = {0xe52c32, 0x3b82f6, 0x22c55e, 0xf59e0b};
  int[] layout = new int[36];
  for (int i = 0; i < 36; i++) layout[i] = i % 4;
  GridPuzzleVision.Pixels patterned = (x, y) -> {
   int col = x / 80, row = y / 80, idx = row * 6 + col;
   float nx = (x % 80) / 80f, ny = (y % 80) / 80f;
   if (nx < .12f || nx > .88f || ny < .12f || ny > .88f) return 0x2a2a35;
   int base = colors[layout[idx]];
   // Add a bright chromatic emblem in the center of tokens of type 0 and 1
   if (nx >= .36f && nx <= .64f && ny >= .36f && ny <= .64f && (layout[idx] == 0 || layout[idx] == 1)) {
    return layout[idx] == 0 ? 0xffa040 : 0x60f0ff;
   }
   return base;
  };
  GridPuzzleVision.Observation obs = GridPuzzleVision.detect(patterned, 0, 0, 480, 480, 6, 6, 0, 0, 0, 0, 1000L, null, true);
  check(obs.usable() && obs.complete() && obs.unknownIndices().length == 0,
    "patterned tokens with contrasting chromatic centers cluster cleanly without RING_NO_MATCH: " + obs.unknownReasonCounts());

  // Now inject a white tutorial hint hand over cell (1, 1) (index 7)
  GridPuzzleVision.Pixels withHand = (x, y) -> {
   int col = x / 80, row = y / 80, idx = row * 6 + col;
   if (idx == 7) {
    float nx = (x % 80) / 80f, ny = (y % 80) / 80f;
    if (nx >= .25f && nx <= .75f && ny >= .25f && ny <= .75f) return 0xfafafa; // Bright white glove
    if (nx >= .12f && nx <= .88f && ny >= .12f && ny <= .88f) return 0x181818; // Cartoon dark outline
   }
   return patterned.get(x, y);
  };
  GridPuzzleVision.Observation handObs = GridPuzzleVision.detect(withHand, 0, 0, 480, 480, 6, 6, 0, 0, 0, 0, 1100L, obs.palette(), true);
  check(!handObs.usable() && handObs.type(1, 1) == -1
    && handObs.reason(1, 1) == GridPuzzleVision.CellReason.HINT_HAND_OCCLUSION,
    "white hint hand is flagged as HINT_HAND_OCCLUSION and kept UNKNOWN (-1)");
 }

 private static void testBubbleGeometricReacquisition() {
  int[] palette = {0x87ceeb, 0xe52c32, 0xf08016, 0xf0cf20, 0x4ab63b};
  BufferedImage img1 = new BufferedImage(320, 640, BufferedImage.TYPE_INT_RGB);
  var g1 = img1.createGraphics();
  g1.setColor(new Color(palette[0]));
  g1.fillRect(0, 0, 320, 640);
  for (int row = 0; row < 3; row++) {
   for (int col = 0; col < 6 - row % 2; col++) {
    int x = Math.round(26 + (col + row % 2 * .5f) * 48), y = Math.round(80 + row * 48 * .8660254f);
    g1.setColor(new Color(palette[1 + (col + row) % 4]));
    g1.fillOval(x - 23, y - 23, 46, 46);
   }
  }
  g1.setColor(new Color(palette[1]));
  g1.fillOval(160 - 23, 600 - 23, 46, 46);
  g1.setColor(new Color(palette[3]));
  g1.fillOval(90 - 23, 605 - 23, 44, 44);
  g1.dispose();

  BubbleVision.Read first = BubbleVision.readRecovering(img1::getRGB, 320, 640, 0, 0, 320, 640, 0, 0, 0, 0, null);
  check(first.usable() && !first.reacquired() && first.reserveColor() == 3,
    "initial bubble read finds launcher and reserve color 3");

  // Shift the entire scene vertically by 18px (simulating camera/header shift where old cached launcher probe misses)
  BufferedImage img2 = new BufferedImage(320, 640, BufferedImage.TYPE_INT_RGB);
  var g2 = img2.createGraphics();
  g2.setColor(new Color(palette[0]));
  g2.fillRect(0, 0, 320, 640);
  g2.drawImage(img1, 0, -28, null);
  g2.dispose();

  BubbleVision.Read reacquired = BubbleVision.readRecovering(img2::getRGB, 320, 640, 0, 0, 320, 640, 0, 0, 0, 0, first.board());
  check(reacquired.usable() && reacquired.reacquired() && reacquired.board().count() == 17,
    "readRecovering automatically reacquires shifted bubble geometry in one pass");
 }

 private static void testKarkanoidDescendingBlocksPriority() {
  int w = 480, h = 720;
  int[] px = new int[w * h];
  java.util.Arrays.fill(px, 0xd4e2ea);
  // Draw paddle at y=[0.84..0.88], x=[0.36..0.64]
  for (int y = (int) (h * .84f); y < (int) (h * .88f); y++)
   for (int x = (int) (w * .36f); x < (int) (w * .64f); x++) px[y * w + x] = 0xb56d32;
  // Draw descending ball at (0.65, 0.55)
  int cx = (int) (w * .65f), cy = (int) (h * .55f), r = (int) (w * .025f);
  for (int y = cy - r; y <= cy + r; y++)
   for (int x = cx - r; x <= cx + r; x++)
    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) px[y * w + x] = 0xb40d18;
  // Draw two upper blocks: one high at y=0.18, one dangerously low near defeat line at y=0.62, x=0.25
  for (int y = (int) (h * .16f); y < (int) (h * .20f); y++)
   for (int x = (int) (w * .70f); x < (int) (w * .82f); x++) px[y * w + x] = 0x387e25;
  for (int y = (int) (h * .60f); y < (int) (h * .64f); y++)
   for (int x = (int) (w * .20f); x < (int) (w * .32f); x++) px[y * w + x] = 0x2474df;

  ArcadeObservation obs1 = ArcadeVision.detect(ArcadeProfile.PADDLE, (x, y) -> px[y * w + x], w, h, 0, 0, w, h, 0, 0, 0, 0, 1000L, null);
  check(obs1.usable() && obs1.obstacles.size() == 2, "Karkanoid vision detects both upper blocks: " + obs1.obstacles.size());
  check(obs1.obstacles.get(0).bottom > obs1.obstacles.get(1).bottom, "blocks sorted with lowest (most urgent) first");
  check(Math.abs(ArcadePolicy.urgentBlockTargetX(obs1) - 0.26f) < 0.03f, "urgentBlockTargetX identifies lowest descending block");
  check(ArcadePolicy.paddleDefeatMargin(obs1) < 0.25f, "paddleDefeatMargin detects block approaching defeat line");
 }

 private static void testHexonAdapterEndToEnd() {
  int w = 400, h = 560;
  int[] px = new int[w * h];
  java.util.Arrays.fill(px, 0x9098a0); // Neutral background
  // Paint 5x5 hex sockets neutral, except cell (0,0) red (count=6) and tray[0] red (count=4), tray[1] green (count=3)
  for (int row = 0; row < 5; row++) {
   for (int col = 0; col < 5; col++) {
    int cx = Math.round(HexaVision.hexCenterX(0, w, 5, row, col));
    int cy = Math.round(HexaVision.hexCenterY(0, h, 5, row));
    int rgb = (row == 0 && col == 1) ? ((6 << 24) | 0xe52c32) : 0x889098;
    for (int dy = -14; dy <= 14; dy++)
     for (int dx = -14; dx <= 14; dx++)
      if (cy + dy >= 0 && cy + dy < h && cx + dx >= 0 && cx + dx < w) px[(cy + dy) * w + cx + dx] = rgb;
   }
  }
  int ty = Math.round(HexaVision.trayCenterY(0, h));
  int[] trayColors = {((4 << 24) | 0xe52c32), ((3 << 24) | 0x22c55e), 0x889098};
  for (int s = 0; s < 3; s++) {
   int tx = Math.round(HexaVision.trayCenterX(0, w, s));
   for (int dy = -14; dy <= 14; dy++)
    for (int dx = -14; dx <= 14; dx++)
     if (ty + dy >= 0 && ty + dy < h && tx + dx >= 0 && tx + dx < w) px[(ty + dy) * w + tx + dx] = trayColors[s];
  }

  HexaVision.Read r1 = HexaVision.read((x, y) -> px[y * w + x], w, h, 0, 0, w, h, 0, 0, 0, 0, 5, 5);
  check(r1.usable() && r1.board.occupiedCellCount() == 1 && r1.board.availableTrayCount() == 2,
    "HexaVision reads hex cells and tray slots: " + r1.reason);
  HexaPolicy.Move move = HexaPolicy.choose(r1.board);
  check(move != null && move.traySlot == 0 && move.clearedStacks == 1 && move.clearedTiles == 10,
    "HexaPolicy places red stack adjacent to red(6) to reach threshold 10 and clear");

  HexaSession session = new HexaSession();
  check(session.observe(r1.board, 1000L, 1000L) == HexaSession.Status.WAIT_STABLE, "1st frame waits");
  check(session.observe(r1.board, 1100L, 1100L) == HexaSession.Status.READY, "2nd stable frame ready");
  session.sent(r1.board, move, 1120L);
  check(session.observe(move.expectedBoard, 1350L, 1350L) == HexaSession.Status.WAIT_RESPONSE, "pre-ACK change not verified");
  session.acknowledge(1360L);
  check(session.verifiedMoves() == 0, "ACK alone is not a verified Hexon move");
  session.observe(move.expectedBoard, 1450L, 1450L);
  check(session.observe(move.expectedBoard, 1550L, 1550L) == HexaSession.Status.VERIFIED && session.verifiedMoves() == 1,
    "two post-ACK stable frames verify Hexon move");

  // A clear predicted by the planner cannot validate unrelated post-ACK pixels.
  HexaBoard.Stack[] wrongCells=move.expectedBoard.cells();
  wrongCells[wrongCells.length-1]=HexaBoard.Stack.single(4,2);
  HexaBoard wrongBoard=new HexaBoard(5,5,wrongCells,move.expectedBoard.tray());
  HexaSession unrelated=primedHexa(r1.board,move);
  unrelated.observe(wrongBoard,1350,1350);
  check(unrelated.observe(wrongBoard,1450,1450)==HexaSession.Status.WAIT_RESPONSE
    &&unrelated.verifiedMoves()==0,"unrelated target/tray changes never satisfy a predicted clear");

  HexaBoard.Stack[] wrongTray=move.expectedBoard.tray();
  wrongTray[1]=HexaBoard.Stack.single(r1.board.traySlot(1).topColor==4?3:4,3);
  HexaBoard otherSlotChanged=new HexaBoard(5,5,move.expectedBoard.cells(),wrongTray);
  HexaSession wrongHand=primedHexa(r1.board,move);
  wrongHand.observe(otherSlotChanged,1350,1350);
  check(wrongHand.observe(otherSlotChanged,1450,1450)==HexaSession.Status.WAIT_RESPONSE,
    "unexpected changes in untouched tray slots are not a placement witness");

  HexaBoard.Stack[] refill=move.expectedBoard.tray();
  refill[move.traySlot]=HexaBoard.Stack.single(2,3);
  HexaBoard refilled=new HexaBoard(5,5,move.expectedBoard.cells(),refill);
  HexaSession expectedRefill=primedHexa(r1.board,move);
  expectedRefill.observe(refilled,1350,1350);
  check(expectedRefill.observe(refilled,1450,1450)==HexaSession.Status.VERIFIED,
    "expected exact board plus refill of only the consumed slot is verified");

  HexaSession forged=new HexaSession();forged.observe(r1.board,1000,1000);forged.observe(r1.board,1100,1100);
  boolean rejected=false;
  try{forged.sent(r1.board,new HexaPolicy.Move(move.traySlot,move.targetIndex,5,
      0,1,10,25,999,wrongBoard),1120);}
  catch(IllegalArgumentException expected){rejected=true;}
  check(rejected,"forged expected-board and predicted-clear metadata cannot enter a pending action");

 }

 private static HexaSession primedHexa(HexaBoard board,HexaPolicy.Move move){
  HexaSession session=new HexaSession();session.observe(board,1000,1000);session.observe(board,1100,1100);
  session.sent(board,move,1120);session.acknowledge(1200);return session;
 }

 private static void testKotobashnyaAdapterEndToEnd() {
  int w = 360, h = 640;
  int[] px = new int[w * h];
  java.util.Arrays.fill(px, 0x1e293b);
  // Platform at y=[0.70..0.74], x=[0.20..0.80] (teal: r=60, g=150, b=160)
  for (int y = (int) (h * .70f); y < (int) (h * .74f); y++)
   for (int x = (int) (w * .20f); x < (int) (w * .80f); x++) px[y * w + x] = 0x3c96a0;
  // Enemy on platform at y=[0.62..0.70], x=[0.44..0.56] (purple: r=200, g=40, b=150)
  for (int y = (int) (h * .62f); y < (int) (h * .70f); y++)
   for (int x = (int) (w * .44f); x < (int) (w * .56f); x++) px[y * w + x] = 0xc82896;
  // Cat player descending from above enemy: frame 1 at y=[0.44..0.52], frame 2 at y=[0.50..0.58], x=[0.46..0.54] (ginger: r=235, g=140, b=50)
  int[] px2 = px.clone();
  for (int y = (int) (h * .44f); y < (int) (h * .52f); y++)
   for (int x = (int) (w * .46f); x < (int) (w * .54f); x++) px[y * w + x] = 0xeb8c32;
  for (int y = (int) (h * .50f); y < (int) (h * .58f); y++)
   for (int x = (int) (w * .46f); x < (int) (w * .54f); x++) px2[y * w + x] = 0xeb8c32;

  KotobashnyaState s1 = KotobashnyaVision.detect((x, y) -> px[y * w + x], w, h, 0, 0, w, h, 0, 0, 0, 0, 1000L, null);
  check(s1.usable() && !s1.tracked && s1.subMode == KotobashnyaState.SubMode.TOWER_CLIMB_STOMP,
    "frame 1 detects climb/stomp scene without velocity");
  check(KotobashnyaPolicy.choose(s1).action != KotobashnyaPolicy.Action.STOMP_ENEMY,
    "untracked single frame never authorizes stomp");

  KotobashnyaState s2 = KotobashnyaVision.detect((x, y) -> px2[y * w + x], w, h, 0, 0, w, h, 0, 0, 0, 0, 1100L, s1);
  check(s2.usable() && s2.tracked && s2.playerVy > 0.2f, "frame 2 measures downward descent velocity");
  KotobashnyaPolicy.Decision stomp = KotobashnyaPolicy.choose(s2);
  check(stomp.action == KotobashnyaPolicy.Action.STOMP_ENEMY, "verified descending trajectory above enemy authorizes stomp");

  // Side contact hazard test: cat at same height as enemy must evade, NEVER stomp!
  KotobashnyaState sideHazard = new KotobashnyaState(
    KotobashnyaState.SubMode.TOWER_CLIMB_STOMP, 1200L,
    new ArcadeObservation.Box(0.36f, 0.62f, 0.44f, 0.70f),
    s2.platforms, s2.enemies, null, null, 0.1f, 0.0f, 0f, 0f, 0.9f, true, false);
  KotobashnyaPolicy.Decision evade = KotobashnyaPolicy.choose(sideHazard);
  check(evade.action == KotobashnyaPolicy.Action.JUMP_LEFT, "side proximity to enemy triggers immediate evasion away from enemy");

  // Feedback verification
  KotobashnyaFeedback fb = new KotobashnyaFeedback();
  long tok = fb.dispatched(stomp, s2, 1100L);
  fb.acknowledge(tok,1150L);
  check(fb.verified() == 0, "ACK alone never verifies Kotobashnya action");
  KotobashnyaState afterStomp = new KotobashnyaState(
    KotobashnyaState.SubMode.TOWER_CLIMB_STOMP, 1250L,
    new ArcadeObservation.Box(0.46f, 0.42f, 0.54f, 0.50f),
    s2.platforms, List.of(), null, null, 0f, -0.4f, 0f, 0f, 0.9f, true, false);
  check(fb.observe(afterStomp, 1250L) == KotobashnyaFeedback.State.VERIFIED && fb.verified() == 1,
    "enemy removal + upward rebound verifies stomp");

  KotobashnyaFeedback delayedAck=new KotobashnyaFeedback();
  long lateToken=delayedAck.dispatched(stomp,s2,1100);delayedAck.acknowledge(lateToken,1250);
  check(delayedAck.observe(afterStomp,1260)==KotobashnyaFeedback.State.WAITING,
    "frame captured at/before ACK never becomes post-ACK evidence");
  KotobashnyaFeedback missingAck=new KotobashnyaFeedback();missingAck.dispatched(stomp,s2,1100);
  check(missingAck.observe(afterStomp,1700)==KotobashnyaFeedback.State.PAUSED,
    "missing Android ACK pauses instead of authorizing another attempt");
  KotobashnyaState reboundOnly=new KotobashnyaState(s2.subMode,1250,afterStomp.player,s2.platforms,
    s2.enemies,null,null,0,-.4f,0,0,.9f,true,false);
  check(!KotobashnyaFeedback.verifiedResponse(stomp,s2,reboundOnly),
    "upward rebound with the target enemy still present is not a verified stomp");
  KotobashnyaState disappearanceOnly=new KotobashnyaState(s2.subMode,1250,s2.player,s2.platforms,
    List.of(),null,null,0,.3f,0,0,.9f,true,false);
  check(!KotobashnyaFeedback.verifiedResponse(stomp,s2,disappearanceOnly),
    "enemy disappearance alone is not a verified stomp");

  ArcadeObservation.Box slab=new ArcadeObservation.Box(.32f,.55f,.68f,.59f);
  ArcadeObservation.Box top=new ArcadeObservation.Box(.3f,.7f,.7f,.74f);
  KotobashnyaState slabBefore=new KotobashnyaState(KotobashnyaState.SubMode.TOWER_STACK_SLAB,
    1000,null,List.of(),List.of(),slab,top,0,0,0,0,.9f,true,false);
  KotobashnyaState passiveSlab=new KotobashnyaState(slabBefore.subMode,1200,null,List.of(),List.of(),
    new ArcadeObservation.Box(.32f,.50f,.68f,.54f),top,0,0,0,0,.9f,true,false);
  KotobashnyaPolicy.Decision lock=new KotobashnyaPolicy.Decision(KotobashnyaPolicy.Action.LOCK_SLAB,.5f,.55f,50,"test");
  check(!KotobashnyaFeedback.verifiedResponse(lock,slabBefore,passiveSlab),
    "passive moving-slab animation without a new tower level is not lock success");
  KotobashnyaState cameraOnly=new KotobashnyaState(slabBefore.subMode,1200,null,List.of(),List.of(),
    new ArcadeObservation.Box(.32f,.50f,.68f,.54f),new ArcadeObservation.Box(.3f,.65f,.7f,.69f),
    0,0,0,-.1f,.9f,true,false);
  check(!KotobashnyaFeedback.verifiedResponse(lock,slabBefore,cameraOnly),
    "common camera translation is not a locked slab");

 }
}
