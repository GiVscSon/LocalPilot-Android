# LocalPilot · безопасная проверка перед обновлением трёх APK

## Что исправлено

`tools/triad_device_preflight.py` проверяет на **реальном подключённом Android** три подписанных APK **до установки**. Он никогда не выполняет `adb install`, `adb uninstall`, `pm clear` или сброс данных.

Проверяется реальный подписант и `versionCode` локального APK, после чего из текущей установки через `adb shell pm path` и `adb pull` считывается установленный `base.apk` только в системную временную папку компьютера. Скрипт не изменяет телефон. Все сравнения выполняются локально с `aapt` и `apksigner` из Android SDK.

- `SIGNATURE_MISMATCH`: останавливает обновление при смене сертификата. **Не удалять приложение** ради установки.
- `VERSION_NOT_INCREASED`: останавливает обновление, если новая версия не выше установленной.
- `NEW_PACKAGE_NO_EXISTING_DATA`: установленной версии с таким идентификатором нет. Это новая установка, **не перенос данных** из другого package ID.
- `existingDevInstall`: отдельный `org.localpilot.android.dev.install1081` установлен параллельно, перенос данных в `org.localpilot.android` автоматом не происходит.
- Состояние `releaseReady` всегда `false`; скрипт не утверждает, что на устройстве прошли игровые тесты.

## PowerShell на Windows

Подключите телефон по ADB (USB либо ранее настроенный беспроводной ADB), установите в PATH `adb`, Android SDK `aapt` и `apksigner`. Возьмите именно три APK с **одного SHA** и постоянным релизным сертификатом. Не используйте временные debug-APK в качестве стабильного обновления.

```powershell
python tools/triad_device_preflight.py `
  --serial SERIAL_FROM_ADB_DEVICES `
  --app .\release\LocalPilot.apk `
  --gamelab .\release\GameLab.apk `
  --modellab .\release\ModelLab.apk `
  --adb adb `
  --aapt "$env:ANDROID_HOME\build-tools\35.0.0\aapt.exe" `
  --apksigner "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" `
  --output .\release\device-preflight.json
```

**Замените `SERIAL_FROM_ADB_DEVICES`** значением из вывода `adb devices`; отсутствие подключенного устройства приводит к безопасному отказу.

После PASS можно отдельно планировать ручное `adb install -r`, **не `adb uninstall`**. PASS доказывает только совместимость идентификатора, подписи и версии для обычного обновления. Он **не** доказывает перенос частных данных, работу NES, GGUF/Vulkan, эффективность действий LocalPilot или успешный релиз.

Тесты без телефона: `python -m unittest discover -s tests -p test_triad_device_preflight.py -v`.

## Обязательные условия настоящего релиза

Приватный долгосрочный ключ принадлежит владельцу и должен храниться вне репозитория. Без этого ключа нельзя обеспечить подписанные обновления. Не загружайте ключи/пароли в переписку, не переносите пароли через публичный Issue. Нужны успешные CI одного SHA, подтверждение подписанта, проверка на POCO X7 Pro, невредящее обновление и сохранение результатов обучения/моделей. Пропуск любого пункта означает только test candidate.