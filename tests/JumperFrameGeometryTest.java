package org.localpilot.android;

public final class JumperFrameGeometryTest {
    private static int checks;
    public static void main(String[] args) {
        JumperFrameGeometry full=new JumperFrameGeometry(854,1280,854,1280,0,24,854,1256);
        eq(0,full.left);eq(24,full.top);eq(854,full.right);eq(1256,full.bottom);
        near(213.5f,full.toScreenX(213.5f));near(700,full.toScreenY(700));
        eq(400,full.toFrameX(400));eq(900,full.toFrameY(900));
        JumperFrameGeometry cropped=new JumperFrameGeometry(854,1232,854,1280,0,24,854,1256);
        eq(0,cropped.left);eq(0,cropped.top);eq(854,cropped.right);eq(1232,cropped.bottom);
        near(427,cropped.toScreenX(427));near(640,cropped.toScreenY(616));
        JumperFrameGeometry scaled=new JumperFrameGeometry(427,616,854,1280,0,24,854,1256);
        near(427,scaled.toScreenX(213.5f));near(640,scaled.toScreenY(308));
        for(int[] screen:new int[][]{{1282,1920},{1080,2400},{2400,1080},{3840,2160},{7680,4320},{120,180},{720,720}}){
            int w=screen[0],h=screen[1];CaptureResolution cap=new CaptureResolution(w,h);
            eq(1,cap.width<=w&&cap.height<=h?1:0);
            eq(1,Math.max(cap.width,cap.height)<=720?1:0);
            eq(1,Math.min(cap.width,cap.height)<=480?1:0);
            eq(0,cap.width%2);eq(0,cap.height%2);
            eq(1,(long)cap.width*cap.height<=345600?1:0);
            JumperFrameGeometry mirror=new JumperFrameGeometry(cap.width,cap.height,w,h,0,24,w,h-24,true);
            eq(0,mirror.left);eq(cap.width,mirror.right);
            eq((int)Math.floor(24*cap.height/(float)h),mirror.top);
            eq((int)Math.ceil((h-24)*cap.height/(float)h),mirror.bottom);
            near(w*.5f,mirror.toScreenX(cap.width*.5f));
            near(h*.5f,mirror.toScreenY(cap.height*.5f));
            eq(Math.round(w*.25f*cap.width/w),mirror.toFrameX(w*.25f));
            eq(Math.round(h*.75f*cap.height/h),mirror.toFrameY(h*.75f));
        }
        // Tablet video: portrait game at x532..1388, not the whole1920px display.
        JumperFrameGeometry field=new JumperFrameGeometry(720,480,1920,1282,532,36,1388,1255,true);
        near(746.6667f,field.toScreenX(280));eq(280,field.toFrameX(746));
        near(1173.3334f,field.toScreenX(440));eq(440,field.toFrameX(1174));
        eq(199,field.left);eq(521,field.right);
        eq(13,field.top);eq(470,field.bottom);
        try{new CaptureResolution(0,1920);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        try{new CaptureResolution(20000,1920);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        System.out.println("PASS "+checks+" jumper-frame-geometry assertions");
    }
    private static void eq(int expected,int actual){checks++;if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
    private static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>.1f)throw new AssertionError(expected+" != "+actual);}
}
