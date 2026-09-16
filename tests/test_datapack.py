"""Datapack consistency: broken references and Interlude rules (tools/interlude/checks.py)."""

import pytest

import checks
import datasets as ds
from conftest import format_problems

pytestmark = pytest.mark.acis


@pytest.fixture(scope="module")
def results():
	"""Runs each check once; the checks share cached datapack loaders."""
	cache = {}

	def run(name):
		if name not in cache:
			cache[name] = checks.DATAPACK_CHECKS[name]()
		return cache[name]

	return run


@pytest.mark.parametrize("name", checks.DATAPACK_CHECKS)
def test_check(name, results):
	problems = checks.unexpected(name, results(name))
	assert not problems, f"{checks.DATAPACK_CHECKS[name].__doc__.strip()}\n{format_problems(problems)}"


@pytest.mark.parametrize("name,key", [(name, key) for name, known in checks.KNOWN.items() for key in known])
def test_known_problem_still_exists(name, key, results):
	"""A fixed known problem must be removed from checks.KNOWN, so it cannot come back silently."""
	assert key in {str(k) for k in results(name)}, f"{key} is fixed, remove it from checks.KNOWN['{name}']"


# A check that never reports anything proves nothing, so every check below is shown a broken datapack.

def test_quest_kill_targets_reports_a_monster_without_spawns(monkeypatch):
	"""Karik carries the 100 horns of quest 375; taking his spawns away must be noticed."""
	karik = 20629
	assert karik in checks.world_spawned(), "quest 375 needs Karik spawned, see build_spawns.UNDERSPAWNED"
	world, scripts = checks.world_spawned() - {karik}, checks.script_spawned() - {karik}
	monkeypatch.setattr(checks, "world_spawned", lambda: world)
	monkeypatch.setattr(checks, "script_spawned", lambda: scripts)
	problems = checks.quest_kill_targets()
	assert [k for k in problems if k.startswith(f"{karik} ")], problems


def test_spawn_zones_reports_an_unknown_territory(tmp_path, monkeypatch):
	(tmp_path / "zones" / "npcSpawnTerritories").mkdir(parents=True)
	(tmp_path / "zones" / "npcSpawnTerritories" / "z.xml").write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list><zone name="il_known" type="NpcSpawnTerritory" /></list>', encoding="utf-8")
	(tmp_path / "spawnlist").mkdir()
	(tmp_path / "spawnlist" / "s.xml").write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list enabled="true">'
		'<spawn name="a" zone="il_known"><npc id="20629" count="1" /></spawn>'
		'<spawn name="b" zone="il_gone"><npc id="20629" count="1" /></spawn></list>', encoding="utf-8")
	monkeypatch.setattr(checks, "DATA", tmp_path)
	assert dict(checks.spawn_zones()) == {"il_gone": ["spawnlist/s.xml"]}


def test_recipes_interlude_items_reports_a_later_chronicle_product(tmp_path, monkeypatch):
	# 14854 Recipe - Spiteful Soul Energy belongs to the H5 version of quest 503.
	(tmp_path / "recipes.xml").write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list>'
		'<item id="1" name="ok" itemId="3838"><ingredient id="1458" count="1" /><production id="3846" count="1" /></item>'
		'<item id="2" name="late" itemId="14854"><production id="14855" count="1" /></item></list>', encoding="utf-8")
	monkeypatch.setattr(checks, "DATA", tmp_path)
	problems = checks.recipes_interlude_items()
	assert {key.split()[0] for key in problems} == {"14854", "14855"}, problems


def test_quest_dialog_links_reports_a_case_label_that_is_neither_file_nor_link(tmp_path, monkeypatch):
	quest = tmp_path / "script" / "com" / "l2jserver" / "datapack" / "quests" / "Q00999_Example"
	quest.mkdir(parents=True)
	(quest.parent / "QuestLoader.java").write_text("\t\tQ00999_Example.class,\n", encoding="utf-8")
	(quest / "30000-01.htm").write_text("<html><body>ok</body></html>", encoding="utf-8")
	(quest / "Q00999_Example.java").write_text(
		'\t\t\tcase "30000-01.htm": {\n'  # a file
		'\t\t\tcase "30000-02.htm": {\n'  # an event a dialog links to
		'\t\t\tcase "30000-99.htm": {\n',  # neither
		encoding="utf-8")
	monkeypatch.setattr(ds, "GAME", tmp_path)
	monkeypatch.setattr(checks, "html_texts",
		lambda: (("data/html/x.htm", '<a action="bypass -h Quest Q00999_Example 30000-02.htm">go</a>'),))
	assert set(checks.quest_dialog_links()) == {"Q00999_Example/30000-99.htm"}
