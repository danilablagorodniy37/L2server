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


def test_interlude_stats_apply_the_acis_formulas():
	"""Level 70, STR 40, INT 21, DEX 30, CON 43, MEN 20: lvlMod 1.59 and the aCis stat bonuses."""
	import build_npc_stats as b
	s = {"level": "70", "str": "40", "int": "21", "dex": "30", "wit": "20", "con": "43", "men": "20", "exp": "4900", "sp": "300",
		"hp": "1000", "hpRegen": "10", "mp": "500", "mpRegen": "2", "pAtk": "100", "mAtk": "50", "pDef": "200", "mDef": "150",
		"crit": "4", "atkSpd": "253", "walkSpd": "60", "runSpd": "120"}
	stats = b.interlude_stats(s)
	lvl_mod = 1.59
	assert stats["hp"] == 1000  # both cores multiply HP by CON
	assert stats["pAtk"] == pytest.approx(100 * b.STR[40] * lvl_mod)
	assert stats["mAtk"] == pytest.approx(50 * b.INT[21] ** 2 * lvl_mod ** 2)
	assert stats["pDef"] == pytest.approx(200 * lvl_mod)
	assert stats["mDef"] == pytest.approx(150 * b.MEN[20] * lvl_mod)
	assert stats["crit"] == round(4 * b.DEX[30] * 10)
	assert b.STR[40] == 1.2 and b.CON[43] == 1.58  # same values as data/stats/statBonus.xml of H5


def test_middle_chest_picks_a_chest_not_a_mimic():
	"""A treasure box maker lists chests of three levels and their mimics; the middle chest is taken."""
	import xml.etree.ElementTree as ET
	maker = ET.fromstring(
		'<npcmaker><ai type="random_spawn_treasurebox"/>'
		'<npc id="18266" total="1" respawn="90sec"/><npc id="21802" total="1" respawn="90sec"/>'
		'<npc id="18267" total="1" respawn="90sec"/><npc id="21803" total="1" respawn="90sec"/>'
		'<npc id="18268" total="1" respawn="90sec"/><npc id="21804" total="1" respawn="90sec"/></npcmaker>')
	npcs = {
		18266: {"type": "L2Chest", "level": 24}, 18267: {"type": "L2Chest", "level": 27}, 18268: {"type": "L2Chest", "level": 30},
		21802: {"type": "L2Chest", "level": 24}, 21803: {"type": "L2Chest", "level": 27}, 21804: {"type": "L2Chest", "level": 30},
	}
	npc, template = build_spawns.middle_chest(maker, npcs)
	assert npc.get("id") == "18267" and template["level"] == 27


class TestCleanClientIni:
	"""tools/client/clean_ini.py takes the previous owner's settings out of the client's L2.ini."""

	INI = (
		"[URL]\r\n"
		"ServerAddr=127.0.0.1\r\n"
		"GamePointURL=http://pvp-moon.ru/login\r\n"
		"L2HomeURL=http://l2world.ru/\r\n"
		"L2UseReplayManager=true\r\n"
		"\r\n"
		"[AutoLogOn]\r\n"
		"IsL2AutoLogOn=Ture\r\n"
		"L2ID=zodiac\r\n"
		"L2Passwd=zodiac\r\n"
		"\r\n"
		"[PrimeShop]\r\n"
		"UsePrimeShop=true\r\n"
	)

	def test_clears_the_other_servers_pages_and_login(self):
		import clean_ini
		fixed, changed = clean_ini.clean(self.INI)
		assert "GamePointURL=\r\n" in fixed
		assert "L2HomeURL=\r\n" in fixed
		assert "IsL2AutoLogOn=False\r\n" in fixed
		assert "L2ID=\r\n" in fixed
		assert "zodiac" not in fixed
		assert "UsePrimeShop=false\r\n" in fixed, "the item mall of the later chronicles stays shut"
		assert len(changed) == 7, changed

	def test_leaves_our_own_settings_alone(self):
		import clean_ini
		fixed, _ = clean_ini.clean(self.INI)
		assert "ServerAddr=127.0.0.1\r\n" in fixed
		assert fixed.count("[URL]") == 1

	def test_a_clean_file_changes_nothing(self):
		import clean_ini
		once, _ = clean_ini.clean(self.INI)
		twice, changed = clean_ini.clean(once)
		assert once == twice, "cleaning a clean file must not touch it"
		assert changed == []

	def test_adds_a_missing_setting_to_its_section(self):
		import clean_ini
		fixed, changed = clean_ini.clean("[PrimeShop]\r\nSomethingElse=1\r\n")
		assert "UsePrimeShop=false" in fixed
		assert any("added" in line for line in changed), changed


