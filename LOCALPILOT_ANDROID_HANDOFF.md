# LocalPilot Android — точка продолжения 0.3.3 Dev

Проверки: 21 октября 2026. Основной LocalPilot для ПК остаётся отдельной задачей в `GiVscSon/my-agent`; Android-код туда не переносился.

## Исходники и сохранение

Checkout: `/workspace/LocalPilot-Android`, origin `https://github.com/GiVscSon/LocalPilot-Android.git`, ветка `fix/android-pipeline-diagnostics`. Перед продолжением проверить фактические HEAD/status и AGENTS.md (при последней проверке применимых инструкций не найдено). Main остаётся `2ff0f2165ea5b2f90ffe9af33942f0428b99dedc`.

Полная база — пользовательский Android0.3.2 ZIP, импортированный коммитом `c1cbf3f`, а не старый прототип android-logic-pilot или проект для ПК. ZIP: 113815 байт, SHA256 `9cde636c6d3248e24ab3cb0016e88e5608ecdcd03227796a6ab0e97dfc34401c`; проверены CRC и36/36 исходных хэшей. Происхождение — `docs/imports/0.3.2/PROVENANCE.md`.

Полная резервная копия checkout до импорта, включая .git: `/workspace/localpilot-backups/LocalPilot-Android-before-import-20261020T211624142405Z/checkout-full.tar.gz`. Предыдущее опубликованное состояние ветки — `057cd6e8e9a05c8baed8b47a5996fcb150fb42ae`. Приватные ключи/SDK/кеши/APK не включаются в Git. Основной архив ПК LocalPilot_project(3).zip пока не получен.

## Пользовательские логи: что действительно работает

Оригиналы и подробный отчёт сохранены вне Git в `/workspace/localpilot-diagnostics/2026-10-21-user-run/`. Не публиковать raw logs или пользовательские кадры.

- Проверены20 корректных последовательных переходов2048: легальные ходы, одна новая плитка, награды и хэши. Сумма награды148, максимальная плитка32. Все ppo=false: запись опыта, не обучение нейросети.
- В audit319 корректных записей:32 PROPOSED,32 ANDROID_COMPLETED,20 VERIFIED_2048,235 STOP. Все отправленные жесты имеют ACK; отказов/отмен отправленных жестов в журнале нет.
- Полная серия20 ходов завершилась по бюджету; STOP внутри неё отсутствует. Другие события нельзя однозначно отнести к режиму без дополнительных данных.
- CAPTURE48/48 успешных, READ44/48 (4 ошибки), POLICY20/20, ACK32/218 (186 ошибок). last_error=STALE_TICKET — только последняя ошибка ACK, а не доказательство причины всех186.
- Первоначальный общий отказ «все n=0» для этого запуска не подтверждён. Причины четырёх отдельных READ-отказов без кадров не установлены. Старый журнал не сохранял причины каждого STOP/отказа.

Эти логи относятся к прежнему APK и не заменяют приёмку новой0.3.3 на устройстве.

## Изменения 0.3.3

1. STOP явно убирает привязку в статусе; ручные кнопки недоступны без действующего разрешения. Повторное нажатие без binding/permit даёт инструкцию без фиктивного send.
2. ActionGate различает отсутствие/устаревание/чужой ticket, смену пакета, окна, размеров и истечение TTL. TTL700мс, одноразовое потребление и проверки окна сохранены.
3. Метрики сохраняют прежние n/attempts/errors/cancelled/last_error и добавляют ограниченную гистограмму error_counts/error_overflow. Последняя ошибка явно отделена от распределения всех ошибок.
4. «Автополе» получает реальные нарисованные границы встроенных2048/TTT/Lights из виртуального Accessibility-узла. Узел передаёт только геометрию/профиль, не скрытые значения клеток: чтение остаётся по пикселям.
5. Внешний автопоиск поддерживает только стандартную палитру2048: единственная полная доска, проверка16 клеток/разделителей/краёв, отказ при неоднозначности, перекрытии, обрезке и неизвестной палитре. Не универсальное OCR.
6. Поиск работает в worker с собственной ограниченной копией кадра; STOP/таймаут инвалидируют поздний результат. Копирование до8млн пикселей выполняется синхронно в callback; большее разрешение требует ручного выбора. Кадры не сохраняются.
7. Компактная панель пробует переместиться вне поля; если не помещается, работа останавливается. Основные кнопки прокручиваются, STOP остаётся сверху. После поиска всё равно нужны стабильные кадры и подтверждение клеток перед вводом.
8. «Установленные игры» показывает доступные launcher-приложения: фильтр игр/все, поиск. Выбор открывает приложение и ждёт стабильного совпадения пакета/окна/границ, затем привязывает без разрешения ввода. Нет QUERY_ALL_PACKAGES, INTERNET или новых runtime-зависимостей.

Сохранены прежние исправления0.3.2 Dev: отдельное сворачивание без STOP, hide со STOP, ожидание layout перед демо, диагностика parser и отмена зависшего/позднего screenshot callback.

Doodle Jump, Mega Jump, Sonic Jump, Icy Tower, Kid Icarus, Subway Surfers, Temple Run, Jetpack Joyride, Sonic Dash и Sonic Jump Fever пока не имеют игровых автопрофилей. Выбор приложения и ручные жесты не означают автономную игру. Пользователь выбрал управление касаниями/экранными кнопками. Подробности — `docs/ARCADE_PROFILES.ru.md`.

