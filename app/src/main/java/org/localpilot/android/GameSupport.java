package org.localpilot.android;

import org.localpilot.android.learning.TaskSchema;

/** Availability describes connected code paths, never a promise of field acceptance. */
public final class GameSupport {
 private GameSupport(){}
 public static int externalProfile(TaskSchema.GameId game){
  return switch(game){
   case ARI_JUMPER->0;case KARKANOID->1;case MARKET_RUSH->2;case GOSHAN_FLAPPY->3;
   case GAME_2048->4;case MATCH_WORLD->6;case SHIPUCHKA->9;case NASHCHUPAY_KHOD->11;
   default->-1;
  };
 }
 public static String describe(TaskSchema.GameId game){
  if(externalProfile(game)>=0)return "Внешняя игра: визуальный контроллер подключён. Полевая приёмка на телефоне/планшете не выполнена.";
  return switch(game){
   case HEXON,KOTOBASHNYA->"Доступны лабораторная модель и симулятор. Автоматическое управление этой внешней игрой не подключено.";
   case NES_PLATFORMER,NES_TACTICAL_ARENA->"NES: локальный JSNES, ручные кнопки и ROM-bound опыт. Эта LAB-модель не управляет ROM автоматически.";
   default->"Синтетический профиль. Ядро DOS/SEGA не интегрировано; запуска ROM здесь нет.";
  };
 }
}