class TestClientInterface:
	"""tools/client/xdat.py reads and writes system/Interface.xdat, the window layout of the client."""

	@staticmethod
	def layout(*windows):
		return {"shortcuts": [], "windows": list(windows), "separator": 1, "wndDefPos": [], "fonts": [], "styles": [],
			"chatChannels": [], "tail": b""}

	def test_writes_back_what_it_read(self):
		"""Windows nest: a window inside a window keeps its own children, and the file says so."""
		import xdat
		inner = xdat.new("Window", "InnerWnd", children=[xdat.new("TextBox", "txtHello", text="hello")])
		button = xdat.new("Button", "btnOne", size=1, sizeAbsolute=1, width=36, height=36,
			usePosition=1, relativePoint=1, anchorPoint=1, relativeTo=None, x=7.0, y=35.0)
		blob = xdat.write(self.layout(xdat.new("Window", "TestWnd", script="TestWnd", children=[button, inner])))
		again = xdat.read(blob)
		assert xdat.write(again) == blob
		window = again["windows"][0]
		assert [c["name"] for c in window["children"]] == ["btnOne", "InnerWnd"]
		assert window["children"][1]["children"][0]["text"] == "hello"
		assert (window["children"][0]["width"], window["children"][0]["y"]) == (36, 35.0)

	def test_a_string_outside_latin1_is_utf16(self):
		import xdat
		word = "Привет"
		blob = xdat.write(self.layout(xdat.new("Window", "Wnd", children=[xdat.new("TextBox", "txt", text=xdat.Text(word))])))
		text = xdat.read(blob)["windows"][0]["children"][0]["text"]
		assert text == word
		assert isinstance(text, xdat.Text)

	def test_walk_goes_into_nested_windows(self):
		import xdat
		tree = xdat.new("Window", "A", children=[xdat.new("Window", "B", children=[xdat.new("Button", "C")])])
		assert [(depth, node["name"]) for depth, node, _parent in xdat.walk([tree])] == [(0, "A"), (1, "B"), (2, "C")]

	@pytest.mark.client
	def test_the_real_file_comes_back_byte_for_byte(self):
		import xdat
		from conftest import CLIENT
		data = (CLIENT / "Interface.xdat.orig").read_bytes()
		layout = xdat.read(data)
		assert xdat.write(layout) == data
		assert len(layout["windows"]) > 200
		assert xdat.find(layout, "InventoryWnd") is not None


class TestClientCrypt:
	"""tools/client/l2dat.py: the Lineage2VerNNN files of the client."""

	def test_ver111_is_xor_0xac(self, tmp_path):
		import l2dat
		plain = bytes(range(256)) * 4
		path = tmp_path / "Some.u"
		path.write_bytes(l2dat.encode(plain, "111"))
		assert l2dat.version(path.read_bytes()) == "Lineage2Ver111"
		assert path.read_bytes()[28] == 0x00 ^ 0xAC
		assert l2dat.decode(path) == (plain, "111")

	def test_ver121_key_comes_from_the_file_name(self, tmp_path):
		import l2dat
		assert l2dat.xor_121("L2UI_CH3.utx") == 0x68, "sum of the characters of l2ui_ch3.utx, & 0xFF"
		plain = bytes([0xC1, 0x83, 0x2A, 0x9E]) + bytes(100)
		path = tmp_path / "L2UI_CH3.utx"
		path.write_bytes(l2dat.encode(plain, "121", path.name))
		assert l2dat.decode(path) == (plain, "121")
		# a copy under another name is still keyed as the original
		copy = tmp_path / "L2UI_CH3.utx.orig"
		copy.write_bytes(path.read_bytes())
		assert l2dat.decode(copy, "L2UI_CH3.utx")[0] == plain


@pytest.mark.client
class TestClientPackages:
	"""tools/client/upackage.py and utexture.py on the packages of the client copy."""

	def test_interface_u_keeps_the_source_of_its_classes(self):
		import upackage
		from conftest import CLIENT
		package = upackage.Package.open(CLIENT / "Interface.u.orig")
		assert (package.version, package.licensee) == (123, 37)
		sources = package.sources()
		assert len(sources) > 200
		assert sources["MenuWnd"].startswith("class MenuWnd extends UICommonAPI")

	def test_reads_an_rgba_texture_and_puts_the_same_pixels_back(self):
		import upackage
		import utexture
		from conftest import CLIENT
		package = upackage.Package.open(CLIENT.parent / "SysTextures" / "L2UI_CT1.utx.orig", "L2UI_CT1.utx")
		texture = utexture.find(package, "InventoryWnd.Inventory_DF_EquipSlot")
		assert (texture.width, texture.height, texture.format, len(texture.levels)) == (256, 512, utexture.RGBA8, 1)
		assert utexture.replace(package, {texture: texture.image()}) == package.data

	def test_blanks_a_dxt_texture(self):
		import upackage
		import utexture
		from conftest import CLIENT
		package = upackage.Package.open(CLIENT.parent / "SysTextures" / "L2Font-e.utx.orig", "L2Font-e.utx")
		texture = utexture.find(package, "mini_logo-e")
		assert texture.format == utexture.DXT3
		assert texture.image().getextrema()[3][1] > 0, "the logo has visible pixels"
		blanked = upackage.Package(utexture.replace(package, {texture: None}))
		assert utexture.find(blanked, "mini_logo-e").image().getextrema()[3] == (0, 0)
