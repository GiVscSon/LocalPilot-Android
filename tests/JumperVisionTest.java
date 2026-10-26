package org.localpilot.android;

public final class JumperVisionTest {
    private static int checks;
    public static void main(String[] args) {
        Scene scene = new Scene();
        scene.fill(100, 900, 220, 920, 0xf28c00);
        scene.fill(560, 700, 680, 720, 0xf28c00);
        scene.fill(90, 500, 210, 520, 0xf28c00);
        scene.fill(620, 350, 740, 370, 0xf28c00);
        scene.fill(320, 1100, 440, 1120, 0xf28c00);
        scene.fill(440, 780, 530, 825, 0xc91b18);
        scene.fill(370, 500, 420, 560, 0xffd000);
        JumperState first = detect(scene, null, 1000, 0, 0, 0, 0);
        check(first.usable());
        check(first.platforms.size() == 5);
        check(first.hazards.size() == 1);
        near(395, first.player.centerX(), 2);
        near(530, first.player.centerY(), 2);
        check(first.player.confidence >= .55f);
        JumperState calibrated=JumperVision.detect(scene,scene.width,scene.height,0,0,scene.width,scene.height,
                0,0,0,0,1000,null,new JumperVision.ColorProfile(0xffd000,0xf28c00,0xc91b18));
        check(calibrated.usable());check(calibrated.platforms.size()==5);

        Scene moved = new Scene();
        moved.fill(100, 910, 220, 930, 0xf28c00);
        moved.fill(560, 710, 680, 730, 0xf28c00);
        moved.fill(90, 510, 210, 530, 0xf28c00);
        moved.fill(620, 360, 740, 380, 0xf28c00);
        moved.fill(320, 1110, 440, 1130, 0xf28c00);
        moved.fill(440, 790, 530, 835, 0xc91b18);
        moved.fill(390, 500, 440, 560, 0xffd000);
        JumperState second = detect(moved, first, 1100, 0, 0, 0, 0);
        near(200, second.playerVx, 1);
        near(100, second.scrollVy, 1);

        JumperState excluded = detect(scene, null, 1000, 360, 490, 430, 570);
        check(excluded.player == null);
        check(!excluded.usable());
        // The actual tablet recording scales orange platforms below34px.
        // Replay the same geometry at bounded capture sizes, including blue sidebars.
        for(int[] size:new int[][]{{480,720},{320,480},{720,480}}){
            int w=size[0],h=size[1];int fieldWidth=Math.min(w,(int)(h*854f/1280));
            int left=(w-fieldWidth)/2;
            PixelBoardReader.Pixels scaled=(x,y)->x<left||x>=left+fieldWidth?0x245888:
                    scene.rgb(Math.min(853,(x-left)*854/fieldWidth),Math.min(1279,y*1280/h));
            JumperState bounded=JumperVision.detect(scaled,w,h,left,0,left+fieldWidth,h,0,0,0,0,1000,null);
            check(bounded.usable());check(bounded.platforms.size()==5);check(bounded.hazards.size()==1);
            near(left+395f*fieldWidth/854,bounded.player.centerX(),3);
            near(530f*h/1280,bounded.player.centerY(),3);
            JumperState noPlayer=JumperVision.detect(scaled,w,h,left,0,left+fieldWidth,h,
                    left+360*fieldWidth/854,490*h/1280,left+430*fieldWidth/854,570*h/1280,1000,null);
            check(!noPlayer.usable());
        }
        // Helmet is not yellow: the visible body can be wider than the whole sprite.
        Scene body=new Scene();
        body.fill(100,900,220,920,0xf28c00);body.fill(560,700,680,720,0xf28c00);
        body.fill(90,500,210,520,0xf28c00);body.fill(620,350,740,370,0xf28c00);
        body.fill(320,1100,440,1120,0xf28c00);body.fill(370,800,466,840,0xf3c929);
        check(detect(body,null,1000,0,0,0,0).usable());
        body.fill(370,800,466,840,0xc8b478);
        check(detect(body,null,1000,0,0,0,0).player==null);
        body.fill(370,800,374,804,0xffd000);
        check(detect(body,null,1000,0,0,0,0).player==null);
        edgeContinuation();
        badGeometry();
        if (checks != 51) throw new AssertionError("expected 51 checks, got " + checks);
        System.out.println("PASS " + checks + " jumper-vision assertions");
    }

