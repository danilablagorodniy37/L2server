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


def test_interlude_npc_stats_reports_h5_values(tmp_path, monkeypatch):
	"""The H5 Gremlin (P. Atk. 8.47 with an Average P. Atk. passive) is not the Interlude one."""
	import build_npc_stats
	(tmp_path / "npcs.xml").write_text(
		'<?xml version="1.0" encoding="UTF-8"?>\n<list>\n'
		'\t<npc id="20001" level="1" type="L2Monster">\n'
		'\t\t<acquire expRate="29.39" sp="2" />\n'
		'\t\t<stats str="40" int="21" dex="30" wit="20" con="43" men="20">\n'
		'\t\t\t<vitals hp="39.74519" hpRegen="2" mp="40.0" mpRegen="0.9" />\n'
		'\t\t\t<attack physical="8.47458" magical="5.78704" random="30" critical="4" accuracy="4.75" attackSpeed="253" />\n'
		'\t\t\t<defence physical="44.44444" magical="29.59" />\n'
		'\t\t</stats>\n'
		'\t\t<skillList>\n\t\t\t<skill id="4410" level="11" />\n\t\t</skillList>\n'
		'\t</npc>\n</list>\n', encoding="utf-8")
	monkeypatch.setattr(build_npc_stats, "NPCS", tmp_path)
	assert set(checks.interlude_npc_stats()) == {"20001"}


def test_kamael_isle_reports_a_gatekeeper_that_is_gone(monkeypatch):
	"""Without Ragara a Kamael cannot leave the Isle of Souls."""
	ragara = 32163
	assert ragara in checks.world_spawned()
	# keep everything else, take Ragara away
	world = checks.world_spawned() - {ragara}
	monkeypatch.setattr(checks, "world_spawned", lambda: world)
	problems = checks.kamael_isle()
	assert [key for key in problems if key.startswith(f"{ragara} ")], problems


def test_phantom_hunting_reports_a_level_without_a_ground(tmp_path, monkeypatch):
	"""Bots of a level with no hunting ground would stand in town forever."""
	import build_phantom_hunting as hunting
	out = tmp_path / "hunting.txt"
	out.write_text(
		"# <x> <y> <z> <min level> <max level> <monsters> <zone name>\n"
		"50568 152408 -2656 20 25 14 Execution Grounds\n"
		"1 2 3 30 35 4 Cruma Tower\n"
		"1 2 3 30 35 12 Nowhere\n", encoding="utf-8")
	monkeypatch.setattr(hunting, "OUT", out)
	problems = checks.phantom_hunting()
	assert "Nowhere" in problems and "Cruma Tower" in problems
	assert [key for key in problems if key.startswith("level ")], problems


def test_phantom_trade_reports_an_item_the_core_refuses(tmp_path, monkeypatch):
	"""A quest item or a non-tradable one makes the store stay empty and the log fill with warnings."""
	import build_phantom_trade as trade
	out = tmp_path / "trade.txt"
	out.write_text("# <item id> <price> <name>\n79 100 Sword of Damascus\n5011 100 Star of Destiny\n", encoding="utf-8")
	monkeypatch.setattr(trade, "OUT", out)
	problems = checks.phantom_trade()
	assert [key for key in problems if key.startswith("5011 ")], problems
	assert not [key for key in problems if key.startswith("79 ")], problems


def test_server_rates_reports_multiplied_amounts(tmp_path, monkeypatch):
	"""Multiplying the amount instead of the chance is what makes a monster drop fifteen swords."""
	rates = {key: str(checks.RATE) for key in (
		"RateXp", "RateSp", "RatePartyXp", "RatePartySp", "RateDropManor", "PetXpRate", "SinEaterXpRate",
		"DeathDropChanceMultiplier", "CorpseDropChanceMultiplier", "RaidDropChanceMultiplier",
		"QuestDropChanceMultiplier", "RateQuestRewardXP", "RateQuestRewardSP", "RateQuestRewardAdena")}
	rates.update({
		"DeathDropAmountMultiplier": "44", "CorpseDropAmountMultiplier": "1", "RaidDropAmountMultiplier": "1",
		"QuestDropAmountMultiplier": "1", "DropAmountMultiplierByItemId": f"57,{checks.RATE}",
		"DropChanceMultiplierByItemId": "57,1", "UseQuestRewardMultipliers": "False"})
	(tmp_path / "rates.properties").write_text(
		"".join(f"{key} = {value}\n" for key, value in rates.items()), encoding="utf-8")
	(tmp_path / "general.properties").write_text("PreciseDropMultipliesStackableOnly = True\n", encoding="utf-8")
	monkeypatch.setattr(checks, "CONFIG", tmp_path)

	problems = checks.server_rates()

	assert "rates.properties DeathDropAmountMultiplier" in problems, problems
	assert "rates.properties UseQuestRewardMultipliers" in problems, problems
	assert "rates.properties RateXp" not in problems, problems


