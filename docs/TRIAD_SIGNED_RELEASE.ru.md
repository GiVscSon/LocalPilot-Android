# Подписание трёх APK: выпускной барьер PR #98

**Не путать подписанный кандидат и подтверждённый стабильный релиз.** Нужны один и тот же SHA, проверка сертификата, сохранение данных при обновлении и реальный прогон на POCO X7 Pro. CI не может самостоятельно подтвердить физический запуск на пользовательском телефоне.

## Пакеты

- `LocalPilot.apk`: `org.localpilot.android`, главный релизный package, не `.dev.install1081`.
- `GameLab.apk`: `org.localpilot.gamelab`, самостоятельный NES и симуляторы.
- `ModelLab.apk`: `org.localpilot.modellab`, локальный ARM64 GGUF + опциональный mmproj, без Accessibility и игровых жестов. Веса скачиваются отдельно.

**Новый выбор владельца (12.11.2026): три РАЗНЫХ постоянных ключа.** Один ключ на пакет: LocalPilot, GameLab, ModelLab. Для каждого package ID хранится собственный защищённый PKCS12 и отдельный публичный SHA-256 сертификата. Ранее установленные APK под временными debug-подписями не обновляются обычным способом новым сертификатом. Удалять установленные APK для обхода ошибки нельзя: нужна согласованная миграция/экспорт данных или новая установка с уведомлением.

## Подготовка владельцем ключей

1. На доверенном компьютере с PowerShell 7, JDK keytool и GitHub CLI сначала создайте защищённое GitHub Environment `android-signing`. Рекомендуется обязательное подтверждение владельца и ограничение разрешённых веток.
2. **Трижды** вызовите `tools/setup_triad_signing.ps1`, каждый раз с другой ролью пакета и `-CreateNew`:
   - `pwsh ./tools/setup_triad_signing.ps1 -Package LocalPilot -CreateNew`
   - `pwsh ./tools/setup_triad_signing.ps1 -Package GameLab -CreateNew`
   - `pwsh ./tools/setup_triad_signing.ps1 -Package ModelLab -CreateNew`
   Для каждой команды используйте отдельный PKCS12 (скрипт создаёт отдельные имена по умолчанию) и запомните его отдельный пароль. Не создавайте эти постоянные ключи на одноразовом CI runner или в чате.
3. В окружении `android-signing` должны присутствовать **12 environment secrets**, по четыре для каждого префикса `LOCALPILOT`, `GAMELAB`, `MODELLAB`: `*_RELEASE_KEYSTORE_B64`, `*_RELEASE_STORE_PASS`, `*_RELEASE_KEY_PASS`, `*_RELEASE_KEY_ALIAS`. Дополнительно **три публичные environment variables**: `*_RELEASE_CERT_SHA256`. Не используйте репозиторные secrets для приватных ключей.
4. Сохраните резервные зашифрованные копии всех трёх PKCS12 и паролей в двух независимых долговременных хранилищах. Публичные отпечатки можно фиксировать в release manifest.
5. Только после этого вручную запустите workflow `Signed triad candidate · protected key + fail-closed audit` для выбранного полного 40-символьного `source_sha` в соответствующей ветке. Pull request сам по себе **никогда не запускает выпускную подпись**.

Workflow не переходит на debug-подпись при пропущенном секрете, неверном отпечатке или повторном использовании одного сертификата для двух пакетов. Подписанный результат остаётся `candidate/test-only`, а не stable.

## Автоматическая проверка

`python3 -m unittest discover -s tests -p test_triad_release_gate.py -v` тестирует 12 разрешённых и запрещённых случаев без SDK.

Workflow собирает из одного SHA `:app:assembleRelease`, `:game-lab:assembleRelease`, `:model-lab:assembleRelease`, выравнивает APK (в том числе 16 KiB выравнивание `.so`), подписывает каждым из трёх независимых постоянных сертификатов и проверяет через `aapt` и `apksigner`:

- точные package names, версии, minSdk 30/targetSdk 35 и ZIP CRC;
- наличие настоящего NES-движка в LocalPilot/GameLab и ARM64 inference `.so` в ModelLab;
- отсутствие скрытых GGUF-весов и привилегий игрового управления в ModelLab;
- ровно один подписант на APK, современная APK-подпись, SHA-256 в точности равный закреплённому отпечатку **этого пакета**, и различие всех трёх отпечатков;
- SHA-256 файлов и исходный SHA в `CANDIDATE-MANIFEST.json`, где **`releaseReady=false`** и **`physicalAndroidTest=NOT_VERIFIED`**.

При последующих выпусках `tools/triad_release_gate.py --previous <CANDIDATE-MANIFEST.json>` (схема `localpilot-triad-candidate-v2`) запрещает изменение подписанта, package ID или неувеличенный `versionCode`. При первом подписании baseline создаётся один раз. Сначала проверить миграцию, затем публиковать стабильный релиз.

## Ручная физическая приёмка

На POCO X7 Pro необходимы установочные испытания трёх APK, реальные кадры NES, анализ изображения с mmproj, STOP/повторный запуск локальной генерации и устойчивость переходов GameLab ↔ ModelLab ↔ LocalPilot. Для каждого пакета проверить `adb install -r` поверх **предыдущего APK с этим же сертификатом** без `adb uninstall` и без потери данных. Не снижать Fail-Closed, ActionGate, InputPermit или TTL. Понадобятся проверка всех 10 GitHub CI на одном SHA и хэши финальных файлов.

Подписанный кандидат **не является стабильным релизом**, пока эти критерии не подтверждены. `ModelLab` хранит скачанные модели в собственном приватном каталоге, который нельзя терять при обновлении.

Источники: https://developer.android.com/studio/publish/app-signing и https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets.
## Защита уже установленных приложений перед обновлением

Новый read-only скрипт [DEVICE_TRIAD_PREFLIGHT.ru.md](DEVICE_TRIAD_PREFLIGHT.ru.md) сравнивает три APK с **текущими подписантами и версиями на физическом Android** через `adb pull`, `aapt`, `apksigner`. Он блокирует замену сертификата или downgrade **до** установки, не удаляет приложения и не переносит данные автоматически. Скрипт покрыт отдельной 12-кейсной отрицательной матрицей и входит в CI.

## Безопасная настройка с Windows

Используйте [tools/setup_triad_signing.ps1](../tools/setup_triad_signing.ps1) с параметром `-Package` по одному разу для каждого приложения, как описано выше. Скрипт проверяет окружение до чтения или создания ключа, никогда не записывает приватные ключи в репозиторий и отправляет каждый из них только через стандартный ввод GitHub CLI в защищённые environment secrets.

**Без доступа к постоянному доверенному хранилищу создание ключей и выпуск откладываются.** Кодовая подготовка и CI-проверки политики могут выполняться независимо.
