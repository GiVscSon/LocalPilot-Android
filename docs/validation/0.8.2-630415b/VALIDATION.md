# LocalPilot 0.8.2/code54: проверка событийного кандидата

Исходники: `630415b0e23f6383c5055cccc5b1af882189db82`, main, PR #142 merged.
Проверенный merge-коммит перенесён обычным fast-forward, без изменения SHA APK.
HEAD разработки: `4869708039fb6fa519c97ab3f5b23d28c13db23f`.
Этот отдельный commit отчётов не является новым SHA приложения.

- 30 CI checks SUCCESS; 4 отменённых дубликата заменены одноимёнными SUCCESS.
- [Полное приложение / 6 runtime-профилей](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43583788134): API30/35/36 × Pixel2/PixelC PASS, один x86 APK `6f4bbb17331e023baeb26039271dbe63197d0d069a3dcce61d857a57e326255a`. 96 действий, 1536 CPU/PPU кадров, обновление 4999 параметров в каждом эпизоде; активные веса не продвинуты.
- [Build/JVM/Lint](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43583788132) PASS; [оконный захват](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43583788097) PASS.
- [Единый скриптовый конвейер](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43583788104) PASS. Событийный аудит выполнен до overlay на чистых исходниках; исторический overlay-build имеет source_dirty=true и не подменяет точную ARM64-сборку.
- [NES/модели/пять seed](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43583788091) PASS. Precision Student — IMITATION/7-input-профиль, 20/20 frozen целей на каждом из пяти seed; scripted20/20, random0/20. Это не экзамен Android PPO64 и не доказательство улучшения PPO/GAE. Совпадения отдельных переходов offline train/holdout отражены в CSV; независимый frozen exam использует новые reset recipes и не меняет веса. Не заявляем нулевое пересечение всех состояний.

## Исправления и новая проверка

Старый ACK не может подтвердить новый Bubble action_id даже внутри того же сеанса.
Shadow-автомат проверяет стабильное ожидаемое поле и удаление обычной группы.
STOP очищает текущую оценку; verifiedReward=null/trainingEligible=false.
11 типов синтетических сценариев выполнены 200 раз: 1658 assertions, 19 ожидаемых
shadow-целей, 0 ложных, 0 принятых старых ACK, 0 разрешённых BubbleSession.sent
после STOP. Это контракт локального контроллера, не Android E2E жестов.
Реальных размеченных переходов: 0. Pixel/YOLO benchmark NOT RUN.
JSON/CSV/Markdown скачаны из CI; CSV SHA256 независимо совпал с JSON.

## APK

[ARM64 test APK](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43583788134/artifacts/14109472853), 21 852 081 байт.
SHA256 `e919414b401ebaf1c2496bacffdb1272b28e0470fc6a6b81a87695903f2cce89`.
После скачивания независимо проверены ZIP CRC, source asset, подпись, DEX/assets,
ELF+ZIP16KiB. Внутри GameLab/NES/ModelLab/JNI CPU. Подпись отладочная; этот APK
имеет тестовый applicationId и не доказывает совместимость обновления пользователя.

[Unsigned production APK](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/43584538793/artifacts/14109369525)
собран из того же SHA630415b: 11 733 241 байт,
SHA256 `1d98c6cbb2529daeaa51ab06150b07f6d7acfdc3355f527c60185ca8dda8fe4f`.
После скачивания проверены строгий data-only artifact gate, CRC, source,
package/manifest и ELF16K. Это **неподписанный** APK; sign_candidate=false,
защищённое подписание намеренно не запускалось без подключённого ключа.

## Дополнение: новая постоянная подпись · 14.11.2026

Пользователь явно разрешил новую подпись и чистую установку вместо совместимости
с прежним сертификатом. Создан RSA3072/PKCS12 ключ; зашифрованный keystore и пароль
сохранены отдельными файлами в закрытой папке личного Google Drive. Права проверены:
shared=false, только owner. Секретов и сертификатов в Git нет; только публичный fingerprint.
Ключ нужно повторно использовать для следующих обновлений, не создавать на каждый APK.

[Скачать подписанный APK](https://drive.google.com/file/d/1URrvhWOsW2u9cAqGfnqQ7xmnCk8HIFfY/view?usp=drivesdk),
11 738 829 байт, пакет `org.localpilot.android`, версия0.8.2/code54.
APK SHA256 `892f6bf4ad2b9380e93ccbb2b01f7a9c81f06492b382eed5bfb0c542e32fd7fa`.
Signer SHA256 `b960ab2feccf7f6c47602779695d13297755f97f654444d9e0f0737d3a1408c2`.
Подписи v2/v3, source630415b, CRC, ZIP/ELF16KiB и идентичность всех ZIP payload
entries исходному проверенному unsigned APK — PASS. Скачанная обратно из Drive
копия имеет тот же SHA256. Загрузка GitHub release assets по-прежнему возвращает401;
рабочая копия APK выдана через Drive. Автоматическое подписание CI пока не подключено.

При конфликте подписи сначала экспортировать нужные данные, затем переустановить
прежний пакет. Удаление приложения стирает его локальные данные. Другие .dev-пакеты
могут остаться рядом. [Инструкция](signed-fresh-install/INSTALL.ru.txt).
Физическая приёмка NOT_RUN; stable release_ready=false. Старый сертификат больше
не является блокером: это осознанно новая установочная линия.

## Исторические ограничения до разрешения новой подписи

Постоянная совместимая подпись не подключена (environments total_count=0);
физических ADB устройств0. Phone/tablet upgrade, Vulkan/POCO, 200 настоящих игровых
переходов и независимое улучшение PPO NOT RUN/NOT PROVEN. Доверенных Bubble-наград
в обучение0. Legacy10 мини-игр×3seed — только SIMULATED_GAME, отдельно в CSV.
Новые VLM/YOLO/VRPO не встроены под видом проверенной функциональности.
`release_ready=false`; стабильный релиз не опубликован.

Архив базы: `archive/main-before-event-reward-20261114` → `8b6cfca`.
[Схема и проверенные источники](../../research/VISUAL-AGENT-EVENT-REWARDS-20261114.ru.md).
