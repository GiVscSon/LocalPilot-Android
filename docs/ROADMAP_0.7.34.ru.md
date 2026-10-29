# LocalPilot Android 0.7.34: матричная инженерная дорожная карта
Дата: 27.10.2026. **Версия 0.7.34-dev = кандидат разработки, НЕ релиз для установки на основное устройство.** Исходная база: `sync/localpilot-android-0711` (0.7.33-dev/code49), рабочая ветка: `fix/grid-diagnostics-bubble-hud-20261027`.

Общие точки: [Unified handoff](LOCALPILOT_UNIFIED_HANDOFF_2026-10-27.ru.md), [Codex AGENTS.md](../AGENTS.md), [главный PC/Android Google Doc](https://docs.google.com/document/d/1d_U4NLmOQTyzvRQ1PdUVMkLGh0-eH_PCwleFPetbFK8/edit).

## Цель распознавания, уточнённая пользователем 29.10.2026

Сначала определять объект по форме, границам и геометрии; цвет использовать как атрибут уже найденного предмета. Наблюдать объект между кадрами и переносить его идентичность при небольшом изменении масштаба, освещения, текстуры и окружения. При неоднозначности повторно наблюдать и восстанавливать состояние без неподтверждённого действия. Критерии — размеченные реальные кадры и их преобразования, отдельные ложные срабатывания на фоне и спецобъектах, затем проверка последовательностей и post-ACK результата.

Первый ограниченный шаг для Bubble: [объектная проверка до цветового класса](BUBBLE_OBJECTS_FIRST_2026-10-29.ru.md). Универсальное сопровождение объектов и полностью независимый от палитры начальный поиск пока не завершены.

## Нельзя принимать советы на веру
- `GridPuzzleVision.MAX_TYPES=24` уже больше предложенных «увеличить до 12»; фактическое ограничение допускаемой сцены `distinct.size()<=12` и консервативность диагностики. Смешивать 9 семантических предметов с 9 чистыми RGB-кластерами опасно.
- `TRACE_LINE` уже поддерживает прямые диагональные линии. **Ломаные** 8-связные маршруты требуют полилинейного `dispatchGesture` и подтверждения правил игры.
- `JumperState.horizontalDistance()` уже учитывает wrap. У `ArcadeObservation` нет `BALL` в шести RL слотах, `ArcadePolicy.paddle()` отдельно отслеживает `player` как мяч. `ArcadeEnv` = синтетическая физика других жанров, не модель Карканоида.
- `GridPuzzleSession` не имеет «порога 7 неизвестных»: текущие `Observation.usable()`, `complete()` и `GridPuzzlePolicy.known()` прекращают управление при **любой** недоказанной клетке. Не ослаблять без нового независимого верификатора.
- Android ACK не равен успешному жесту. Нельзя принимать порог 2/3 изменённых клеток или изменение маски как достаточный результат: анимация/подсказка могут его вызвать.
- `CapturePacer` считает `throttled`, `notWanted`, `invalidClock`. Большое число skippedFrames может быть намеренной экономией, не медленным `RgbaFrameDecoder`. Не обещать 4–5× прироста без p50/p95 wall-clock, CPU, thermals, battery.
- `GameAreaOverlay` отвечает за выбор ROI, а плавающая панель STOP и `movePanelAway()` реализованы в `PilotService`. Не добавлять туда второй несогласованный контроллер.
- Автоматические повторы раундов, бустеры, касания «Играть ещё» не включать в Live без доказанного распознавания кнопки и окна. `Stomp` и особые шары пока эксперимент.

## Шесть этапов, измеримые критерии, текущий статус

### Этап 1. Baseline и резервная копия
**Файлы/среда:** `app/build.gradle`, `build_debug.py`, `tools/Compile.java`, `tests/Verify.java`, `tests/SystemVerify.java`, Android SDK35/Gradle8.9/JDK21, сохранённый signer.

- [x] Прямо сверены базовые исходники 0.7.33 в `sync/localpilot-android-0711`; в отдельной ветке открыты изменения без force push.
- [x] [GitHub Build/JVM 40488512182](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/40488512182) на коммите `b1adb3e7`: SUCCESS; это не Android e2e.
- [ ] Полный emulator acceptance на одном head API30/35/36. По реальным логам, до новых игровых изменений часть запусков падает: [issue #7](https://github.com/GiVscSon/LocalPilot-Android/issues/7).
- [ ] Перед установкой поверх прежнего APK сверить `applicationId` и SHA-256 **подписывающего сертификата**, backup private learning/checkpoint. Разный `org.localpilot.android` vs `.dev` не позволит обновить то же приложение, подпись может отличаться. Не удалять данные.

**Граница этапа:** Java/Gradle базовый PASS; emulator/phone NOT PASS.

### Этап 2. Клеточные игры: «Нащупай ход» и «Мэтч Ворлд»
**Файлы:** `GridPuzzleVision.java`, `GridPuzzlePolicy.java`, `GridPuzzleSession.java`, `PilotService.java`, `tests/GridVerify.java`, `tests/GridPuzzleVisionTest.java`, `tests/GridRecordedTest.java`.

- [x] Безопасная часть реализована: `GridPuzzleVision.Observation` умеет `reason(row,col)`, `unknownIndices()`, `unknownSummary(limit)`; в интерфейсе первые 3 причины с координатами. Existing semantic protectedSkull/UNKNOWN сохранены.
- [x] Юнит-матрица на синтетических клетках и масках дополнена и входит в CI.
- [ ] Повторить офлайн-анализ двух скриншотов 6×6 (11:58/12:13) с **реально сохранённым ROI и палитрой**: причины 7 неизвестных по каждой клетке, без классификации спецэлемента как обычного.
- [ ] Разметить специальные чёрно-оранжевые предметы и руку-подсказку только с реальными эталонами, затем замер ложноположительных срабатываний.
- [ ] 8-neighbor polyline и quests-only chainLength=4: внедрять после воспроизводимой ручной механики, собственной проверки изменения доски и safety policy. Не заменять straight TRACE_LINE слепой цепочкой.

**Граница этапа:** диагностирование подготовлено, но 7/36 UNKNOWN и неверифицированные 3 жеста на физическом Android пока не устранены.

### Этап 3. Автопарковка и производительность
**Файлы:** `PanelDocking.java` (новый изолированный pure-Java), `PilotService.java`, `GameAreaOverlay.java`, `CapturePacer.java`, `RgbaFrameDecoder.java`, `tests/PanelDockingTest.java`, `tests/PanelDockingVerify.java`.

- [x] Исправлено размещение компактной STOP-панели: `PanelDocking.choose` ищет пригодный прямоугольник со смещением у границ ROI, а не лишь в 4 углах; fallback без отступа только если не закрывает поле.
- [x] На старте Grid/Bubble/2048/AutoBoard сначала геометрия, затем **фактическая повторная проверка после layout**. При неудаче STOP без жестов и фальшивого подтверждения.
- [x] Добавлена property-матрица 5000 псевдослучайных прямоугольников; `java tests/PanelDockingVerify.java` включен в CI, BUILD PASS на `b1adb3e7`.
- [x] В рабочей ветке компактный Bubble HUD указывает `Шипучка · READ` вместо ложного `6×6`.
- [ ] Отдельно измерить кадры и реальные причины `CapturePacer`: pacing/idle/clock и decode p50/p95, сколько времени занимают `Image.Plane` и actual `Bitmap`. ROI decode и stride использовать только после эталонной проверки идентичности кадра, ориентации, pixelStride/rowStride, памяти и цветов; не обнулять счётчики ради красивого HUD.
- [ ] Динамические игры с полноэкранной ROI: в пределах экрана **может не существовать** места для панели вне изображения. Отдельный UX/safe-stop, без обещания «никогда не перекрывает».

**Граница этапа:** JVM/сборка PASS; геометрия на реальном POCO и emulator gameplay не подтверждена.

### Этап 4. Gameplay booster/jumper/arcade
**Файлы:** `BubbleVision`, `BubblePolicy`, `JumperState`, `JumperPolicy`, `ArcadeObservation`, `ArcadePolicy`, `ArcadeController`, `tests/BubbleVerify`, `tests/ArcadeVerify`.
- [ ] Shadow Mode для шарика-бомбы: сначала доказать местоположение/заряд/эффект по двухкадровому тесту, затем разрешить отдельное действие по игровому ROI, не по одной оранжевой точке.
- [x] Wrap Ари реализован в исходниках (не изменялся).
- [ ] Stomp Котобашни после измерения collision semantics, playerVy и хитбоксов, без определения HAZARD как безопасного по одной скорости.
- [x] Мяч Карканоида — выделенный `ArcadeObservation.player`, а не отсутствующий RL слот BALL.
- [ ] Descending blocks/line of defeat, прогноз баллистики и риск по кадрам, offline baseline-vs-new; затем Shadow→Live после безопасности.

### Этап 5. Гексон и результат раунда
**Файлы:** новые `HexaBoard/HexaVision/HexaPolicy`, `GameOverScreen`, `RoundOutcome`, `PilotService`, `tests/ResultVerify`.
- [ ] Hex: 6 соседей, реальные ориентации/слоты/стопки и правила сортировки. Пока ни один оптимальный шаг не доказан.
- [ ] Детектор неопасной кнопки «Играть снова» без покупки, рекламы, запроса авторизации; двойная post-ACK проверка перехода.
- [ ] Авто-перезапуск только явное opt-in с ограниченным числом раундов и сохраняемым STOP, без заявлений об успешном «фарме».

### Этап 6. Верификация, подпись, установка
- По одному и тому же окончательному head выполнить:
  `java tests/Verify.java tests/legacy_parity.txt`,
  `java tests/GridVerify.java`,
  `java tests/PanelDockingVerify.java`,
  `java tests/BubbleVerify.java`,
  `java tests/RuntimeVerify.java`,
  `java tests/ResultVerify.java`,
  `java tests/UniversalVerify.java`,
  `java tests/SystemVerify.java`,
  `python3 -m unittest discover -s tests -p 'test_release_tools.py' -v`,
  `python3 build_debug.py --gradle gradle`,
  `python3 tools/package_release.py` (**пакует уже собранный и проверенный APK**, не является заменой сборки).
- Внимание: `./gradlew` использовать только если wrapper в конкретном checkout присутствует, иначе репозиторий CI использует Gradle 8.9 из setup-gradle.
- Android device acceptance API 30/35/36 должен пройти без пропавшего target row и кнопки панели, **на одном head**, а физический Android обязан проверить безопасность STOP, корректность окна, панель/ROI и 5 подтверждённых ходов. Скриншоты/лог пользователя не коммитить.
- **0.7.34 выпускается только после всех заявленных релизных тестов, подтверждения реального сертификата и безопасного обновления без потери данных.** Пока `0.7.34-dev` в ветке = технический кандидат.

## Точка передачи Codex
Прочитать `AGENTS.md`, этот план, `docs/LOCALPILOT_UNIFIED_HANDOFF_2026-10-27.ru.md`, issues #1/#2/#3/#7 и реальные исходники. Для каждой задачи: baseline → fixture → shadow → JVM → emulator → physical device → GitHub docs/Google Doc. Обновлять PASS/FAIL/NOT RUN с точным head и ссылками на job. **Нельзя утверждать, что другой чат Codex уже прочитал изменения**.