## Проверки и ограничения

Фактический итог текущей сборки — `VALIDATION.txt`.

- JVM Verify:9224 утверждения PASS без пропусков:27 experience,83 metrics,38 pipeline-read,125 board-selection,51 pixel-locator,135 gate,6131 board/reader/parity,2634 hardening/mini. Синтетические изображения не заменяют реальные кадры игр.
- Gradle8.9 wrapper/distribution проверены по официальным SHA256. Обязательные цели: assembleDebug,lintDebug,assembleDebugAndroidTest.
- Android UI runner: COMPILED, NOT RUN. Нет KVM; программный API30 эмулятор перезапустил framework до запуска APK, adb install завершился `cmd: Failure calling service package: Broken pipe (32)`. Это отказ стенда, не результат работы приложения. Не повторять неизменённый стенд.
- Новые автопоиск/панель/выбор приложения не проверены на физическом устройстве. Не заявлять фактический screenshot→read→gesture, focus/rotation/Stop-race, FPS/энергопотребление новой0.3.3 без реальной проверки.

## Сборка и подпись

Gradle APK: `app/build/outputs/apk/debug/app-debug.apk`. Приложение **LocalPilot Android Dev**, пакет `org.localpilot.android.dev`, version0.3.3-dev/code7, Android11+, target35. Устанавливается рядом с оригиналом и обновляет прежнюю Gradle Dev-сборку с тем же локальным ключом.

Gradle certificate SHA256: `55626377256dda3a6b5824b7278eb12db75b8a7d1c80baa9d2af4402ccac2684`. Ключ оригинального org.localpilot.android не предоставлен. Оригинал не удалять. Standalone использует другой debug-ключ; не смешивать способы обновления Dev.

Инструменты сохранены в /workspace/android-build-tools: jdk-21.0.12.1+1, sdk, gradle-home и приватный android-user. Из корня проекта:

```bash
/workspace/android-build-tools/jdk-21.0.12.1+1/bin/java tests/Verify.java tests/legacy_parity.txt
JAVA_HOME=/workspace/android-build-tools/jdk-21.0.12.1+1 GRADLE_USER_HOME=/workspace/android-build-tools/gradle-home ANDROID_HOME=/workspace/android-build-tools/sdk ANDROID_USER_HOME=/workspace/android-build-tools/android-user JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts ./gradlew --no-daemon -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
python3 tools/package_release.py
```

Упаковщик создаёт отдельные файлы0.3.3 в release, сохраняя прежние0.3.2. Ключи, .build/build, SDK, кеши и пользовательские данные исключены.

## Приёмка на готовом устройстве

1. Установить Dev APK, включить именно Dev Accessibility-службу, сохранить прежнее приложение/данные.
2. Открыть встроенную2048 → «Автополе», сверить16 клеток до подтверждения20 ходов. Повторить для TTT/Lights, проверить положение панели.
3. Проверить STOP во время поиска/игры, сворачивание, истечение разрешения, поворот/смену окна. Неизвестная палитра/неполное поле должны остановить ввод.
4. Выбор установленной игры сам не разрешает ввод. Сторонняя2048 требует отдельной проверки реального кадра; для аркад пока выбор/наблюдение/ручные разрешённые жесты.
5. Экспорт метрик/опыта/audit останавливает ввод. Смотреть новые коды и гистограммы, не принимать last_error за все отказы.

Native PilotUiInstrumentation без AndroidX предназначен для Demo×10/STOP/сворачивания, а не для новых ROI. После установки app и androidTest APK на готовом стенде и включения Dev-службы:

```text
adb shell am instrument -w org.localpilot.android.dev.test/org.localpilot.android.PilotUiInstrumentation
```

Нужны явный result=PASS и JSON external files `instrumentation/pilot-ui-evidence.json`. Runner использует FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES. Компиляция или exit code adb сами по себе не подтверждают успех.

## GitHub и скриншоты

Git read/push по github.com работает; main не менялась. Проверка/создание PR через REST/GraphQL api.github.com вернули Forbidden. PR не создан, CI/Release не подтверждены. Описание: `docs/PR_DESCRIPTION.md`.

Сравнение: https://github.com/GiVscSon/LocalPilot-Android/compare/main...fix/android-pipeline-diagnostics?expand=1

Если доступ к API изменится, сначала проверить существующий PR, затем создать draft с body file. Не извлекать прокси-ключи и не просить секреты в чате.

Пользователь попросил реальные игровые скриншоты для телефона и планшета. Запросы itunes.apple.com и play.google.com блокируются прокси: домены отсутствуют в allowlist. Изображения не получены, размеры не подтверждены. В сохранённый черновик среды добавлены itunes.apple.com,apps.apple.com,play.google.com,play-lh.googleusercontent.com,*.mzstatic.com с сохранением прежних правил (включая api.github.com). Черновик не применяет сеть: нужны review/save/publish в настройках среды. После применения возобновить загрузку; фиксировать источник, версию, исходные размеры и phone/tablet-категорию. iOS-референс не считать кадром пользовательской Android-версии.
