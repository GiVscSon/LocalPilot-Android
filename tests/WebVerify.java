import javax.tools.ToolProvider;
import java.nio.file.*;
import java.net.*;
class WebVerify {
 public static void main(String[] args)throws Exception{
  Path out=Files.createTempDirectory("localpilot-web-verify-");
  int result=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"-d",out.toString(),"app/src/main/java/org/localpilot/android/web/GoalReward.java","tests/GoalRewardTest.java","app/src/main/java/org/localpilot/android/web/WebLesson.java","app/src/main/java/org/localpilot/android/web/WebHttp.java","app/src/main/java/org/localpilot/android/web/WebLessonStore.java","tests/WebProfilesTest.java","tests/WebLessonTest.java","tests/WebHttpTest.java");
  if(result!=0)throw new IllegalStateException("web compile failed");
  try(var loader=new URLClassLoader(new URL[]{out.toUri().toURL()})){
   loader.loadClass("org.localpilot.android.web.GoalRewardTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
   loader.loadClass("org.localpilot.android.web.WebLessonTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
   loader.loadClass("org.localpilot.android.web.WebProfilesTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
   loader.loadClass("org.localpilot.android.web.WebHttpTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
  }
 }
}