def test_phantom_squads_reports_a_camp_without_monsters(tmp_path, monkeypatch):
	"""A party camped where nothing spawns would stand around doing nothing."""
	(tmp_path / "phantoms.properties").write_text(
		"Count = 300\nTraders = 40\nTownBots = 100\nSmallParties = 0\n"
		"Camp1 = 4 9 78 0 0 0 Nowhere\n", encoding="utf-8")
	monkeypatch.setattr(checks, "CONFIG", tmp_path)

	problems = checks.phantom_squads()

	assert "Nowhere" in problems, problems


def test_phantom_squads_reports_a_camp_line_that_does_not_parse(tmp_path, monkeypatch):
	"""A camp line with a missing number would leave the parties unspawned."""
	(tmp_path / "phantoms.properties").write_text(
		"Count = 300\nTraders = 0\nTownBots = 0\nSmallParties = 0\n"
		"Camp1 = 4 9 78 136958 -89946 Ketra Orc Outpost\n", encoding="utf-8")
	monkeypatch.setattr(checks, "CONFIG", tmp_path)

	problems = checks.phantom_squads()

	assert "Camp1" in problems, problems


def test_phantom_squads_reports_a_crowd_that_does_not_fit(tmp_path, monkeypatch):
	"""More shopkeepers, townsfolk and party members than bots leaves nobody to hunt."""
	(tmp_path / "phantoms.properties").write_text(
		"Count = 100\nTraders = 40\nTownBots = 100\nSmallParties = 0\n"
		"Camp1 = 4 9 78 136958 -89946 -3697 Ketra Orc Outpost\n", encoding="utf-8")
	monkeypatch.setattr(checks, "CONFIG", tmp_path)

	problems = checks.phantom_squads()

	assert "Count" in problems, problems


def test_hunting_html_reports_a_ground_without_a_link(tmp_path, monkeypatch):
	"""A ground missing from the GM page is a ground the GM cannot fly to."""
	import build_hunting_html as page
	out = tmp_path / "HuntingGrounds.htm"
	out.write_text("<html><body>Cruma Tower 30-49</body></html>", encoding="utf-8")
	monkeypatch.setattr(page, "OUT", out)

	problems = checks.hunting_html()

	assert problems, "every ground of hunting.txt should be reported as missing"
	assert any("no link with its place" in reason for reasons in problems.values() for reason in reasons), problems


def test_phantom_ids_reports_a_buff_that_does_not_exist(tmp_path, monkeypatch):
	"""A wrong skill id means the bots go hunting without that buff and nobody notices."""
	src = checks.PHANTOM_SRC
	(tmp_path / "PhantomCombat.java").write_text(
		(src / "PhantomCombat.java").read_text(encoding="utf-8"), encoding="utf-8")
	(tmp_path / "PhantomFactory.java").write_text(
		(src / "PhantomFactory.java").read_text(encoding="utf-8"), encoding="utf-8")
	(tmp_path / "PhantomBuffs.java").write_text(
		(src / "PhantomBuffs.java").read_text(encoding="utf-8").replace("new Buff(1204, 2)", "new Buff(999999, 2)"),
		encoding="utf-8")
	monkeypatch.setattr(checks, "PHANTOM_SRC", tmp_path)

	problems = checks.phantom_ids()

	assert "buff 999999" in problems, problems


def test_phantom_ids_reports_a_soulshot_of_a_later_chronicle(tmp_path, monkeypatch):
	"""Shots the server does not have leave the bots hitting at half strength."""
	src = checks.PHANTOM_SRC
	for name in ("PhantomFactory.java", "PhantomBuffs.java"):
		(tmp_path / name).write_text((src / name).read_text(encoding="utf-8"), encoding="utf-8")
	# 22082 is a Blessed Spiritshot of a later chronicle, not an Interlude soulshot
	(tmp_path / "PhantomCombat.java").write_text(
		(src / "PhantomCombat.java").read_text(encoding="utf-8").replace("1835, 1463", "22082, 1463"), encoding="utf-8")
	monkeypatch.setattr(checks, "PHANTOM_SRC", tmp_path)

	problems = checks.phantom_ids()

	assert [key for key in problems if key.startswith("soulshot 22082")], problems


def test_phantom_settings_reports_a_setting_nobody_reads(tmp_path, monkeypatch):
	"""A setting left in the file after the code stopped reading it is a trap for the next change."""
	(tmp_path / "phantoms.properties").write_text("Enabled = True\nLongGone = 5\n", encoding="utf-8")
	src = tmp_path / "src"
	src.mkdir()
	(src / "Phantoms.java").write_text(
		'class Phantoms { void a() { property("Enabled", "False"); number("Count", 100); } }', encoding="utf-8")
	monkeypatch.setattr(checks, "CONFIG", tmp_path)
	monkeypatch.setattr(checks, "PHANTOM_SRC", src)

	problems = checks.phantom_settings()

	assert "LongGone" in problems, problems
	assert "Count" in problems, "a setting the scripts read must be in the file"
	assert "Enabled" not in problems, problems


