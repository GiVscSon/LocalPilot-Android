package org.localpilot.android;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Object-first vision detector for "Котобашня" (Cat Tower).
 * Separates cat hero, floor platforms, patrolling enemies, and moving stack slabs
 * from pixel observations without assuming unverified sub-mechanics.
 */
public final class KotobashnyaVision {
 private KotobashnyaVision() {}

 public static KotobashnyaState detect(PixelBoardReader.Pixels pixels,
   int width, int height, int left, int top, int right, int bottom,
   int excludedLeft, int excludedTop, int excludedRight, int excludedBottom,
   long timestampMs, KotobashnyaState previous) {
  if (pixels == null || width < 1 || height < 1 || left < 0 || top < 0 || right > width || bottom > height
    || right - left < 120 || bottom - top < 180 || timestampMs < 0
    || (previous != null && timestampMs <= previous.timestampMs))
   throw new IllegalArgumentException("kotobashnya vision geometry/time");
  if (excludedRight > excludedLeft && excludedBottom > excludedTop
    && Math.max(left, excludedLeft) < Math.min(right, excludedRight)
    && Math.max(top, excludedTop) < Math.min(bottom, excludedBottom)) {
   return new KotobashnyaState(KotobashnyaState.SubMode.UNKNOWN, timestampMs,
     null, List.of(), List.of(), null, null, 0, 0, 0, 0, 0, false, false);
  }
  int step = Math.max(1, (right - left + 359) / 360);
  int w = (right - left + step - 1) / step, h = (bottom - top + step - 1) / step;
  byte[] role = new byte[w * h];
  int whiteDialog = 0;
  for (int y = 0; y < h; y++) {
   for (int x = 0; x < w; x++) {
    int rgb = pixels.rgb(left + x * step, top + y * step);
    int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
    int hi = Math.max(r, Math.max(g, b)), lo = Math.min(r, Math.min(g, b));
    if (lo > 215 && y > h * 0.35f && y < h * 0.70f && x > w * 0.2f && x < w * 0.8f) whiteDialog++;
    // 1: Cat hero (warm orange/ginger or cream with ear/body contrast)
    if (r >= 200 && g >= 110 && g <= 185 && b >= 30 && b <= 95 && r - b >= 115) role[y * w + x] = 1;
    // 2: Tower platform / stone floor (teal/slate/brown horizontal ledges)
    else if (g >= 100 && b >= 110 && r <= 115 && hi - lo >= 25) role[y * w + x] = 2;
    // 3: Patrolling enemy (purple/crimson spiky/round hazard)
    else if (r >= 165 && b >= 110 && g <= 85 && r - g >= 90) role[y * w + x] = 3;
    // 4: Stack slab block (golden/amber wide building block)
    else if (r >= 210 && g >= 185 && b <= 85 && r - b >= 135) role[y * w + x] = 4;
   }
  }
  boolean terminal = whiteDialog > (w * 0.6f * h * 0.35f) * 0.55f;
  List<ArcadeObservation.Box> catBlobs = extractBoxes(role, w, h, (byte) 1, 0.03f, 0.22f, 0.03f, 0.22f);
  List<ArcadeObservation.Box> platBlobs = extractBoxes(role, w, h, (byte) 2, 0.14f, 0.85f, 0.012f, 0.09f);
  List<ArcadeObservation.Box> enemyBlobs = extractBoxes(role, w, h, (byte) 3, 0.03f, 0.22f, 0.025f, 0.18f);
  List<ArcadeObservation.Box> slabBlobs = extractBoxes(role, w, h, (byte) 4, 0.15f, 0.78f, 0.035f, 0.16f);

  ArcadeObservation.Box player = chooseNearest(catBlobs, previous == null ? null : previous.player, 0.5f, 0.65f);
  float dt = previous != null && timestampMs > previous.timestampMs ? (timestampMs - previous.timestampMs) / 1000f : 0f;
  boolean validDt = dt >= 0.015f && dt <= 0.65f;

  if (player != null && !platBlobs.isEmpty()) {
   float pvx = 0f, pvy = 0f;
   boolean tracked = false;
   if (validDt && previous.player != null
     && Math.abs(player.x() - previous.player.x()) < 0.38f
     && Math.abs(player.y() - previous.player.y()) < 0.42f) {
    pvx = (player.x() - previous.player.x()) / dt;
    pvy = (player.y() - previous.player.y()) / dt;
    tracked = true;
   }
   List<KotobashnyaState.Enemy> enemies = new ArrayList<>();
   for (ArcadeObservation.Box eb : enemyBlobs) {
    float evx = 0f;
    if (validDt && previous != null) {
     for (KotobashnyaState.Enemy old : previous.enemies) {
      if (Math.abs(old.box.y() - eb.y()) < 0.08f && Math.abs(old.box.x() - eb.x()) < 0.25f) {
       evx = (eb.x() - old.box.x()) / dt;
       break;
      }
     }
    }
    enemies.add(new KotobashnyaState.Enemy(eb, evx, true));
   }
   return new KotobashnyaState(KotobashnyaState.SubMode.TOWER_CLIMB_STOMP, timestampMs,
     player, platBlobs, enemies, null, null, pvx, pvy, 0f, 0f,
     terminal ? 0f : 0.86f, tracked, terminal);
  }

  if (slabBlobs.size() >= 2) {
   slabBlobs.sort(Comparator.comparingDouble(b -> b.top));
   ArcadeObservation.Box moving = slabBlobs.get(0);
   ArcadeObservation.Box settled = slabBlobs.get(1);
   if (moving.bottom <= settled.top + 0.04f) {
    float svx = 0f;
    boolean tracked = false;
    if (validDt && previous != null && previous.movingSlab != null
      && Math.abs(moving.y() - previous.movingSlab.y()) < 0.06f) {
     svx = (moving.x() - previous.movingSlab.x()) / dt;
     tracked = true;
    }
    return new KotobashnyaState(KotobashnyaState.SubMode.TOWER_STACK_SLAB, timestampMs,
      null, List.of(), List.of(), moving, settled, 0f, 0f, svx, 0f,
      terminal ? 0f : 0.84f, tracked, terminal);
   }
  }
  return new KotobashnyaState(KotobashnyaState.SubMode.UNKNOWN, timestampMs,
    null, List.of(), List.of(), null, null, 0, 0, 0, 0, 0, false, terminal);
 }

