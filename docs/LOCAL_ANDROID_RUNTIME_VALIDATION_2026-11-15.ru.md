# Локальный Android-стенд и проверка NES · 15.11.2026

Точка интеграции кода: **0.8.4/code56**, коммит
`2c97623faa6e1643def4fd7a8db9e40ddd462b47`.
Канонический main проверен повторно: `fbbbeb9cb4ffa54ecc26582e4a640b963b80b8d3`;
он включён без замены его Control Tower/G0/G2 изменений. Предыдущий проверенный
APK относится к `ce71fece2c14c6a62b92b0d434c113c2d63ce964`;
исправление fingerprint — `a6fbd426fd85264103c62a6cdb8621635fda7065`.
[Draft PR #155](https://github.com/GiVscSon/LocalPilot-Android/pull/155).
Владелец этого PR — G1/framebuffer и проверка сохранений.
[Control Tower #159](https://github.com/GiVscSon/LocalPilot-Android/issues/159)
и `docs/LOCALPILOT-AGENT-HANDOFF.md` обязательны при следующем продолжении.
G1 longplay256 уже ведётся в отдельном PR168; не дублировать его.
Это проверяемый этап, не завершение Harness RFC и не стабильный релиз.

## Что изменено

- В STEP_MODE выполняются все настоящие CPU/PPU-кадры и все проверки lease.
  Только преобразование/загрузка изображения выполняются один раз в конце
  одобренного шага. В realtime остаётся отрисовка каждого кадра.
  Частичный отменённый шаг показывает фактический PPU-буфер без выдачи награды.
- FNV fingerprint хранится в подписанной 32-битной форме. Это те же биты,
  что у беззнакового эталона. На локальном API30 WebView83 обнаружено искажение
  больших беззнаковых промежуточных значений; простая замена умножения сдвигами
  не помогла и не включена в исправление.
- Перед сохранением JS сверяет fingerprint с текущим PPU-буфером.
  Android-приёмка независимо пересчитывает его по сохранённым RGB-пикселям.
  Совпадающих заголовка, JSON-метаданных и SHA файла теперь недостаточно.
- В штатную instrumentation-проверку включён тест подписанного fingerprint:
  пять синтетических RGB-буферов, 5000 вызовов sendFrame, независимый BigInt-эталон,
  JSON и настоящий JavaScriptInterface. Он не запускает ROM и не пишет опыт.

STOP, TTL, InputPermit, эпохи, единый владелец ввода и предел 2,5 секунды для
PPO-шага сохранены. Проверки целостности не обходятся ради медленного стенда.

## Стенд

`/workspace/android-lab/`, `emulator-5554`, AOSP API30/x86_64, 480×800,
2 CPU/2GiB, Android Emulator37.2.13, системный WebView83.0.4103.120.
KVM отсутствует: используется программная эмуляция CPU/SwiftShader, Vulkan выключен.
Это не измерение GPU или производительности телефона.

SDK: `/workspace/toolchain/android-sdk`, JDK21 и Gradle8.9 в
`/workspace/toolchain/`. Standalone APK устанавливается через install-r;
данные, активная политика и ожидающий кандидат сохраняются.
В Android установлены одно приложение LocalPilot и его instrumentation APK.

```sh
python3 /workspace/android-lab/lab.py start
python3 /workspace/android-lab/lab.py wait --timeout 900
python3 /workspace/android-lab/lab.py status
python3 /workspace/android-lab/lab.py smoke /absolute/path/app-debug.apk
```

Повторяемый SDK/AVD runner самого проекта: `tools/release_lab_android.py`.
Для существующего локального стенда можно использовать `check --serial emulator-5554`;
он проверяет source SHA, подпись, реальные байты установленного APK и перезапуск.
Не применять wipe-data к существующему userdata.

## Подтверждённые результаты и ограничения

- Для `ce71fec`: assembleDebug, assembleDebugAndroidTest и lintDebug — PASS.
  Настоящий JSNES save/resume40+7, точный STEP4/8/16, отмена/STOP,
  frame/snapshot bridge и пять Python-проверок приёмки сохранений — PASS.
  В Node-тестах Android-мост моделируется; это явно обозначено в выводе.
- Реальный локальный Android: установка и launcher/UI — PASS.
  APK SHA256: `5bea48343c9e9d469e4437cda9e7ac051976e17f9e3b17699a5ee9d1fb6b15fa`.
  Отчёт: `/workspace/android-lab/evidence/20261115T131422Z/report.json`.
- Реальный WebView-тест подписанного fingerprint на `a6fbd42` — PASS,
  все пять эталонов и Java-мост. Это синтетическая арифметическая регрессия,
  не игровой эпизод: `evidence/fingerprint-a6fbd42.json`.
- Холодная локальная автоигра `ce71fec` — FAIL: первый шаг превысил исходные
  2,5 секунды; JavaScript STOP подтвердил отпускание ввода, новые подтверждённые
  переходы не появились. `evidence/resume-ce71fec/report.json`.
  Профиль CPU max также не устранил таймаут и не оставлен настройкой запуска.
- Проверка после прогрева: FAIL на предварительном условии. За90секунд
  не получено наблюдаемого подтверждения120кадров; UI показывал60кадров и0касаний.
  Автоигра и восстановление в этом сценарии не начались.
  `evidence/resume-ce71fec-warm/report.json`. Это не FPS-бенчмарк.
- После cold boot наблюдался System UI ANR; ранние попытки сохранены как FAIL/
  PARTIAL. После явного Wait повторная launcher-проверка прошла.

Внешний adb-root использовался только для чтения собственных тестовых файлов
на одноразовом AOSP AVD. Приложение осталось non-debuggable; модель и приложение
не получили root, новых разрешений или обхода ActionGate. Фото, ROM-состояния,
сырые replay и веса находятся вне Git в `/workspace/android-lab/evidence/`.

## GitHub Actions

- [Прямая Android API30/35/36 матрица для ce71fec](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43717890315)
  — PASS, все3 API, source HEADce71fec (workflow_dispatch).
- [NES CPU/PPU для предыдущей проверки](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43716877424)
  и [Android API30/35/36](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43716877273)
  — PASS. Последний запуск тестировал PR merge `bd169fed6290c96a64190bfdaebe8fd6fe40b4d5`,
  а не отдельно a6fbd42. Его дерево `e1879cc1af26053d82be91818b1487a19c7fc08c`
  совпадает с деревом ce71fec.
- [Whole System](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43716877268)
  — PASS на предыдущем PR merge. Не выдавать это за физическую Android-проверку.

## Обучение и незавершённая работа

На базе `1413aad` локальный Android действительно выполнил16 Student-шагов/
256 кадров и четыре Adam-обновления, изменив4999 параметров кандидата.
Это не новое обучение версии ce71fec. Артефакты проверены внешним read-only аудитом:
`evidence/student-1413aad/report.json`. Активный SHA `cb44f0338a376ef7ae1c7e0c5214036d7d81fc5a69ae53b34f7b236503f4fb4a`;
кандидат `0a4d834c2774d8f6d7d6875e2d5c69749b4e00b9f8f335510111d0f1d0afb1b3`.
Веса GGUF не менялись.

Отдельная headless JSNES-оценка этих Android-весов на20 парных сценариях дала
NO_PROVEN_IMPROVEMENT; кандидат не активирован. Это не Android holdout.
Старый manifest не содержит новой replay-привязки0.8.4; не дописывать её молча
и не объявлять этот кандидат прошедшим новый официальный pipeline.

Остаются: независимый Android holdout с достаточным набором сценариев,
продвижение/откат проверенной политики, подтверждённый policy REALTIME_MODE,
локальная проверка native GGUF/mmproj и физические CPU/Vulkan измерения.
Текущий локальный Java APK не содержит native GGUF; это не его runtime-приёмка.
Другие консоли и GGUF/LoRA fine-tuning не завершены.

Следующие шаги:

1. Проверить новый HEAD PR155 после включения mainfbbbeb9; привязать новый APK/CI
   к точному SHA в PR, не переносить PASSce71fec на другой бинарный файл.
2. Проверить фактический результат PR168/Actions43742466592; не объявлять256действий
   успешными до завершения sustained job. Не менять TTL ради локального TCG.
3. Проверять новый replay-bound кандидат только через0.8.4 pipeline; сохранить старые веса.
4. Согласовать более строгий независимый holdout с изменениями текущего main
   (отдельный PR150 остаётся незавершённым); затем реализовывать promotion/rollback.
