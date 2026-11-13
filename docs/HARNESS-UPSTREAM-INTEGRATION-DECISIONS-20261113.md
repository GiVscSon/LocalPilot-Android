# LocalPilot Harness: интеграционный аудит внешних проектов · 2026-11-13

Принцип: **один Android APK, один `HarnessCoordinator`, общий журнал/модели**. Никакие внешние инструменты не объявляются встроенными, пока нет проверенного `frame → observe → act → check → reward → learn → evaluation` на реальной ROM в одном APK.

## Проверенные источники и решения

### 1. [AgentEmu](https://github.com/dk8827/AgentEmu)
- Rust NES, WASM/WebGPU, человек+агент, imitation/DAgger, пример PPO, export weights и native/WASM parity; MIT для собственного кода.
- **Применяем архитектурный подход, не копируем эмулятор целиком**: у LocalPilot уже JSNES + `HarnessCoordinator` + компактный Student. Внедряем отдельную проверку паритета сохранённых/загруженных весов и независимый on-policy NES evaluation gate. Ранее опубликованная матрица и CI JSNES не доказывают игровой успех.
- Не копируем чужие ROM и специфические политики, обученные под коммерческие картриджи.

### 2. [libretro-kmp](https://github.com/kotlinds/libretro-kmp)
- Kotlin Multiplatform front end; Android JNI, кадры, кнопки, память, аудио, save states; Apache-2.0. **Ядра не входят в библиотеку** и могут иметь несовместимые для дистрибуции лицензии.
- Следующий **отдельный пилот**, не текущий релиз: один легально распространяемый ARM64 core, интеграция через `EnvironmentAdapter`, пин SHA/лицензии, самостоятельная reset/step/ROM/frame/audio/memory/state проверка на устройстве.
- Нельзя смешивать `JSNES` и `libretro` в двух параллельных input-owner: мост всегда лишь передаёт в `HarnessCoordinator` команду из существующей `AgentController`. Не включать ядро в APK, пока нет теста на тех же кадрах/кнопках/STOP.

### 3. [Stable-Retro](https://github.com/Farama-Foundation/stable-retro)
- Отдельная desktop Gymnasium-совместимая исследовательская среда, основная лицензия MIT. Подойдёт для Python PPO / benchmarking / reward definitions, не для копирования пакета целиком в Android.
- Экспортируем только **наши** проверенные эпизоды и training manifest, затем тестируем импорт конкретных весов в Student runtime; внешняя модель не может быть автоматически назначена на ROM без паритета и rollback.
- Лицензии эмуляторных ядер и ROM отдельно.

### 4. [Retro-AI](https://github.com/qam4/retro-ai)
- Плагины memory/vision/intrinsic reward, C++/Python, MIT. Наша интеграционная точка: профили `RewardProvider` с явным `source`, `task` и `UNKNOWN` по умолчанию. На данный момент на Android реально реализована только точная `HomebrewGoalReward` по RAM встроенной NES.
- Никаких универсальных наград по разнице пикселей. Каждому стороннему ROM нужен собственный проверяемый профиль.

### 5. [PocketLLM](https://github.com/Kimi-syl/PocketLLM)
- Android llama.cpp + native Vulkan, local HTTP, MCP и моделями; собственный код MIT, ряд third-party компонентов имеет **иные** лицензии. Не переносить JNI/ggml-пакет: это создало бы второй движок в одном APK.
- Использовать как **reference** для проб CPU/Vulkan, подсчёта реально выделенных GPU-слоёв и диагностического списка возможностей. Нельзя включить GPU лишь изменением `VULKAN_COMPILED`.

### 6. [mesen-agent](https://github.com/atonamy/mesen-agent)
- Headless эмулятор/отладка. Использовать как **внешний тестовый оракул** на CI / ПК: сверка CPU zero-page, deterministic frame budget, stop, ROM snapshots; не встраивать GPL исходники без проверки лицензии и всей цепочки зависимостей.

### 7. [AndroidMCP](https://github.com/SixOfFive/AndroidMCP), Agora, Lemuroid
- В **ближайшем релизе не интегрировать**. MCP-доступ к Android не нужен для изолированного NES controller; дополнительные разрешения, второй input owner и сетевой API повышают риск. Условия копирования AndroidMCP ещё требуют проверки. GPL-ветви Agora/Lemuroid отдельно.
- Если позже понадобится подключение, только `ToolCapability` с явными разрешениями, отзывом, журналом и запретом самостоятельного `dispatchGesture`.

## Приёмка новой части обязательна
1. Список ядер из **действительного runtime**, не UI mock. Без загруженного core запись `NOT_CONNECTED` и нет кнопки Play.
2. Любая модель в каталоге имеет `modelId+SHA+backend+role`, фактическую точку вызова и запись `MODEL_USED`; отсутствие использования показывается честно.
3. `STOP`, TTL, один actor, ROM+epoch, action→release→fresh frame. ACK сам по себе не награда.
4. Reward либо `UNKNOWN`, либо подтверждённой схемы для **конкретной** ROM; не переносить целевые метрики Homebrew на другие игры.
5. Веса тренировочной сети сохраняются и повторно загружаются с SHA/паритетом; обучение на отделённом train, оценка на holdout, затем независимое on-policy тестирование до назначения стабильной политики.
6. ARM64 install и UI эмулятор, физический POCO X7 Pro, perf p50/p95, Vulkan layer allocation, соблюдение лицензий. Старые собранные APK хранятся только для отката.

## Текущее состояние
- NES JSNES и `HarnessCoordinator` реализованы, PR #124 имеет подтверждённые CI.
- PR #132: verified homebrew goal-state reward (8/8 CI PASS на SHA `41a39b1d`).
- PR #134: candidate Student neural network 7→12→6, one-step binder; **CI проверяется на актуальном HEAD**. Это ещё **не** on-policy выигрыш, не мультиконсольный libretro runtime и не стабильный релиз.

Следующий технический критерий: Student CANDIDATE сохраняется, загружается с численно одинаковыми выходами, выполняет разрешённый шаг в настоящем JSNES на отдельном NES-сеансе и показывает достоверный task reward; в случае несоответствия останавливается, без автоматического повышения версии.
