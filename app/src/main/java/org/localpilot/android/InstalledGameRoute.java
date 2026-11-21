package org.localpilot.android;

/** Admission for a user-selected installed Android game.
 * It never infers a mechanic from an app name and cannot issue a gesture permit.
 * Actual pixels, controller outcomes and input ownership remain with PilotService. */
public final class InstalledGameRoute {
 private InstalledGameRoute(){}
 public enum State {
  BLOCKED_TARGET, WINDOW_MISMATCH, PROFILE_REQUIRED,
  BROWSER_CONTEXT_REQUIRED, PROFILE_CONFIGURED
 }
 public static State inspect(String ownPackage,String requestedPackage,String observedPackage,
                             int savedProfile,boolean browser,boolean confirmedBrowserContext){
  if(!VisualGameTarget.allowed(requestedPackage,ownPackage))return State.BLOCKED_TARGET;
  if(!requestedPackage.equals(observedPackage))return State.WINDOW_MISMATCH;
  if(!VisualGameTarget.supportedProfile(savedProfile))return State.PROFILE_REQUIRED;
  if(VisualGameTarget.requiresProfileChoice(savedProfile,browser,confirmedBrowserContext))
   return State.BROWSER_CONTEXT_REQUIRED;
  return State.PROFILE_CONFIGURED;
 }
 public static String explanation(State state){
  return switch(state){
   case BLOCKED_TARGET->"STOP: системное или недопустимое приложение";
   case WINDOW_MISMATCH->"STOP: открыто другое окно; выберите игру заново";
   case PROFILE_REQUIRED->"Для этой игры нет настроенной механики: выберите «Игра» на панели. Автоуправление не запущено";
   case BROWSER_CONTEXT_REQUIRED->"Нужно подтвердить конкретную игру в браузере: «Имя игры» и профиль. Жесты запрещены";
   case PROFILE_CONFIGURED->"Профиль сохранён. Это не доказательство распознавания игрового кадра";
  };
 }
}
