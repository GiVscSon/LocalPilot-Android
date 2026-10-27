# AGENTS.md — навигация для Codex (НЕ актуальный код)

ВНИМАНИЕ: ветка `main` в репозитории `GiVscSon/LocalPilot-Android` содержит историческую точку продолжения. **Актуальные Android Java-исходники версии 0.7.33-dev/code49 на 27.10.2026 находятся в ветке `sync/localpilot-android-0711`.** Не пытаться ремонтировать игру на основании одной документационной `main` и не импортировать старые 0.3.x поверх новых файлов.

ПЕРВОЕ ДЕЙСТВИЕ: прочитать
- [Общий инженерный handoff 27.10.2026](https://github.com/GiVscSon/LocalPilot-Android/blob/sync/localpilot-android-0711/docs/LOCALPILOT_UNIFIED_HANDOFF_2026-10-27.ru.md)
- [Актуальный Android handoff](https://github.com/GiVscSon/LocalPilot-Android/blob/sync/localpilot-android-0711/LOCALPILOT_ANDROID_HANDOFF.md)
- [Issue P0 по Match World](https://github.com/GiVscSon/LocalPilot-Android/issues/1)
- [Общий Google Doc для PC/Android и чатов Codex](https://docs.google.com/document/d/1d_U4NLmOQTyzvRQ1PdUVMkLGh0-eH_PCwleFPetbFK8/edit)

Проблемы P0: перекрытие игрового ROI оверлеем и отсутствие визуального подтверждения post-ACK; по скриншотам «3 шага» не означают встроенный лимит. У Карканоида по сообщению пользователя наступающие блоки и невидимая линия поражения. Детали и тестовая матрица в unified handoff.

ОСНОВНОЙ ПК LocalPilot отдельный от LocalPilot Android. Не смешивать репозитории/данные/ключи. Защищать STOP, идентичность окна, свежесть кадров и единственного исполнителя. Не выдавать отчёты сборки за результат на физическом Android. До любого кода проверить реальный git HEAD и сделать backup.
