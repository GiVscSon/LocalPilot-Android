package org.localpilot.shared;

/**
 * Single source of truth for the two-installation LocalPilot game workspace.
 * Pure Java is compiled into both Android applications.
 *
 * The tester is a separate Android package by design: PilotService must not
 * operate on its own window. Shared visual design is NOT shared input authority.
 */
public final class HubContract {
 private HubContract(){}
 public static final String LAB_PACKAGE="org.localpilot.gamelab";
 public static final String PILOT_PACKAGE_PREFIX="org.localpilot.android";
 public static final int PROTOCOL_VERSION=1;

 public static final int BACKGROUND=0xff0b1020;
 public static final int SURFACE=0xff172e45;
 public static final int TEXT=0xfff3f6fa;
 public static final int SECONDARY=0xffb8c7d9;
 public static final int ACCENT=0xff5ad0bd;
 public static final int WARNING=0xffffb774;

 /** Keep simulated moves, emulator ROM checks and verified Market gameplay apart. */
 public enum Evidence {
  REAL_GAME("Настоящая игра"),
  SIMULATED_GAME("GameLab · моделируемая игра"),
  RETRO_PREFLIGHT("Ретро · проверка формата, ядро не запущено"),
  RETRO_EMULATED("Ретро · кадры эмулятора");
  public final String label;
  Evidence(String label){this.label=label;}
 }
 public static boolean countsForMarketAcceptance(Evidence origin,
    boolean physicalDevice,boolean androidAck,boolean gameSpecificVisualWitness){
  return origin==Evidence.REAL_GAME && physicalDevice
     && androidAck&&gameSpecificVisualWitness;
 }
 /** Package identity is mandatory: even a visually changed GameLab frame is
  * training in a simulator, not acceptance of an actual Market mini-game. */
 public static boolean officialMarketPackage(String pkg){
  return "ru.beru.android".equals(pkg)||"ru.yandex.market".equals(pkg)
    ||"com.yandex.market".equals(pkg);
 }
 public static boolean countsForMarketAcceptance(Evidence origin,String pkg,
    boolean physicalDevice,boolean androidAck,boolean gameSpecificVisualWitness){
  return officialMarketPackage(pkg)
    &&countsForMarketAcceptance(origin,physicalDevice,androidAck,gameSpecificVisualWitness);
 }
 public static boolean ownPackage(String observed,String pilotPackage){
  return observed!=null&&observed.equals(pilotPackage);
 }
 public static boolean supportedLabPackage(String packageName){
  return LAB_PACKAGE.equals(packageName);
 }
 public static boolean supportedPilotPackage(String packageName){
  return packageName!=null&&(packageName.equals(PILOT_PACKAGE_PREFIX)
    ||packageName.startsWith(PILOT_PACKAGE_PREFIX+".dev."));
 }
}
