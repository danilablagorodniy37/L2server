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


def test_leader_minions_reports_a_leader_whose_privates_are_gone(monkeypatch):
	"""Male Spiked Stakato brings the female along; without her, quest 640 loses a monster."""
	import build_minions
	leader, minion = 22109, 22108
	assert minion in build_minions.template_minions().get(leader, set()), "run tools/interlude/build_minions.py"
	stripped = {k: (v - {minion} if k == leader else v) for k, v in build_minions.template_minions().items()}
	monkeypatch.setattr(build_minions, "template_minions", lambda: stripped)
	problems = checks.leader_minions()
	assert [k for k in problems if k.startswith(f"{leader} ")], problems


def test_leader_minions_reports_a_leader_the_manager_does_not_know(tmp_path, monkeypatch):
	"""A Privates block nobody spawns is dead weight, so the leader must be in MinionSpawnManager."""
	import build_minions
	manager = tmp_path / "MinionSpawnManager.java"
	manager.write_text("NPC.add(1);\n", encoding="utf-8")
	monkeypatch.setattr(build_minions, "MANAGER", manager)
	problems = checks.leader_minions()
	assert problems, "every leader with minions should now be unknown to the manager"
	assert all(places == ["not in MinionSpawnManager"] for places in problems.values()), problems


def test_html_quest_buttons_reports_a_button_of_an_unloaded_script(monkeypatch):
	"""The gatekeepers used to offer "Call Alegria" although the birthday event is not loaded."""
	import build_html_links
	monkeypatch.setattr(checks, "html_texts", lambda: (
		("data/html/teleporter/30080.htm", '<a action="bypass -h npc_%objectId%_Quest CharacterBirthday">Call Alegria</a>'),
		("script/com/l2jserver/datapack/gracia/x.htm", '<a action="bypass -h npc_%objectId%_Quest CharacterBirthday">not shown</a>'),
	))
	monkeypatch.setattr(build_html_links, "loaded_script_names", lambda: {"NoblesseTeleport"})
	assert dict(checks.html_quest_buttons()) == {"CharacterBirthday": ["data/html/teleporter/30080.htm"]}


def test_interlude_enchant_costs_reports_an_adena_price(tmp_path, monkeypatch):
	"""Pointing a skill back at an H5 group (adena, no experience) must be noticed."""
	import build_enchant_costs
	skills = tmp_path / "skills"
	skills.mkdir()
	(skills / "s.xml").write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list>'
		'<skill id="1" levels="37" name="Triple Slash" enchantGroup1="2" enchantGroup2="104"></skill></list>', encoding="utf-8")
	monkeypatch.setattr(build_enchant_costs, "SKILLS", skills)
	problems = checks.interlude_enchant_costs()
	assert set(problems) == {"skill 1 route 1"}, problems


def test_interlude_residence_skills_reports_a_castle_skill(tmp_path, monkeypatch):
	"""Gludio castle (1) must not give Residence Health again; fortress 106 may."""
	import build_residence_skills
	tree = tmp_path / "pledgeSkillTree.xml"
	tree.write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list><skillTree type="pledgeSkillTree">'
		'<skill skillName="Residence Health" skillId="593" skillLvl="1" getLevel="4" residenceSkill="true">'
		'<residenceId>1</residenceId><residenceId>106</residenceId></skill></skillTree></list>', encoding="utf-8")
	monkeypatch.setattr(build_residence_skills, "TREE", tree)
	assert dict(checks.interlude_residence_skills()) == {"593 Residence Health": ["residence 1"]}


def test_build_residence_skills_keeps_fortresses_and_drops_empty_skills():
	import build_residence_skills
	text = (
		'\t\t<skill skillName="Residence Health" skillId="593" skillLvl="1" getLevel="4" residenceSkill="true">\n'
		"\t\t\t<socialClass>APPRENTICE</socialClass>\n"
		"\t\t\t<residenceId>1</residenceId>\n"
		"\t\t\t<residenceId>106</residenceId>\n"
		"\t\t</skill>\n"
		'\t\t<skill skillName="Gludio Territory Benefaction" skillId="848" skillLvl="1" getLevel="5" residenceSkill="true">\n'
		"\t\t\t<socialClass>APPRENTICE</socialClass>\n"
		"\t\t\t<residenceId>81</residenceId>\n"
		"\t\t</skill>\n"
	)
	result, entries, skills = build_residence_skills.without_residences(text)
	assert (entries, skills) == (2, 1)
	assert "<residenceId>106</residenceId>" in result and "<residenceId>1</residenceId>" not in result
	assert "skillId=\"848\"" not in result


def test_manor_interlude_items_reports_a_coba_seed(tmp_path, monkeypatch):
	"""H5 put the level 79-82 Coba seeds into the castle manors."""
	import build_items
	seeds = tmp_path / "seeds.xml"
	seeds.write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list><castle id="1">'
		'<crop id="5073" seedId="5016" mature_Id="5103" reward1="1864" reward2="1878" />'
		'<crop id="6545" seedId="15327" mature_Id="6559" reward1="4044" reward2="4042" />'
		'</castle></list>', encoding="utf-8")
	monkeypatch.setattr(build_items, "SEEDS", seeds)
	problems = checks.manor_interlude_items()
	assert {key.split()[0] for key in problems} == {"15327"}, problems


def test_teleports_interlude_reports_token_price_and_moved_point(tmp_path, monkeypatch):
	"""An Olympiad Token price and the H5 landing point of the Cursed Village must both be noticed."""
	import fix_teleports
	sql = tmp_path / "teleport.sql"
	sql.write_text(
		"INSERT INTO `teleport` VALUES\n"
		"('Coliseum - 1 Olympiad Token',9900,146440,46723,-3400,1,1,13722),\n"
		"('Cursed Village - 1000 adena',9052,62084,-40935,-2802,1000,1,57);\n", encoding="utf-8")
	monkeypatch.setattr(fix_teleports, "TELEPORT_SQL", sql)
	problems = checks.teleports_interlude()
	assert set(problems) == {"9900 Coliseum - 1 Olympiad Token", "9052 Cursed Village - 1000 adena"}, problems


def test_fix_teleports_changes_currency_and_point():
	import fix_teleports
	text = (
		"('Coliseum - 1 Olympiad Token',9900,146440,46723,-3400,1,1,13722),\n"
		"('Cursed Village - 1000 adena',9052,62084,-40935,-2802,1000,1,57), -- retail\n"
		"('Rune -> Den of Evil',1116,68693,-110438,-1904,7500,0,57);"
	)
	result, changed = fix_teleports.fixed_rows(text)
	assert changed == 3
	assert "('Coliseum - 1 Noblesse Gate Pass',9900,146440,46723,-3400,1,1,6651)," in result
	assert "('Cursed Village - 1000 adena',9052,57670,-41672,-3154,1000,1,57), -- retail" in result
	assert result.endswith("('Rune -> Den of Evil',1116,68693,-110438,-1904,3000,0,57);")
	assert fix_teleports.fixed_rows(result) == (result, 0)
