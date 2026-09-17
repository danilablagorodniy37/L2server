"""Unit tests of the helpers in tools/interlude that rewrite datapack files."""

import re
import xml.etree.ElementTree as ET
from collections import defaultdict

import pytest

import build_html_links
import build_spawns
import checks
import datasets as ds
import kamael
import port_quest_html


class TestRemoveLinks:
	@staticmethod
	def broken(kind, list_id):
		return list_id in (2, 3)

	def test_removes_whole_line(self):
		text = (
			'<html><body>Trader:<br>\r\n'
			'<a action="bypass -h npc_%objectId%_Buy 1">Buy weapons.</a><br>\r\n'
			'<a action="bypass -h npc_%objectId%_multisell 2">Buy talismans.</a><br>\r\n'
			'<a action="bypass -h npc_%objectId%_Quest">Quest</a>\r\n'
			'</body></html>'
		)
		result, removed = build_html_links.remove_links(text, self.broken)
		assert removed == 1
		assert "multisell 2" not in result
		assert 'Buy 1">Buy weapons.</a><br>\r\n<a action="bypass -h npc_%objectId%_Quest">' in result

	def test_cuts_link_out_of_longer_line_and_keeps_the_rest(self):
		text = 'Text <a action="bypass -h npc_%objectId%_exc_multisell 3">Enhance.</a><br1><a action="bypass -h npc_%objectId%_Buy 1">Buy.</a>\n'
		result, removed = build_html_links.remove_links(text, self.broken)
		assert removed == 1
		assert result == 'Text <a action="bypass -h npc_%objectId%_Buy 1">Buy.</a>\n'

	def test_leaves_other_bypasses_alone(self):
		text = '<a action="bypass -h npc_%objectId%_Chat 2">Chat.</a><br>\n<a action="bypass -h Quest Q00001_Letters 2">Q</a>\n'
		assert build_html_links.remove_links(text, self.broken) == (text, 0)


class TestSpawnlistSql:
	def test_row_pattern(self):
		row = '("unset", 1, 32478, 148400, 26576, -2200, 0, 0, 16500, 60, 0, 0, 0),'
		assert ds._SPAWN_ROW.match(row).groups() == ("32478", "148400", "26576", "-2200")

	def test_fix_separators_terminates_statements_and_drops_empty_inserts(self):
		lines = [
			"INSERT INTO `spawnlist` VALUES",
			'("a", 1, 1, 0, 0, 0, 0, 0, 0, 60, 0, 0, 0),',
			'("b", 1, 2, 0, 0, 0, 0, 0, 0, 60, 0, 0, 0),',
			"INSERT INTO `spawnlist` VALUES",
			"-- nothing left here",
			"INSERT INTO `spawnlist` VALUES",
			'("c", 1, 3, 0, 0, 0, 0, 0, 0, 60, 0, 0, 0);',
			'("d", 1, 4, 0, 0, 0, 0, 0, 0, 60, 0, 0, 0);',
		]
		assert build_spawns.fix_separators(lines) == [
			"INSERT INTO `spawnlist` VALUES",
			'("a", 1, 1, 0, 0, 0, 0, 0, 0, 60, 0, 0, 0),',
			'("b", 1, 2, 0, 0, 0, 0, 0, 0, 60, 0, 0, 0);',
			"-- nothing left here",
			"INSERT INTO `spawnlist` VALUES",
			'("c", 1, 3, 0, 0, 0, 0, 0, 0, 60, 0, 0, 0),',
			'("d", 1, 4, 0, 0, 0, 0, 0, 0, 60, 0, 0, 0);',
		]

	def test_remove_rows_keeps_isle_of_souls(self):
		region = next(iter(kamael.REGIONS))
		lines = [
			("-- [20_20]", "20_20", None),
			("INSERT INTO `spawnlist` VALUES", "20_20", None),
			("-- Late NPC", "20_20", None),
			('("x", 1, 99, 0, 0, 0, 0, 0, 0, 60, 0, 0, 0),', "20_20", 99),
			("-- Guard", "20_20", None),
			('("x", 1, 7, 0, 0, 0, 0, 0, 0, 60, 0, 0, 0);', "20_20", 7),
			(f"-- [{region}]", region, None),
			("INSERT INTO `spawnlist` VALUES", region, None),
			('("x", 1, 99, 0, 0, 0, 0, 0, 0, 60, 0, 0, 0);', region, 99),
		]
		text, removed = build_spawns.remove_rows(lines, keep_ids={7})
		assert removed == {99: 1}
		assert "Late NPC" not in text
		assert text.count("99,") == 1 and "-- Guard" in text

	@pytest.mark.parametrize("value,expected", [("", 0), ("30sec", 30), ("5min", 300), ("2hour", 7200)])
	def test_seconds(self, value, expected):
		assert build_spawns.seconds(value) == expected