    private static Scene edgeScene(int left,int right){
        Scene scene=new Scene();scene.fill(100,900,220,920,0xf28c00);scene.fill(560,700,680,720,0xf28c00);
        scene.fill(90,500,210,520,0xf28c00);scene.fill(620,350,740,370,0xf28c00);
        scene.fill(320,1100,440,1120,0xf28c00);scene.fill(left,540,right,600,0xffd000);return scene;
    }
    private static void edgeContinuation(){
        JumperState edge=detect(edgeScene(790,850),null,1000,0,0,0,0);
        check(edge.usable());
        Scene fragment=edgeScene(0,12);
        JumperState wrapped=detect(fragment,edge,1100,0,0,0,0);
        check(wrapped.usable());near(6,wrapped.player.centerX(),1);near(400,wrapped.playerVx,1);
        check(wrapped.player.left==0&&wrapped.player.right==12); // Keep only visible evidence.
        check(detect(fragment,null,1100,0,0,0,0).player==null);
        check(detect(fragment,edge,1400,0,0,0,0).player==null);
        JumperState interior=detect(edgeScene(370,430),null,1000,0,0,0,0);
        check(detect(fragment,interior,1100,0,0,0,0).player==null);
        check(detect(edgeScene(0,4),edge,1100,0,0,0,0).player==null);
        var colors=new JumperVision.ColorProfile(0xffd000,0xf28c00,0xc91b18);
        check(JumperVision.detect(fragment,854,1280,0,0,854,1280,0,0,0,0,1100,edge,colors).usable());
        int offset=73,frameWidth=1000;
        Scene first=edgeScene(790,850);
        PixelBoardReader.Pixels paddedFirst=(x,y)->x<offset||x>=offset+854?0x245888:first.rgb(x-offset,y);
        PixelBoardReader.Pixels paddedNext=(x,y)->x<offset||x>=offset+854?0x245888:fragment.rgb(x-offset,y);
        JumperState padded=JumperVision.detect(paddedFirst,frameWidth,1280,offset,0,offset+854,1280,0,0,0,0,1000,null);
        JumperState paddedWrapped=JumperVision.detect(paddedNext,frameWidth,1280,offset,0,offset+854,1280,0,0,0,0,1100,padded);
        check(paddedWrapped.usable());near(offset+6,paddedWrapped.player.centerX(),1);near(400,paddedWrapped.playerVx,1);
        Scene hud=edgeScene(370,430);hud.fill(740,10,780,50,0xffd000);
        check(Math.abs(detect(hud,null,1000,0,0,0,0).player.centerY()-570)<2);
        hud.fill(370,540,430,600,0xfaf8ef);
        check(detect(hud,null,1000,0,0,0,0).player==null);
        Scene hiddenPlatform=edgeScene(370,430);hiddenPlatform.fill(370,540,430,600,0xfaf8ef);
        check(detect(hiddenPlatform,null,1000,140,890,230,930).player==null);
    }

    private static JumperState detect(Scene scene, JumperState previous, long timestamp,
                                      int el, int et, int er, int eb) {
        return JumperVision.detect(scene, scene.width, scene.height, 0, 0, scene.width, scene.height,
                el, et, er, eb, timestamp, previous);
    }

    private static void badGeometry() {
        try {
            JumperVision.detect((x, y) -> 0xffffff, 854, 1280, -1, 0, 854, 1280,
                    0, 0, 0, 0, 1, null);
            throw new AssertionError("invalid bounds accepted");
        } catch (IllegalArgumentException expected) { checks++; }
        try {
            Scene scene = new Scene();
            JumperState first = detect(scene, null, 1, 0, 0, 0, 0);
            detect(scene, first, 1, 0, 0, 0, 0);
            throw new AssertionError("stale timestamp accepted");
        } catch (IllegalArgumentException expected) { checks++; }
    }

    private static void near(float expected, float actual, float tolerance) {
        checks++;
        if (Math.abs(expected - actual) > tolerance) throw new AssertionError(expected + " != " + actual);
    }

    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }

    private static final class Scene implements PixelBoardReader.Pixels {
        final int width = 854, height = 1280;
        final int[] pixels = new int[width * height];
        Scene() { java.util.Arrays.fill(pixels, 0xfaf8ef); }
        void fill(int left, int top, int right, int bottom, int color) {
            for (int y = top; y < bottom; y++) java.util.Arrays.fill(pixels, y * width + left, y * width + right, color);
        }
        @Override public int rgb(int x, int y) { return pixels[y * width + x]; }
    }
}
