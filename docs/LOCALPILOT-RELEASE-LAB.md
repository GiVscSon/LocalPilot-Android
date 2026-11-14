# LocalPilot ReleaseLab: отдельный Android-эмулятор для испытания APK

**Назначение:** самостоятельная испытательная среда для выпускаемых APK, а не второй LocalPilot, не NES/JSNES-эмулятор и не замена реальному POCO X7 Pro. ReleaseLab работает на Windows/ПК с Android SDK и в отдельной GitHub Actions-матрице, запускает Android Virtual Device (AVD), устанавливает конкретный APK, проверяет идентичность файла и фиксирует диагностические артефакты.

## Почему отдельный AVD, а не ещё один APK

- NES эмулируется **внутри LocalPilot**; Android AVD эмулирует **телефон или планшет, на котором запущен LocalPilot**.
- Главный LocalPilot по-прежнему содержит единый `HarnessCoordinator` и выбранные модели. ReleaseLab не создаёт второго игрового агента и не добавляет окна в установленное приложение.
- AVD имеет отдельное имя: `LocalPilot_ReleaseLab_API30_pixel_2`, `LocalPilot_ReleaseLab_API35_pixel_2`, `LocalPilot_ReleaseLab_API36_pixel_c` и т. д. Никогда не выполняет команды на физическом устройстве и не удаляет пользовательские данные.

## Установка на Windows

1. Установить Android Studio или SDK Command-line Tools и Android Emulator; нужны `platform-tools`, `cmdline-tools;latest`, `build-tools;35.0.0`, `emulator` и соответствующий образ `system-images;android-<API>;[default|google_apis];x86_64`. Для локального ускорения желательно включить Windows Hypervisor Platform (WHPX). Подписи APK и ключи должны быть подготовлены **отдельно**; ReleaseLab их не создаёт.
2. В PowerShell указать путь SDK, например `$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"`.
3. При наличии **x86_64-сборки** LocalPilot выполнить (подставив реальный путь и Git SHA):

```powershell
python tools/release_lab_android.py run --api 35 --profile pixel_2 --apk D:\LocalPilot\test\app-debug.apk --expected-source 0123456789abcdef0123456789abcdef01234567 --fresh --output build\reports\release-lab-api35
```

Для планшета используйте `--profile pixel_c`; для API 30 или 36 укажите соответствующий `--api`. Без `--headless` откроется окно Android-эмулятора; с `--headless` проверка будет без окна. `--fresh` очищает **только** AVD с именем `LocalPilot_ReleaseLab_...`, не телефон и не обычный рабочий эмулятор.

Если Android-эмулятор уже запущен:

```powershell
python tools/release_lab_android.py check --serial emulator-5554 --api 35 --apk D:\LocalPilot\test\app-debug.apk --expected-source 0123456789abcdef0123456789abcdef01234567 --output build\reports\release-lab-existing
```

Проверка обновления с сохранением Android app data, только при **совпадающих package ID и SHA-256 сертификата**:

```powershell
python tools/release_lab_android.py run --api 35 --profile pixel_2 --fresh --baseline D:\LocalPilot\old.apk --apk D:\LocalPilot\new.apk --expected-source 0123456789abcdef0123456789abcdef01234567
```

Для штатного release-кандидата указать также `--expected-signer <64 символа hex>`, полученный из ранее утверждённого сертификата. Эта опция не открывает доступ к keystore и не записывает секреты в CI.

**Важное ограничение ABI:** Android x86_64 AVD не подтверждает работоспособность ARM64 JNI/Vulkan. Если APK содержит только `lib/arm64-v8a`, проверка **откажет**, а не сделает вид, что тест прошёл. В CI используется специальный тестовый APK, собранный из **тех же Java/JSNES исходников** с `-PunifiedModelLab=true -PunifiedEmulatorAbi=true`. Его нельзя выдавать за подписанный ARM64 release. Оригинальный ARM64 APK проверяется отдельно на целостность, источник, сертификат и обязательно на физическом Android.

## Что именно проверяет ReleaseLab

1. ZIP CRC, встроенный `assets/localpilot-source.json`, фактический SHA-256 APK, Android `package/version` через `aapt`, работоспособность подписи через `apksigner`, при указании ожидаемого SHA/сертификата строгое сравнение.
2. Устройство действительно локальный AVD (`emulator-####`, `ro.kernel.qemu=1`), API и ABI соответствуют задаче. Несовместимые файлы и другие приложения не устанавливаются.
3. Точная побайтовая копия APK установлена в `/data/app/.../base.apk`. При наличии старого APK выполняется обновление через `adb install -r`, **без удаления данных** и с проверкой идентичности подписи.
4. Реально запускается экспортированная `MainActivity`. Проверяется Android UI hierarchy, реальный compositor PNG, crash logcat, процесс до/после `force-stop` и повторный запуск.
5. Формирует `report.json`, `launch.png`, `relaunch.png`, XML экранов и ограниченный crash-logcat. Любой обязательный отказ даёт статус `FAIL/BLOCKED` и ненулевой exit code. Изолированные данные не уходят из AVD.

`EMULATOR_PASS` значит **только** пройденный install/launch/restart smoke. Это не означает успех реального GGUF инференса, обучения PPO, NES-игры, Android Accessibility жестов, Vulkan GPU, upgrade сохранения конкретных приватных весов или полноценную готовность релиза. Для более глубокой проверки используются уже существующие `unified_in_app_smoke.py`, `nes_autoplay_resume_acceptance.py` и `run_capture_acceptance.py` со своими доказательствами.

## CI и критерий выпуска

`.github/workflows/localpilot-release-lab.yml` собирает производный **x86_64 TEST-ONLY APK** с точной Git-привязкой, проводит unit-тесты самого стенда и прогоняет ReleaseLab на виртуальных телефоне/планшете API 30/35/36. Загрузки и модели не включаются в APK; это не выпуск нового приложения.

До реального **releaseReady=true** должны быть независимо выполнены: same-SHA воспроизводимая сборка, постоянная Android подпись, успешное обновление с данными, реальная ARM64 установка на POCO X7 Pro/планшет, безопасный STOP, GGUF+NES→проверка, обучение и объективная политика с доказанной наградой, GPU/Vulkan и задержки. ReleaseLab всегда выставляет `releaseReady:false`, даже при успехе эмулятора.

## Проверка самого тестового стенда без SDK

```bash
python -m unittest discover -s tests -p 'test_release_lab_android.py' -v
```

Все автоматические тесты здесь проверяют **контракт и отказоустойчивость**. Они не выдают синтетические данные за результаты эмулятора.