class TestSqlPatterns:
	def test_teleport_row_with_escaped_quote(self):
		sql = "('Giran -> Hardin\\'s Private Academy',28,105918,109759,-3192,4400,0,57),\n('Plain',2,1,1,1,1,0,57);"
		assert [int(v) for v in re.findall(checks.TELEPORT_ROW, sql, re.M)] == [28, 2]

	def test_boss_row(self):
		sql = "(25001,-54416,146480,-2887,0,129600,86400,95986,514), -- Greyclaw Kutus (23)"
		assert re.findall(checks.BOSS_ROW, sql, re.M) == ["25001"]


def test_properties_parser(tmp_path, monkeypatch):
	(tmp_path / "x.properties").write_text("# comment\n! other\nMaxPlayerLevel = 80\nReward=6651,50;57,1\n\nEmpty =\n", encoding="utf-8")
	monkeypatch.setattr(checks, "CONFIG", tmp_path)
	assert checks.properties("x.properties") == {"MaxPlayerLevel": "80", "Reward": "6651,50;57,1", "Empty": ""}


@pytest.mark.parametrize("name,expected", [
	("Sword of Valhalla", True),
	("Sword of Valhalla - Health", True),
	("Sword of Valhalla - Unknown Ability", False),
	("Dynasty Rapier", False),
	("Rapier {PvP}", False),
	("Rapier (Event)", False),
])
def test_interlude_like_weapon(name, expected):
	assert kamael._is_interlude_like_weapon(name, {"Health", "Haste"}) is expected


class TestFloatingSpawns:
	def test_spawn_row_keeps_everything_but_z(self):
		import fix_floating_spawns as ffs
		row = '("unset", 1, 20050, -72321, 110988, -3194, 0, 0, 10000, 60, 0, 0, 0),'
		m = ffs.SPAWN_ROW.match(row)
		assert (m.group(2), m.group(3), m.group(4), m.group(5)) == ("20050", "-72321", "110988", "-3194")
		assert f"{m.group(1)}-3544{m.group(6)}" == row.replace("-3194", "-3544")

	def test_boss_row(self):
		import fix_floating_spawns as ffs
		row = "(25375,22500,80300,-2772,0,129600,86400,87696,426), -- Zombie Lord Farakelsus (20)"
		m = ffs.BOSS_ROW.match(row)
		assert f"{m.group(1)}22522,80427,-3184{m.group(6)}" == "(25375,22522,80427,-3184,0,129600,86400,87696,426), -- Zombie Lord Farakelsus (20)"


def test_extra_npcs_are_spawned():
	"""Every NPC build_spawns imports on purpose is in the generated spawnlist."""
	spawned = {npc_id for npc_id, _ in checks.enabled_xml_spawns()}
	assert set(build_spawns.EXTRA_NPCS) <= spawned, sorted(set(build_spawns.EXTRA_NPCS) - spawned)


