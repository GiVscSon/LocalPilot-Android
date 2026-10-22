package org.localpilot.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable screen-space observation for the Yandex Market "Прыгун Ари" profile. */
public final class JumperState {
    public enum Kind { PLAYER, PLATFORM, HAZARD }

    public static final class ObjectBox {
        public final Kind kind;
        public final float left, top, right, bottom, confidence;

        public ObjectBox(Kind kind, float left, float top, float right, float bottom, float confidence) {
            if (kind == null || !Float.isFinite(left) || !Float.isFinite(top)
                    || !Float.isFinite(right) || !Float.isFinite(bottom)
                    || right <= left || bottom <= top || !Float.isFinite(confidence)
                    || confidence < 0 || confidence > 1) throw new IllegalArgumentException("object bounds");
            this.kind = kind;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.confidence = confidence;
        }

        public float centerX() { return (left + right) * .5f; }
        public float centerY() { return (top + bottom) * .5f; }
        public float width() { return right - left; }
        public float height() { return bottom - top; }
    }

    public final int frameWidth, frameHeight, roiLeft, roiTop, roiRight, roiBottom;
    public final long timestampMs;
    public final ObjectBox player;
    public final List<ObjectBox> platforms, hazards;
    public final float confidence;
    public final float playerVx, playerVy, scrollVy;

    JumperState(int frameWidth, int frameHeight, int roiLeft, int roiTop, int roiRight, int roiBottom,
                long timestampMs, ObjectBox player, List<ObjectBox> platforms, List<ObjectBox> hazards,
                float confidence, float playerVx, float playerVy, float scrollVy) {
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.roiLeft = roiLeft;
        this.roiTop = roiTop;
        this.roiRight = roiRight;
        this.roiBottom = roiBottom;
        this.timestampMs = timestampMs;
        this.player = player;
        this.platforms = Collections.unmodifiableList(new ArrayList<>(platforms));
        this.hazards = Collections.unmodifiableList(new ArrayList<>(hazards));
        this.confidence = confidence;
        this.playerVx = playerVx;
        this.playerVy = playerVy;
        this.scrollVy = scrollVy;
    }

    public boolean usable() {
        if(player==null||player.confidence<.55f||platforms.size()<5||confidence<.55f)return false;
        float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;boolean nearby=false;
        for(ObjectBox platform:platforms){
            min=Math.min(min,platform.centerY());max=Math.max(max,platform.centerY());
            if(Math.abs(platform.centerY()-player.centerY())<=frameHeight*.34f
                    &&Math.abs(platform.centerX()-player.centerX())<=frameWidth*.42f)nearby=true;
        }
        return nearby&&max-min>=frameHeight*.14f;
    }
}
