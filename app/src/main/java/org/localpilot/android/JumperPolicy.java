package org.localpilot.android;

/** Conservative target-selection policy used before real-game RL examples exist. */
public final class JumperPolicy {
    public enum Action { TAP_LEFT, TAP_RIGHT, WAIT }
 private static float trackTolerance = 38f;
 private static float maxVerticalReach = 430f;
 private static float maxHorizontalReach = 390f;

    private JumperPolicy() { }

    public static Action nextAction(JumperState state) {
        return nextAction(state,true,false,false);
    }

    /** Stomps and edge farming are opt-in mechanics; calibration and fresh input permits remain caller obligations. */
    public static Action nextAction(JumperState state,boolean allowWrapAround,boolean stompEnemies,boolean farmEdgeWraps) {
        if (state == null || !state.usableForMechanics(stompEnemies)) return Action.WAIT;
        float scale=coordinateScale(state);
        JumperState.ObjectBox player=state.player;
        JumperState.ObjectBox target=selectTarget(state,allowWrapAround,stompEnemies,farmEdgeWraps);
        boolean dangerLeft=false,dangerRight=false;
        for(int i=0;i<state.hazards.size();i++) {
            JumperState.ObjectBox hazard=state.hazards.get(i);
            if (hazard==target && stompEnemies && canStomp(state,hazard)) continue;
            float dy=Math.abs(hazard.centerY()-player.centerY());
            float dx=distance(player.centerX(),hazard.centerX(),state,allowWrapAround);
            float margin=Math.max(56f*scale,player.width()*.65f+hazard.width()*.35f);
            if(dy<=Math.max(105f*scale,player.height()*.9f) && Math.abs(dx)<=margin) {
                if(dx<0)dangerLeft=true;else dangerRight=true;
            }
        }
        if(dangerLeft&&dangerRight)return Action.WAIT;
        if(dangerLeft)return Action.TAP_RIGHT;
        if(dangerRight)return Action.TAP_LEFT;
        if(target==null)return Action.WAIT;
        float delta=distance(player.centerX(),target.centerX(),state,allowWrapAround);
        if(Math.abs(delta)<=trackTolerance*scale)return Action.WAIT;
        return delta<0?Action.TAP_LEFT:Action.TAP_RIGHT;
    }

    /** The selected enemy surface, if any; returning it never authorizes a gesture. */
    public static JumperState.ObjectBox selectedStompTarget(JumperState state,boolean allowWrapAround,boolean farmEdgeWraps) {
        if(state==null||!state.usableForMechanics(true))return null;
        JumperState.ObjectBox target=selectTarget(state,allowWrapAround,true,farmEdgeWraps);
        return target!=null&&target.kind==JumperState.Kind.HAZARD?target:null;
    }

    public static boolean canStomp(JumperState state,JumperState.ObjectBox enemy) {
        if(state==null||state.player==null||enemy==null||enemy.kind!=JumperState.Kind.HAZARD
                ||state.player.confidence<.75f||enemy.confidence<.75f||!Float.isFinite(state.playerVy)
                ||!Float.isFinite(state.scrollVy)||state.playerVy<=0||state.playerVy-state.scrollVy<=0)return false;
        float scale=coordinateScale(state),gap=enemy.top-state.player.bottom;
        return scale>0 && gap>=-stompTolerance(state) && gap<=maxVerticalReach*scale
                && state.player.centerY()<enemy.top && enemy.width()>=state.player.width()*.25f;
    }

    public static float stompTolerance(JumperState state) {
        return Math.max(2f,Math.min(state.player.height()*.15f,24f*coordinateScale(state)));
    }

