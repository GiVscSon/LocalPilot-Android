# LocalPilot 0.8.2 · безопасное объединение исходников и релизных архивов

Дата аудита: 14.11.2026. Каноническая отправная точка: GitHub `main` = `630415b0e23f6383c5055cccc5b1af882189db82`, `0.8.2` / `versionCode 54`.

## Что обнаружено

Переданный полный ZIP содержит 400/400 файлов с правильными SHA-256 **относительно приложенного собственного манифеста**, но это не доказывает соответствие коммиту GitHub. Второй присланный релизный ZIP отличается: 14 общих файлов с разным содержимым, 18 уникальных записей в полном архиве (без встроенного манифеста) и 13 уникальных записей в релизном. В том числе: `app/build.gradle`, `EmbeddedGgufRuntime.java`, `MainActivity.java`, классы Harness и часть тестов. Проверка GitHub API на точном заявленном SHA подтверждает несоответствия.

Старый `apply_localpilot_all_in_one_fixes.py` проваливает self-test: ожидает 9 Java-классов вместо действительного количества. Он безусловно перезаписывает файлы и удаляет `*.zip.bak`; не применять к живой копии. Восстановить новую версию из этих ZIP путём массового копирования **нельзя**. Не переносить неподтверждённые модельные веса или эмуляторные профили как будто они прошли real-device acceptance.

Подписанный пользовательский APK `LocalPilot-0.8.2-arm64.apk` имеет SHA-256 `892f6bf4ad2b9380e93ccbb2b01f7a9c81f06492b382eed5bfb0c542e32fd7fa`; его v2/v3 подписи и 16 КиБ ELF alignment проверялись независимо. Это **не** подтверждение запуска на физическом телефоне.

## Теперь предусмотрен воспроизводимый канонический источник

Команды выполняются в **настоящем Git clone** с историей, а не внутри ZIP без `.git`:

```bash
python3 -m unittest discover -s tests -p test_external_source_bundle_audit.py -v
python3 -m unittest discover -s tests -p test_canonical_source_zip.py -v
python3 tools/build_canonical_source_zip.py --repository . --ref HEAD --output /tmp/LocalPilot-Android-SOURCE-VERIFIED.zip
python3 tools/audit_external_source_bundle.py --source /tmp/LocalPilot-Android-SOURCE-VERIFIED.zip --repository . --ref HEAD
```

Проверка **старых** архивов:

```bash
python3 tools/audit_external_source_bundle.py \
  --source /path/LocalPilot_Android_0_8_2_Full_Repository_630415b_2026-11-14.zip \
  --manifest /path/REPOSITORY_MANIFEST_0_8_2.json \
  --repository . --ref 630415b0e23f6383c5055cccc5b1af882189db82 \
  --other-zip /path/LocalPilot_Android_0_8_2_630415b_UNIFIED_REPOSITORY_AND_RELEASE_BUNDLE_2026-11-14.zip \
  --report /tmp/localpilot-082-source-audit.json
```

Аудитор проверяет реальные байты Git blobs, пути ZIP и манифест. При расхождении возвращает ненулевой код, не исполняет ZIP, не меняет файлы проекта. Генератор читает **только неизменяемые Git blobs** указанного SHA, отказывает при подозрительном содержимом, добавляет SHA-256 manifest, не перезаписывает существующий архив и проводит обратную проверку. GitHub Actions публикует источник с SHA-256 как временный артефакт для каждого PR и коммита main.

## Правила интеграции

- Сохранять текущие единые NES HarnessCoordinator, STOP, InputPermit/TTL, session/frame/ROM guards, ModelLab и обучение только на доказанном опыте.
- Архивные изменения переносить выборочно через PR с тестами и реальным использованием кода, а не с заменой 400 файлов. Нет подтверждённой награды или физического теста = не объявлять готовность релиза.
- Исходником истины остаётся GitHub `main`; контрольная ветка до объединения: `checkpoint/main-v0.8.2-630415b-pre-source-reconciliation-20261114`.

**Проверено локально до PR:** 15 Python тестов source audit и canonical source PASS, сверка двух исходных архивов PASS с выявленными отличиями. Отдельные CI и физический Android e2e требуют собственного подтверждения и не подменяются этими тестами.
