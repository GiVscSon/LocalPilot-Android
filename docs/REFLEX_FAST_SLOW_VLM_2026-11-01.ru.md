# LocalPilot Android · Dual Loop / Qwen-VL Reflex, 01.11.2026

## Статус

Experimental / draft, **НЕ релиз и НЕ активированная автономия**. Kotlin наблюдатель уже подключён в **пассивном режиме** к Java PilotService; остальные компоненты пока не активированы как автономные действия. Эта ветка добавляет Kotlin-компоненты как отдельный пакет `org.localpilot.android.reflex` и Kotlin/serialization/coroutines/JUnit-зависимости. Основная Java-служба `PilotService` остаётся **единственной** AccessibilityService и единственным исполнителем жестов; существующие 10 игровых адаптеров не заменены. Нет доказательства физического прохождения игр или измерения задержек 2–4 мс.

## Файлы

- `ReflexRule.kt`: сериализуемые DTO для локального рефлекса и недоверенного предложения модели. Точный gamePackage, window ID, frame dimensions и pHash устанавливает Android.
- `FastVisualMatcher.kt`: **настоящий DCT pHash** 32×32 / low 8×8 AC63, Hamming 0..64. Пример из чата, где использован порог яркости 8×8, является **aHash**, не pHash; старые aHash-значения несовместимы. Выполнять вне UI thread, замерить на настоящем устройстве.
- `DeadlockDetector.kt`: monotonic elapsedRealtime, задержка верификации, stasis, отдельные `VisualChangeNeedsGameVerification` и `VerifiedProgress`; никакого автоматического повтора жеста и никакого принятия Android ACK за успех.
- `ReflexCompiler.kt`: strict JSON proposal -> disabled candidate + Android pHash. Отказывает неопределённым, некорректным и слишком рискованным кандидатам.
- `ReflexRuleStore.kt`: ограниченное `AtomicFile`-хранилище внутри `filesDir`; правила при загрузке **всегда выключены**.
- `VlmApiClient.kt`: explicit consent and HTTPS-only, OpenAI-compatible chat/completions, jpeg image. Ни ключи, ни реальный IP локального ПК в код не добавляются.
- `SelfDistillationLogger.kt`: opt-in единичный кандидат -> самостоятельное доказательство успеха -> запись WebP + JSONL через AtomicFile. Результат одного `Continue` никогда не записывается.
- `LocalPilotOverlayHud.kt`: optional read-only ROI radar without independent STOP/resume or gestures. Native STOP panel remains. В текущий capture pipeline радар по умолчанию не подключён.
- `ReflexCaptureBridge.kt`: **фактически подключён** к board-game `takeScreenshot` и arcade `MediaProjection` кадрам через `PilotService` (сначала общий freshness/window/ROI gate). Не чаще одного pHash за 850 мс; если pHash не меняется 4.5 с, отражает `STASIS_OBSERVED` без отправки VLM запроса и без ввода. Сброс при STOP/смене epoch/ROI/window. Настройка `Ещё → Рефлексы · наблюдение вкл/выкл` и `Ещё → Рефлексы · диагностический статус`. В default OFF.
- `tests/ReflexIntegrationContractVerify.java`: проверка статического safety-контракта включена в GitHub CI.
- `app/src/test/java/.../ReflexCoreTest.kt`: deterministic tests of pHash, monotonic state machine, timing, wrong-session rejection.

## Конфигурация LLaMA-Factory

Экспорт находится в app-specific external directory, например:
`/sdcard/Android/data/org.localpilot.android.dev.install1029/files/distillation_dataset/`
(точный packageId вычислять из **установленного APK**; он не равен `com.localpilot.android` из предложенного текста).
Содержит `qwen_vl_train.jsonl`, `dataset_info.json`, `images/*.webp`. После USB/ADB копирования помещать `dataset_info.json` в LLaMA-Factory dataset_dir. Формат `messages` с `role/content` и `images` должен иметь `formatting: sharegpt` с соответствующими tags; официальный справочник: https://github.com/hiyouga/LlamaFactory/blob/main/data/README.md.

