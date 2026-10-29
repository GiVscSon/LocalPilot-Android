# Подготовка Android-приёмки после локальной матрицы · 29.10.2026

Локальный код приложения соответствует опубликованному 2649b22. Все 19 JVM/tooling наборов из .github/workflows/android.yml прошли в песочнице: Verify, GridVerify, CalibrationVerify, CaptureTimingVerify, PanelDockingVerify, BubbleVerify, LearningVerify, ProcessExitVerify, AuditVerify, CaptureCleanupVerify, RuntimeVerify, Upgrade726Verify, Upgrade727Verify, ResultVerify, UniversalVerify, ControllerVerify, SystemVerify, RlVerify, release_tooling. Новая правка затрагивает только instrumentation и этот отчёт; приложение и защита ввода не меняются.

Run40832797508 attempt1: API30 остановилась на ожидании меню механики. В финальном dump меню открыто, в нём есть «Прыгун Ари»; note сервиса «Выберите механику игры». Это не доказывает, что приложение выбрало старый профиль. Тест давал 6 секунд, а selectInstalledTarget допускает 10 секунд до стабильного окна. present также переходил к дублирующему UiAutomation-запросу; вклад Binder/cache в эту конкретную задержку остаётся гипотезой.

Тест теперь ждёт живое видимое меню на main looper без дублирующего чтения accessibility-дерева: 10 секунд существующего получения окна и 1 секунда на layout в тесте. Выбор выполняется реальным touchOwnControl с проверкой геометрии. awaitBrowserMechanic и проверки подтверждения/повторного выбора/STOP остаются. Таймауты приложения, TTL, APK и механика игры не менялись. Задержка только в тесте не является исправлением скорости приложения.

В песочнице доступны Java17 compiler module и ct.sym: --release17 сохранён. Android SDK, Gradle, adb, emulator и /dev/kvm отсутствуют. Поэтому локальная Android-сборка и native e2e NOT RUN. Нужны сборка и три полные API30/35/36 матрицы на финальном коммите, затем сверка signer с фактически установленным APK и телефонная приёмка. Предыдущие зелёные результаты 2649b22 не приписывать новому head.

