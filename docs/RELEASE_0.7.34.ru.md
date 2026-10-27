# LocalPilot Android 0.7.34-dev / code50
27.10.2026. **Инженерный кандидат (RC), НЕ заявленный релиз.** Живая партия на POCO X7 Pro и проверка обновления поверх старого APK не проводились.

## Главное изменение
- `PanelDocking.java`: самостоятельный алгоритм подбора безопасного места для компактной плавающей STOP-панели относительно ROI; горизонтальные/вертикальные интервалы, минимальное перемещение, учёт safe area, безопасный отказ при отсутствии свободного места.
- `PilotService.java`: для Bubble/Grid/2048/AutoBoard вызывает docking при старте и **проверяет фактическое отсутствие перекрытия после layout**. Если WindowManager изменил расположение и панель всё ещё мешает игре, вход не начинается. Старое сообщение `PANEL_COVERS_ROI` не отключено.
- `GridPuzzleVision.java`: bounded причины отказа по клеткам `Observation.reason(row,col)`, `unknownIndices`, `unknownSummary(3)`. На смартфоне это поможет выяснить, какие ровно 7 клеток из 36 не распознаны. Разрешение действий на неполной сетке НЕ включено.
- Компактный HUD Шипучки сообщает правильный профиль, а не предыдущую сетку 6×6.
- `tests/PanelDockingTest.java`: 5000 псевдослучайных конфигураций и отдельные крайние случаи; `tests/PanelDockingVerify.java` добавлен к GitHub JVM matrix.
- `app/build.gradle`: `versionName '0.7.34'`, debug `0.7.34-dev`, `versionCode 50`.

## Подтверждённые проверки и открытые блокеры
- [PASS на исходном code49 + полная новая матрица JVM/Gradle и debug build, коммит b1adb3e7](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/40488512182). Прогон кода code50 приводится отдельно ниже после завершения CI; он пока НЕ подтверждён в этом документе.
- Эмуляторные матрицы [API30 failure](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/40487871145) и [API35 failure](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/40488255281) выявили нестабильные элементы UI: «The current window dialog has no visible target row» и «Visible clickable UI control not found: Показать панель». Часть падений относится к коммитам ДО изменений панели. [Issue #7](https://github.com/GiVscSon/LocalPilot-Android/issues/7).
- Нужны успешные три последовательных полных device прогона API 30/35/36 для одного и того же head плюс реальный Android. Прохождение одних JVM-тестов не достаточно.
- По прежним пользовательским скриншотам проблемы «Нащупай ход» 7/36 и Match World после 3 ACK не исправлены полностью; реализована лишь инструментализация для установления причин, без опасного снятия проверок.
- Никакие игровые бустеры, HexaSort, Stomp, autoRestartRound или заменяющие антибот-фильтр джиттеры не включены.

## Тестовая команда
```bash
java tests/Verify.java tests/legacy_parity.txt
java tests/GridVerify.java
java tests/PanelDockingVerify.java
java tests/BubbleVerify.java
java tests/RuntimeVerify.java
java tests/UniversalVerify.java
python3 -m unittest discover -s tests -p 'test_release_tools.py' -v
python3 build_debug.py --gradle gradle
python3 tools/package_release.py
```
Полный список матрицы и этапов см. [ROADMAP_0.7.34.ru.md](ROADMAP_0.7.34.ru.md).

## Установка на Android: НЕ ВЫПОЛНЕНА
Проверять версию, `applicationId` и сертификат. Gradle debug использует `org.localpilot.android.dev`, отличающийся от основного `org.localpilot.android`; разные подписи могут исключать обновление поверх старой программы. **Нельзя удалять APK ради установки**, пока не сохранены learning data и private checkpoints. Ни одна CI-сборка не проверена здесь на настоящем устройстве.

## Следующее действие
Дождаться/посмотреть `0.7.34-dev/code50` workflow, устранить [Issue #7](https://github.com/GiVscSon/LocalPilot-Android/issues/7), сделать инструментацию геометрии и 5 подтверждённых реальных ходов. Дальнейший gameplay по этапам 4–5 допускается лишь после базовой стабильности.
