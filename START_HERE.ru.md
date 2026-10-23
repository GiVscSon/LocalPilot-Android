# Передача LocalPilot другой модели — 23 октября 2026

Работай с этим существующим проектом. Не заменяй его старым клоном.
Цель пользователя — улучшить LocalPilot Android для игры в настоящие мобильные
2D-игры, с сохранением опыта и загрузкой при новом сеансе. Отдельные браузерные
прототипы пользователь отменил: описания механик предназначены для LocalPilot.

## Что передано

- Последний собранный APK: 0.7.20-dev, versionCode 36.
- applicationId: org.localpilot.android.dev; MainActivity:
  org.localpilot.android.MainActivity. Минимальная Android API 30.
- Код APK собран из коммита 4a098fee9a608bed5ed872c88676afd6983cc224.
  Следующие изменения исходников в комплекте касаются только документации.
- Ветка: sync/localpilot-android-0711 репозитория GiVscSon/LocalPilot-Android.
- GitHub Release: https://github.com/GiVscSon/LocalPilot-Android/releases/tag/v0.7.20-dev
  Страница опубликована без вложений: uploads.github.com вернул Forbidden.
- APK SHA256: f91a3de5264f7743b8e858123ba0c8b32ee76029f95b5d3032ea2a6bcc60a027.
- Сертификат SHA256: 55626377256dda3a6b5824b7278eb12db75b8a7d1c80baa9d2af4402ccac2684.
  Приватный ключ подписи в комплект исходников не включён. Для обновления поверх
  установленного приложения потребуется тот же ключ; не удаляй данные пользователя
  ради обхода конфликта подписей.

## С чего начать проверку

1. Прочитай UNIVERSAL_DEVELOPMENT_PROMPT.ru.md и docs/USER_MECHANICS_TRAINING.ru.md.
2. Проверь app/build.gradle, git status и фактические исходники PilotService,
   ArcadeController, ArcadeFeedback, ArcadeVision, ArcadeControls,
   ArcadeLearningStore, GridPuzzle*; не полагайся только на историю беседы.
3. Прочитай docs/RELEASE_0.7.20.ru.md и docs/VALIDATION_0.7.20.txt.
   LOCALPILOT_ANDROID_HANDOFF.md содержит историю, включая устаревшие ограничения
   сети/версий. При расхождении используй текущие исходники и новые документы.
4. Собери и проведи приёмку на реальном устройстве. Здесь физические Android-тесты
   не выполнялись; компиляция instrumentation не означает прохождения UI-тестов.

## Подтверждённые проверки последнего APK

- assembleDebug, lintDebug, compileDebugAndroidTestJavaWithJavac — PASS.
- Lint: 0 ошибок, 42 предупреждения.
- GridVerify: 3860 утверждений; Verify: все наборы PASS;
  ResultVerify: 1046; UniversalVerify: 172.
- Статический снимок 7×7: 49 клеток корректно разделены на четыре типа.
  Это не проверка прохождения игры. Поле 6×6 с едой/черепами отклоняется.
- Целостность ZIP APK и подпись v2 подтверждены.

## Сборка и тесты

Нужны JDK 17+ (проверяли на JDK 21), Gradle 8.9, SDK 35, Build Tools 35.0.0.
В текущей среде инструменты установлены вне репозитория:

```bash
export JAVA_HOME=/workspace/android-build-tools/jdk-21.0.12.1+1
export ANDROID_HOME=/workspace/android-build-tools/sdk
export ANDROID_USER_HOME=/workspace/android-build-tools/android-user
export GRADLE_USER_HOME=/workspace/android-build-tools/gradle-home
/workspace/android-build-tools/gradle-8.9/bin/gradle --offline --no-daemon \
  :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestJavaWithJavac
"$JAVA_HOME/bin/java" tests/GridVerify.java
"$JAVA_HOME/bin/java" tests/Verify.java
"$JAVA_HOME/bin/java" tests/ResultVerify.java
"$JAVA_HOME/bin/java" tests/UniversalVerify.java
```

Эти пути и кэш не входят в ZIP. В другой среде установи инструменты и зависимости;
первое получение зависимостей потребует сети, поэтому убери --offline.
Ключ подписи и локальные конфигурации с секретами не публикуй.

## Что действительно требует работы

- Универсальность идентификации приложения уже расширена; универсальность
  распознавания и управления произвольными играми ещё не достигнута.
- ARI/PADDLE/TRAFFIC/FLAPPY — отдельные контроллеры. TRAFFIC не является
  физическим Hill Climb, а FLAPPY не заменяет управление удержанием Jetpack.
- Новые сетки поддерживают обычные цветные заполненные поля, обмен и удаление
  групп, ручной цвет цели. Нет общей поддержки усилителей и автоматического OCR.
- Пузырьки, бурение, прыжок на предмет и новые механики из описаний пользователя
  пока не реализованы полностью. Не помечай их готовыми по одному описанию.
- Сохраняются измеренные модели движения и статистика; универсальной нейросетевой
  политики нет. Нельзя обещать, что каждая следующая партия станет лучше.
- Экран результата «Играй снова» не является положительным примером.
  Android ACK не является наградой и не подтверждает движение в игре.
- При смене игры нужно заново подтвердить окно/область/конфигурацию; старый кадр
  нельзя использовать для действий в новом контексте. Проверить это на устройстве.

Сохраняй STOP, ограничения свежести кадра, привязку окна и разрешения Android.
Пользователь играет на самом телефоне; ADB не является обязательным условием.
Не перезаписывай main, старые APK и релизы; не используй force push.
Разделяй синтетические проверки, статические кадры и настоящие партии на телефоне.
