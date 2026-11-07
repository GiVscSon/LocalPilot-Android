import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
class CaptureRoiVerify {
 public static void main(String[]args)throws Exception{
  var out=Files.createTempDirectory("capture-roi-");
  int code=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"--release","17","-d",out.toString(),
   "app/src/main/java/org/localpilot/android/Board2048.java",
   "app/src/main/java/org/localpilot/android/PixelBoardReader.java",
   "app/src/main/java/org/localpilot/android/RgbaFrameDecoder.java",
   "app/src/main/java/org/localpilot/android/BoundedPixels.java",
   "app/src/main/java/org/localpilot/android/FrameDecodePlan.java",
   "app/src/main/java/org/localpilot/android/DecodedFrameBuffer.java",
   "app/src/main/java/org/localpilot/android/VisionV2Core.java",
   "app/src/main/java/org/localpilot/android/JumperState.java",
   "app/src/main/java/org/localpilot/android/JumperVision.java",
   "app/src/main/java/org/localpilot/android/SceneRecovery.java",
   "app/src/main/java/org/localpilot/android/ArcadeProfile.java",
   "app/src/main/java/org/localpilot/android/ArcadeObservation.java",
   "app/src/main/java/org/localpilot/android/ArcadeVision.java",
   "app/src/main/java/org/localpilot/android/CustomVisionConfig.java",
   "app/src/main/java/org/localpilot/android/CustomArcadeVision.java",
   "app/src/main/java/org/localpilot/android/GameOverScreen.java",
   "tests/CaptureRoiTest.java");
  if(code!=0)throw new IllegalStateException("capture ROI compilation failed");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.CaptureRoiTest").getMethod("main",String[].class).invoke(null,(Object)args);
  }
 }
}
