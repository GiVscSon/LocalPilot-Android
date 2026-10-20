# LocalPilot Android — точка продолжения

Проверено в текущей облачной среде 20 октября 2026 года. Основной LocalPilot для ПК остаётся отдельной задачей в `GiVscSon/my-agent`; Android-код туда не переносился.

## Репозиторий и исходники

- Checkout: `/workspace/LocalPilot-Android`, origin: `https://github.com/GiVscSon/LocalPilot-Android.git`.
- База `main`: `2ff0f2165ea5b2f90ffe9af33942f0428b99dedc` — ранее содержала только этот handoff. Применимых AGENTS.md при проверке нет; в новом чате проверить снова.
- Рабочая ветка: `fix/android-pipeline-diagnostics`. Оригинальные исходники 0.3.2 импортированы отдельным коммитом `c1cbf3f`.
- Пользователь предоставил ZIP 0.3.1, затем актуальный ZIP 0.3.2. Использована полная база 0.3.2, а не независимый старый прототип `android-logic-pilot` и не проект для ПК.
- Архив 0.3.2: 113815 байт, SHA-256 `9cde636c6d3248e24ab3cb0016e88e5608ecdcd03227796a6ab0e97dfc34401c`; CRC и 36/36 исходных хэшей проверены. Подробности и резервная копия — `docs/imports/0.3.2/PROVENANCE.md`.
- APK, модели, SDK, кеши и приватные ключи в Git не включены. Архив не добавляет сторонних runtime-зависимостей.

## Что установлено по исходникам

Пользователь сообщал `CAPTURE n=2`, остальные этапы `n=0`, и остановку тестового игрока после скрытия панели. Точной причины отказа на его телефоне пока нет.

1. В исходной версии `hidePanel()` всегда вызывал Stop, отзывал ввод, очищал ROI и отменял callbacks. Это полное скрытие с остановкой, а не сворачивание.
2. Демо запускалось под большой панелью без её сворачивания. Проверка пересечения в `send()` могла корректно блокировать первый жест.
3. CAPTURE записывался до вызова parser, READ — только после успешного чтения. Исключение parser оставляло READ/POLICY/ACK равными нулю без счётчика ошибок. Это совместимо с наблюдаемыми метриками, но не доказывает конкретную ошибку ROI или палитры на телефоне.
4. Stop удалял общий таймаут захвата, оставляя `captureBusy` до прихода callback. Если callback не приходил, новый авторежим мог остаться заблокирован.

## Изменения

- Отдельная кнопка «Свернуть / Развернуть» сохраняет привязку, разрешение и текущий режим; STOP остаётся доступен. «STOP и скрыть» по-прежнему останавливает работу. После создания новой панели флаг сворачивания сбрасывается.
- Демо сворачивает панель и ждёт pre-draw после layout до первого шага. Отложенный старт всё равно проверяет epoch; Stop не позволяет возобновить его.
- `StageMetrics` сохраняет прежние успешные n/процентили и добавляет lifetime attempts/errors/cancelled/last_error. Коды ошибок ограничены; raw exception messages и кадры в экспорт метрик не попадают.
- `PipelineRead` оборачивает настоящий parser: отказ даёт `READ n=0 attempts=1 errors=1 last_error=PARSE_REJECTED` либо `READER_FAILED`, а панель показывает этап и причину. READ не становится успешным при исключении.
- Начало/ошибка CAPTURE, POLICY и ANDROID_ACK тоже учитываются. Stop/таймаут инвалидируют request ID, освобождают ожидание; старый callback закрывает свой buffer, не возобновляя игру и не меняя новый захват.
- Ограничения ROI, палитры, свежести, окна, лимитов и разрешения сохранены. Гипотеза о `isActive() && isFocused()` при касаниях overlay пока не проверена на устройстве; условие не ослаблялось.
- Исправлены два исходных lint WrongConstant: символическая `LinearLayout.VERTICAL` вместо числа.

## Проверки этой задачи

