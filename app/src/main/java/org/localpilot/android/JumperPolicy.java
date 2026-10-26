package org.localpilot.android;

/** Conservative target-selection policy used before real-game RL examples exist. */
public final class JumperPolicy {
    public enum Action { TAP_LEFT, TAP_RIGHT, WAIT }
 private static float trackTolerance = 38f;
 private static float maxVerticalReach = 430f;
 private static float maxHorizontalReach = 390f;

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
            float horizontal = Math.abs(JumperState.horizontalDistance(player.centerX(),platform.centerX(),state.roiRight-state.roiLeft));
            if (vertical < 52f*scale || vertical > maxVerticalReach*scale || horizontal > maxHorizontalReach*scale) continue;
            if(verticalMotion < -28f*scale && platform.centerY() >= player.centerY()) continue;
            if(verticalMotion > 28f*scale && platform.centerY() <= player.centerY()) continue;
            if (unsafeLanding(platform, player, state)) continue;
            float score = vertical * .55f + horizontal * .45f;
            if (score < best) { best = score; target = platform; }
        }
        boolean dangerLeft = false, dangerRight = false;
        for (JumperState.ObjectBox hazard : state.hazards) {
            float dy = Math.abs(hazard.centerY() - player.centerY());
            float dx = JumperState.horizontalDistance(player.centerX(),hazard.centerX(),state.roiRight-state.roiLeft);
            float margin = Math.max(56f*scale, player.width() * .65f + hazard.width() * .35f);
            if (dy <= Math.max(105f*scale, player.height() * .9f) && Math.abs(dx) <= margin) {
                if (dx < 0) dangerLeft = true; else dangerRight = true;
            }
        }
        if (dangerLeft && dangerRight) return Action.WAIT;
        if (dangerLeft && !dangerRight) return Action.TAP_RIGHT;
        if (dangerRight && !dangerLeft) return Action.TAP_LEFT;
        if (target == null) return Action.WAIT;
        float delta = JumperState.horizontalDistance(player.centerX(),target.centerX(),state.roiRight-state.roiLeft);
        if (Math.abs(delta) <= trackTolerance*scale) return Action.WAIT;
        return delta < 0 ? Action.TAP_LEFT : Action.TAP_RIGHT;
    }

    public static float coordinateScale(JumperState state){
        return Math.min((state.roiRight-state.roiLeft)/854f,(state.roiBottom-state.roiTop)/1280f);
    }
    public static void reset(){trackTolerance=38f;maxVerticalReach=430f;maxHorizontalReach=390f;}
    public static void restore(String model){if(model==null)return;float tolerance=trackTolerance,vertical=maxVerticalReach,horizontal=maxHorizontalReach;try{for(String p:model.split(" ")){String[] kv=p.split("=",2);if(kv.length!=2)continue;float v=Float.parseFloat(kv[1]);if(!Float.isFinite(v))return;switch(kv[0]){case "tolerance":tolerance=Math.max(18,Math.min(70,v));break;case "vertical":vertical=Math.max(260,Math.min(650,v));break;case "horizontal":horizontal=Math.max(220,Math.min(560,v));break;}}}catch(RuntimeException ignored){return;}trackTolerance=tolerance;maxVerticalReach=vertical;maxHorizontalReach=horizontal;}
    public static String model(){return "tolerance="+trackTolerance+" vertical="+maxVerticalReach+" horizontal="+maxHorizontalReach;}
    public static void learn(boolean successful){if(successful){trackTolerance=Math.max(18,trackTolerance*.995f);maxVerticalReach=Math.min(650,maxVerticalReach+1.5f);}else{trackTolerance=Math.min(70,trackTolerance+1.5f);maxVerticalReach=Math.max(260,maxVerticalReach-4f);}}

    private static boolean unsafeLanding(JumperState.ObjectBox platform, JumperState.ObjectBox player,
                                         JumperState state) {
        for (JumperState.ObjectBox hazard : state.hazards) {
            float dx = Math.abs(JumperState.horizontalDistance(platform.centerX(),hazard.centerX(),state.roiRight-state.roiLeft));
            float dy = Math.abs(hazard.centerY() - platform.centerY());
            if (dx < platform.width() * .5f + hazard.width() * .55f + player.width() * .25f
                    && dy < hazard.height() * .65f + player.height() * .7f) return true;
        }
        return false;
    }
}
