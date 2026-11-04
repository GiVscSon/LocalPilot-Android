# Реестр игровых моделей LocalPilot-Android

**Назначение:** долговременный каталог моделей, их весов, чекпоинтов, архитектур, схем входов/действий и переносимости в настольный LocalPilot. Это исходные данные проекта; их нельзя считать доказательством обучения на реальных играх.

**Владелец исходников:** `GiVscSon/LocalPilot-Android`. Настольный проект LocalPilot остаётся отдельным. Формат обмена `localpilot-modelpack-v1`, загрузчик на Python `tools/modelpack_pc.py`.

## Четыре реально обучаемые Double DQN-модели

Реализация: `app/src/main/java/org/localpilot/android/rl/DqnTrainer.java`, физика и схема наблюдений: `rl/ArcadeEnv.java`.

Каждая сеть: `64 → 64 ReLU → 64 ReLU → N`, float32, Double DQN / replay buffer / Adam. Вход `arcade-objects-v1`: 64 числа (16 общих признаков, 6 объектов по 8 признаков). При экспорте сохраняются онлайн-веса, целевая сеть, optimizer, replay buffer, RNG и счётчики, если checkpoint действительно существует.

| ID модели | Игровой жанр | Действия в модели | Файл checkpoint на Android | Статус применения |
| --- | --- | --- | --- | --- |
| `DQN_JUMPER` | вертикальный джампер / Ари | `left,right,neutral` | `files/arcade-rl/JUMPER.zip` | Симулятор; перед переносом в Ари требуется адаптер observation/action |
| `DQN_PLATFORMER` | платформер / Котобашня | `left,right,jump,neutral,left_jump,right_jump` | `files/arcade-rl/PLATFORMER.zip` | Симулятор; на реальной игре не принят |
| `DQN_LANES` | полосы / Маркет Раш | `left,right,jump,slide,neutral` | `files/arcade-rl/LANES.zip` | Симулятор; требуется адаптер к настоящему контроллеру |
| `DQN_JETPACK` | реактивный полёт, идеи для Гошана | `release,hold` | `files/arcade-rl/JETPACK.zip` | Симулятор; не эквивалентен настоящему Flappy/TAP |

**Правило:** отсутствие файла = `not_trained_on_device`. Наличие checkpoint без `updates > 0` = `untrained_checkpoint_exported`. Наличие обработанного обучения = `trained_checkpoint_exported`. Эти статусы определяются на том устройстве, где запущен экспорт, а не догадкой по GitHub.

## Карканоид: основная малая сеть и перенос в реальный PADDLE

Реализация: `rl/PaddleWeightLab.java`, `PaddleTransfer.java`, `ArcadeController.java`.

- `PADDLE_FOLLOW_BALL`: программная исходная сеть 2→4 ReLU→1, 13 параметров, преследует шар; **не обученная**.
- `PADDLE_IMPACT`: правка выходных весов для прогнозируемой точки падения шара; программная физическая политика.
- `PADDLE_TRAINED_CURRENT`: калиброванное на восьми физических условиях смещение выходного параметра; экспортируется **только если обученная кандидатная модель реально сохранена в текущей сессии** и прошла независимую проверку.
- `PADDLE_LIVE_SCOPED`: переносимые параметры реально выбранной Android-игры из `files/arcade-learning-v1.log`, ограниченные ключом `package + modelSlot + PADDLE`. Импорт в настоящую игру идёт через ActionGate/InputPermit и visual witness, а не напрямую из ПК.

В архиве модели: `paddle/follow_ball.json`, `paddle/impact.json`, при наличии `paddle/trained_current.json`, `live/arcade-learning-v1.log`.

## Программируемые WeightSpace-модели

Реализация: `rl/WeightSpaceSkills.java`. Матрица 3×4, веса создаются по правилам, **это не обученные DQN или Qwen**.

| ID | Назначение | Экспорт |
| --- | --- | --- |
| `WS_PLATFORM_RECOVERY` | исходная политика выхода Ари из тупика | `weightspace/platform_recovery.json` |
| `WS_PLATFORM_RECOVERY_PATCHED` | точечная правка одного веса, безопасное отступление | `weightspace/platform_recovery_patched.json` |
| `WS_LANE_ESCAPE` | оценка безопасной полосы | `weightspace/lane_escape.json` |
| `WS_GAP_FLIGHT` | выбор между ожиданием и набором высоты | `weightspace/gap_flight.json` |

`TextRepetitionGuard` является программной проверкой ответа внешней модели, а не отдельным обучаемым checkpoint. Qwen, DreamerV3, PPO, SAC, OC-STORM, VPT, ONNX сами по себе не находятся в данном Android-проекте в виде обученных весов.

## Как сохранить реальные веса с устройства

В новой версии Android откройте **Аркады · обучение на Android**, нажмите STOP для завершения обучения и выберите **«Сохранить ВСЕ модели для LocalPilot ПК (.zip)»**. Выберите место через стандартный файловый диалог Android и перенесите архив на ПК.

В ZIP находятся `manifest.json`, `SHA256SUMS`, полный DQN-checkpoint (если он действительно существует), `policy.f32le` с весами для инференса, отдельные файлы WeightSpace/PADDLE и сохранённые параметры реальных профилей. Проверяются SHA-256 и версии схем, отсутствующие модели не подделываются.

На Windows с Python 3.9+:

```powershell
py .\modelpack_pc.py "D:\LocalPilot\LocalPilot-Android-modelpack.zip" list
py .\modelpack_pc.py "D:\LocalPilot\LocalPilot-Android-modelpack.zip" dqn JUMPER "D:\LocalPilot\observation64.json"
py .\modelpack_pc.py "D:\LocalPilot\LocalPilot-Android-modelpack.zip" weightspace platform_recovery_patched "D:\LocalPilot\features4.json"
```

На ПК **можно вычислять решения**, но подключение к реальному управлению ПК требует собственного, согласованного адаптера признаков и действий. ZIP не является ни Windows-программой, ни GGUF/Ollama-моделью.

## Долгосрочные правила

1. Сохранять каждый отдельный checkpoint/веса и `manifest.json` в переносимом пакете, с SHA-256 и номером схемы. Не перезаписывать базовую модель без версии/резервной копии.
2. Разделять `simulation_training`, `programmed_weights`, `device_trained`, `real_visual_verified`. ACK не равен успешному ходу.
3. Не смешивать веса LocalPilot-Android и настольного LocalPilot без совместимого преобразования observation/action. Репозитории и релизы остаются отдельными.
4. Для реального Android нужны STOP, ActionGate, InputPermit, TTL, Fail-Closed, защита UNKNOWN, визуальное подтверждение следующего кадра.
5. При появлении новой игровой модели добавлять её ID, жанр, shape, входы, действия, файл, контрольный SHA-256, результаты независимой проверки и историю версий в этот реестр.
6. Для принятия полной версии LocalPilot-Android issue #69 нужны пять визуально доказанных действий каждой из 10 игр на телефоне и планшете.

**Примечание:** индивидуальные обученные веса и replay buffer существуют на конкретном Android-устройстве и не появляются в GitHub автоматически. Пока файл не экспортирован с устройства, проект хранит код и схему, но не его уникальное обучение.
