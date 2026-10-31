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
также границу прилавка, `belowCounterRejected=N` и `counterConfirmed=true/false`.
Значение `N` считает в **текущем кадре** геометрически подходящие зелёные
компоненты, отсеянные за границей прилавка. Оно не является счётчиком за всю
игровую сессию; при `counterConfirmed=false` положение прилавка не подтверждено.
Диагностика не меняет решение о жесте и не использует глобальный счётчик.
Для 2048 даже при `2048_READ_REJECTED` печатается `DIAG_2048_CELL_R2C4`:
центр клетки по CSV ROI, цвет кольца (RGB/HSV), разброс яркости и центральный
пиксель. Это измерения, а не разрешение угадывать неизвестные плитки.
Геометрия для ROI [54,825,638,1409]: R2C4 = (565,1044); реальный JPG
не входит в опубликованный набор. Запуск через Gradle фиксирует UTF-8.

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

## Проверка полноты комплекта кадров (fail-closed, 31.10)

CSV-разметка теперь является обязательным списком ожидаемых кадров: если строка
имеется, а файл отсутствует, результат `MISSING_IMAGE`, ненулевой exit code.
Пустая папка с непустым CSV также **FAIL**, а не `SKIP`. Без указанной папки
или с пустой папкой без CSV штатный CI продолжает печатать `SKIP`.
Отклоняются некорректные координаты исключаемой области (`INVALID_EXCLUDED_ROI`)
и JPG/PNG больше 32 МиБ (`IMAGE_FILE_TOO_LARGE`). Это проверки входных
данных, **не улучшение игрового распознавания**. Синтетический контракт
`java -Djava.awt.headless=true tests/LocalJpgContractVerify.java` запускается
в Android build workflow без реальных скриншотов и проверяет, что неполный
комплект или немаркированный снимок никогда не дают успеха.

Архив `LocalPilot_Point3_JPG_Runner_and_Acceptance.zip` содержит runner,
CSV и офлайн-отчёт, но **не содержит сами десять JPG**. Его `offline-report.txt`
фиксирует 2048_READ_REJECTED и несколько `usable=false`; это исторический
FAIL для конкретных кадров, а не воспроизведённый PASS на текущем HEAD.
До повторного запуска реального набора и приёмки на физическом устройстве PR остаётся Draft.

## Дополнительные синтетические проверки (31.10)

Контракт offline replay проверяет исходную 32 на первом кадре 2048, цвет кольца R2C4, два отвергнутых объекта за FLAPPY-прилавком и изоляцию диагностики от управляющего ActionGate. Синтетические PNG не подтверждают качество распознавания отсутствующих десяти реальных JPG и не заменяют физическую приёмку APK.
