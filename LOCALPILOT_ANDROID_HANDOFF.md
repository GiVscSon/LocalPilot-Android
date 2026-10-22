# LocalPilot Android — история и внешняя новая точка сохранения

## STAGE 3: POST-ACK ВИЗУАЛЬНАЯ ПРОВЕРКА (22.10.2026)

**Изолированный исходник 0.6.0-dev после Stage2, НЕ 0.7.11 APK:** в реальном `PilotService` воспроизведён риск преждевременного подтверждения жеста по кадру, снятому до Android `onCompleted()` и обработанному после него. Реализовано `ArcadePolicy.responseAfterAck`, требует timestamp кадра строго позже времени ACK; `PilotService` сохраняет ACK время через `SystemClock.elapsedRealtime()` и сбрасывает при STOP/смене действия/успешной проверке. Безопасность нового метода и подготовленного инварианта подтверждена **276 Java assertions (258 временных комбинаций)**, всего **9691 JVM assertions PASS** с полным набором. RL Stage3 повторно запустили, но он был прерван тайм-аутом; не объявлять его новым PASS. Предыдущий RL Stage2 успешно проходил 680187 assertions, его код Stage3 не изменён.

`git apply --check` и независимое применение patch к Stage2 с точным побайтовым совпадением 5 файлов PASS; 9 source-level Android интеграционных инвариантов PASS. **Ни реальное устройство, ни Gradle Android SDK, ни код 0.7.10/0.7.11 не проходили Stage3.** Архив и исправленные исходники опубликованы в Google Drive / ChatGPT Library `/LocalPilot Android/`: https://drive.google.com/drive/folders/1nhnjW4e3CAz8hQiOtKSosZGY2PVcEYOM. GitHub-отчёт [STAGE3_POST_ACK_VISUAL_CAUSALITY_2026-10-22.md](docs/labs/STAGE3_POST_ACK_VISUAL_CAUSALITY_2026-10-22.md).

Следующая проверка: **реальные исходники 0.7.11**, проверить что защита не сделана там уже, только затем перенести минимальный фикс; после полного Android build и e2e на телефоне подтвердить либо опровергнуть исправление. Не подменять Cloud новыми лабораторными файлами 0.6.

---

## STAGE 2: ПИКСЕЛЬНЫЙ CLOSED-LOOP И СВЕЖАЯ ОБРАТНАЯ СВЯЗЬ (22.10.2026)

- В отдельной исходной **0.6.0-dev** лабораторной копии найден и исправлен подтверждённый дефект: `ArcadePolicy.response` принимал изменение позиции объекта с той же / более старой меткой времени за подтверждение жеста. Теперь требуется строго новое usable-наблюдение, корректный профиль и ненулевой action. Тест до исправления падал, после проходит.
- С реальными Java классами этой ветки разработки (`ArcadeVision`, `ArcadePolicy`, `ActionGate`) выполнено 12 синтетических traffic closed-loop сценариев по пикселям, задержки 0/100/200мс и четыре варианта скорости; 12/12 преодолённых препятствий против 0/12 у неподвижного baseline, после каждого действия проверяется визуальный ответ, не просто ACK. Синтетическая физика упрощена, никаких утверждений об Android / 0.7.11 gameplay success.
- Подтверждён полный Java комплект 0.6-lab **9415 assertions PASS**, RL **680187 PASS**, отдельные arcade **44+9+87 PASS**. Патч `git apply --check` на независимой старой копии PASS, все 6 изменённых файлов совпали по SHA256.
- Новые артефакты `LOCALPILOT_ANDROID_STAGE2_VERIFIED_2026-10-22.zip`, `LocalPilot_Android_0.6.0_stage2_source.zip`, `LOCALPILOT_STAGE2_FEEDBACK_CLOSED_LOOP.patch` и отчёт сохранены: https://drive.google.com/drive/folders/1nhnjW4e3CAz8hQiOtKSosZGY2PVcEYOM и ChatGPT Library `/LocalPilot Android/`.
- **НЕТ** полного исходника версии 0.7.10 или 0.7.11 в этом checkout, Android SDK/телефона в нынешней среде; действующий APK 0.7.10 не обновлялся и никаких результатов реальных игр не заявлено. Новый патч нельзя применять поверх 0.7.11 без проверки. Документ подробностей: [Stage 2 lab report](docs/labs/STAGE2_VISUAL_FEEDBACK_AND_CLOSED_LOOP_2026-10-22.md).

