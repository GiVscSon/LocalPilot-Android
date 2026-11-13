package org.localpilot.android;

import java.util.Locale;

/** Deterministic commands for a GGUF assistant; model outputs never become commands. */
public final class ModelLabAgentPlan {
 public enum Action { HELP, LIST, INSPECT, INSPECT_ALL, BENCHMARK, COMPARE, RECOMMEND, PHOTO, DENY }
 public final Action action;
 public final String reason;
 private ModelLabAgentPlan(Action action,String reason){this.action=action;this.reason=reason;}
 private static ModelLabAgentPlan of(Action a,String why){return new ModelLabAgentPlan(a,why);}
 public static ModelLabAgentPlan parse(String user){
  if(user==null||user.isBlank()||user.length()>350)
   return of(Action.HELP,"Введи короткий запрос на русском или английском.");
  String q=user.toLowerCase(Locale.ROOT).trim().replace('ё','е');
  // A chat request cannot secretly mutate installed packages, run shell, or remove weights.
  if(q.matches(".*(удал|стерет|сотри|erase|delete|rm |shell|adb|exec|install|установ|запуст.*команд).*"))
   return of(Action.DENY,"Изменение файлов/пакетов выполняется только отдельной кнопкой с подтверждением.");
  if(q.contains("сравн")||q.contains("compare")||q.contains("разниц"))
   return of(Action.COMPARE,"Сравнить две модели на одном и том же запросе.");
  if((q.contains("все")||q.contains("all"))&&
      (q.contains("пров")||q.contains("инспект")||q.contains("inspect")||
       q.contains("метаданн")||q.contains("gguf")))
   return of(Action.INSPECT_ALL,"Проверить доступные модели по очереди.");
  if(q.contains("фото")||q.contains("изображен")||q.contains("скрин")||
     q.contains("картин")||q.contains("image")||q.contains("vision"))
   return of(Action.PHOTO,"Изображение можно анализировать после ручного выбора.");
  if(q.contains("подбер")||q.contains("выбер")||q.contains("рекоменд")||
     q.contains("recommend")||q.contains("which model"))
   return of(Action.RECOMMEND,"Выбрать модель по задаче и доступному каталогу.");
  if(q.contains("бенч")||q.contains("скорост")||q.contains("замерь")||
     q.contains("тест")||q.contains("генерац")||q.contains("benchmark")||
     q.contains("latency")||q.contains("test")||q.contains("run model"))
   return of(Action.BENCHMARK,"Запустить короткий локальный текстовый тест.");
  if(q.contains("метаданн")||q.contains("архитектур")||q.contains("формат")||
     q.contains("информац")||q.contains("заголовок")||
     q.contains("inspect")||q.contains("metadata")||q.contains("header"))
   return of(Action.INSPECT,"Прочитать и проверить GGUF-заголовок.");
  if(q.contains("спис")||q.contains("модел")||q.contains("catalog")||
     q.contains("list")||q.contains("available")||q.contains("gguf"))
   return of(Action.LIST,"Показать каталог проверенных GGUF.");
  return of(Action.HELP,"Доступны проверка GGUF, каталог, тест, сравнение и выбор модели.");
 }
 public static String help(){
  return "Команды локального GGUF-агента:\n"+
    "• «список моделей» — какие GGUF установлены\n"+
    "• «покажи архитектуру» — прочитать метаданные выбранного GGUF\n"+
    "• «проверь все GGUF» — проверить модели по очереди (до восьми)\n"+
    "• «тест скорости» — запустить выбранную модель\n"+
    "• «сравни модели» — два GGUF на одинаковом вопросе\n"+
    "• «подбери модель» — рекомендация для 12 ГБ RAM\n"+
    "• «проанализируй фото» — инструкция к ручному выбору снимка.\n"+
    "Команды агента не удаляют и не устанавливают файлы и не управляют играми.";
 }
}
