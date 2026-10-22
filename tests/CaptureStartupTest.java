package org.localpilot.android;
public class CaptureStartupTest {
 private static int checks;
 private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("capture check "+checks);}
 public static void main(String[] args){
  CaptureStartup s=new CaptureStartup();
  check(s.consentMayClose(0));long a=s.begin(0);check(!s.consentMayClose(a));
  check(s.active(a));check(s.waiting(a));check(s.timeout(a,119999)==null);
  check("CONSENT_TIMEOUT".equals(s.timeout(a,120000)));
  check(s.move(a,CaptureStartup.Stage.CONSENT,CaptureStartup.Stage.SERVICE,100));
  check(!s.move(a,CaptureStartup.Stage.CONSENT,CaptureStartup.Stage.SERVICE,101));
  check(!s.consentMayClose(a));
  check(s.timeout(a,10099)==null);check("SERVICE_TIMEOUT".equals(s.timeout(a,10100)));
  check(s.move(a,CaptureStartup.Stage.SERVICE,CaptureStartup.Stage.FIRST_FRAME,200));
  check(s.consentMayClose(a));
  check(s.timeout(a,3199)==null);check("FIRST_FRAME_TIMEOUT".equals(s.timeout(a,3200)));
  check(s.move(a,CaptureStartup.Stage.FIRST_FRAME,CaptureStartup.Stage.RUNNING,300));
  check(s.consentMayClose(a));check(!s.waiting(a));check(s.timeout(a,1000000)==null);
  check(s.recognized(a,301));check(!s.recognized(a,302));
  check("STARTUP_SECURITYEXCEPTION".equals(CaptureStartup.errorCode("SecurityException")));
  new StageMetrics().failure(StageMetrics.Stage.CAPTURE,CaptureStartup.errorCode("ForegroundServiceStartNotAllowedException"));
  new StageMetrics().failure(StageMetrics.Stage.CAPTURE,CaptureStartup.errorCode("bad error/value"));
  checks+=2;
  s.stop(400);check(s.consentMayClose(a));check(!s.active(a));check(s.timeout(a,1000000)==null);
  check(!s.move(a,CaptureStartup.Stage.SERVICE,CaptureStartup.Stage.FIRST_FRAME,500));
  long b=s.begin(500);check(a!=b);check(s.consentMayClose(a));check(!s.consentMayClose(b));check(!s.active(a));
  check(!s.move(a,CaptureStartup.Stage.CONSENT,CaptureStartup.Stage.SERVICE,600));
  s.error(a,"STALE_CALLBACK");check(!s.report().contains("STALE_CALLBACK"));
  s.error(b,"SecurityException");check(s.report().contains("CONSENT SecurityException"));
  CaptureStartup recreated=new CaptureStartup();check(recreated.begin(0)!=b);
  for(int i=0;i<100;i++){s.stop(i);s.begin(i);}
  check(s.report().split("\n").length==40);
  s.stop(100);check(!s.waiting(b));
  System.out.println("Capture startup: "+checks+" assertions PASS");
 }
}