---

## ИЗОЛИРОВАННЫЙ AUDIT НОВОГО КОНТРОЛЛЕРА 0.7.11 (22.10.2026)

Пользователь прислал код `ArcadeSystemTest.java` и **неполный** `ArcadeController.java`. Выявлены две ошибки в логике guards: принятие откатившегося / повторного timestamp в `decide`, обучение скорости руления по абсолютному dx при противоположном фактическом движении в `observeGesture`. Минимальный Java-харнесс **14 assertions PASS**, но реальный код 0.7.11 **не компилировался**, APK **не собирался**, исправления **не установлены**. В тестовом стенде отсутствовали сравнение predicted>=baseline и реальные stale/future timestamp сценарии. Подготовлен безопасный fail-closed patcher с SHA проверкой, кандидатами и бэкапами.

**Пакет:** https://drive.google.com/drive/folders/1nhnjW4e3CAz8hQiOtKSosZGY2PVcEYOM (`LOCALPILOT_0711_CONTROLLER_LAB_2026-10-22.zip`, `ARCADE_CONTROLLER_0711_REVIEW_2026-10-22.md`); **GitHub отчёт:** [ARCADE_CONTROLLER_0711_GUARD_AUDIT_2026-10-22.md](docs/labs/ARCADE_CONTROLLER_0711_GUARD_AUDIT_2026-10-22.md). Следующий шаг в реальном Cloud workspace: проверить актуальный SHA и source, применить точечно только подходящие guards, полноценно прогнать ArcadeSystemTest и e2e на устройстве. Старые исходники 0.4 и 0.6 нельзя подменять за 0.7.11.

---

## ЛАБОРАТОРНЫЕ ИСПРАВЛЕНИЯ 0.6.0 (22.10.2026)

Подтверждены и исправлены в **изолированной**, ранее загруженной исходной базе 0.6.0-dev две ошибки: masked ROI pixels=black могли имитировать калиброванного тёмного игрока; скорость/скролл продолжали рассчитываться после многосекундных пропусков кадров. 14 новых регрессионных проверок PASS; штатные 9319 JVM assertions + 680187 RL + 44 arcade PASS. Portable Git-патч воспроизводим и протестирован на чистом распакованном исходнике 0.6.0.

**Не применять этот патч к 0.7.10/0.7.11 без проверки свежих исходников; APK 0.7.10 не пересобирался.** Подробности, точные файлы и ограничения: https://github.com/GiVscSon/LocalPilot-Android/blob/feat/android-arcade-training/docs/labs/VISION_MASK_STALE_FRAME_0.6.0_2026-10-22.md . Патч, обновлённый ZIP и логи сохранены в https://drive.google.com/drive/folders/1nhnjW4e3CAz8hQiOtKSosZGY2PVcEYOM и Библиотеке ChatGPT `/LocalPilot Android/`.

---

## ПОЛУЧЕН И ПРОВЕРЕН APK 0.7.10-dev (22.10.2026)

