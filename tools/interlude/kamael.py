"""What counts as Kamael content: kept even though it is absent from aCis (Interlude)."""

import re
import xml.etree.ElementTree as ET

import datasets as ds

# Isle of Souls with Kamael Village (map squares from spawnlist.sql "-- [X_Y]" markers).
REGIONS = {"16_19", "17_19", "16_20"}

# Quests absent from aCis that belong to the Kamael race: class paths, sagas,
# Isle of Souls newbie quests, the Isle of Souls pass for other races and the
# H5 tutorial that also drives Kamael newbies.
QUESTS = {
	60, 61, 62, 63, 64, 65, 66, 67, 68, 69,
	172, 173, 174, 175, 178, 179, 182,
	209, 236, 255,
	280, 281, 283, 284, 286,
}

_SCRIPTS = ds.GAME / "script" / "com" / "l2jserver" / "datapack"
SCRIPT_DIRS = [
	_SCRIPTS / "village_master" / "KamaelChange1",
	_SCRIPTS / "village_master" / "KamaelChange2",
	_SCRIPTS / "ai" / "npc" / "coach" / "KamaelCoach",
]

_INT_CONST = re.compile(r"\b(\d{4,6})\b")

# Item variants that did not exist in Interlude: event, rental and PvP copies,
# Common Items, S80+ weapon families.
_LATE_VARIANT = re.compile(r"\(Event\)|- Event|limited period|of Fortune|Baguette|\{PvP\}|^Common Item|^Foundation |Dynasty|Icarus|Vesper|Vorpal|Elegia|Moirai|Santa Claus")


def _interlude_weapon_suffixes():
	"""Special Ability suffixes (the part after ' - ') used by Interlude weapons."""
	suffixes = set()
	for f in ds._xml_files(ds.ACIS / "items"):
		for it in ET.parse(f).getroot().iter("item"):
			if it.get("type") == "Weapon" and " - " in it.get("name"):
				suffixes.add(it.get("name").split(" - ", 1)[1])
	return suffixes


def _is_interlude_like_weapon(name, suffixes):
	return not _LATE_VARIANT.search(name) and (" - " not in name or name.split(" - ", 1)[1] in suffixes)


def quest_dirs():
	quests = _SCRIPTS / "quests"
	for qid in sorted(QUESTS):
		yield from quests.glob(f"Q{qid:05d}_*")
		yield from (quests / "Dummy").glob(f"Q{qid:05d}_*.java")


def referenced_ids():
	"""All 4-6 digit numbers found in Kamael quest and class scripts."""
	ids = set()
	for d in list(quest_dirs()) + SCRIPT_DIRS:
		files = [d] if d.is_file() else d.rglob("*.java")
		for f in files:
			ids.update(int(v) for v in _INT_CONST.findall(f.read_text(encoding="utf-8", errors="replace")))
	return ids


def npc_ids(npcs, spawns_by_region):
	"""NPC templates spawned on the Isle of Souls or referenced by Kamael scripts."""
	result = {npc for region in REGIONS for npc in spawns_by_region.get(region, ())}
	result |= referenced_ids() & set(npcs)
	return result


def item_ids(items):
	"""Kamael weapons and bolts up to S grade, starting equipment, Kamael quest items."""
	suffixes = _interlude_weapon_suffixes()
	result = {i for i, it in items.items() if ds.is_kamael_item(it) and it["grade"] in ds.INTERLUDE_GRADES and _is_interlude_like_weapon(it["name"], suffixes)}
	text = (ds.GAME / "data" / "stats" / "initialEquipment.xml").read_text(encoding="utf-8")
	for block in re.findall(r"<equipment classId=\"(\d+)\">(.*?)</equipment>", text, re.S):
		if int(block[0]) in ds.KAMAEL_CLASS_IDS:
			result |= {int(i) for i in re.findall(r'<item id="(\d+)"', block[1])}
	result |= {i for i in referenced_ids() if i in items and items[i]["quest_item"]}
	return result
