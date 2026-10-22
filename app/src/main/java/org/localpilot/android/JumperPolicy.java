package org.localpilot.android;

/** Conservative target-selection policy used before real-game RL examples exist. */
public final class JumperPolicy {
    public enum Action { TAP_LEFT, TAP_RIGHT, WAIT }
    private static final float TRACK_TOLERANCE = 38f;
    private static final float MAX_VERTICAL_REACH = 430f;
    private static final float MAX_HORIZONTAL_REACH = 390f;

    private JumperPolicy() { }

    public static Action nextAction(JumperState state) {
        if (state == null || !state.usable()) return Action.WAIT;
        float scale=coordinateScale(state);
        JumperState.ObjectBox player = state.player;
        JumperState.ObjectBox target = null;
        float best = Float.MAX_VALUE;
        float verticalMotion=state.playerVy-state.scrollVy;
        for (JumperState.ObjectBox platform : state.platforms) {
            float vertical = Math.abs(platform.centerY() - player.centerY());
            float horizontal = Math.abs(platform.centerX() - player.centerX());
            if (vertical < 52f*scale || vertical > MAX_VERTICAL_REACH*scale || horizontal > MAX_HORIZONTAL_REACH*scale) continue;
            if(verticalMotion < -28f*scale && platform.centerY() >= player.centerY()) continue;
            if(verticalMotion > 28f*scale && platform.centerY() <= player.centerY()) continue;
            if (unsafeLanding(platform, player, state)) continue;
            float score = vertical * .55f + horizontal * .45f;
            if (score < best) { best = score; target = platform; }
        }
        boolean dangerLeft = false, dangerRight = false;
        for (JumperState.ObjectBox hazard : state.hazards) {
            float dy = Math.abs(hazard.centerY() - player.centerY());
            float dx = hazard.centerX() - player.centerX();
            float margin = Math.max(56f*scale, player.width() * .65f + hazard.width() * .35f);
            if (dy <= Math.max(105f*scale, player.height() * .9f) && Math.abs(dx) <= margin) {
                if (dx < 0) dangerLeft = true; else dangerRight = true;
            }
        }
        if (dangerLeft && dangerRight) return Action.WAIT;
        if (dangerLeft && !dangerRight) return Action.TAP_RIGHT;
        if (dangerRight && !dangerLeft) return Action.TAP_LEFT;
        if (target == null) return Action.WAIT;
        float delta = target.centerX() - player.centerX();
        if (Math.abs(delta) <= TRACK_TOLERANCE*scale) return Action.WAIT;
        return delta < 0 ? Action.TAP_LEFT : Action.TAP_RIGHT;
    }

    public static float coordinateScale(JumperState state){
        return Math.min((state.roiRight-state.roiLeft)/854f,(state.roiBottom-state.roiTop)/1280f);
    }

    private static boolean unsafeLanding(JumperState.ObjectBox platform, JumperState.ObjectBox player,
                                         JumperState state) {
        for (JumperState.ObjectBox hazard : state.hazards) {
            float dx = Math.abs(hazard.centerX() - platform.centerX());
            float dy = Math.abs(hazard.centerY() - platform.centerY());
            if (dx < platform.width() * .5f + hazard.width() * .55f + player.width() * .25f
                    && dy < hazard.height() * .65f + player.height() * .7f) return true;
        }
        return false;
    }
}
