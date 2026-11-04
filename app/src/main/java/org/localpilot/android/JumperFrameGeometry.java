package org.localpilot.android;

/** Maps MediaProjection frames back to Android screen coordinates for gesture delivery. */
public final class JumperFrameGeometry {
    public final int frameWidth, frameHeight, left, top, right, bottom;
    public final int screenLeft, screenTop, screenRight, screenBottom;
    private final boolean fullDisplay;
    private final float scaleX,scaleY;

    public JumperFrameGeometry(int frameWidth, int frameHeight, int displayWidth, int displayHeight,
                               int appLeft, int appTop, int appRight, int appBottom) {
        this(frameWidth,frameHeight,displayWidth,displayHeight,appLeft,appTop,appRight,appBottom,false);
    }

    public JumperFrameGeometry(int frameWidth,int frameHeight,int displayWidth,int displayHeight,
                               int appLeft,int appTop,int appRight,int appBottom,boolean scaledDisplay) {
        if (frameWidth < 1 || frameHeight < 1 || displayWidth < 1 || displayHeight < 1
                || appLeft < 0 || appTop < 0 || appRight <= appLeft || appBottom <= appTop
                || appRight > displayWidth || appBottom > displayHeight) throw new IllegalArgumentException("capture geometry");
        this.frameWidth=frameWidth;this.frameHeight=frameHeight;
        this.screenLeft=appLeft;this.screenTop=appTop;this.screenRight=appRight;this.screenBottom=appBottom;
        fullDisplay=scaledDisplay||frameWidth == displayWidth && frameHeight == displayHeight;
        scaleX=frameWidth/(float)displayWidth;scaleY=frameHeight/(float)displayHeight;
        if (fullDisplay) {
            left=Math.max(0,(int)Math.floor(appLeft*scaleX));top=Math.max(0,(int)Math.floor(appTop*scaleY));
            right=Math.min(frameWidth,(int)Math.ceil(appRight*scaleX));bottom=Math.min(frameHeight,(int)Math.ceil(appBottom*scaleY));
        } else if (Math.abs(frameWidth-(appRight-appLeft))<=2 && Math.abs(frameHeight-(appBottom-appTop))<=2) {
            left=0;top=0;right=frameWidth;bottom=frameHeight;
        } else {
            // Android's single-app projection may return a scaled, app-only surface.
            left=0;top=0;right=frameWidth;bottom=frameHeight;
        }
    }

    public float toScreenX(float x) {
        return fullDisplay?x/scaleX:screenLeft + x * (screenRight-screenLeft) / frameWidth;
    }
    public float toScreenY(float y) {
        return fullDisplay?y/scaleY:screenTop + y * (screenBottom-screenTop) / frameHeight;
    }
    public int toFrameX(float x) { return Math.max(0,Math.min(frameWidth,Math.round(fullDisplay?x*scaleX:(x-screenLeft)*frameWidth/(screenRight-screenLeft)))); }
    public int toFrameY(float y) { return Math.max(0,Math.min(frameHeight,Math.round(fullDisplay?y*scaleY:(y-screenTop)*frameHeight/(screenBottom-screenTop)))); }
}
