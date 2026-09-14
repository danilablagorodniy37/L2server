"""Loaders for the High Five datapack (game/) and the aCis Interlude reference datapack.

aCis is only read, never modified. Its location can be overridden with the
ACIS_DATAPACK environment variable.
"""

import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GAME = ROOT / "game"
ACIS = Path(os.environ.get("ACIS_DATAPACK", ROOT.parent / "acis_public-master" / "aCis_datapack")) / "data" / "xml"

KAMAEL_CLASS_IDS = set(range(123, 137))
KAMAEL_WEAPON_TYPES = {"RAPIER", "ANCIENTSWORD", "CROSSBOW"}
INTERLUDE_GRADES = {"NONE", "D", "C", "B", "A", "S"}


def _xml_files(folder):
	return sorted(Path(folder).glob("*.xml"))


def _sets(elem):
	return {s.get("name"): s.get("val") for s in elem.findall("set") if s.get("name")}


def h5_items():
	"""id -> dict(name, type, weapon_type, grade, etcitem_type)"""
	items = {}
	for f in _xml_files(GAME / "data" / "stats" / "items"):
		for it in ET.parse(f).getroot().findall("item"):
			sets = _sets(it)
			items[int(it.get("id"))] = {
				"name": it.get("name"),
				"type": it.get("type"),
				"weapon_type": sets.get("weapon_type"),
				"grade": (sets.get("crystal_type") or "NONE").upper(),
				"etcitem_type": sets.get("etcitem_type"),
				"quest_item": sets.get("is_questitem") == "true",
				"file": f.name,
			}
	return items


def acis_ids(kind):
	"""IDs defined by aCis for kind in {'items', 'npcs', 'skills'}."""
	tag = {"items": "item", "npcs": "npc", "skills": "skill"}[kind]
	ids = set()
	for f in _xml_files(ACIS / kind):
		for e in ET.parse(f).getroot().iter(tag):
			ids.add(int(e.get("id")))
	return ids


def h5_npcs():
	"""id -> dict(name, type, level)"""
	npcs = {}
	for f in _xml_files(GAME / "data" / "stats" / "npcs"):
		for n in ET.parse(f).getroot().findall("npc"):
			npcs[int(n.get("id"))] = {"name": n.get("name"), "type": n.get("type"), "level": int(n.get("level") or 0)}
	return npcs


def h5_skills():
	"""id -> name"""
	skills = {}
	for f in _xml_files(GAME / "data" / "stats" / "skills"):
		for s in ET.parse(f).getroot().findall("skill"):
			skills[int(s.get("id"))] = s.get("name")
	return skills


def acis_class_skills():
	"""classId -> set of (skillId, skillLvl) learnable in aCis for exactly that class."""
	result = {}
	for f in _xml_files(ACIS / "classes"):
		for c in ET.parse(f).getroot().findall("class"):
			class_id = None
			for s in c.findall("set"):
				if s.get("id") is not None:
					class_id = int(s.get("id"))
			skills = c.find("skills")
			result[class_id] = {(int(s.get("id")), int(s.get("lvl"))) for s in (skills.findall("skill") if skills is not None else [])}
	return result


# ("location", count, npc_templateid, locx, locy, locz, randomx, randomy, heading, respawn_delay, respawn_random, loc_id, periodOfDay)
_SPAWN_ROW = re.compile(r'^\("[^"]*", *\d+, *(\d+), *(-?\d+), *(-?\d+), *(-?\d+),')


def h5_spawns():
	"""List of (npc_id, x, y, z) from game/sql/spawnlist.sql."""
	spawns = []
	with open(GAME / "sql" / "spawnlist.sql", encoding="utf-8", errors="replace") as f:
		for line in f:
			m = _SPAWN_ROW.match(line)
			if m:
				spawns.append(tuple(int(v) for v in m.groups()))
	return spawns


def is_kamael_item(item):
	return item["weapon_type"] in KAMAEL_WEAPON_TYPES or (item["etcitem_type"] or "").upper() == "BOLT"
