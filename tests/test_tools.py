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
	"""tools/client/xdat.py reads system/Interface.xdat, the window layout of the client."""

	@staticmethod
	def text(word):
		"""A string the way the file holds it: one byte of length, the characters, a NUL."""
		raw = word.encode("ascii") + b"\x00"
		return bytes([len(raw)]) + raw

	def blob(self):
		return (
			b"\x02\x00\x00\x00"
			+ self.text("Window") + self.text("TestWnd") + self.text("undefined") + (b"\x01" * 8)
			+ self.text("Button") + self.text("btnOne") + self.text("undefined") + self.text("TestWnd") + (b"\x02" * 4)
			+ self.text("Texture") + self.text("VitalityPointBar") + self.text("TestWnd") + (b"\x03" * 6))

	def test_reads_the_strings_with_their_places(self):
		import xdat
		found = xdat.strings(self.blob())
		assert [text for _offset, text in found][:3] == ["Window", "TestWnd", "undefined"]
		assert all(offset >= 0 for offset, _text in found)

	def test_finds_the_widgets_and_what_they_belong_to(self):
		import xdat
		found = xdat.widgets(self.blob())
		assert [(w.kind, w.name) for w in found] == [
			("Window", "TestWnd"), ("Button", "btnOne"), ("Texture", "VitalityPointBar")]
		assert {w.window for w in found} == {"TestWnd"}
		assert all(w.size > 0 for w in found), "every record has a size"

	def test_counts_a_window(self):
		import xdat
		rows = xdat.summary(xdat.widgets(self.blob()))
		assert rows[0][0] == "TestWnd"
		assert rows[0][1] == 3

	def counted_blob(self):
		"""A window whose four byte child count sits right before its children, as the file has it."""
		head = self.text("Window") + self.text("TestWnd") + self.text("undefined") + (b"\x01" * 8)
		kids = (self.text("Button") + self.text("btnOne") + self.text("TestWnd") + (b"\x02" * 4)
			+ self.text("Texture") + self.text("VitalityPointBar") + self.text("TestWnd") + (b"\x03" * 6))
		return head + (2).to_bytes(4, "little") + kids

	def test_names_the_systems_of_the_later_chronicles(self):
		import xdat
		rows = {label: count for label, count, _bytes, _windows in xdat.systems(xdat.widgets(self.blob()))}
		assert rows["vitality"] == 1, "the vitality bar of High Five is found"
		assert rows["attributes"] == 0

	def test_says_which_windows_count_wrong(self):
		"""The check has to name the windows the reader cannot account for."""
		import xdat
		blob = self.counted_blob()
		assert xdat.mismatches(blob) == []
		wrong = blob.replace((2).to_bytes(4, "little"), (5).to_bytes(4, "little"), 1)
		assert xdat.mismatches(wrong) == [("TestWnd", 5, 2)]

	def test_records_of_one_kind_have_one_shape(self):
		"""Same kind, same number of strings and numbers - that is what makes the file readable."""
		import xdat
		found = xdat.shapes(xdat.widgets(self.counted_blob()))
		assert all(len(shapes) == 1 for shapes in found.values()), found
		assert found["Button"][0][0] == (3, 4), "Button, its name, its window, then four bytes"

	def test_dump_holds_every_record_with_its_bytes(self):
		import xdat
		blob = self.counted_blob()
		data = xdat.dump(blob)
		assert (data["bytes"], data["widgets"], data["counts_wrong"]) == (len(blob), 3, [])
		button = [row for row in data["records"] if row["name"] == "btnOne"][0]
		assert (button["kind"], button["window"], button["numbers"]) == ("Button", "TestWnd", 4)
		assert button["raw"] == "02020202", "the numbers of the record, as they stand in the file"


