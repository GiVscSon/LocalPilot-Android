package org.localpilot.android;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Dependency-free color/shape detector for the supplied "Прыгун Ари" screen. */
public final class JumperVision {
    private static final int MAX_PIXELS = 8_000_000;
    private static final int STRIDE = 2;

    private JumperVision() { }

    public static JumperState detectRecovering(PixelBoardReader.Pixels pixels,int width,int height,
            int left,int top,int right,int bottom,int el,int et,int er,int eb,long timestamp,
            JumperState previous,ColorProfile colors,SceneRecovery.Pass pass) {
        JumperState raw=detect(pixels,width,height,left,top,right,bottom,el,et,er,eb,timestamp,previous,colors);
        if(raw.usable()||pass==SceneRecovery.Pass.ORIGINAL)return raw;
        JumperState retry=detect(SceneRecovery.pixels(pixels,pass),width,height,left,top,right,bottom,
                el,et,er,eb,timestamp,previous,null);
        return retry.usable()?retry:raw;
    }

    public static final class ColorProfile {
        private final int player, platform, hazard;
        public ColorProfile(int player, int platform, int hazard) {
            if (!valid(player) || !valid(platform) || !valid(hazard)) throw new IllegalArgumentException("sample colors");
            this.player = player & 0x00ffffff;
            this.platform = platform & 0x00ffffff;
            this.hazard = hazard & 0x00ffffff;
        }
        private int forKind(JumperState.Kind kind) {
            switch (kind) { case PLAYER: return player; case PLATFORM: return platform; case HAZARD: return hazard; default: return -1; }
        }
        private static boolean valid(int color) { return color >= 0 && color <= 0x00ffffff; }
    }

    public static JumperState detect(PixelBoardReader.Pixels pixels, int width, int height,
                                     int left, int top, int right, int bottom,
                                     int excludedLeft, int excludedTop, int excludedRight, int excludedBottom,
                                     long timestampMs, JumperState previous) {
        return detect(pixels, width, height, left, top, right, bottom, excludedLeft, excludedTop,
                excludedRight, excludedBottom, timestampMs, previous, null);
    }

