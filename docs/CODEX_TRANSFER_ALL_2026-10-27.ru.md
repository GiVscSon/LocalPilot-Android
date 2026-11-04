# CODEX TRANSFER / LocalPilot-Android / 27.10.2026

СТАТУС: единая передача материалов текущего разговора и инженерных результатов. Документ создан по прямому чтению GitHub/Google Doc и пользовательскому техническому ТЗ; не свидетельствует о запуске Codex-чата, прохождении физического e2e или слиянии PR.

## 0. Обязательные ссылки / порядок чтения
1. Этот документ: docs/CODEX_TRANSFER_ALL_2026-10-27.ru.md.
2. docs/ROADMAP_0.7.34.ru.md, docs/RELEASE_0.7.34.ru.md и docs/LOCALPILOT_UNIFIED_HANDOFF_2026-10-27.ru.md в активной ветке.
3. AGENTS.md и LOCALPILOT_ANDROID_HANDOFF.md, затем app/build.gradle и реальные исходники.
4. Основная рабочая ветка-кандидат Android 0.7.34-dev/code50: fix/grid-diagnostics-bubble-hud-20261027, draft PR #8: https://github.com/GiVscSon/LocalPilot-Android/pull/8.
5. Предыдущая стабильная база разработки Android 0.7.33-dev/code49: sync/localpilot-android-0711. Не строить Android из исторической main.
6. Альтернативная изолированная P0-ветка с кодом 0.7.33-dev/code49: fix/localpilot-0734-p0-panel-grid-20261027, draft PR #6: https://github.com/GiVscSon/LocalPilot-Android/pull/6.
7. Issues: #1 Match World https://github.com/GiVscSon/LocalPilot-Android/issues/1 ; #2 Grid 7/36 ; #3 Bubble HUD ; #7 emulator acceptance https://github.com/GiVscSon/LocalPilot-Android/issues/7.
8. Общая PC/Android точка в Google Docs: https://docs.google.com/document/d/1d_U4NLmOQTyzvRQ1PdUVMkLGh0-eH_PCwleFPetbFK8/edit.

ВАЖНО: две P0-ветки разошлись. PR #8 с code50 является более новым отдельным кандидатом и НЕ содержит автоматически коммиты PR #6. Разбирать PR #6 как набор экспериментальных решений, а не сливать/черри-пикать вслепую. Перед любым кодом проверить текущие git status, HEAD, базу, diff, подписант и сохранить backup. Коммиты документов меняют HEAD и требуют повторного CI для нового HEAD.

## 1. Переданное пользовательское ТЗ: 6 этапов

Этап 1 — baseline: JDK17+ / Android SDK35 / Gradle, tools/Compile.java, build_debug.py, tests/Verify.java, tests/SystemVerify.java, исходный signer и checkpoints. Чистый JVM/Gradle baseline и сравнение с 0.7.33. Не менять рабочее приложение без бэкапа.

Этап 2 — Grid: GridPuzzleVision, GridPuzzleSession, GridPuzzlePolicy. Цель — 6x6 «Нащупай ход» (7 UNKNOWN/36), 7x7 «Мэтч Ворлд» (PANEL_COVERS_ROI и 3 ACK без проверенного изменения). Нужны диагностические причины UNKNOWN по (row,col), работа со спецэлементами и белой подсказкой по реальным ROI/палитре, отдельная проверка правил swaps/trace. Не пропускать UNKNOWN в live без строгой проверки только затронутых клеток и сцены.

Этап 3 — панель/capture: PilotService, PanelDocking/GameAreaOverlay/RegionPickerGeometry, RgbaFrameDecoder и CapturePacer. Компактная STOP-панель вне boardRoi/visualRoi, проверка фактического расположения после WindowManager layout, при невозможности безопасного размещения STOP. Отделить takeScreenshot от MediaProjection; сбросить или чётко размечать исторические счётчики.

Этап 4 — Bubble/Jumper/Arcade: Шипучка и возможный «шарик-бомба», Ари с wrap, Котобашня/Stomp как неподтвержденная механика, Карканоид с отслеживанием мяча, спускающихся блоков и линии поражения. Режим shadow до подтверждённых правил и hitboxes.