class TestClientInterfaceFormat:
	"""tools/client/xdat_layout.py reads the structure of Interface.xdat out of the file itself."""

	KINDS = ("Texture", "TextBox", "Button", "CheckBox", "ListCtrl", "ComboBox", "BarCtrl", "Tab")

	@staticmethod
	def text(word):
		raw = word.encode("ascii") + b"\x00"
		return bytes([len(raw)]) + raw

	@classmethod
	def widget(cls, kind, name, extra=(), numbers=8):
		"""A record: the kind, the name of the widget, a few more strings, then its numbers."""
		return cls.text(kind) + cls.text(name) + b"".join(cls.text(one) for one in extra) + (b"\x11" * numbers)

	@classmethod
	def window(cls, name, children, bias=0, after=0):
		"""A window record; somewhere in its numbers stands how many children follow it."""
		head = cls.widget("Window", name, ("undefined",), 12)
		return head + (len(children) + bias).to_bytes(4, "little") + (b"\x22" * after) + b"".join(children)

	@classmethod
	def file(cls, windows=8, header=b"\x10" * 16, bias=0, rare=True, after=0):
		"""A file the size and shape of the real one: many windows, many kinds, names that repeat."""
		out = [header]
		for w in range(windows):
			name = f"Some{w}Wnd"
			children = [cls.widget(cls.KINDS[(w + c) % len(cls.KINDS)], f"w{w}_{c}", (name, "undefined"), 4 + (c % 4) * 4)
				for c in range(3 + (w % 5))]
			if rare and (w == 2):
				# a kind that stands in the file exactly once, as MinimapCtrl does in the real one
				children.append(cls.widget("MinimapCtrl", "theMap", (name,), 6))
			out.append(cls.window(name, children, bias, after))
		return b"".join(out)

	def test_reads_the_kinds_out_of_the_file(self):
		"""The names of the windows and the word inside every record are not kinds, and must not be taken for them."""
		import xdat_layout
		kinds, container, spot, bias, right, total = xdat_layout.discover(self.file())
		assert container == "Window"
		assert (spot, bias) == ("end-4", 0)
		assert right == total > 0, "every window has to count right"
		assert set(self.KINDS) <= kinds
		assert "undefined" not in kinds
		assert not [name for name in kinds if name.endswith("Wnd") and name != "Window"], sorted(kinds)

	def test_finds_the_kind_that_stands_in_the_file_once(self):
		"""The first cut broke on a kind the reader did not know; now the short window gives it away."""
		import xdat_layout
		blob = self.file()
		kinds, _container, _spot, _bias, right, total = xdat_layout.discover(blob)
		assert "MinimapCtrl" in kinds
		assert right == total
		# without it one window is short, and that is what points at it
		without = xdat_layout.read(blob, kinds - {"MinimapCtrl"})
		assert xdat_layout.suspects(blob, xdat_layout.strings(blob), kinds - {"MinimapCtrl"}, "Window", "end-4", 0)
		assert xdat_layout.agreement(blob, without, "Window", "end-4", 0)[0] < total

	def test_reads_a_count_that_takes_the_window_in(self):
		"""Some counts include the holder itself; that is read off the file too, not assumed."""
		import xdat_layout
		kinds, container, spot, bias, right, total = xdat_layout.discover(self.file(bias=1))
		assert (container, spot, bias) == ("Window", "end-4", 1)
		assert right == total > 0

	def test_finds_the_count_when_more_numbers_follow_it(self):
		"""The count does not have to be the last thing in the record, and where it is is looked for."""
		import xdat_layout
		kinds, container, spot, bias, right, total = xdat_layout.discover(self.file(after=12))
		assert (container, spot, bias) == ("Window", "end-16", 0)
		assert right == total > 0

	def test_writes_the_file_back_byte_for_byte(self):
		import xdat_layout
		blob = self.file()
		again, dropped = xdat_layout.Layout.of(blob).rebuild()
		assert (again, dropped) == (blob, 0)

	def test_empties_a_window_and_leaves_the_others_as_they_were(self):
		import xdat
		import xdat_layout
		blob = self.file()
		before = xdat_layout.Layout.of(blob)
		held = {holder.name: [record.name for record in children] for holder, children in before.groups}
		fixed, gone, bytes_gone, problems = xdat.cut(blob, ["Some3Wnd"])
		assert problems == []
		assert gone == len(held["Some3Wnd"]) > 0
		assert bytes_gone == (len(blob) - len(fixed)) > 0
		after = xdat_layout.Layout(fixed, before.kinds, before.container, before.spot, before.bias)
		assert after.understood(), after.wrong()
		now = {holder.name: [record.name for record in children] for holder, children in after.groups}
		assert now["Some3Wnd"] == [], "the window is still there, with nothing in it"
		assert {name: rows for name, rows in now.items() if name != "Some3Wnd"} \
			== {name: rows for name, rows in held.items() if name != "Some3Wnd"}

	@classmethod
	def lying(cls, window=4, says=99):
		"""The same file with one window saying it holds more children than it does."""
		blob = bytearray(cls.file())
		import xdat_layout
		layout = xdat_layout.Layout.of(bytes(blob))
		holder = layout.groups[window][0]
		at = xdat_layout.count_place(holder, layout.spot)
		blob[at:at + 4] = says.to_bytes(4, "little")
		return bytes(blob), holder.name

	def test_refuses_a_file_it_cannot_account_for(self):
		"""One window that does not add up means the file is not understood; nothing is written."""
		import xdat
		blob, _name = self.lying()
		fixed, gone, bytes_gone, problems = xdat.cut(blob, ["Some3Wnd"])
		assert (fixed, gone, bytes_gone) == (blob, 0, 0)
		assert problems and "not understood" in problems[0]

	def test_never_empties_the_window_that_does_not_add_up(self):
		"""Where the count is wrong the reader does not know where the widgets end, so it stops."""
		import xdat
		blob, name = self.lying()
		fixed, gone, bytes_gone, problems = xdat.cut(blob, [name], force=True)
		assert (fixed, gone, bytes_gone) == (blob, 0, 0)
		assert problems and any("does not add up itself" in line for line in problems)

	def test_empties_what_does_add_up_in_a_file_that_does_not_all(self):
		"""A window that counts right can still be emptied, and the file says what it left alone."""
		import xdat
		import xdat_layout
		blob, lied = self.lying()
		before = xdat_layout.Layout.of(blob)
		held = {holder.name: [record.name for record in children] for holder, children in before.groups}
		fixed, gone, _bytes_gone, problems = xdat.cut(blob, ["Some3Wnd"], force=True)
		assert gone == len(held["Some3Wnd"]) > 0
		assert any("do not add up" in line for line in problems), problems
		after = xdat_layout.Layout(fixed, before.kinds, before.container, before.spot, before.bias)
		now = {holder.name: [record.name for record in children] for holder, children in after.groups}
		assert now["Some3Wnd"] == []
		assert now[lied] == held[lied], "the window that lies about its children is untouched"

	def test_refuses_a_file_that_holds_the_places_of_its_records(self):
		"""Nothing may move in a file that points at its own records, and a cut moves everything after it."""
		import xdat
		import xdat_layout
		blob = self.file(header=b"\x10" * 400)
		starts = [record.start for record in xdat_layout.Layout.of(blob).records]
		table = b"".join(start.to_bytes(4, "little") for start in starts)
		blob = table + blob[len(table):]
		fixed, gone, _bytes_gone, problems = xdat.cut(blob, ["Some3Wnd"])
		assert (fixed, gone) == (blob, 0)
		assert problems and "places of its own records" in problems[0]

	def test_lowers_a_count_of_records_in_the_header(self):
		"""When the header says how many records the file has, a cut has to say the new number."""
		import xdat
		import xdat_layout
		plain = self.file()
		records = len(xdat_layout.Layout.of(plain).records)
		header = b"\x10" * 4 + records.to_bytes(4, "little") + b"\x10" * 8
		blob = self.file(header=header)
		assert xdat_layout.Layout.of(blob).header_counters() == [(4, "records")]
		fixed, gone, _bytes_gone, problems = xdat.cut(blob, ["Some3Wnd"])
		assert problems == []
		assert int.from_bytes(fixed[4:8], "little") == (records - gone)