def test_underspawned_npcs_got_their_acis_makers():
	"""Monsters listed as underspawned are imported with as many spawns as aCis has."""
	imported = defaultdict(int)
	for f in ds._xml_files(ds.GAME / "data" / "spawnlist"):
		root = ET.parse(f).getroot()
		if root.get("enabled", "true") == "true":
			for npc in root.iter("npc"):
				imported[int(npc.get("id"))] += int(npc.get("count", 1))
	for npc_id in build_spawns.UNDERSPAWNED:
		assert imported[npc_id] >= 10, f"{npc_id} has only {imported[npc_id]} spawns"


def test_script_spawned_finds_npcs_that_only_a_script_places():
	"""Grave Keymaster (quest 503) stands nowhere in the world; the quest spawns him."""
	assert 27179 not in checks.world_spawned()
	assert 27179 in checks.script_spawned()


class TestPortQuestHtml:
	@staticmethod
	def build(tmp_path, acis_files):
		acis = tmp_path / "acis" / "Q353_PowerOfDarkness"
		acis.mkdir(parents=True)
		for name, text in acis_files.items():
			(acis / name).write_text(text, encoding="utf-8")
		h5 = tmp_path / "h5" / "Q00353_PowerOfDarkness"
		h5.mkdir(parents=True)
		return acis.parent, h5.parent

	def test_renames_bypasses_and_drops_the_old_dialogs(self, tmp_path, monkeypatch):
		acis_root, h5_root = self.build(tmp_path, {
			"31044-01.htm": '<a action="bypass -h Quest Q353_PowerOfDarkness 31044-02.htm">Ask</a>\n\n',
			"31044-02.htm": "<html><body>Second</body></html>",
		})
		stale = h5_root / "Q00353_PowerOfDarkness" / "31044-77.html"
		stale.write_text("old H5 dialog", encoding="utf-8")
		monkeypatch.setattr(port_quest_html, "ACIS_HTML", acis_root)
		monkeypatch.setattr(port_quest_html, "H5_QUESTS", h5_root)

		port_quest_html.port(353)

		ported = h5_root / "Q00353_PowerOfDarkness"
		assert not stale.exists()
		assert sorted(f.name for f in ported.glob("*.htm*")) == ["31044-01.htm", "31044-02.htm"]
		text = (ported / "31044-01.htm").read_text(encoding="utf-8")
		assert "bypass -h Quest Q00353_PowerOfDarkness 31044-02.htm" in text
		assert text.endswith(">Ask</a>\n"), repr(text)

	def test_refuses_a_quest_that_is_not_there(self, tmp_path, monkeypatch):
		acis_root, h5_root = self.build(tmp_path, {"31044-01.htm": "x"})
		monkeypatch.setattr(port_quest_html, "ACIS_HTML", acis_root)
		monkeypatch.setattr(port_quest_html, "H5_QUESTS", h5_root)
		with pytest.raises(SystemExit):
			port_quest_html.port(640)


@pytest.mark.parametrize("item_id,interlude", [
	(52, False),  # Hemp Cloak in Interlude, Vesper Dual Sword in H5
	(139, False),  # Dusk Sword in Interlude, an agathion pack in H5
	(153, True),  # Sickle, only renamed Sigil in H5
	(6622, True),  # Secret Book of Giants, renamed Giant's Codex
])
def test_reused_item_ids_are_not_interlude(item_id, interlude):
	assert (item_id in ds.interlude_item_ids()) is interlude
	assert item_id in ds.acis_ids("items")


def test_without_late_crops_keeps_interlude_crops():
	import build_items
	text = (
		'\t<castle id="1">\n'
		'\t\t<crop id="5073" seedId="5016" mature_Id="5103" reward1="1864" reward2="1878" alternative="false" />\n'
		'\t\t<crop id="6545" seedId="15327" mature_Id="6559" reward1="4044" reward2="4042" alternative="false" />\n'
		'\t</castle>\n'
	)
	result, removed = build_items.without_late_crops(text, allowed={5073, 5016, 5103, 1864, 1878, 6545, 6559, 4044, 4042})
	assert removed == [15327]
	assert 'seedId="5016"' in result and 'seedId="15327"' not in result
