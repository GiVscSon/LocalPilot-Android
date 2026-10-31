import javax.tools.ToolProvider;
import java.net.*;
import java.nio.file.*;
class AuditVerify {
 public static void main(String[]args)throws Exception{
  Path out=Files.createTempDirectory("localpilot-audit-verify-");var sources=new java.util.ArrayList<String>();sources.add("-d");sources.add(out.toString());
  for(String name:new String[]{"SafeJournal","RotatingJournal","AuditExport","AuditStopCode"})sources.add("app/src/main/java/org/localpilot/android/"+name+".java");
  sources.add("tests/AuditExportTest.java");
  if(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,sources.toArray(String[]::new))!=0)throw new AssertionError("compile failed");
  try(var cl=new URLClassLoader(new URL[]{out.toUri().toURL()})){cl.loadClass("org.localpilot.android.AuditExportTest").getMethod("main",String[].class).invoke(null,(Object)args);}
 }
}
