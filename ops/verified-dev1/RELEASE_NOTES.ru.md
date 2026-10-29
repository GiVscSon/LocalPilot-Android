Проверенный Dev-предрелиз **0.7.34-dev / code50**, пакет `org.localpilot.android.dev`.
Исходный коммит: `9d977d852e1ccb70a162abcf93b7dc3dd185980a`.

Исправлена потеря уже подтверждённых выстрелов в «Шипучке», когда следующий
жест отклонён до отправки из-за устаревшего кадра. Отменяется только
неотправленный план; подтверждения сохраняются, для новой попытки нужны
два свежих устойчивых кадра. Правила STOP, ActionGate, InputPermit,
проверка визуального результата и тайм-аут скриншота 4000 мс сохранены.

Проверки:

- Все 22 JVM-раннера и 10 тестов релизных инструментов PASS.
- Android APK, lint и компиляция инструментальных тестов PASS.
- Три полные матрицы API 30/35/36 на одном точном SHA PASS:
  [прогон 1](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/40937714200),
  [прогон 2](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/40937878368),
  [прогон 3](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/40938378702).
- Использован неизменённый APK из
  [прошедшей CI-сборки](https://github.com/GiVscSon/LocalPilot-Android/actions/runs/40937714357).
  Контрольные суммы, CRC и подпись независимо проверены.

**Физическая приёмка телефона/планшета и совместимость подписи с установленным
APK ещё не подтверждены.** Не удаляйте прежнее приложение ради обновления;
порядок проверки приведён во вложенном `INSTALL_AND_ACCEPTANCE.ru.md`.
Редкий [SCREENSHOT_TIMEOUT](https://github.com/GiVscSon/LocalPilot-Android/issues/24)
остаётся открытым диагностируемым ограничением; его устранение не заявляется.

APK SHA-256:
`c79ce6293d1f3f89f0c3c94fe7f6749361de4c3f9ec3fec6aa63bd4ba91536af`

Сертификат подписанта SHA-256:
`010a04cb0888a4627b095def584f131b5161cff81c4d74c1513cbb932bfafcde`
