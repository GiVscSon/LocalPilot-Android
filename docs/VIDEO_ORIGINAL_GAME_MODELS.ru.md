# Игровые модели из видео о Weight-Space Programming (MIT CSAIL)

**Этот каталог относится к моделям ИЗ РОЛИКА, а не к нашим DQN-моделям LocalPilot-Android.**
Зафиксировано: 4 ноября 2026 года. Оригинальное исследование опубликовано 2 ноября 2026 года.

## Оригинальный источник

- Работа: https://rikiyat.github.io/blog/weight-space-programming/
- Все опубликованные журналы и веса: https://github.com/RikiyaT/lm_understand-logs
- **Зафиксированный upstream commit:** `4d09ba6d6a7e891dd8063974dfe81b3d78e3494f`.
- Исходная лицензия для весов/данных: **CC BY 4.0**; скрипты авторов MIT; цитируемые тексты MinAtar в исходных промптах GPL-3.0. Сохраняем оригинальное `LICENSE` рядом со скачанными весами.

### 1. Четыре действительно обученные PPO-модели (Experiment 1)

Все четыре обучались примерно **1 миллиард шагов**, архитектура actor **500 → 32 → 32 → 5** с tanh, вход 10×10×5, действия no-op/up/down/left/right. Эти игры являются **собственными 10×10 grid-вариантами авторов**, а не готовыми Android-клонами.

| Game | ID, seed | Файл оригинальных full-precision весов |
| --- | --- | --- |
| Breakout | `a9f535cc`, seed 0 | `exp1_which_game/networks/a9f535cc.npz` |
| Pong | `dfc6cb65`, seed 0 | `exp1_which_game/networks/dfc6cb65.npz` |
| Pac-Man | `bdf99279`, seed 2 | `exp1_which_game/networks/bdf99279.npz` |
| Freeway | `0bbcc3ea`, seed 2 | `exp1_which_game/networks/0bbcc3ea.npz` |

В `.npz` присутствуют actor и critic; в `networks/<id>_actor.txt` приведены округлённые actor-веса, использовавшиеся в запросе Astra. Учёт обучения: `exp1_which_game/networks/meta/<id>.json`.

### 2. Обученный физический агент и его исправление (Experiment 4)

Модель Kinetix **679 → 128 → пять слоёв по 128 → 16 действий**, PPO около 1 млрд шагов. Исходная модель застревала на рампе; после правки приблизительно 3,54% весов проходила 1024/1024 тестов.

| Variant | Файл |
| --- | --- |
| PPO до исправления | `exp4_fix_car/networks/ppo_final_seed0.npz` |
| Actor после адресного изменения Astra | `exp4_fix_car/networks/edited_actor.npz` |

Это две **разные формы** содержимого: первая содержит actor и critic, вторая только исправленный actor. Не смешивать параметры механически. Логи: `exp4_fix_car/README.md`, `eval/base_result.json`, `eval/oneshot_result.json`.

### 3. Три сети, созданные напрямую из правил MinAtar (Experiment 5)

**Не обучены PPO и не использовали градиенты.** Astra создала веса за один ответ. Все три: **600 → 32 → 32 → 6 tanh**, 20 486 параметров, 10×10×6 объектных каналов, действия `noop,left,up,right,down,fire`.

| Game | Путь к выпущенным весам | Средний счёт автора / 1000 игр |
| --- | --- | --- |
| Breakout | `exp5_write_from_scratch/networks/breakout/hand.npz` | 517,4 |
| Seaquest | `exp5_write_from_scratch/networks/seaquest/hand.npz` | 53,1 |
| Asterix | `exp5_write_from_scratch/networks/asterix/hand.npz` | 53,2 |

**Важно:** PPO-базовые сравнения для этих трёх игр опубликованы только как **результаты**, их PPO-checkpoint отсутствуют в репозитории. Не выдавать базовые баллы за доступные веса.

### 4. Игра Go 9×9 (Experiment 6)

**Четыре версии Astra-сконструированной сети:** 0, 10, 25, 50 разрешённых проверочных проходов. Все имеют архитектуру **243 → (512 × 6) → 82 ReLU**, 1 480 274 параметра. Проходы нужны для оценки и точечной коррекции, **не для обычного градиентного обучения**.