**Не загружать реальные изображения игр/уведомления/логины в публичный GitHub.** Dataset opt-in OFF по умолчанию. Crash между сохранением картинки и commit JSONL может оставить orphan image; JSONL при этом не ссылается на неполный файл. Новые данные не становятся обучающей истиной без game-specific проверки перехода; всё равно возможны шумные метки.

## Реально подключённый пассивный контур

Сборка и проверки на SHA нужно повторять после последнего изменения: [PR #65](https://github.com/GiVscSon/LocalPilot-Android/pull/65). Включение наблюдения через «Ещё» вызывает STOP и требует перезапустить игровую сессию; это не делает скрытых жестов. Счётчики для диагностики есть, но p95 latency DCT на реальном устройстве пока НЕ измерен. Нет подтверждения автономного использования VLM или записи реального обучающего датасета.

## Обязательный контракт будущей интеграции в PilotService

1. Привязать конкретный пакет, windowId, bounds, frame geometry, sessionEpoch и ROI. Только свежие кадры из существующего capture loop, никогда с непроверенным focus/STOP/overlay overlap.
2. При stasis для VLM не запускать сетевой запрос без активного пользовательского opt-in. Вся VLM выдаёт только inert proposal; `ABSTAIN` не создаёт рефлекс.
3. Android компилирует proposal от **того же исходного screenshot**, затем хранит кандидат disabled. Перед любым возможным вводом обязательно заново проверить `ActionGate`, `InputPermit`, текущий window bounds, ROI, focus, разрешение пользователя, TTL, cooldown, one-in-flight и запрет на UNKNOWN. Не применять автоматически только по одному pHash.
4. Если **разрешённый** жест был исполнен старым единым dispatcher, вызвать `registerBeforeDispatch` и затем `onAndroidCompleted`. Примерный порядок в коде обвязки: `capture -> gate -> one executor -> gesture callback -> fresh capture -> visual change -> game-specific verifier`.
5. `VisualChangeNeedsGameVerification` не равен успеху. Только `confirmGameTransition(...verified=true)` после независимого подтверждения ожидаемого игрового результата возвращает `VerifiedProgress`, на основании которого `SelfDistillationLogger.commitVerified` вправе записать образец. При STOP/window mismatch/quarantine нужно `rejectPending`, logger discard.
6. Не перезапускать жест из `RequestRetryReview` автоматически. Новое действие требует свежих разрешений и независимой политики; `maxRetries` в DTO пока не исполняется как автоматический retry.
7. HUD не заменяет native STOP, и его рамки не должны попадать в screenshot. Для capture необходимо синхронно завершить скрытие overlay перед свежим кадром, и проверить, что frame metadata не изменилось.

## Невыполненная часть

Пассивное встраивание в `PilotService` уже выполнено: скриншоты досок и аркад, явный opt-in только на локальное pHash наблюдение, базовая диагностика и перенаправление **существующих** verified-событий `2048`, `BubbleSession`, `GridPuzzleSession`, `ArcadeFeedback/JumperFeedback`, `MiniPolicy` в детектор. Эти verified-события **не** доказывают успех VLM-рефлексов и не пишутся в self-distillation. Требуются отдельно: готовый UI-путь для настройки VLM/HTTPS и подтверждения предложений, игровой UI-постверификатор для конкретного рефлекса, реальная dispatch-интеграция только через прежний `sendObserved`, безопасная активация кандидатов, экспорт/удаление датасета, бенчмарк pHash и физический e2e. Дефолтное наблюдение НЕ включает сетевой обмен и НЕ допускает жесты от Qwen-VL. На данной ветке модули компилируются только по результатам Android CI (должен быть проверен после коммита). Не включать автономное использование/обучение из коробки и не выдавать APK за проверенный релиз.

**Нельзя ослаблять:** ActionGate, STOP, InputPermit, единственного исполнителя, binding, ROI panel overlap, TTL, provenance кадров и запрет на случайное заполнение UNKNOWN.
