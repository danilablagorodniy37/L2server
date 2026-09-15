# Тесты

Нужны Python 3.12+, `pytest` и `lxml` (`pip install pytest lxml`).
Всё запускается из корня репозитория: `tools\test.bat` или `python -m pytest`.

| Файл | Что проверяет | Когда пропускается |
|---|---|---|
| `test_datapack.py` | Проверки из `tools/interlude/checks.py`: битые ссылки на предметы, NPC, скиллы, магазины, мультиселлы, телепорты; XML по XSD; контент поздних хроник в магазинах, дропе, спавнах, деревьях скиллов, загрузчиках скриптов; конфиги Interlude; данные ботов (снаряжение, реплики, города) | нет aCis в `../acis_public-master` |
| `test_geodata.py` | NPC с точными координатами стоят на полу геодаты, а не висят выше 300 (исправление: `fix_floating_spawns.py`) | нет геодаты или JDK |
| `test_scripts.py` | Все скрипты `game/script` компилируются против `l2jserver.jar` (как при старте сервера, ~10 с) | нет `javac` |
| `test_tools.py` | Функции скриптов `tools/interlude`, которые переписывают файлы датапака | никогда |
| `test_database.py` | Таблицы `l2jdb_custom` совпадают с `game/sql` | MySQL на порту 3307 недоступен |
| `test_server_boot.py` | Логин и игровой сервер стартуют, сервер регистрируется на логине, в логе нет WARN/ERROR (~1 мин) | без `--run-server` или если сервер уже запущен |

Тесты ядра (JUnit, 231 тест): `core\mvnw.cmd test` или `tools\test.bat --core`.

## Что делать, если тест упал
- Сообщение показывает первые проблемы; полный список: `python tools/interlude/validate.py --all`.
- Проблема досталась от исходного H5 и её пока не чиним — добавить в `KNOWN` в `checks.py` с причиной.
  Когда её исправят, `test_known_problem_still_exists` напомнит убрать запись.
- `test_tables_match_sql_files` — после правки SQL-файла таблицу нужно перезалить:
  `mysql -uroot -proot -P3307 l2jdb_custom < game/sql/<файл>.sql`.

## Новая проверка
Функция в `checks.py`, которая возвращает `{проблема: [где]}`, с docstring-описанием,
и запись в `DATAPACK_CHECKS`. Тест и строка в `validate.py` появятся сами.