Этап 5 — Гексон/результат: отдельная HexaVision/HexaBoard/HexaPolicy только после реальной разметки сот/правил; GameOverScreen/RoundOutcome autoRestart только явное opt-in, лимиты раундов, безопасная идентификация кнопки (не реклама/покупка/авторизация), проверка следующего кадра после ACK.

Этап 6 — релиз: тесты *.java Verify, GridVerify, BubbleVerify, RuntimeVerify, ResultVerify, UniversalVerify, SystemVerify, PanelDockingVerify, release-tooling tests; lint/assembleDebug/compileAndroidTest; три последовательных API30/35/36 на одном SHA; физический POCO Android, один подтверждённый swap, пять подряд post-ACK VERIFIED ходов; подпись/обновление без потери данных. Только затем стабильный 0.7.34.

## 2. Реально подтверждено по исходникам (не путать с пользовательскими гипотезами)
- app/build.gradle в PR #8 = 0.7.34-dev/code50, minSdk30, targetSdk35, debug applicationId org.localpilot.android.dev (основной applicationId org.localpilot.android).
- GridPuzzleVision.MAX_TYPES=24, не 8; увеличение до 12 — не исправление. Ограничение качества и semantic class/palette, usable и unknown — отдельная тема. specialCellsMask в Java = BitSet, НЕ 64-битный long: сетки допускают до 10x10, поэтому long потеряет 36 позиций. protectSkulls использует семантическую проверку и включается в соответствующем профиле.
- GridPuzzlePolicy.TRACE_LINE уже поддерживает прямые горизонтальные, вертикальные и диагональные цепочки. Ломаные полилинии из восьми соседей НЕ эквивалентны существующему gesture. «Нащупай ход» (selectedProfile=11) сейчас SWAP_MATCH3 с защитой черепов; пользовательское утверждение про диагональную цепочку проверить вручную прежде чем менять профиль.
- GridPuzzleSession: FRESH=300ms, STABLE=80ms, SETTLE=180ms, timeout=2500ms; ACK не равен игровому результату; нужны два свежих устойчивых post-ACK кадра. Нельзя считать specialInvolved=1 достаточным подтверждением, иначе меняющаяся подсказка выдаст ложный прогресс. В PR #8 консервативный verifier пока не заменён.
- JumperState.horizontalDistance уже реализует wrap, не дублировать.
- В Карканоиде мяч анализируется как ArcadeObservation.player; механическое закрепление несуществующего BALL в шести слотах RL некорректно.
- GameAreaOverlay — редактор ROI; основная плавающая STOP-панель, compact и movePanelAway в PilotService. Не создавать конкурирующий контроллер оверлея.
- CapturePacer различает throttled/notWanted/invalidClock. SkippedFrames не является числом ошибок CV.
- Раздельные контуры: Grid/2048/Bubble используют Accessibility takeScreenshot; arcade/Jumper используют MediaProjection. На пользовательских снимках decode/skippedFrames стояли на месте, когда READ рос; возможно исторические показатели предыдущего видеосеанса, не доказательство медленного RGBA decoder.

## 3. Что реально сделано в двух PR и как интегрировать

PR #8 — актуальный кандидат code50:
- PanelDocking.choose: чистая Java, подбор кандидатов вне ROI; после WindowManager layout проверяется фактический panelBounds, при overlap безопасная остановка. 5000 псевдослучайных конфигураций JVM заявлено в тестах.
- GridPuzzleVision.Observation даёт reason(row,col), unknownIndices, unknownSummary() для локализации 7/36, не превращая UNKNOWN в игровую фишку.
- Compact Bubble HUD пишет «Шипучка», а не старую 6x6 сетку; улучшены документы и тестовый контур.
- В коде 0.7.34-dev уже есть подписанные изменения app/build.gradle. НЕ стабилен, физическое устройство NOT RUN, причины UNKNOWN на реальной игре ещё не устранены.