    private static JumperState.ObjectBox selectTarget(JumperState state,boolean wrapping,boolean stomps,boolean farm) {
        float scale=coordinateScale(state),best=Float.MAX_VALUE;
        float verticalMotion=state.playerVy-state.scrollVy;
        JumperState.ObjectBox player=state.player,target=null;
        for(int i=0;i<state.platforms.size();i++) {
            JumperState.ObjectBox platform=state.platforms.get(i);
            float vertical=Math.abs(platform.centerY()-player.centerY());
            float direct=platform.centerX()-player.centerX();
            float dx=distance(player.centerX(),platform.centerX(),state,wrapping),horizontal=Math.abs(dx);
            if(vertical<52f*scale||vertical>maxVerticalReach*scale||horizontal>maxHorizontalReach*scale)continue;
            if(verticalMotion< -28f*scale&&platform.centerY()>=player.centerY())continue;
            if(verticalMotion>28f*scale&&platform.centerY()<=player.centerY())continue;
            if(unsafeLanding(platform,player,state,wrapping))continue;
            float score=vertical*.55f+horizontal*.45f;
            if(verticalMotion<=28f*scale&&platform.centerY()>=player.centerY())score+=maxVerticalReach*.55f*scale;
            if(farm&&wrapping&&Math.abs(dx)<Math.abs(direct))score-=120f*scale;
            if(score<best){best=score;target=platform;}
        }
        if(stomps)for(int i=0;i<state.hazards.size();i++) {
            JumperState.ObjectBox enemy=state.hazards.get(i);
            if(!canStomp(state,enemy))continue;
            float direct=enemy.centerX()-player.centerX();
            float dx=distance(player.centerX(),enemy.centerX(),state,wrapping);
            if(Math.abs(dx)>maxHorizontalReach*scale||unsafeLanding(enemy,player,state,wrapping))continue;
            float score=Math.abs(enemy.centerY()-player.centerY())*.55f+Math.abs(dx)*.45f-50f*scale;
            if(farm&&wrapping&&Math.abs(dx)<Math.abs(direct))score-=120f*scale;
            if(score<best){best=score;target=enemy;}
        }
        return target;
    }

    private static float distance(float from,float to,JumperState state,boolean wrapping) {
        return wrapping?JumperState.horizontalDistance(from,to,state.roiRight-state.roiLeft):to-from;
    }

    public static float coordinateScale(JumperState state){
        return Math.min((state.roiRight-state.roiLeft)/854f,(state.roiBottom-state.roiTop)/1280f);
    }
    public static void reset(){trackTolerance=38f;maxVerticalReach=430f;maxHorizontalReach=390f;}
    public static void restore(String model){if(model==null)return;float tolerance=trackTolerance,vertical=maxVerticalReach,horizontal=maxHorizontalReach;try{for(String p:model.split(" ")){String[] kv=p.split("=",2);if(kv.length!=2)continue;float v=Float.parseFloat(kv[1]);if(!Float.isFinite(v))return;switch(kv[0]){case "tolerance":tolerance=Math.max(18,Math.min(70,v));break;case "vertical":vertical=Math.max(260,Math.min(650,v));break;case "horizontal":horizontal=Math.max(220,Math.min(560,v));break;}}}catch(RuntimeException ignored){return;}trackTolerance=tolerance;maxVerticalReach=vertical;maxHorizontalReach=horizontal;}
    public static String model(){return "tolerance="+trackTolerance+" vertical="+maxVerticalReach+" horizontal="+maxHorizontalReach;}
    public static void learn(boolean successful){if(successful){trackTolerance=Math.max(18,trackTolerance*.995f);maxVerticalReach=Math.min(650,maxVerticalReach+1.5f);}else{trackTolerance=Math.min(70,trackTolerance+1.5f);maxVerticalReach=Math.max(260,maxVerticalReach-4f);}}

    private static boolean unsafeLanding(JumperState.ObjectBox platform, JumperState.ObjectBox player,
                                         JumperState state,boolean wrapping) {
        for (int i=0;i<state.hazards.size();i++) {
            JumperState.ObjectBox hazard=state.hazards.get(i);
            if(hazard==platform)continue;
            float dx = Math.abs(distance(platform.centerX(),hazard.centerX(),state,wrapping));
            float dy = Math.abs(hazard.centerY() - platform.centerY());
            if (dx < platform.width() * .5f + hazard.width() * .55f + player.width() * .25f
                    && dy < hazard.height() * .65f + player.height() * .7f) return true;
        }
        return false;
    }
}
