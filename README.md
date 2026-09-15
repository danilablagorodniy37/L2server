# L2server — Interlude + Kamael

Кастомная сборка на базе L2J High Five 2.6.3.0-SNAPSHOT: контент откатывается до Interlude,
из поздних хроник остаётся только раса Камаэль. Клиент High Five.

План и статус работ: [TASKS.md](TASKS.md).

## Структура
| Папка | Что это |
|---|---|
| `game/` | Игровой сервер: конфиги, датапак (`data/`), скрипты (`script/`), SQL, библиотеки |
| `login/` | Логин-сервер 2.6.7.3 |
| `core/` | Исходники ядра игрового сервера (Maven). Собранный jar кладётся в `game/l2jserver.jar` |
| `tools/` | Установка базы, сборка ядра, скрипты анализа H5 ↔ Interlude |

Геодата (~900 МБ) в git не хранится. Её нужно скопировать в `game/data/geodata/`
из `L2 SERVER - 2\l2j-server-game-2.6.3.0-SNAPSHOT\data\geodata`.

## Требования
- Java 21
- MySQL 8 на порту 3307, пользователь `root`/`root`
  (меняется в `game/config/database.properties`, `login/config/database.properties` и `tools/db_install.bat`)
- Python 3 — только для скриптов в `tools/interlude/`

## Первый запуск
1. `tools\db_install.bat l2jdb` — создаёт базу `l2jdb_custom` и копирует аккаунты из `l2jdb`
   (без аргумента база будет пустой; аккаунты создаются автоматически при первом входе).
2. `login\startLoginServer.bat`
3. `game\startGameServer.bat` — готов, когда в `game/logs/server.log` появится `Server Bartz loaded in N seconds`.

Рабочий High Five в `L2 SERVER - 2` использует те же порты (2106, 7777, 9014),
поэтому одновременно можно запускать только одну из сборок.

## Сборка ядра
`tools\build_core.bat` — собирает `core/` через Maven Wrapper и копирует `l2jserver.jar` в `game/`.
Скрипты из `game/script/` сервер компилирует сам при запуске, для них сборка не нужна.

## Откат до Interlude
Скрипты в `tools/interlude/` сравнивают датапак с aCis
(`../acis_public-master/aCis_datapack`, путь меняется переменной `ACIS_DATAPACK`)
и вычищают контент поздних хроник. Что считается камаэльским контентом, задано в `kamael.py`.

| Скрипт | Что делает |
|---|---|
| `analyze.py` | Отчёт: сколько предметов, NPC, скиллов и спавнов H5 нет в Interlude |
| `build_skill_trees.py` | Деревья скиллов классов по aCis, Камаэли до 80 уровня |
| `build_skill_stats.py` | Значения скиллов (расход, откат, сила, длительность) из aCis |
| `build_spellbooks.py` | Книги для изучения скиллов и их продажа в магазинах |
| `build_drops.py` | Дроп и спойл монстров из aCis |
| `build_quests.py` | Убирает квесты поздних хроник из `QuestLoader` |
| `build_ai.py` | Убирает AI-скрипты поздних зон и систем из `AILoader` |
| `build_spawns.py` | Чистит `spawnlist.sql`, переносит монстров Interlude из aCis |
| `build_shops.py` | Чистит магазины и мультиселлы |
| `build_items.py` | Чистит дроп, рецепты, стартовую экипировку |
| `build_teleports.py` | Убирает телепорты в поздние зоны и ссылки на них в HTML |
| `build_bosses.py` | Убирает поздних рейд- и эпик-боссов |

Скрипты уже применены, результат в git. Они рассчитаны на исходные файлы H5:
повторный запуск на уже очищенных данных почти везде безопасен, но `build_skill_trees.py`
и `build_spawns.py` нужно запускать только на исходных файлах (`git checkout <коммит импорта> -- <файл>`).
После изменения SQL-файлов таблицы в базе нужно перезалить.
