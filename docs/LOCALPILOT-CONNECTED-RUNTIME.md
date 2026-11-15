# Связанный runtime LocalPilot: схема и порядок реализации

Дата15.11.2026. База `fbbbeb9cb4ffa54ecc26582e4a640b963b80b8d3`, версия0.8.4/code56.
Это детализация G2/G4 существующего [Control Tower #159](https://github.com/GiVscSon/LocalPilot-Android/issues/159),
[RFC](LOCALPILOT-HARNESS-RFC-v1.md) и [общих этапов](LOCALPILOT-CONTROL-TOWER-STAGES.md).
Не заменяет общую очередь и не создаёт другого управляющего движка.

## Общая схема

```mermaid
flowchart TD
    UI[Console / ModelLab / GameLab / Обучение / Диагностика] --> APP[PilotApplication: общий runtime приложения]
    APP --> REG[Существующий ModelLabStore + HarnessModelBinding]
    APP --> H[Существующий HarnessCoordinator: единственный владелец ввода]
    ROM[Библиотека ROM: проверенные байты и профиль] --> NES[Существующий JSNES]
    H -->|наблюдать без действия| NES
    NES --> OBS[Наблюдение: ticket, epoch, frame, hash, ROM, RAM, PNG]
    OBS --> ROUTE[Типизированный запрос модели]
    REG --> ROUTE
    ROUTE --> GGUF[Тот же EmbeddedGgufRuntime: CPU GGUF / mmproj]
    ROUTE --> FAST[Существующая StudentPolicy: свой совместимый профиль]
    GGUF --> PROPOSAL[Предложение: модель SHA + исходный ticket]
    FAST --> PROPOSAL
    PROPOSAL --> H
    H --> PERMIT[ActionPermit: TTL / epoch / STOP / legal action]
    PERMIT --> NES
    NES --> VERIFY[Новый кадр + ACK + проверенная RAM; отдельно reward]
    VERIFY --> EXP[ConsoleExperienceMemory / VerifiedPpoReplayStore]
    EXP --> LEARN[Тот же LearningCoordinator: только допустимый опыт]
    LEARN --> CAND[Кандидат с SHA исходного replay]
    CAND --> EXAM[StudentEvaluationGate: независимый экзамен]
    EXAM -->|нет доказанного улучшения| KEEP[Сохранить активную модель; кандидат не активировать]
    EXAM -->|прошёл полный допуск| VERSION[Атомарное продвижение и откат: следующий отдельный gate]
    VERSION --> REG
    EXP --> ARCH[Проверенный архив / SAF / продолжение]
```

Экран не владеет моделью или игровым вводом. Выбор модели меняет общий registry;
Play получает решение от того же native runtime, не открывая экран ModelLab ради
каждого автоматического хода. Все предложения проходят прежний контроллер.
GGUF с неизвестной наградой не становится PPO-опытом. Несовместимые Student и GGUF
не обмениваются весами. Установка файла не означает INFERENCE_PASS.

## Подтверждённые разрывы базы

- Автоматический GGUF путь передаёт кадр через Intent в ModelLabActivity и возвращает
  MOVE Intent. Смена экрана участвует в игровом цикле; NES уходит в onPause.
- Каталог и native runtime живут в static-полях ModelLabActivity, хотя должны
  принадлежать приложению. ModelLabApp имеет отдельное управление lifecycle.
- Ticket/epoch/frame уже защищены HarnessCoordinator, но не образуют общий
  типизированный контракт обращения к модели вне Activity.
- PPO-кандидат и CI-экзамен существуют; on-device promotion/rollback отсутствуют.
- В RFC упомянут ConsoleAgentBridge.java, но такого файла в текущем main нет:
  фактический транспорт — Intent. Не создавать фиктивный «существующий» bridge.

## Этапы в существующих gates

1. **G2: общий runtime и прямой канал модели.** Перенести владение прежними
   EmbeddedGgufRuntime/ModelLabStore в объект Application; ModelLab и Console
   используют его. Добавить typed observation/reply с тем же настоящим Ticket.
   Автоматический GGUF ход выполняется без смены Activity. STOP/фон/замена binding/
   TTL отменяют ответ; busy остаётся до фактического завершения JNI.
   Выход: JVM отрицательные тесты транспорта; Android compile/Lint; настоящий
   Android отказ без модели; отдельно реальный GGUF→NES trace без UI-переключений.
2. **G4: единый контекст эпизода.** Протянуть episodeId/epoch/ROM SHA/model SHA/
   stopRevision/evidenceRef через router, transition и replay. Console/ModelLab/
   Episodes/Learn читают одну проекцию. Проверить смену модели/ROM и старые callbacks.
3. **G5: завершённый lifecycle кандидата.** На том же runtime выполнять независимый
   on-device экзамен с минимум20 парами по общему контракту; отделить NO_IMPROVEMENT
   от ошибки исполнения. Сохранить кандидата/отказ/метрики; внедрить атомарное
   продвижение с сохранением прежних весов и проверенный откат. Не активировать
   старые unbound кандидаты и результаты из неподтверждённого JSON.
4. **G1/G4: нормальные ROM-профили.** После действительной проверки ROM определить
   пространства наблюдений/действий и reward adapter конкретной игры. Сначала shadow,
   затем разрешённая автоигра, отдельные модели и статистика. Импорт произвольной ROM
   сам по себе не разрешает автономный ввод и не создаёт награду.
5. **G0/G3/G6: общая приёмка.** Один pinned source и пользовательский APK; независимые
   положительные/отрицательные проверки всего пути; API30/35/36×phone/tablet;
   permanent signer, upgrade/data retention, offline и физические измерения.
   BUILD/UI/EMULATOR не равны DEVICE/RELEASE.

Первым реализуется этап1. Остальные этапы здесь запланированы, не объявлены выполненными.
G1 longplay уже занят PR #168; не дублировать его. PR #150 рассматривается отдельно
для более строгого экзамена, старую ancestry #156/#157 поверх main не переносить.

Откат первого этапа — архивная ветка archive/main-before-connected-runtime-20261115
на fbbbeb9. Ключ подписи, модели, ROM и пользовательский опыт не очищаются.