PR #6 — отдельный ранний P0-эксперимент на code49, head f034c9450944262e9ab966136984fd505aa44933:
- Новый PanelParking.java + PanelParkingTest.java; PilotService.movePanelAway с гарантированным 16dp отступом для обычной ROl, корректный отказ при отсутствии места. Он менее развит относительно PR #8: нет той же post-layout повторной гарантии, возможны различия по fullscreen.
- PilotService.stop сбрасывает video captureSession/статистику, режимный HUD сохраняет исходные ключи decode/skippedFrames и указывает source=takeScreenshot или MediaProjection.
- GridPuzzleSession сохраняет beforeSpecial и допускает mask-only изменение доски как visual response после ACK и двух стабильных кадров; новые тесты GridPuzzleSessionTest.
- ВНИМАНИЕ: последнее mask-only правило рискованно на анимированных подсказках. Требует offline false-positive tests на настоящих последовательностях, только потом перенос.
- Не смешивать одновременно PanelParking и PanelDocking; сравнить coverage, screen insets, post-layout, Stop, negative cases, API30 и использовать единственную реализацию.

## 4. Проверки (раздельная provenance)

PR #6: GitHub Build/JVM SUCCESS https://github.com/GiVscSon/LocalPilot-Android/actions/runs/40488378398, code49/f034c945. Emulator matrix https://github.com/GiVscSon/LocalPilot-Android/actions/runs/40488378391: API35 PASS, API36 PASS, API30 FAIL. Первая конкретная API30 ошибка: java.lang.AssertionError: Android screen capture consent did not appear, PilotUiInstrumentation.checkProjectionLifecycle:546, await:1041. Лог ранее показывает временные adb daemon connection failures; связь с первопричиной НЕ доказана. Успешный Build не означает финальное устройство acceptance.

PR #8: ранее JVM/Build SUCCESS на b1adb3e7 https://github.com/GiVscSon/LocalPilot-Android/actions/runs/40488512182; code50 и последние коммиты требуют свежей матрицы на точном HEAD. Предыдущие фактические device сбои собраны в Issue #7: API30 (нет видимой строки выбора окна в bindWindow, Run 40487871145); API35 (нет кнопки «Показать панель», Run 40488255281). Дополнительные уточнения Issue #7: API35 foreground системный диалог Quickstep isn't responding, вероятный системный ANR, не доказанный баг кнопки; API30 focused TYPE_APPLICATION root присутствует, chooseWindow зависает «Ожидаю список окон Android…», возможный windowRoot/metadata gap, root cause не доказан. Есть флак AuditExportTest.liveWriter с journal busy, тестовую семантику правили в 9e155f23; production SafeJournal/RotatingJournal не изменять для обхода теста. Physical Android / реальные Яндекс Игры = NOT RUN.

APK из PR #6 доступен как CI artifact в Build run, но signer CI отличается от сертификата уже установленной dev-версии. Это НЕ бесшовное обновление и не релиз 0.7.34. Не просить удалить приложение и не терять learning/checkpoints. Прошлые архивы пользовательских исходников и APK могут быть недоступны; GitHub исходники актуальнее.

## 5. Новые инженерные советы пользователя: реализовывать только после экспериментов
1. ROI decode + stride 2/4 вместо полного кадра — гипотеза экономии памяти. Контролировать pixelStride/rowStride, rotation, frames ownership, zero-allocation, различие frame skip по pacing/idle. «16x fewer sampled pixels» НЕ равно «16x faster end-to-end»; 70% меньше нагрев и 4–5x speedup НЕ измерены. Сначала профилировать CAPTURE/DECODE/CV/ACK p50/p95/p99.
2. Auto-docking: уже в PR #8; не прятать STOP/панель на 16ms вместо истинной проверки ROI.
3. Scene Signature: четыре угловых пикселя недостаточны для достоверного профиля неизвестной игры; только безопасная подсказка после нескольких стабильных кадров, ручная авторизация режима, сохранение window ID/ROI/session.
4. Auto-restart: только explicit opt-in, ограничение по числу раундов, проверенная кнопка, запрет рекламы/платежа, fresh state, ACK+post verification.
5. Special boosters Match World / Bubble bomb: обучить только на реальных сэмплах; отдельные классы и trackable mask, не объявлять любую тёмную фишку CELL_BOOSTER=99 и не делать свайп только по оттенку.
6. Gesture timing: тестировать наблюдаемый результат swipe 110–160ms vs controls, не вводить случайный jitter для имитации человека или обхода антибота, не переопределять GestureFlight/ActionGate.
7. HSV/CIE LAB: эксперимент с контролируемым dataset при разной яркости; статистика false pos/neg и сохранённая palette v1; никакой несовместимой миграции без конвертера.
8. Training/train_pc.py / 500k steps / checkpoint: сравнить реальную симуляционную физику с рынком. 500k за 3–5 минут и «идеальная реакция» не доказаны; нельзя считать подмену synthetic reward настоящей игровой наградой.
9. Разделять накопленные capture счетчики / хранить lastSessionReport без загрязнения нового режима. Числа 2370/4090 и 699/2452 на прежних скриншотах вероятнее stale UI, но доказать через session IDs.
10. Жёсткие ограничения на обучающие примеры нужно изучать по конкретному хранилищу; нельзя безусловно считать «1000 examples» глобальным пределом и очищать данные без проверенного экспорта.

