# Референсы мобильных аркад: источники и границы проверки

Состояние на 21 октября 2026 года. Это каталог источников для последующей проверки и проектирования профилей. Документ не подтверждает автоигру ни в одной из перечисленных игр.

## Что фактически получено

**Ни одного изображения из официальных каталогов или сайтов издателей в этой проверке не получено.** Размеры, ориентация, содержимое и наличие отдельных phone/tablet-скриншотов не установлены. Здесь нет скачанных изображений, результатов распознавания либо измерений игровой точности.

Запросы через штатный proxy к следующим публичным адресам завершились `Tunnel connection failed: 403 Forbidden` до получения страницы:

- `https://www.limasky.com/`
- `https://getsetgames.com/`
- `https://www.icytower.com/`
- `https://sybogames.com/subway-surfers/`
- `https://imangistudios.com/`
- `https://www.halfbrick.com/games/jetpack-joyride`
- `https://www.hardlightstudio.com/`
- `https://www.sega.com/games/sonic-jump`
- `https://www.nintendo.com/us/`

App Store API `itunes.apple.com` и Google Play `play.google.com` также недоступны в текущей среде по результатам основной задачи. Изменение сетевого черновика не подтверждает применение правил к runtime. Доступность нужно проверить после применения настройки; отказ proxy не означает, что игра или страница удалены.

## Публичные маршруты к официальным референсам

Ниже приведены адреса каталогов и издательских сайтов для проверки. Содержимое этих страниц сейчас не получено: наличие приложения в текущем каталоге, издателя, версии и сами изображения следует подтвердить ответом сервера. Известный package ID не доказывает доступность приложения сегодня. Для старых игр возможны снятые с публикации версии; похожее название другой игры не заменяет исходный продукт.

| Игра | Издательский маршрут | Адрес Google Play для проверки | Семейство и статус |
|---|---|---|---|
| Doodle Jump | [Lima Sky](https://www.limasky.com/) | [`com.lima.doodlejump`](https://play.google.com/store/apps/details?id=com.lima.doodlejump) | Вертикальные прыжки по платформам; референсы не получены |
| Mega Jump | [Get Set Games](https://getsetgames.com/) | [`com.getsetgames.megajump`](https://play.google.com/store/apps/details?id=com.getsetgames.megajump) | Вертикальный подъём и сбор предметов; текущая публикация старого продукта не подтверждена |
| Sonic Jump | [SEGA: маршрут продукта](https://www.sega.com/games/sonic-jump), [HARDlight](https://www.hardlightstudio.com/) | [`com.sega.sonicjump`](https://play.google.com/store/apps/details?id=com.sega.sonicjump) | Вертикальный платформер; версия и доступность не подтверждены |
| Icy Tower | [Сайт Icy Tower](https://www.icytower.com/) | Точный Android package/вариант в этой проверке не установлен | Вертикальный платформер; сначала определить мобильную версию, не смешивать с исходной PC-игрой и клонами |
| Kid Icarus | [Nintendo](https://www.nintendo.com/us/) | Официальный Android package не установлен | Консольный платформер как визуальный/механический референс. Официальный релиз для Android-телефона или планшета здесь не подтверждён |
| Subway Surfers | [SYBO: Subway Surfers](https://sybogames.com/subway-surfers/) | [`com.kiloo.subwaysurf`](https://play.google.com/store/apps/details?id=com.kiloo.subwaysurf) | Раннер с полосами; референсы не получены |
| Temple Run | [Imangi Studios](https://imangistudios.com/) | [`com.imangi.templerun`](https://play.google.com/store/apps/details?id=com.imangi.templerun) | Раннер с поворотами и препятствиями; не подменять Temple Run 2 |
| Jetpack Joyride | [Halfbrick: Jetpack Joyride](https://www.halfbrick.com/games/jetpack-joyride) | [`com.halfbrick.jetpackjoyride`](https://play.google.com/store/apps/details?id=com.halfbrick.jetpackjoyride) | Боковой раннер с управлением тягой; референсы не получены |
| Sonic Dash | [SEGA](https://www.sega.com/), [HARDlight](https://www.hardlightstudio.com/) | [`com.sega.sonicdash`](https://play.google.com/store/apps/details?id=com.sega.sonicdash) | Раннер с полосами; не подменять Sonic Dash 2 |
| Sonic Jump Fever | [SEGA](https://www.sega.com/), [HARDlight](https://www.hardlightstudio.com/) | [`com.sega.sonicjumpfever`](https://play.google.com/store/apps/details?id=com.sega.sonicjumpfever) | Вертикальный платформер; отдельный продукт, не Sonic Jump; текущая публикация не подтверждена |

Для iPhone/iPad точный App Store ID нужно получить из ответа официального API и проверить по названию, `sellerName`, `bundleId` и ссылке `trackViewUrl`. Запрос поиска имеет вид `https://itunes.apple.com/search?term=Doodle%20Jump&entity=software&limit=25`; результат поиска не является готовым совпадением. Идентификаторы непроверенных приложений и URL изображений здесь не выдуманы.

В полученных App Store метаданных поля `screenshotUrls` и `ipadScreenshotUrls` следует сохранять раздельно. Их возможное присутствие в схеме API не означает, что они заполнены для конкретной игры. Google Play может показывать разные наборы медиа; принадлежность к телефону/планшету нельзя выводить только из соотношения сторон изображения.

## Что требуется для профиля управления

Для каждого продукта нужно связать референсы с конкретными package/bundle ID, версией, платформой и режимом игры. После фактической загрузки сохранить исходный URL, время получения, SHA-256, формат и измеренные размеры изображения. Рекламный скриншот полезен для знакомства с HUD и механикой; проверять управление следует по кадрам именно установленной Android-версии.

| Семейство | Что нужно распознавать | Что отдельно проверить на Android |
|---|---|---|
| Вертикальные прыжки: Doodle Jump, Mega Jump, Sonic Jump, Sonic Jump Fever; выбранная мобильная Icy Tower | Игрок, платформы, враги, вертикальная прокрутка камеры, границы экрана, терминальные экраны | Реальные способы управления конкретной версии. Жест Accessibility не подменяет наклон устройства или показания акселерометра; наличие подходящего сенсорного режима нужно подтвердить |
| Раннеры: Subway Surfers, Temple Run, Sonic Dash | Полоса/положение игрока, препятствия, повороты, дистанция до столкновения, пауза и завершение раунда | Направления и длительности жестов, дополнительные действия, а для версий с наклоном — отдельный способ такого ввода. Одинаковое семейство не делает профили взаимозаменяемыми |
| Jetpack Joyride | Высота игрока, препятствия, движение камеры, экраны меню и завершения | Удержание и отпускание касания как отдельные действия; короткого тапа/свайпа недостаточно для заявления о рабочем контроллере |
| Kid Icarus | Зависит от выбранного консольного выпуска | Сначала определить сам продукт и среду запуска. Консольный кадр не подтверждает наличие Android-приложения или доступного сенсорного управления |

До проверки реальных кадров, управления, Stop, смены окна и исхода действий эти аркады остаются кандидатами для разработки. Подтверждённая геометрия встроенных игр LocalPilot или распознавание стандартной палитры 2048 не подтверждают распознавание платформ, персонажей и препятствий перечисленных аркад.
