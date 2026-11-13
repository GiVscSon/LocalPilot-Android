package org.localpilot.android;

/** Pure-Java sanitization of sampled content for the visible diagnostics screen. */
public final class ModelLabAnswer {
 private ModelLabAnswer(){}
 public static String visible(String raw){
  if(raw==null||raw.length()>6000)return "Ответ отсутствует или слишком длинный.";
  String answer=raw.trim();
  // Qwen may return an internal reasoning preface.
  if(answer.startsWith("<think>")){
   int close=answer.indexOf("</think>");
   if(close<0)return "Ответ обрезан во внутреннем блоке модели.";
   answer=answer.substring(close+8).trim();
  }
  if(answer.isBlank()||answer.contains("<think>")||answer.contains("</think>")||
      answer.contains("<|")||answer.contains("|>"))
   return "Ответ содержит служебные токены или не завершён.";
  return answer;
 }
}