## 6. Безопасность, контракт и инварианты, необходимые Codex
- package org.localpilot.android; политики чистая Java, без android.* импорта; Gradle Java17. Stage CV не аллоцирует избыточные массивы в pixel loops; для Grid может быть BitSet/маски свыше 64 клеток. Проверить тест VisionAllocationTest и существующие Scratch, не обобщать неподтвержденный zero-allocation на весь метод.
- Все действия через ActionGate.Ticket, InputPermit, GestureFlight, один executor; STOP, TTL, window owner, generation, session, ROI, frame freshness. ACK без подтверждения исхода не даёт reward или новый gesture. Pending/timeout fail closed. No clicks on UNKNOWN/untrusted result screen.
- Сохранять LOCALPILOT-WEB-v3, localpilot-dqn-v1, legacy_parity, SafeJournal/RotatingJournal SHA-256, старые checkpoints и production signer. Не публиковать исходные пользовательские изображения, модели, ключи, сертификаты.
- Отдельный PC LocalPilot: JEV стратегический, быстрый CPU моторный контур, Ollama основной, LM Studio fallback, EXL3 эксперимент, PPO/GAE/BC строго по provenance. Android/PC репозитории и релизные пайплайны не смешивать; код Android не подтверждает наличие JEV в APK.

## 7. Следующее действие Codex с максимальным эффектом
A. На PR #8 прочитать git HEAD и последние CI; исправлять P0 emulator API30 window-root / API35 Quickstep-system ANR distinguish, и flaky AuditExport Test только по точному логу. Добиться 3 последовательных зелёных матриц API30/35/36 на одном head, без отключения проверки безопасности.
B. Для grid добавить ограниченный trace CAPTURE→READ→POLICY→GESTURE→ACK→POST_ACK_VERIFY с frame/session/generation/window/ROI и конкретной причиной отказа; без пикселей пользователя в логах.
C. Получить сохранённые ROI и две реальные 6x6 сцены с неизвестными 7 клетками; повторить offline GridVision в разных освещениях, сократить UNKNOWN без ложного принятия черепа/динамита.
D. Реальная физическая приёмка на POCO: безопасный STOP и overlay; 1 валидный swap, затем 5 последовательных verified post-ACK действий; не трогать пользовательскую установку без бэкапа и совпадения signer.
E. После P0 по очереди бустеры, угрозы Карканоида, Гексон, авто-раунды, обучение. Не заявлять ускорение или автономные награды до метрик.

Полезные команды из актуальной дорожной карты: java tests/GridVerify.java ; java tests/Verify.java tests/legacy_parity.txt ; java tests/PanelDockingVerify.java ; java tests/BubbleVerify.java ; java tests/RuntimeVerify.java ; java tests/ResultVerify.java ; java tests/UniversalVerify.java ; java tests/SystemVerify.java ; python3 -m unittest discover -s tests -p test_release_tools.py -v ; python3 build_debug.py --gradle gradle ; python3 tools/package_release.py (только после verified APK).

Метод отчёта: на каждом изменении таблица/матрица PASS, FAIL, NOT RUN; точные commit SHA, ссылка workflow/job, первая ошибка и отличие от последней успешной проверки; итог блокер + один проверяемый следующий шаг + критерий перехода. Код править безопасно в Android ветке; общий handoff + Google Doc обновить. Другие беседы/сессии Codex автоматически не получают новое содержимое без явного чтения этих файлов.