 private static ArcadeObservation.Box chooseNearest(List<ArcadeObservation.Box> boxes,
   ArcadeObservation.Box prev, float fx, float fy) {
  ArcadeObservation.Box best = null;
  float bestDist = Float.MAX_VALUE;
  float tx = prev == null ? fx : prev.x(), ty = prev == null ? fy : prev.y();
  for (ArcadeObservation.Box b : boxes) {
   float d = (b.x() - tx) * (b.x() - tx) + (b.y() - ty) * (b.y() - ty);
   if (d < bestDist) { bestDist = d; best = b; }
  }
  return best;
 }

 private static List<ArcadeObservation.Box> extractBoxes(byte[] role, int w, int h, byte target,
   float minW, float maxW, float minH, float maxH) {
  boolean[] seen = new boolean[role.length];
  int[] queue = new int[role.length];
  List<ArcadeObservation.Box> out = new ArrayList<>();
  for (int i = 0; i < role.length; i++) {
   if (seen[i] || role[i] != target) continue;
   int head = 0, tail = 1;
   queue[0] = i;
   seen[i] = true;
   int minX = i % w, maxX = minX, minY = i / w, maxY = minY;
   while (head < tail) {
    int at = queue[head++], x = at % w, y = at / w;
    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
    for (int dy = -1; dy <= 1; dy++) {
     for (int dx = -1; dx <= 1; dx++) {
      int nx = x + dx, ny = y + dy;
      if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
      int next = ny * w + nx;
      if (!seen[next] && role[next] == target) {
       seen[next] = true;
       queue[tail++] = next;
      }
     }
    }
   }
   float bw = (maxX - minX + 1f) / w, bh = (maxY - minY + 1f) / h;
   if (bw >= minW && bw <= maxW && bh >= minH && bh <= maxH) {
    out.add(new ArcadeObservation.Box(minX / (float) w, minY / (float) h,
      Math.min(1f, (maxX + 1f) / w), Math.min(1f, (maxY + 1f) / h)));
   }
  }
  return out;
 }
}