**Последняя фактически полученная Android-сборка:** `LocalPilot_Android_0.7.10_dev.apk` (пакет `org.localpilot.android.dev`, versionName `0.7.10-dev`, versionCode26, Android11+/minSdk30, targetSdk35), **415914 байт**, SHA256 `4efcaa74f426074bc53c2ee59dcce6a3a647be046752945372e155d6a54928d1`.
- Проверка структуры ZIP CRC PASS, бинарного манифеста и APK v2 RSA/SHA256 (криптографическая подпись и content digest) PASS; сертификат SHA256 `55626377256dda3a6b5824b7278eb12db75b8a7d1c80baa9d2af4402ccac2684`, совпадает с APK 0.7.0, но НЕ с CI APK 0.4.0.
- Дельта от полученного 0.7.0 APK/code16: `GameViewport`, `GameAreaOverlay`, `RgbaFrameDecoder`, `SceneRecovery`, `GestureFlight`, `DiagnosticBundle`, `web/GoalReward`, `web/WebLessonStore` в DEX. Это бинарное наличие классов, **не результат испытаний на реальном телефоне**.
- Одновременно пользователь передал `GITHUB_PROJECTS_REVIEW_2026-10-21.ru.md`: сравнительный документ по FGA, Klick'r, Airtest, Droidrun, AndroidWorld, uiautomator2, GKD; документ не доказывает интеграцию чужих алгоритмов.
- Бэкап APK 0.7.10 + APK 0.7.0 + исходники 0.6.0 + обзор + аудит и манифест: https://drive.google.com/drive/folders/1nhnjW4e3CAz8hQiOtKSosZGY2PVcEYOM (`LOCALPILOT_ANDROID_0710_CHECKPOINT_2026-10-22.zip`, `AUDIT_LOCALPILOT_ANDROID_0710_2026-10-22.md`), копия в Библиотеке ChatGPT `/LocalPilot Android/`.
- **Не путать**: 0.7.10 = последний физически полученный APK, 0.7.11/code27 = ещё более поздняя **отдельная Cloud-версия по скриншотам**, исходники и APK не получены. Исходники 0.6.0 не пересоздают 0.7.10, актуальные исходники остаются в внешнем Cloud-workspace. Не применять старый checkout 0.4 поверх новых исходников.
- Приёмка на Android ещё не выполнена. Не удалять установленную 0.4.0-dev без сохранения приватных данных, т.к. её debug-подпись несовместима с текущей Dev-линейкой.

---

## ПОДТВЕРЖДЁННЫЕ ВЛОЖЕНИЯ 0.6.0 SOURCE + 0.7.0 APK (22.10.2026)

Из нового пользовательского загрузочного набора **независимо проверено**:
- `LocalPilot_Android_0.7.0_dev.apk`: package `org.localpilot.android.dev`, version `0.7.0-dev`/code16, min30/target35, 341064 байта; SHA256 `4ed95905aa89162cfad4d12deec861f5bd6b84cc8e9257c1ed190b5ebf032f1f`; APK v2 RSA/SHA256 signature **cryptographically verified**, certificate `55626377256dda3a6b5824b7278eb12db75b8a7d1c80baa9d2af4402ccac2684`.
- `LocalPilot_Android_0.6.0_dev_source.zip`: 230662 байта, 91 элемент, CRC PASS, SHA256 `1b73a854804a80e6a10727882956ea2594b8d26b260c1293891381253b69abaa`; независимые Java тесты Verify 9305 assertions PASS, RL Verify 680187 assertions PASS.
- **Критическая несовместимость обновления:** ранее выданный CI debug APK 0.4.0 имел тот же applicationId, но иной debug-certificate `b2d5475f207ec1e77d6d5716861a24e6b01b2e2894b832457cc7dc6bd88316e0`. Не удалять старую Dev-установку без экспорта приватных данных/чекпоинтов; Android обновление с несовместимой подписью не сработает.
- APK 0.7 включает классы `WebLearningActivity`, `WebVault`, `WebHttp`, `CaptureStartup`, `CrashDiagnostics` и INTERNET permission, которых нет в 0.6 источниках. Следовательно 0.6 ZIP **не полный source для APK 0.7**. Полного 0.7 source / APK 0.7.11 в этом checkout пока нет.
- Бэкап + матрица: https://drive.google.com/drive/folders/1nhnjW4e3CAz8hQiOtKSosZGY2PVcEYOM , `LOCALPILOT_ANDROID_070_CHECKPOINT_2026-10-22.zip` и `AUDIT_LOCALPILOT_ANDROID_2026-10-22.md`. Не смешивать 0.4 GitHub source, 0.6 source, 0.7 APK и 0.7.11 удалённый Cloud source. **Живую приёмку Android не выполняли.**

## ВНЕШНЯЯ ТОЧКА 0.7.11-dev, 22.10.2026: НЕ ПЕРЕЗАПИСЫВАТЬ НОВУЮ РАБОТУ

