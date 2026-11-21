package org.localpilot.android;
public final class InstalledGameRouteTest {
 private static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 public static void main(String[] ignored){
  String self="org.localpilot.android.dev",nfs="com.ea.game.nfs14_row";
  check(InstalledGameRoute.inspect(self,nfs,nfs,-1,false,false)==InstalledGameRoute.State.PROFILE_REQUIRED,
   "NFS must not inherit old arcade profile");
  check(InstalledGameRoute.inspect(self,nfs,"com.yandex.market",2,false,true)==InstalledGameRoute.State.WINDOW_MISMATCH,
   "other app cannot reuse profile");
  check(InstalledGameRoute.inspect(self,self,self,2,false,true)==InstalledGameRoute.State.BLOCKED_TARGET,
   "self control denied");
  check(InstalledGameRoute.inspect(self,"com.android.settings","com.android.settings",2,false,true)
    ==InstalledGameRoute.State.BLOCKED_TARGET,"system UI denied");
  check(InstalledGameRoute.inspect(self,nfs,nfs,8,false,true)==InstalledGameRoute.State.PROFILE_REQUIRED,
   "unsupported script route denied");
  check(InstalledGameRoute.inspect(self,"com.browser.app","com.browser.app",2,true,false)
    ==InstalledGameRoute.State.BROWSER_CONTEXT_REQUIRED,"browser must confirm game slot");
  check(InstalledGameRoute.inspect(self,nfs,nfs,2,false,false)==InstalledGameRoute.State.PROFILE_CONFIGURED,
   "explicit installed profile can enter the existing vision pipeline");
  check(!InstalledGameRoute.explanation(InstalledGameRoute.State.PROFILE_CONFIGURED).contains("успешно"),
   "configured does not mean successful gameplay");
  System.out.println("InstalledGameRoute: 8 admission checks PASS (HOST_UNIT)");
 }
}
