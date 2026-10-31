package org.localpilot.android;

/**
 * Whitelisted reason codes in the append-only audit STOP.metric field.
 * No dynamic message, package, ROI or window identity is ever persisted.
 * Existing v1 records with metric=0 remain valid.
 */
public final class AuditStopCode {
 public static final long UNSPECIFIED=0, USER_STOP=1, MENU=2, EXPORT=3,
  TARGET_BOUNDS_CHANGED=10, PANEL_COVERS_ROI=11, SCREENSHOT_TIMEOUT=12,
  GRID_PALETTE_DRIFT=13, PARSE_REJECTED=14, RESPONSE_TIMEOUT=15,
  WINDOW_UNAVAILABLE=16, CAPTURE_FAILURE=17, REBIND_ROI=18,
  SERVICE_PANEL_FAILURE=19, CONFIGURATION=20;
 private AuditStopCode(){}
 public static long from(String reason){
  if(reason==null||reason.isBlank())return UNSPECIFIED;
  if(reason.contains("GRID_PALETTE_IDENTITY_DRIFT"))return GRID_PALETTE_DRIFT;
  if(reason.contains("TARGET_BOUNDS_CHANGED"))return TARGET_BOUNDS_CHANGED;
  if(reason.contains("PANEL_COVERS_ROI")||reason.contains("панель перекрывает")||reason.contains("Панель STOP закрывает"))
   return PANEL_COVERS_ROI;
  if(reason.contains("SCREENSHOT_TIMEOUT"))return SCREENSHOT_TIMEOUT;
  if(reason.contains("PARSE_REJECTED"))return PARSE_REJECTED;
  if(reason.contains("NO_RESPONSE")||reason.contains("не подтвердила")||reason.contains("нет видимого ответа"))
   return RESPONSE_TIMEOUT;
  if(reason.contains("Окно игры изменилось")||reason.contains("Окно изменилось")||reason.contains("Фокус потерян"))
   return WINDOW_UNAVAILABLE;
  if(reason.contains("Ошибка кадра")||reason.contains("Ошибка обработки кадра")||reason.contains("Не удалось включить поток экрана"))
   return CAPTURE_FAILURE;
  if(reason.contains("Перепривязка ROI")||reason.contains("Область не сохранена"))return REBIND_ROI;
  if(reason.contains("Панель недоступна")||reason.contains("Не удалось разместить панель"))
   return SERVICE_PANEL_FAILURE;
  if(reason.startsWith("Экспорт")||reason.contains("Диагностика: STOP"))return EXPORT;
  if(reason.startsWith("Дополнительные действия"))return MENU;
  if(reason.equals("STOP")||reason.startsWith("STOP из уведомления"))return USER_STOP;
  if(reason.contains("Выберите механику игры")||reason.contains("Выберите область игры")
    ||reason.contains("Режим распознавания изменён")||reason.contains("Привязка"))return CONFIGURATION;
  return UNSPECIFIED;
 }
}