- Исходная 0.3.2: JVM Verify прошёл 8811 assertions. Это базовый прогон до исправлений.
- Текущая версия: **8902 assertions PASS**, без пропусков: 27 experience, 59 metrics, 38 pipeline-read, 13 gate, 6131 board/reader/parity, 2634 hardening/mini. Это проверочные утверждения внутри JVM runner, не 8902 Android-теста.
- Новые parser fixtures используют настоящие PixelBoardReader/MiniReader: корректные палитры 2048/TTT/Lights, неправильный ROI, неизвестная палитра, неожиданное исключение. Подтверждают диагностику ошибок, не реальный screenshot pipeline.
- Gradle wrapper 8.9 проверен по официальным SHA-256 distribution и JAR. `./gradlew --version` выполнен.
- `:app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest` прошли. **Lint: 0 ошибок, 15 предупреждений**; подавления или baseline не добавлялись. Предупреждения относятся к ресурсам/локализации, accessibility custom views, static service reference, backup rules, API31-атрибуту и RTL.
- Dev APK и instrumentation APK проходят apksigner verify. `aapt` подтверждает отдельный пакет `org.localpilot.android.dev`, 0.3.2-dev/code6, Android11+, target35.
- Дополнительный `build_debug.py` тоже реально собрал, выровнял и подписал Dev APK. Ключ `.build/debug.keystore` остаётся локальным и игнорируется Git.
- Сквозной Android-тест **не выполнен**. Его сборка не доказывает работу overlay, screenshot или жестов на телефоне. API30 x86_64 эмулятор работает без KVM: после сохранения/удаления подтверждённо устаревших locks повторная загрузка дошла до PM/window/accessibility, но `zygote`/framework перезапустились до запуска приложения. `adb install` завершился exit1: `cmd: Failure calling service package: Broken pipe (32)`. Это отказ тестовой среды; приложение не запускалось. Повторять такой же запуск без изменения стенда не нужно.

## APK и подпись

Gradle APK: `app/build/outputs/apk/debug/app-debug.apk`. Имя приложения **LocalPilot Android Dev**, package `org.localpilot.android.dev`; он устанавливается рядом с оригиналом. Нужна отдельная активация его Accessibility-службы; данные оригинала не копируются и не удаляются.

Исходный ключ подписи оригинала отсутствует. Dev нельзя выдавать за обновление установленного `org.localpilot.android`. Не удалять оригинал ради установки. Gradle и standalone по умолчанию используют разные локальные debug-ключи; для дальнейших обновлений Dev сохранять выбранный способ и ключ.

Переносимый комплект собирается `python3 tools/package_release.py`: Dev APK, source ZIP, README, этот handoff, VALIDATION и SHA-256. Ключи, .build, build, кеши и SDK исключены. Папка release не коммитится.

## Android instrumentation и продолжение

Добавлен native runner `PilotUiInstrumentation` без AndroidX. Он должен проверить реальные кнопки привязки/согласия и Demo×10, видимый STOP, сворачивание с сохранением binding/permit, первое сворачивание после hide/show и остановку будущих ходов. Setup hide/show использует public service API и отражён в evidence. Чтение пикселей этим runner не проверяется.

На отдельном готовом тестовом устройстве установить app-debug.apk и app-debug-androidTest.apk, включить Dev Accessibility-службу и выполнить:

```text
adb shell am instrument -w org.localpilot.android.dev.test/org.localpilot.android.PilotUiInstrumentation
```

Считать успехом только явный `result=PASS` и JSON evidence, а не exit code adb. JSON: external files приложения, `instrumentation/pilot-ui-evidence.json`. UiAutomation использует FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES; обычный uiautomator dump может отключить проверяемую службу.

После устройства нужен отдельный реальный screenshot→read→policy→gesture прогон 2048/мини-игры с проверенным ROI и Stop/focus/rotation. Для пользовательского n=0 нужны текст ошибки панели и экспорт новых метрик. Не объявлять полный отказ распознавания исправленным по JVM-тестам или сборке.

В начале нового чата проверить git status, ветку, свежие изменения и этот файл. Исходные заявления из архивного handoff сохранены как данные в docs/imports/0.3.2/ARCHIVE_HANDOFF.md и не заменяют фактическую проверку.