**Источник:** пользователь прислал четыре скриншота отдельного Cloud-проекта и скрипт финализации версии 0.7.11-dev/code27. Эта версия **не содержится в текущем GitHub checkout**, где исходники 0.4.0-dev/code8; нынешний GitHub CI подтверждает только 0.4.0-dev (`dd2d87e3fbea469bb47b91151b58ade62244b87a`, https://github.com/GiVscSon/LocalPilot-Android/actions/runs/39776351133). В отдельной Cloud-среде вероятное расположение новой версии: `/workspace/LocalPilot-Android`; **здесь доступа к этим файлам нет**.

0.7.11 заявляет предиктивный контур ArcadeVision -> ArcadeController -> ArcadeFeedback, симуляции управления машиной и птенцом, успешные JVM/Gradle проверки и сборку APK размером 430446 байт. Данные получены от пользователя и **не воспроизведены в этом checkout**; пиксельные тесты синтетические, настоящее устройство не проходило приёмку. SHA256 исходного контроллера по заявлению пользователя: `876dedcb3f8c47df7446d24ddf10a4c55eed7ef5304007f9d2abf1766710935c`. SHA256 APK в пользовательском фрагменте вычисляется локально, но само значение не передано.

**Новая общая точка сохранения:** https://drive.google.com/drive/folders/1nhnjW4e3CAz8hQiOtKSosZGY2PVcEYOM , файлы `CONTINUE_LOCALPILOT_ANDROID_2026-10-22.md` и `LOCALPILOT_ANDROID_0711_RECOVERY_2026-10-22.zip`. Последний пакет содержит паспорт и прошлые подтверждённые архивы, **но не содержит APK/исходники 0.7.11**.

**Следующие действия для агента с настоящим Cloud workspace:** сначала найти 0.7.11; прочитать git status, SHA-256, настоящие логи и результаты сборки; сделать полную резервную копию исходников и APK. **Не откатывать и не переносить старую 0.4.0 поверх 0.7.11.** Не публиковать приватные видео и секреты. Android и ПК LocalPilot остаются разными репозиториями. Пока свежая версия не перенесена, продолжение тестов 0.7.11 в GitHub невозможно.

---


21 октября 2026. Рабочая ветка **feat/android-arcade-training**, база36974ddddf839463b2c8ac55e47ea107a32358bc. Перед продолжением читать git status/HEAD и новые AGENTS.md; здесь применимых инструкций не найдено. Android не переносить в my-agent.

## Согласованные требования и реализация

Пользователь выбрал: обучение сначала на Android, затем перенос на ПК; сначала собственные симуляторы, потом сторонние игры; координаты объектов вместо CNN; пауза при уходе Activity в фон. Первый целевой реальный профиль — Doodle Jump с экранным управлением. Версия/кадры установленного Doodle Jump не получены.

Добавлены четыре оригинальных seeded среды JUMPER/PLATFORMER/LANES/JETPACK, общая физика60Hz и4 ticks/action, observation64. Pure-Java Double DQN64→64→64→actions, Adam/Huber, replay20000, gamma0,99, lr0,001, warmup1000, batch32, update/4steps, target/1000updates, epsilon1→0,05/50000steps. Точные контракты — training/README.ru.md и manifest checkpoint.

TrainingActivity предоставляет обучение/паузу/STOP/greedy-playback/оценку50seed/экспорт. Один worker, UI получает immutable сцены, обучение сохраняется при паузе и каждые30с. Checkpoint ZIP хранит весь trainer/replay/RNG/environment с SHA256 и строгой конфигурацией; атомарное сохранение и backup, восстановление корректного backup сохраняет повреждённый исходник отдельно. Приватные модели находятся в files/arcade-rl и не включены в Git/APK.

Автоуправление выбранной поддерживаемой игрой теперь default=true, настройка сохраняется. Повторных consent/preview диалогов в обычном авторежиме нет. InputPermit имеет session grant без искусственных лимитов действий/времени; ограниченный grant сохранён для диагностики. Отдельный Demo×10 остаётся ограниченным тестом. STOP отзывает epoch; возврат/перезапуск службы не возобновляет игру. Выбор приложения запускает автопоиск, неизвестный профиль не допускает автоматический ввод.

Панель160×48dp, фон55% непрозрачности, отдельные непрозрачные STOP/разворачивание. Перекрытие поля/жеста не разрешается прозрачностью. Журнал ротируется:8 сегментов до512КиБ, отдельныеv1 цепочки, экспорт ZIP; старый audit-v1.log не меняется, отдельная legacy-копия. Заполнение примеров выключает сбор с уведомлением, не игру; настоящая IO-ошибка останавливает ввод.

## Проверки и честные ограничения

Финальный статус сборки/подписи/упаковки — VALIDATION.txt. JVM:9224 прежних assertions и680187 новых RL/environment/checkpoint/session/journal assertions; это повторяемые численные проверки, не столько независимых Android-тестов. Численный градиент, уменьшение Huber-ошибки, exact resume, backup recovery и10000 session actions проверены. Четыре настоящих Gymnasium check_env прошли.

Эксперимент100000 шагов на жанр/24751 updates/50 evaluation seeds выполнен на Linux JVM, не Android. Результаты — docs/RL_EXPERIMENT_2026-10-21.ru.md: LANES/JETPACK лучше random в этом тесте, JUMPER не подтвердил преимущество, PLATFORMER регрессировал. Все четыре обученные политики не готовы для реальных игр. Рабочий тренер не равен успешной политике.

Физическое устройство недоступно; новый UI, обучение/нагрев/FPS/пауза Android, overlay и реальные жесты не проверены. Instrumentation скомпилирован, не выполнен. Старый software API30 стенд падал до установки APK с Package Manager Broken pipe и не имеет KVM; не повторять неизменённый запуск.

Пакетorg.localpilot.android.dev, version0.4.0-dev/code8, min30/target35, тот же Gradle debug-сертификат55...2684. Оригинальный org.localpilot.android не удалять. Перед работой сохранён проверенный git bundle: /workspace/localpilot-backups/android-before-rl-20261021/source.bundle.

## Воспроизведение

Из /workspace/LocalPilot-Android:

```bash
/workspace/android-build-tools/jdk-21.0.12.1+1/bin/java tests/Verify.java tests/legacy_parity.txt
/workspace/android-build-tools/jdk-21.0.12.1+1/bin/java tests/RlVerify.java
JAVA_HOME=/workspace/android-build-tools/jdk-21.0.12.1+1 GRADLE_USER_HOME=/workspace/android-build-tools/gradle-home ANDROID_HOME=/workspace/android-build-tools/sdk ANDROID_USER_HOME=/workspace/android-build-tools/android-user JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts ./gradlew --no-daemon -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
JAVA_HOME=/workspace/android-build-tools/jdk-21.0.12.1+1 python3 training/build_core.py
JAVA_HOME=/workspace/android-build-tools/jdk-21.0.12.1+1 /workspace/android-build-tools/rl-python/bin/python training/check_gymnasium.py
python3 tools/package_release.py
```

Изолированный PC Python venv сохранён в /workspace/android-build-tools/rl-python; версии зафиксированы в training/requirements-pc.txt. Android от него не зависит. Для продолжения модели на ПК: LOCALPILOT_CORE_JAR указывает на core-JAR, training/train_pc.py принимает --genre/--steps/--checkpoint. Проверен short continuation smoke; Windows не проверен.

Следующий обязательный device-pass: native обучение→пауза→resume→экспорт, auto2048 бездиалогов больше20 ходов, мини-игры, STOP во время поиска/dispatch, полупрозрачная панель/поворот/перекрытие. Затем отдельный профиль реального Doodle Jump; не объявлять его реализованным по симулятору.

GitHub API ранее Forbidden, Git read/push работает. Описание PR актуализировано в docs/PR_DESCRIPTION.md. Сравнение новой ветки: https://github.com/GiVscSon/LocalPilot-Android/compare/main...feat/android-arcade-training?expand=1 . Проверять фактическую публикацию/PR/CI, не считать их успешными по этому тексту.

## Исторический handoff 0.3.3

Следующий раздел сохранён для происхождения и прежних результатов; его лимиты/порядок разрешения ввода заменены текущими требованиями выше.

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