| Variant | Директория с оригинальными весами |
| --- | --- |
| Astra без обратной связи | `exp6_go_from_scratch/networks/budget_0/` |
| Astra 10 проходов | `exp6_go_from_scratch/networks/budget_10/` |
| Astra 25 проходов | `exp6_go_from_scratch/networks/budget_25/` |
| Astra 50 проходов | `exp6_go_from_scratch/networks/budget_50/` |
| PPO сравнение seed 0 | `exp6_go_from_scratch/networks/ppo_same_size_lr3e-5_s0/` |
| PPO сравнение seed 1 | `exp6_go_from_scratch/networks/ppo_same_size_lr3e-5_s1/` |
| PPO сравнение seed 2 | `exp6_go_from_scratch/networks/ppo_same_size_lr3e-5_s2/` |

**Каждая папка содержит 14 тензоров** `L1.W.npy`, `L1.b.npy` ... `L6.W.npy`, `L6.b.npy`, `out.W.npy`, `out.b.npy`, и `arch.json`. Нелегальные ходы должны маскироваться. На Go PPO-агентах 20,5 млн партий не следует путать эти сети с экспериментами MinAtar.

### 5. Сколько моделей сохранено

**Всего 16 наборов оригинальных игровых весов**:

- 4 grid-PPO модели;
- 2 Kinetix (PPO и отредактированный actor);
- 3 MinAtar, вручную запрограммированные Astra;
- 4 Go-модели Astra;
- 3 Go-PPO соперника.

**Не входят в этот перечень** нерелизнутые PPO MinAtar (только баллы), неигровые DDPM, языковые модели, Qwen и Gemma. Для Qwen есть отдельный эксперимент 8 и отредактированная версия на Hugging Face, но это НЕ игровая политика.

## Скачать и сохранить на Windows / локальный LocalPilot

В этом репозитории добавлен `tools/fetch_video_game_models.py`. Python 3.9+ (стандартная библиотека), компьютер с доступом к GitHub и примерно 60 МБ свободного места.

```powershell
py tools\fetch_video_game_models.py --list
py tools\fetch_video_game_models.py --output "D:\LocalPilot\models\weightspace-video"
```

Для загрузки только девяти архивов grid/PPO, Kinetix и MinAtar:

```powershell
py tools\fetch_video_game_models.py --groups grid_ppo kinetix minatar_astra --output "D:\LocalPilot\models\weightspace-video"
```

Загрузчик получает **Git tree ровно зафиксированного коммита** и сравнивает Git blob SHA-1 каждого скачанного файла; для 9 NPZ SHA-1 дополнительно зафиксирован в коде. При несоответствии файл отвергается. После загрузки сохраняется `MODEL_INDEX.json` с SHA-256 каждого файла, оригинальная лицензия. Старые корректные файлы не перезаписываются.

**Мы ещё не скачали эти оригинальные веса на компьютер пользователя:** их наличие подтверждено в исходном публичном GitHub, а скрипт скачает их после запуска на ПК. Не утверждать, что они уже встроены в APK или совместимы с Android.

## Для будущего переноса на LocalPilot-Android

Эти 16 моделей используют **разные observation/action схемы**. Их нельзя подставлять напрямую в `ArcadeController` или `PilotService`.

- MinAtar Breakout может стать учителем физики для Карканоида, но визуальные каналы и правила отличаются.
- Kinetix показывает стратегию выхода из локального тупика, а не готовые жесты для Прыгуна Ари.
- Go может дать пример программирования весов + обязательного маскирования запрещённых ходов, но не имеет готового соответствия сеточным играм Маркета.
- PPO grid / Asterix / Seaquest можно использовать для **исследования переноса и сравнения**, только после адаптера входа/выхода и проверки на реальном игровом кадре.

**Правило безопасности:** STOP, ActionGate, InputPermit, TTL, Fail-Closed, защита UNKNOWN, Android ACK отдельно от visual witness. Этот каталог сам по себе не даёт ни одного подтверждённого реального хода в issue #69.

## Библиографическая запись

Rikiya Takehi, Jayden Teoh, Alex Zhang, Omar Khattab, *Sparks of Weight-Space Programming: LLMs can natively read & write neural network weights*, MIT CSAIL, 2 ноября 2026. https://rikiyat.github.io/blog/weight-space-programming/
