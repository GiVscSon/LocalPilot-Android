# Android SDK · обновление инструментов LocalPilot (15.11.2026)

Назначение: обновить инструменты разработки **без изменения** версии приложения, `compileSdk`, `targetSdk`, SDK-минимума, JNI ABI, приватного ключа или Android-подписей. Сам APK этим документом не выпускается.

## Версии и политика

- Android SDK Command-line Tools / Android CLI: **22.0 или новее в Stable**. Старые GitHub runner images имели 12.0; обновляйте через `cmdline-tools;latest`.
- Android SDK Platform-Tools / ADB: **37.0.1 или новее в Stable**, не Canary 37.0.3.
- Android Emulator: Stable, актуальный ориентир на 15.11.2026 **37.2.12 или новее в Stable**. Эмулятор может быть обновлён отдельно от APK.
- SDK Platforms API **30 / 35 / 36** для проверок LocalPilot.
- Android Build Tools: сохранить **35.0.0**, пока Gradle, APK/signature и эмуляторы не пройдут совместимую матрицу для более новой версии.
- NDK: сохранить **27.2.12479018**; CMake: **3.22.1**; Java Temurin 21; Gradle 8.9.
- Командные инструменты Linux: существующий `tools/bootstrap_android_sdk.sh` получает официальный Google ZIP `commandlinetools-linux-15859902_latest.zip` и проверяет фиксированный SHA-256. Менеджер также обновляет существующую CLI до 22.0 через `cmdline-tools;latest`.

Официальные страницы: https://developer.android.com/tools/releases/platform-tools, https://developer.android.com/studio/releases/emulator, https://developer.android.com/tools/sdkmanager, https://developer.android.com/studio.

## Linux или GitHub Actions

```bash
bash tools/android_sdk_update.sh --check
# Установка / обновление ADB и платформ API 30/35/36 в стабильном канале:
bash tools/android_sdk_update.sh --update
# Полная CI-среда с сохранёнными версиями NDK/CMake:
bash tools/android_sdk_update.sh --update-ci
# Опциональная загрузка эмулятора и Android 35 x86_64:
bash tools/android_sdk_update.sh --install-emulator
```

Если в среде SDK ещё нет, скрипт использует checksum-pinned bootstrap. Принятие Google SDK licenses является отдельным действием:

```bash
"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses
```

Подтвердить реально установленные версии:

```bash
python3 tools/android_sdk_inventory.py \
  --sdk-root "${ANDROID_SDK_ROOT:-$ANDROID_HOME}" --require-ci \
  --output build/android-sdk-inventory.json
"${ANDROID_SDK_ROOT:-$ANDROID_HOME}/platform-tools/adb" version
```

**KVM:** установка SDK и загрузка образов возможны без `/dev/kvm`, но для реального ускоренного x86_64 эмулятора нужен доступ к виртуализации. В ChatGPT sandbox прямой доступ к `dl.google.com` заблокирован DNS/сетью, отсутствует `/dev/kvm`; локальная установка там не подтверждена. Рабочие Android-эмуляторы запускаются в GitHub Actions.

## Windows PowerShell (домашний ПК)

Если Android Studio уже установила Command-line Tools:

```powershell
$SDK = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $SDK
& "$SDK\cmdline-tools\latest\bin\sdkmanager.bat" --channel=0 "platform-tools" "platforms;android-30" "platforms;android-35" "platforms;android-36" "build-tools;35.0.0" "cmdline-tools;latest"
& "$SDK\platform-tools\adb.exe" version
```

Для Android Emulator из Studio: **Tools → SDK Manager → SDK Tools → Android Emulator**. Обновляйте в стабильном канале. Для ускорения Windows AVD используется Hyper-V/WHPX; отсутствие KVM на сервере не относится к Windows.

## Проверка после обновления

GitHub workflow `.github/workflows/localpilot-all-in-one.yml` устанавливает стабильные пакеты и создаёт `SDK-TOOLCHAIN.json`, включённый в SHA256SUMS APK-кандидата. Это свидетельствует об установленном инструментарии, но НЕ доказывает запуск модели GGUF, улучшение StudentPolicy, физический POCO или выпуск стабильного APK.

Не отключать STOP/TTL/epoch, не переподписывать debug APK для обновления пользовательского приложения и не использовать платформы Canary без отдельного тестового PR.
