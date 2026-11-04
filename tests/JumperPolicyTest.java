package org.localpilot.android;

import java.util.List;

public final class JumperPolicyTest {
    private static int checks;
    public static void main(String[] args) {
        JumperState.ObjectBox player = box(JumperState.Kind.PLAYER, 390, 500, 450, 570, .9f);
        JumperState.ObjectBox left = box(JumperState.Kind.PLATFORM, 180, 440, 300, 460, .9f);
        JumperState.ObjectBox right = box(JumperState.Kind.PLATFORM, 500, 380, 620, 400, .9f);
        JumperState.ObjectBox farTop=box(JumperState.Kind.PLATFORM,10,40,100,60,.9f);
        JumperState.ObjectBox farBottom=box(JumperState.Kind.PLATFORM,740,1140,840,1160,.9f);
        JumperState.ObjectBox farTop2=box(JumperState.Kind.PLATFORM,700,30,820,50,.9f);
        JumperState base = state(player, List.of(left, right, farTop, farBottom, farTop2), List.of());
        eq(JumperPolicy.Action.TAP_LEFT, JumperPolicy.nextAction(base));
        eq(JumperPolicy.Action.TAP_RIGHT, JumperPolicy.nextAction(state(player, List.of(right, farTop, farBottom, farTop2,
                box(JumperState.Kind.PLATFORM,0,20,60,40,.9f)), List.of())));
        eq(JumperPolicy.Action.WAIT, JumperPolicy.nextAction(state(player,
                List.of(box(JumperState.Kind.PLATFORM, 410, 350, 500, 370, .9f), farTop, farBottom, farTop2,
                        box(JumperState.Kind.PLATFORM,0,20,60,40,.9f)), List.of())));
        JumperState.ObjectBox blockingHazard = box(JumperState.Kind.HAZARD, 210, 420, 290, 455, .9f);
        eq(JumperPolicy.Action.TAP_RIGHT, JumperPolicy.nextAction(state(player, List.of(left, right, farTop, farBottom, farTop2), List.of(blockingHazard))));
        eq(JumperPolicy.Action.WAIT, JumperPolicy.nextAction(state(player, List.of(), List.of())));
        eq(JumperPolicy.Action.WAIT, JumperPolicy.nextAction(null));
        JumperState weak = new JumperState(854, 1280, 0, 0, 854, 1280, 1,
                box(JumperState.Kind.PLAYER, 390, 500, 450, 570, .4f), List.of(left, right), List.of(), .4f, 0, 0, 0);
        eq(JumperPolicy.Action.WAIT, JumperPolicy.nextAction(weak));
        for(float scale:new float[]{.25f,.375f,.5625f}){
            eq(JumperPolicy.Action.TAP_LEFT,JumperPolicy.nextAction(scaled(base,scale,160)));
            JumperState blocked=state(player,List.of(left,right,farTop,farBottom,farTop2),List.of(blockingHazard));
            eq(JumperPolicy.Action.TAP_RIGHT,JumperPolicy.nextAction(scaled(blocked,scale,160)));
            JumperState rising=new JumperState(854,1280,0,0,854,1280,1,player,base.platforms,List.of(),.9f,0,-100,0);
            eq(JumperPolicy.nextAction(rising),JumperPolicy.nextAction(scaled(rising,scale,160)));
        }
        JumperState.ObjectBox edgePlayer=box(JumperState.Kind.PLAYER,0,500,36,570,.9f);
        JumperState.ObjectBox across=box(JumperState.Kind.PLATFORM,760,440,840,460,.9f);
        JumperState.ObjectBox interior=box(JumperState.Kind.PLATFORM,220,350,340,370,.9f);
        var edgePlatforms=List.of(across,interior,farTop,farBottom,farTop2);
        eq(JumperPolicy.Action.TAP_LEFT,JumperPolicy.nextAction(state(edgePlayer,edgePlatforms,List.of())));
        eq(JumperPolicy.Action.TAP_RIGHT,JumperPolicy.nextAction(state(edgePlayer,edgePlatforms,
                List.of(box(JumperState.Kind.HAZARD,770,420,830,455,.9f)))));
        eq(JumperPolicy.Action.TAP_RIGHT,JumperPolicy.nextAction(state(edgePlayer,edgePlatforms,
                List.of(box(JumperState.Kind.HAZARD,800,510,840,550,.9f)))));
        var acrossOnly=java.util.stream.IntStream.of(100,300,440,700,1000)
                .mapToObj(y->box(JumperState.Kind.PLATFORM,760,y,840,y+20,.9f)).toList();
        eq(JumperPolicy.Action.TAP_LEFT,JumperPolicy.nextAction(state(edgePlayer,acrossOnly,List.of())));
        if (checks != 20) throw new AssertionError("expected 20 checks, got " + checks);
        System.out.println("PASS " + checks + " jumper-policy assertions");
    }

    private static JumperState.ObjectBox scaledBox(JumperState.ObjectBox b,float s,int x){
        return box(b.kind,x+b.left*s,b.top*s,x+b.right*s,b.bottom*s,b.confidence);
    }
    private static JumperState scaled(JumperState a,float s,int x){
        return new JumperState(x+(int)(854*s)+160,(int)(1280*s),x,0,x+(int)(854*s),(int)(1280*s),1,
            scaledBox(a.player,s,x),a.platforms.stream().map(b->scaledBox(b,s,x)).toList(),
            a.hazards.stream().map(b->scaledBox(b,s,x)).toList(),a.confidence,a.playerVx*s,a.playerVy*s,a.scrollVy*s);
    }
    private static JumperState state(JumperState.ObjectBox player,
                                     List<JumperState.ObjectBox> platforms,
                                     List<JumperState.ObjectBox> hazards) {
        return new JumperState(854, 1280, 0, 0, 854, 1280, 1, player, platforms, hazards, .9f, 0, 0, 0);
    }

    private static JumperState.ObjectBox box(JumperState.Kind kind, float l, float t, float r, float b, float confidence) {
        return new JumperState.ObjectBox(kind, l, t, r, b, confidence);
    }

    private static void eq(JumperPolicy.Action expected, JumperPolicy.Action actual) {
        checks++;
        if (expected != actual) throw new AssertionError("expected " + expected + ", got " + actual);
    }
}
