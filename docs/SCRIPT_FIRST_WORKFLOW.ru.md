# LocalPilot: работа скриптами (0.8.1 Unified)

Исходная ветка PR #85: `feature/unified-16-simulators-build-20261107`.
Основная `main` не заменяется до подтверждения работы на телефоне.

Все команды начинаются из корня клонированного репозитория.

## Получить исходники

```bash
git clone --branch feature/unified-16-simulators-build-20261107 \
  https://github.com/GiVscSon/LocalPilot-Android.git
cd LocalPilot-Android
```

На Windows вместо `python3` допустимо `py -3`.

## Сборка в GitHub без локального Android SDK

Требуются Python 3.10+ и GitHub CLI `gh` с `gh auth login`:

```bash
python3 scripts/localpilot.py ci-start
python3 scripts/localpilot.py ci-status
python3 scripts/localpilot.py ci-download --output downloads/unified
```

`ci-download` скачает APK **только из успешного** Unified workflow.
Базовая `Build Android debug APK` не считается Unified и не подменяет файл.
GitHub Actions проверяет JVM, 16 моделей, собирает оба модуля и проверяет
подпись APK. Повторно запускать Colab не требуется.

Если `gh` не установлен, workflow запускается автоматически при публикации
изменений в этой ветке. Результат доступен на GitHub Actions.

## Проверка полученного APK

```bash
python3 scripts/localpilot.py inspect --apk downloads/unified/app-debug.apk
```

Без Android SDK проверяются ZIP CRC, SHA-256 и наличие 16 файлов моделей.
Для **криптографической** проверки подписи нужен установленный `apksigner`
из Android SDK либо журнал успешного GitHub Actions.
Число моделей само по себе не доказывает работу в реальной игре.

## Безопасная диагностика телефона через Termux / Windows

В Termux нужен Python и ADB (Android Platform Tools). В Windows можно
использовать Python и `platform-tools` из Android SDK.

Найти подключённый адрес/порт: `adb devices -l`. Порт беспроводного ADB
может измениться после переподключения.

```bash
python3 scripts/localpilot.py device-report --serial 192.168.31.235:37735
```

Создаётся `device-reports/<UTC timestamp>/` с окнами, статусом службы и
отфильтрованными сообщениями процесса. По умолчанию **нет снимка экрана**,
нет отправки данных, ADB taps, установки, изменения разрешений или запуска
игры. Если именно нужен кадр игрового поля:

```bash
python3 scripts/localpilot.py device-report --serial 192.168.31.235:37735 --screenshot
```

Перед передачей снимка проверь его на личные данные. Скрипт не связывает
Android ACK с успехом игрового действия.

## Установка только по явной команде

```bash
python3 scripts/localpilot.py install --serial 192.168.31.235:37735 \
  --apk downloads/unified/app-debug.apk --yes
```

Приложение не удаляется и данные не очищаются. Несовпадение сертификата
приведёт к штатному отказу Android. Не удаляй прежний APK без резервной копии
его пользовательских данных. Установка не подтверждает фактическую работу
«Шипучки», Карканоида и других игр на физическом устройстве.

## Скриптовые самотесты

```bash
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

Самотесты не используют физический Android, не отправляют жесты и не создают
обучающие награды из неподтверждённых результатов. STOP, TTL, ActionGate,
InputPermit, UNKNOWN и визуальная проверка остаются обязательными.

### Разделение результатов

* **SIM**: тренировка и оценка на симуляторе.
* **ANDROID_ACK**: Android обработал жест, но результат в игре не доказан.
* **VERIFIED_REAL**: подтверждение независимым свежим игровым кадром.
* **STOP / UNKNOWN**: нет права продолжать ввод без восстановления оснований.

Нельзя автоматически превращать SIM или ACK в VERIFIED_REAL.
