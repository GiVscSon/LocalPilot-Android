# LocalPilot: пункт 3, offline JPG replay и физическая приёмка

## Offline replay

JDK 17+ с ImageIO. Фотографии **не помещать в GitHub**; копировать только в локальную папку.

Из корня репозитория:
```bash
java -Djava.awt.headless=true -Dlocalpilot.jpgDir=/path/to/screens tests/LocalJpgVerify.java
./gradlew localJpgReplay -Dlocalpilot.jpgDir=/path/to/screens
./gradlew testDebugUnitTest -Dlocalpilot.jpgDir=/path/to/screens --info
```

В папке с JPEG/PNG нужен `localpilot-frames.csv`, его строки содержат
`filename,profile,left,top,right,bottom`, опционально
`,excludedLeft,excludedTop,excludedRight,excludedBottom`.
Координаты задаются **в пикселях файла**, а не физического дисплея.
Поддерживаются `BUBBLE`, `GRID6` (protected-skull swap), `GRID7`, `2048`, `PADDLE`, `TRAFFIC`, `FLAPPY`.
Несопоставленные изображения получают `UNVERIFIED` и ненулевой exit code;
исключения/отказы ридера получают `FAIL`. Если папка не задана или пуста,
проверка печатает `SKIP` и не ломает штатный CI.
`OBSERVED` означает вызов реального ридера, **не** доказательство правильного распознавания или хода.

Ридер сетки выводит долю найденных клеток, `CellReason` с координатами,
`partialUsable` и protected-skull. Bubble даёт число шаров, фазу короткого ряда,
разорванные компоненты и причину отказа. 2048 пробует `PixelBoardLocator`,
`Color2048Reader`, запасной `PixelBoardReader` и считает распознанные 32.
Аркада показывает `usable`, уверенность, координаты препятствий, у FLAPPY
также границу прилавка. **Ограничение:** `ArcadeVision` не экспортирует отдельный
список отвергнутых зелёных компонентов ниже прилавка; `belowCounterRejected=not_exposed_by_ArcadeVision`
честно фиксирует эту недостающую диагностическую метрику.

## Приёмка APK на двух устройствах

1. Проверить `applicationId`, `versionName`, `versionCode`, SHA-256 сертификата установленного
   APK и нового: `adb shell pm list packages | grep localpilot`,
   `adb shell pm path <package>`, `adb pull <apk-path> old.apk`,
   `apksigner verify --print-certs old.apk`,
   `apksigner verify --print-certs app-debug.apk`. Обновлять с сохранением данных
   через `adb install -r` **только** при одинаковом пакете и подписи.
   `org.localpilot.android.preview`, `org.localpilot.android.dev` и `org.localpilot.android`
   не являются одной программой. Не удалять preview и модели/ROI.
2. 2048 на планшете и телефоне: STOP-панель не касается ROI, `PANEL_COVERS_ROI=0`,
   `кадры>=1`, `READ>=1`, присутствующая на доске 32 распознана без
   `UNKNOWN/PARSE_REJECTED`, не меньше пяти подряд `SENT->ANDROID_ACK->VERIFIED`.
   Нет `2048_POST_ACK_TIMEOUT` и `FOCUS_PAUSED`.
3. Сетки 6×6 protected-skull **SWAP** и 7×7: ручной ROI на телефоне
   `[103,1241][1099,2262]` требует повторной геометрической привязки к окну
   `ru.beru.android`; не делать жесты по черепам/UNKNOWN и не зависать бесконечно
   в `WAIT_STABLE`. Для каждой игры нужны пять подряд подтверждённых ходов,
   два стабильных кадра после осыпания, `visual_changes>0`. Лог
   `localpilot-experience-v1.log` должен содержать данные, а не только заголовок 25 байт.
4. После серии экспортировать `Locpiand.zip`: в `session.txt`
   проверить `GRID_SESSION/2048 dispatched>=5, verified>=5`. При STOP
   должны присутствовать актуальные `armedBounds`, `currentBounds`,
   точные `session/epoch` и причина текущего отказа, отдельно от исторического
   `LAST_STOP`.

До фактического теста на двух устройствах PR #44 оставлять Draft.
Сохранить ActionGate, InputPermit, TTL, эксклюзивный исполнитель и аварийный STOP.