    public static JumperState detect(PixelBoardReader.Pixels pixels, int width, int height,
                                     int left, int top, int right, int bottom,
                                     int excludedLeft, int excludedTop, int excludedRight, int excludedBottom,
                                     long timestampMs, JumperState previous, ColorProfile profile) {
        if (pixels == null || width < 1 || height < 1 || (long) width * height > MAX_PIXELS
                || left < 0 || top < 0 || right > width || bottom > height
                || right - left < 120 || bottom - top < 180 || timestampMs < 0
                || (previous != null && timestampMs <= previous.timestampMs)) {
            throw new IllegalArgumentException("frame geometry or time");
        }
        int sampleWidth = (right - left + STRIDE - 1) / STRIDE;
        int sampleHeight = (bottom - top + STRIDE - 1) / STRIDE;
        if ((long) sampleWidth * sampleHeight > MAX_PIXELS) throw new IllegalArgumentException("sample limit");
        int[] rgb = new int[sampleWidth * sampleHeight];
        for (int y = 0; y < sampleHeight; y++) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            int py = top + y * STRIDE;
            for (int x = 0; x < sampleWidth; x++) {
                int px = left + x * STRIDE;
                rgb[y * sampleWidth + x] = inside(px, py, excludedLeft, excludedTop, excludedRight, excludedBottom)
                        ? 0 : pixels.rgb(px, py) & 0x00ffffff;
            }
        }
        // Shape thresholds were calibrated at854x1280. Apply them in that
        // coordinate scale, including pillarboxed landscape tablet captures.
        float shapeScale=Math.min((right-left)/854f,(bottom-top)/1280f);
        List<JumperState.ObjectBox> platforms = components(rgb, sampleWidth, sampleHeight, left, top,
                JumperState.Kind.PLATFORM, timestampMs, profile, shapeScale);
        List<JumperState.ObjectBox> hazards = components(rgb, sampleWidth, sampleHeight, left, top,
                JumperState.Kind.HAZARD, timestampMs, profile, shapeScale);
        List<JumperState.ObjectBox> players = components(rgb, sampleWidth, sampleHeight, left, top,
                JumperState.Kind.PLAYER, timestampMs, profile, shapeScale);
        JumperState.ObjectBox player = choosePlayer(players, previous);
        float vx = 0, vy = 0, scrollVy = 0;
        if (player != null && previous != null && previous.player != null) {
            float seconds = Math.min(1.0f, (timestampMs - previous.timestampMs) / 1000f);
            if (seconds > .001f) {
                vx = (player.centerX() - previous.player.centerX()) / seconds;
                vy = (player.centerY() - previous.player.centerY()) / seconds;
                scrollVy = estimateScroll(platforms, previous.platforms, seconds);
            }
        }
        float confidence = player == null ? 0 : Math.min(player.confidence,
                Math.min(1, .45f + platforms.size() * .12f));
        return new JumperState(width, height, left, top, right, bottom, timestampMs,
                player, platforms, hazards, confidence, vx, vy, scrollVy);
    }

    private static boolean inside(int x, int y, int left, int top, int right, int bottom) {
        return right > left && bottom > top && x >= left && x < right && y >= top && y < bottom;
    }

    private static List<JumperState.ObjectBox> components(int[] rgb, int width, int height,
                                                          int originX, int originY,
                                                          JumperState.Kind kind, long now, ColorProfile profile, float scale) {
        boolean[] visited = new boolean[rgb.length];
        int[] queue = new int[rgb.length];
        List<JumperState.ObjectBox> result = new ArrayList<>();
        for (int start = 0; start < rgb.length; start++) {
            if (visited[start] || !matches(rgb[start], kind, profile)) continue;
            int head = 0, tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            int minX = start % width, maxX = minX, minY = start / width, maxY = minY, area = 0;
            while (head < tail) {
                int at = queue[head++], x = at % width, y = at / width;
                area++;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                if (x > 0) tail = add(at - 1, rgb, visited, queue, tail, kind, profile);
                if (x + 1 < width) tail = add(at + 1, rgb, visited, queue, tail, kind, profile);
                if (y > 0) tail = add(at - width, rgb, visited, queue, tail, kind, profile);
                if (y + 1 < height) tail = add(at + width, rgb, visited, queue, tail, kind, profile);
            }
            int boxWidth = (maxX - minX + 1) * STRIDE, boxHeight = (maxY - minY + 1) * STRIDE;
            float aspect = boxWidth / (float) boxHeight;
            float areaPx = area * STRIDE * STRIDE;
            float normalizedWidth=boxWidth/scale,normalizedHeight=boxHeight/scale,normalizedArea=areaPx/(scale*scale);
            if (!shape(kind, normalizedWidth, normalizedHeight, aspect, normalizedArea)) continue;
            float confidence = shapeConfidence(kind, normalizedWidth, normalizedHeight, aspect, normalizedArea);
            result.add(new JumperState.ObjectBox(kind, originX + minX * STRIDE, originY + minY * STRIDE,
                    originX + (maxX + 1) * STRIDE, originY + (maxY + 1) * STRIDE, confidence));
        }
        result.sort(Comparator.comparingDouble((JumperState.ObjectBox b) -> b.centerY())
                .thenComparingDouble(JumperState.ObjectBox::centerX));
        return result;
    }

    private static int add(int index, int[] rgb, boolean[] visited, int[] queue, int tail,
                           JumperState.Kind kind, ColorProfile profile) {
        if (!visited[index] && matches(rgb[index], kind, profile)) {
            visited[index] = true;
            queue[tail++] = index;
        }
        return tail;
    }

    private static boolean matches(int color, JumperState.Kind kind, ColorProfile profile) {
        int r = color >>> 16 & 255, g = color >>> 8 & 255, b = color & 255;
        if (profile != null) {
            int sample = profile.forKind(kind);
            return Math.abs(r - (sample >>> 16 & 255)) <= 72
                    && Math.abs(g - (sample >>> 8 & 255)) <= 72
                    && Math.abs(b - (sample & 255)) <= 72;
        }
        switch (kind) {
            case PLATFORM:
                return r >= 150 && g >= 55 && g <= 185 && b <= 105 && r > g * 1.18;
            case HAZARD:
                return r >= 115 && r > g * 1.35 && r > b * 1.3 && g < 145;
            case PLAYER:
                return r >= 170 && g >= 125 && g <= 230 && b <= 100 && r-b >= 130 && g-b >= 90;
            default:
                return false;
        }
    }

    private static boolean shape(JumperState.Kind kind, float width, float height, float aspect, float area) {
        switch (kind) {
            case PLATFORM: return width >= 34 && height >= 6 && height <= 42 && aspect >= 1.6f && area >= 100;
            case HAZARD: return width >= 22 && height >= 14 && width <= 220 && height <= 180
                    && aspect >= .55f && aspect <= 3.4f && area >= 180;
            case PLAYER: return width >= 20 && height >= 20 && width <= 150 && height <= 180
                    && aspect >= .35f && aspect <= 2.6f && area >= 180 && area <= 18000;
            default: return false;
        }
    }

    private static float shapeConfidence(JumperState.Kind kind, float width, float height, float aspect, float area) {
        float geometry;
        if (kind == JumperState.Kind.PLATFORM) geometry = 1 - Math.min(1, Math.abs(aspect - 5f) / 5f);
        else if (kind == JumperState.Kind.HAZARD) geometry = 1 - Math.min(1, Math.abs(aspect - 1.4f) / 2.0f);
        else geometry = 1 - Math.min(1, Math.abs(aspect - .85f) / 1.2f);
        float size = Math.min(1, (float) Math.sqrt(area) / 30f);
        return Math.max(.35f, Math.min(.96f, .52f + geometry * .28f + size * .16f));
    }

    private static JumperState.ObjectBox choosePlayer(List<JumperState.ObjectBox> players, JumperState previous) {
        JumperState.ObjectBox best = null;
        float bestScore = -Float.MAX_VALUE;
        for (JumperState.ObjectBox p : players) {
            float score = p.confidence + Math.min(.16f, (float) Math.sqrt(p.width() * p.height()) / 500f);
            if (previous != null && previous.player != null) {
                float distance = (float) Math.hypot(p.centerX() - previous.player.centerX(), p.centerY() - previous.player.centerY());
                score -= Math.min(.55f, distance / Math.max(160f, previous.frameHeight * .22f));
            }
            if (score > bestScore) { bestScore = score; best = p; }
        }
        return best;
    }

    private static float estimateScroll(List<JumperState.ObjectBox> current,
                                        List<JumperState.ObjectBox> previous, float seconds) {
        if (current.isEmpty() || previous.isEmpty()) return 0;
        List<Float> shifts = new ArrayList<>();
        for (JumperState.ObjectBox now : current) {
            JumperState.ObjectBox nearest = null;
            float best = Float.MAX_VALUE;
            for (JumperState.ObjectBox old : previous) {
                float distance = (float) Math.hypot(now.centerX() - old.centerX(), now.centerY() - old.centerY());
                if (distance < best) { best = distance; nearest = old; }
            }
            if (nearest != null && best < 100) shifts.add((now.centerY() - nearest.centerY()) / seconds);
        }
        if (shifts.isEmpty()) return 0;
        shifts.sort(Float::compare);
        return shifts.get(shifts.size() / 2);
    }
}