def test_character_creation_reports_a_class_with_nowhere_to_start(tmp_path, monkeypatch):
	"""A starting class with no creation point leaves the player looking at an empty character screen."""
	chars = tmp_path / "stats" / "chars"
	(chars / "baseStats").mkdir(parents=True)
	(tmp_path / "skillTrees").mkdir()
	(chars / "classList.xml").write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list>'
		'<class classId="0" name="Human Fighter" />'
		'<class classId="10" name="Human Mystic" /></list>', encoding="utf-8")
	(chars / "pcCreationPoints.xml").write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list><startPoints>'
		'<spawn x="1" y="2" z="3" /><classId>0</classId></startPoints></list>', encoding="utf-8")
	for class_id, name in ((0, "HumanFighter"), (10, "HumanMystic")):
		(chars / "baseStats" / f"{name}.xml").write_text(
			f'<?xml version="1.0" encoding="UTF-8"?><list><classId>{class_id}</classId></list>', encoding="utf-8")
	(tmp_path / "skillTrees" / "classSkillTree.xml").write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list>'
		'<skillTree type="classSkillTree" classId="0" /><skillTree type="classSkillTree" classId="10" /></list>',
		encoding="utf-8")
	monkeypatch.setattr(checks, "DATA", tmp_path)

	problems = checks.character_creation()

	assert "Human Mystic" in problems, problems
	assert "Human Fighter" not in problems, problems


def test_character_creation_reports_a_class_without_a_skill_tree(tmp_path, monkeypatch):
	"""A class with no skill tree cannot learn anything from its trainer."""
	chars = tmp_path / "stats" / "chars"
	(chars / "baseStats").mkdir(parents=True)
	(tmp_path / "skillTrees").mkdir()
	(chars / "classList.xml").write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list><class classId="0" name="Human Fighter" /></list>', encoding="utf-8")
	(chars / "pcCreationPoints.xml").write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list><startPoints>'
		'<spawn x="1" y="2" z="3" /><classId>0</classId></startPoints></list>', encoding="utf-8")
	(chars / "baseStats" / "HumanFighter.xml").write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list><classId>0</classId></list>', encoding="utf-8")
	(tmp_path / "skillTrees" / "classSkillTree.xml").write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list></list>', encoding="utf-8")
	monkeypatch.setattr(checks, "DATA", tmp_path)

	problems = checks.character_creation()

	assert problems["Human Fighter"] == ["no skill tree"], problems


def test_interlude_epics_reports_a_boss_that_is_nowhere(monkeypatch):
	"""An epic with no spawn row and no script is one nobody can ever fight."""
	epics = checks.INTERLUDE_EPICS + ((29999, "Nobody's Boss", None),)
	monkeypatch.setattr(checks, "INTERLUDE_EPICS", epics)

	problems = checks.interlude_epics()

	assert "Nobody's Boss" in problems, problems


def test_interlude_attributes_reports_elemental_defence_on_a_monster(tmp_path, monkeypatch):
	"""The elemental defence of High Five would come back to life the moment attributes were switched on."""
	import strip_attributes
	npcs = tmp_path / "npcs"
	npcs.mkdir()
	(npcs / "20000-20099.xml").write_text(
		'<?xml version="1.0" encoding="UTF-8"?><list><npc id="20001" name="Rat">'
		'<stats><attribute><defence fire="20" water="20" /></attribute></stats></npc></list>', encoding="utf-8")
	monkeypatch.setattr(strip_attributes, "NPCS", npcs)

	problems = checks.interlude_attributes()

	assert "20000-20099.xml" in problems, problems


def test_strip_attributes_takes_the_block_and_leaves_the_rest():
	"""Only the elemental block goes; the physical and magical defence of the monster stays."""
	import strip_attributes
	text = (
		"\t\t<stats>\n"
		'\t\t\t<defence physical="512" magical="385" />\n'
		"\t\t\t<attribute>\n"
		'\t\t\t\t<defence fire="20" water="20" wind="-5" earth="20" holy="20" dark="20" />\n'
		"\t\t\t</attribute>\n"
		"\t\t</stats>\n")

	fixed, count = strip_attributes.strip(text)

	assert count == 1
	assert "<attribute>" not in fixed
	assert 'physical="512"' in fixed
	assert fixed.count("<defence") == 1


def test_admin_menu_buttons_reports_a_switched_off_system(tmp_path, monkeypatch):
	"""The GM menus used to offer Territory War, Gracia, Hellbound, instances and vitality."""
	menu = tmp_path / "game_menu.htm"
	menu.write_text('<html><body>'
		'<button value="TerritoryWar" action="bypass -h admin_territory_war" width=82 height=20>'
		'<button value="Castle" action="bypass -h admin_siege" width=82 height=20>'
		'</body></html>', encoding="utf-8")
	monkeypatch.setattr(checks, "ADMIN_HTML", tmp_path)
	assert dict(checks.admin_menu_buttons()) == {"admin_territory_war": ["game_menu.htm"]}
