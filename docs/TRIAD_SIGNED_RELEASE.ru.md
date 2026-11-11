# Подписание трёх APK: выпускной барьер PR #98

**Не путать подписанный кандидат и подтверждённый стабильный релиз.** Нужны один и тот же SHA, проверка сертификата, сохранение данных при обновлении и реальный прогон на POCO X7 Pro. CI не может самостоятельно подтвердить физический запуск на пользовательском телефоне.

## Пакеты

- `LocalPilot.apk`: `org.localpilot.android`, главный релизный package, не `.dev.install1081`.
- `GameLab.apk`: `org.localpilot.gamelab`, самостоятельный NES и симуляторы.
- `ModelLab.apk`: `org.localpilot.modellab`, локальный ARM64 GGUF + опциональный mmproj, без Accessibility и игровых жестов. Веса скачиваются отдельно.

Один **постоянный** ключ для всех трёх новых пакетов допустим, но использовать ключ существующего приложения **обязательно**, если требуется обычное обновление того же package ID без удаления. Текущие временные debug-подписи из GitHub Actions меняются между запусками и не подходят для стабильного обновления. Установка нового `org.localpilot.android` рядом с `org.localpilot.android.dev.install1081` не переносит автоматически пользовательские данные: прежнее приложение не удалять.

## Подготовка владельцем ключа без загрузки его в репозиторий

1. На доверенном локальном ПК создайте и сохраните резервную копию долгоживущего PKCS12-ключа командой `keytool -genkeypair -keystore LocalPilot-RELEASE.p12 -storetype PKCS12 -alias localpilot-triad -keyalg RSA -keysize 3072 -validity 11000 -dname "CN=LocalPilot Release, O=LocalPilot"`. Команда интерактивно запросит пароль. **Если у уже установленной версии есть постоянный release-ключ, используйте именно его вместо нового.**
2. Получите публичный SHA-256 сертификата: `keytool -list -v -keystore LocalPilot-RELEASE.p12 -alias localpilot-triad`. Скопируйте SHA256 **без двоеточий**, 64 шестнадцатеричных символа.
3. С помощью защищённого интерфейса GitHub Settings → Secrets and variables → Actions добавьте четыре секрета: `LOCALPILOT_RELEASE_KEYSTORE_B64` (Base64 от байтов PKCS12), `LOCALPILOT_RELEASE_STORE_PASS`, `LOCALPILOT_RELEASE_KEY_PASS`, `LOCALPILOT_RELEASE_KEY_ALIAS`. Не вставляйте ключ/пароли в чат, Issue, git commit, логи или Markdown.
4. Добавьте **публичную** GitHub Actions variable `LOCALPILOT_RELEASE_CERT_SHA256` с сертификатом из пункта 2. При желании создайте защищённое Environment `android-signing`, требующее подтверждения владельца. Workflow никогда не выдает запасную debug-подпись при отсутствии секретов.
5. После попадания workflow в default branch запускайте вручную `Signed triad candidate · protected key + fail-closed audit` с `source_sha`, точно равным выбранному SHA. До первой публикации сохраняйте резервную копию ключа вне GitHub. Секреты живут в Actions; ключ не включается в APK артефакт.

## Автоматическая проверка

`python3 -m unittest discover -s tests -p test_triad_release_gate.py -v` тестирует 12 разрешённых и запрещённых случаев без SDK.

Workflow собирает из одного SHA `:app:assembleRelease`, `:game-lab:assembleRelease`, `:model-lab:assembleRelease`, выравнивает APK (в том числе 16 KiB выравнивание `.so`), подписывает одинаковым постоянным сертификатом и проверяет через `aapt` и `apksigner`:

- точные package names, версии, minSdk 30/targetSdk 35 и ZIP CRC;
- наличие настоящего NES-движка в LocalPilot/GameLab и ARM64 inference `.so` в ModelLab;
- отсутствие скрытых GGUF-весов и привилегий игрового управления в ModelLab;
- ровно один подписант, современная APK-подпись, SHA-256 в точности равный сохранённому публичному отпечатку;
- SHA-256 файлов и исходный SHA в `CANDIDATE-MANIFEST.json`, где **`releaseReady=false`** и **`physicalAndroidTest=NOT_VERIFIED`**.

При последующих выпусках `tools/triad_release_gate.py --previous <CANDIDATE-MANIFEST.json>` запрещает изменение подписанта, package ID или неувеличенный `versionCode`. При первом подписании baseline создаётся один раз. Сначала проверить миграцию, затем публиковать стабильный релиз.

## Ручная физическая приёмка

На POCO X7 Pro необходимы установочные испытания трёх APK, реальные кадры NES, анализ изображения с mmproj, STOP/повторный запуск локальной генерации и устойчивость переходов GameLab ↔ ModelLab ↔ LocalPilot. Для каждого пакета проверить `adb install -r` поверх **предыдущего APK с этим же сертификатом** без `adb uninstall` и без потери данных. Не снижать Fail-Closed, ActionGate, InputPermit или TTL. Понадобятся проверка всех 10 GitHub CI на одном SHA и хэши финальных файлов.

Подписанный кандидат **не является стабильным релизом**, пока эти критерии не подтверждены. `ModelLab` хранит скачанные модели в собственном приватном каталоге, который нельзя терять при обновлении.

Источники: https://developer.android.com/studio/publish/app-signing и https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets.
## Защита уже установленных приложений перед обновлением

Новый read-only скрипт [DEVICE_TRIAD_PREFLIGHT.ru.md](DEVICE_TRIAD_PREFLIGHT.ru.md) сравнивает три APK с **текущими подписантами и версиями на физическом Android** через `adb pull`, `aapt`, `apksigner`. Он блокирует замену сертификата или downgrade **до** установки, не удаляет приложения и не переносит данные автоматически. Скрипт покрыт отдельной 12-кейсной отрицательной матрицей и входит в CI.
