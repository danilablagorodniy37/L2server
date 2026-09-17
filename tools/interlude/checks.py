"""Consistency checks of the Interlude + Kamael datapack, shared by validate.py and tests/.

Every check returns {problem: [places]}; an empty dict means the check passed.
KNOWN lists problems inherited from the original H5 datapack that are accepted
for now, so a check stays green while they are not fixed.
"""

import re
import socket
import subprocess
import xml.etree.ElementTree as ET
from collections import defaultdict
from functools import cache

import build_ai
import build_quests
import build_skill_trees
import datasets as ds
import kamael

DATA = ds.GAME / "data"
CONFIG = ds.GAME / "config"
RATE = 44  # what the server multiplies experience and drop chances by
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
TELEPORT_ROW = r"^\('(?:[^'\\]|\\.)*', *(\d+),"
BOSS_ROW = r"^\((\d+),"
# "private static final int NAME = 1234;" in a script
CONSTANT = re.compile(r"\b([A-Z][A-Z0-9_]*)\s*=\s*(\d+)\s*;")

INTERLUDE_CLASS_IDS = range(0, 119)
MAX_LEVEL = 80

# check name -> problems accepted for now (with the reason).
KNOWN = {
	"skill_references": {
		"5885 lvl 2": "original H5: raid bosses 25642 and 25648 use a skill level that does not exist",
	},
}


# ---------------------------------------------------------------- loaders

@cache
def items():
	return ds.h5_items()


@cache
def npcs():
	"""id -> (element, file name)"""
	result = {}
	for f in ds._xml_files(DATA / "stats" / "npcs"):
		for n in ET.parse(f).getroot().findall("npc"):
			result[int(n.get("id"))] = (n, f.name)
	return result


@cache
def skill_levels():
	"""id -> number of levels (without enchant routes)."""
	levels = {}
	for f in ds._xml_files(DATA / "stats" / "skills"):
		for s in ET.parse(f).getroot().findall("skill"):
			levels[int(s.get("id"))] = int(s.get("levels") or 1)
	return levels


@cache
def acis_items():
	return ds.interlude_item_ids()


@cache
def acis_npcs():
	return ds.acis_ids("npcs")


@cache
def allowed_items():
	"""Items allowed in shops and drops: Interlude or Kamael."""
	return acis_items() | kamael.item_ids(items())


@cache
def kamael_npcs():
	return kamael.referenced_ids() & set(npcs())


def enabled_xml_spawns():
	"""(npc_id, file name) of <list enabled="true"> spawnlists."""
	for f in ds._xml_files(DATA / "spawnlist"):
		root = ET.parse(f).getroot()
		if root.get("enabled", "true") == "true":
			for npc in root.iter("npc"):
				yield int(npc.get("id")), f.name


@cache
def world_spawned():
	"""NPC ids the world spawns by itself: spawnlist.sql, enabled XML spawnlists, boss tables."""
	ids = {int(m.group(1)) for line in (ds.GAME / "sql" / "spawnlist.sql").read_text(encoding="utf-8").splitlines() if (m := ds._SPAWN_ROW.match(line))}
	ids |= {npc_id for npc_id, _ in enabled_xml_spawns()}
	for table in ("raidboss_spawnlist.sql", "grandboss_data.sql"):
		ids |= set(sql_ids(table, BOSS_ROW))
	return ids


@cache
def script_spawned():
	"""NPC ids a datapack script spawns itself (addSpawn, createOnePrivate)."""
	call = re.compile(r"(?:addSpawn|createOnePrivate\w*)\s*\(\s*([A-Z][A-Z0-9_]*|\d+)")
	ids = set()
	for java in (ds.GAME / "script").rglob("*.java"):
		src = java.read_text(encoding="utf-8", errors="replace")
		if "addSpawn" not in src and "createOnePrivate" not in src:
			continue
		consts = {k: int(v) for k, v in CONSTANT.findall(src)}
		for token in call.findall(src):
			ids.add(int(token) if token.isdigit() else consts.get(token, 0))
	return ids


@cache
def acis_spawned():
	"""NPC ids the aCis spawnlist places in the world."""
	ids = set()
	for f in ds._xml_files(ds.ACIS.parent / "xml" / "spawnlist"):
		for npc in ET.parse(f).getroot().iter("npc"):
			ids.add(int(npc.get("id")))
	return ids


def loaded_quests():
	"""Folder names of the quests registered in QuestLoader."""
	loader = ds.GAME / "script" / "com" / "l2jserver" / "datapack" / "quests" / "QuestLoader.java"
	return set(re.findall(r"^\t+(Q\d{5}_\w+)\.class", loader.read_text(encoding="utf-8"), re.M))


def sql_ids(file, pattern):
	return [int(v) for v in re.findall(pattern, (ds.GAME / "sql" / file).read_text(encoding="utf-8"), re.M)]


def properties(name):
	"""key -> value of a game/config/*.properties file."""
	result = {}
	for line in (CONFIG / name).read_text(encoding="utf-8", errors="replace").splitlines():
		line = line.strip()
		if line and not line.startswith(("#", "!")) and "=" in line:
			key, value = line.split("=", 1)
			result[key.strip()] = value.strip()
	return result


def _item_name(i):
	return f"{i} {items()[i]['name']}" if i in items() else str(i)


def _multisell_entries():
	"""(item id, multisell name) of every positive ingredient and product."""
	for f in ds._xml_files(DATA / "multisell"):
		for e in ET.parse(f).getroot().iter():
			if e.tag in ("ingredient", "production") and int(e.get("id")) > 0:
				yield int(e.get("id")), "multisell " + f.stem


def _buylist_entries():
	for f in ds._xml_files(DATA / "buylists"):
		for it in ET.parse(f).getroot().iter("item"):
			yield int(it.get("id")), "buylist " + f.stem


# ---------------------------------------------------------------- references

def item_references():
	"""Items used by shops, multisells, drops, recipes, skill trees and starting gear exist."""
	missing = defaultdict(list)
	known = items()
	sources = list(_buylist_entries()) + list(_multisell_entries())
	for npc_id, (n, _) in npcs().items():
		sources += [(int(it.get("id")), f"npc {npc_id}") for it in n.iter("item")]
	for r in ET.parse(DATA / "recipes.xml").getroot().findall("item"):
		ids = [int(r.get("recipeId"))] + [int(e.get("id")) for e in r.iter() if e.tag in ("ingredient", "production", "productionRare")]
		sources += [(i, "recipe " + r.get("name")) for i in ids]
	for f in ds._xml_files(DATA / "skillTrees"):
		for tree in ET.parse(f).getroot().findall("skillTree"):
			label = f"{tree.get('type')} {tree.get('classId') or ''}".strip()
			sources += [(int(it.get("id")), label) for it in tree.iter("item")]
	sources += [(int(it.get("id")), "initialEquipment") for it in ET.parse(DATA / "stats" / "initialEquipment.xml").getroot().iter("item")]
	for item_id, place in sources:
		if item_id not in known:
			missing[item_id].append(place)
	return missing


def npc_references():
	"""NPCs used by spawns, boss tables and buylists exist."""
	missing = defaultdict(list)
	known = npcs()
	sources = [(npc_id, "spawnlist.sql") for npc_id, *_ in ds.h5_spawns()]
	sources += [(npc_id, "spawnlist/" + fname) for npc_id, fname in enabled_xml_spawns()]
	for table in ("raidboss_spawnlist.sql", "grandboss_data.sql"):
		sources += [(npc_id, table) for npc_id in sql_ids(table, BOSS_ROW)]
	for f in ds._xml_files(DATA / "buylists"):
		sources += [(int(npc.text), "buylist " + f.stem) for npc in ET.parse(f).getroot().iter("npc")]
	for npc_id, place in sources:
		if npc_id not in known:
			missing[npc_id].append(place)
	return missing


def boss_positions():
	"""Raid boss rows that spawn have real coordinates (H5 keeps unused bosses as 0,0,0 rows)."""
	problems = defaultdict(list)
	text = (ds.GAME / "sql" / "raidboss_spawnlist.sql").read_text(encoding="utf-8")
	for boss, x, y in re.findall(r"^\((\d+),(-?\d+),(-?\d+),", text, re.M):
		if int(x) == 0 and int(y) == 0:
			problems[int(boss)].append("raidboss_spawnlist.sql")
	return problems


def skill_references():
	"""Skills used by skill trees and NPC skill lists exist with that level."""
	missing = defaultdict(list)
	levels = skill_levels()
	for npc_id, (n, _) in npcs().items():
		for sk in n.iter("skill"):
			if sk.get("id") is not None and levels.get(int(sk.get("id")), 0) < int(sk.get("level") or 1):
				missing[f"{sk.get('id')} lvl {sk.get('level') or 1}"].append(f"npc {npc_id}")
	for f in ds._xml_files(DATA / "skillTrees"):
		for tree in ET.parse(f).getroot().findall("skillTree"):
			label = f"{tree.get('type')} {tree.get('classId') or ''}".strip()
			for sk in tree.findall("skill"):
				if levels.get(int(sk.get("skillId")), 0) < int(sk.get("skillLvl")):
					missing[f"{sk.get('skillId')} lvl {sk.get('skillLvl')}"].append(label)
	return missing


@cache
def html_texts():
	"""(path relative to game/, text) of every NPC dialog; read once for all checks."""
	return tuple((f.relative_to(ds.GAME).as_posix(), f.read_text(encoding="utf-8", errors="replace")) for f in ds.html_files())


@cache
def _all_html_links():
	link = re.compile(r"bypass -h npc_%objectId%_(multisell|exc_multisell|Buy|goto) (\d+)")
	return tuple((kind, int(value), rel) for rel, text in html_texts() for kind, value in link.findall(text))


def _html_links(kinds):
	for kind, value, rel in _all_html_links():
		if kind in kinds:
			yield value, rel


def html_multisell_links():
	"""NPC dialog buttons open multisells that exist."""
	existing = ds.multisell_ids()
	missing = defaultdict(list)
	for value, rel in _html_links({"multisell", "exc_multisell"}):
		if value not in existing:
			missing[value].append(rel)
	return missing


def html_buylist_links():
	"""NPC dialog buttons open buylists that exist."""
	existing = ds.buylist_ids()
	missing = defaultdict(list)
	for value, rel in _html_links({"Buy"}):
		if value not in existing:
			missing[value].append(rel)
	return missing


def html_teleport_links():
	"""NPC dialog buttons use teleports that exist in teleport.sql."""
	existing = set(sql_ids("teleport.sql", TELEPORT_ROW))
	if not existing:
		return {"teleport.sql": ["no rows parsed"]}
	missing = defaultdict(list)
	for value, rel in _html_links({"goto"}):
		if value not in existing:
			missing[value].append(rel)
	return missing


def html_quest_buttons():
	"""NPC dialogs open only quests and AI scripts that the loaders still load (build_html_links.py)."""
	import build_html_links

	names = build_html_links.loaded_script_names()
	button = re.compile(r"bypass -h npc_%objectId%_Quest ([\w-]+)")
	dead = defaultdict(list)
	for rel, text in html_texts():
		# A dialog inside the folder of an unloaded script is never shown, so it may keep its buttons.
		if not rel.startswith("data/html/"):
			continue
		for name in button.findall(text):
			if name not in names:
				dead[name].append(rel)
	return dead


def script_shop_calls():
	"""Loaded scripts open multisells and buylists that exist."""
	multisells, buylists = ds.multisell_ids(), ds.buylist_ids()
	call = re.compile(r"(separateAndSend|showBuyWindow)\((?:player, *)?(\d+) *[,)]")
	disabled = build_ai.REMOVED | {"hellbound"}
	missing = defaultdict(list)
	for f in (ds.GAME / "script").rglob("*.java"):
		if disabled & set(f.parts):
			continue
		for kind, value in call.findall(f.read_text(encoding="utf-8", errors="replace")):
			if int(value) not in (multisells if kind == "separateAndSend" else buylists):
				missing[f"{kind} {value}"].append(f.relative_to(ds.GAME / "script").as_posix())
	return missing


def quest_dialog_links():
	"""Dialog links of loaded quests (bypass -h Quest <quest> <file>.htm) open a dialog that exists or an event the script handles."""
	quests = ds.GAME / "script" / "com" / "l2jserver" / "datapack" / "quests"
	loaded = set(re.findall(r"^\t+(Q\d{5}_\w+)\.class", (quests / "QuestLoader.java").read_text(encoding="utf-8"), re.M))
	link = re.compile(r"bypass -h Quest (Q\d{5}_\w+) ([\w-]+\.html?)\b")
	missing = defaultdict(list)
	for rel, text in html_texts():
		for quest, dialog in link.findall(text):
			folder = quests / quest
			if quest not in loaded or (folder / dialog).exists():
				continue
			sources = "".join(p.read_text(encoding="utf-8", errors="replace") for p in folder.glob("*.java"))
			if f'"{dialog}"' not in sources:
				missing[f"{quest}/{dialog}"].append(rel)
	# Every dialog name a bypass leads to, so case labels can be told from missing files.
	linked = defaultdict(set)
	for _, text in html_texts():
		for quest, dialog in link.findall(text):
			linked[quest].add(dialog)
	# Dialogs the scripts open by name. A case label may also be an event a dialog links to.
	# Tutorial pages live in data/html.
	literal = re.compile(r'"(\w[\w-]*-[\w-]*\.html?)"')
	for quest in loaded:
		for java in (quests / quest).glob("*.java"):
			for line in java.read_text(encoding="utf-8", errors="replace").splitlines():
				if "showTutorialHTML" in line:
					continue
				is_label = line.lstrip().startswith("case ")
				for dialog in literal.findall(line):
					if (quests / quest / dialog).exists() or (is_label and (dialog in linked[quest])):
						continue
					missing[f"{quest}/{dialog}"].append(java.name)
	return missing


def quest_npcs():
	"""NPCs that loaded quests bind to (talk, kill, droplists, monster tables) exist."""
	quests = ds.GAME / "script" / "com" / "l2jserver" / "datapack" / "quests"
	loaded = set(re.findall(r"^\t+(Q\d{5}_\w+)\.class", (quests / "QuestLoader.java").read_text(encoding="utf-8"), re.M))
	known = npcs()
	calls = re.compile(r"(?:bindStartNpc|bindTalk|bindFirstTalk|bindKill|bindAttack|bindSpawn|withNpcs|addSingleDrop|addGroupedDrop\w*)\(([^;]*?)\)")
	const = re.compile(r"\b([A-Z][A-Z0-9_]*)\s*=\s*(\d+)\s*;")
	missing = defaultdict(list)
	for quest in sorted(loaded):
		for java in (quests / quest).glob("*.java"):
			src = java.read_text(encoding="utf-8", errors="replace")
			consts = {k: int(v) for k, v in const.findall(src)}
			ids = set()
			for args in calls.findall(src):
				first = re.match(r"\s*([A-Z][A-Z0-9_]*|\d+)", args)
				# addSingleDrop(npcId, item, ...) and addGrouped*(npcId, ...): only the first argument is an NPC.
				tokens = re.findall(r"[A-Z][A-Z0-9_]*|\d+", args) if not first or "Drop" not in args else [first.group(1)]
				ids |= {int(t) if t.isdigit() else consts.get(t, 0) for t in tokens}
			ids |= {int(v) for v in re.findall(r"(?:MOBS|MONSTERS|CHANCES)\.put\((\d+),", src)}
			for npc_id in ids:
				if 18000 <= npc_id < 40000 and npc_id not in known:
					missing[npc_id].append(quest)
	return missing


def quest_kill_targets():
	"""Monsters that loaded quests need killed and that aCis puts in the world are spawned here too."""
	quests = ds.GAME / "script" / "com" / "l2jserver" / "datapack" / "quests"
	# bindKill(...) names the monsters directly; a droplist passed to it names them in its own calls.
	call = re.compile(r"bindKill\(([^;]*?)\)|withNpcs\(([^;)]*)\)|addSingleDrop\(\s*([A-Z][A-Z0-9_]*|\d+)")
	# Four Sepulchers monsters are placed by the core, not by the datapack.
	core_spawned_types = {"L2SepulcherMonster", "L2SepulcherNpc"}
	available = world_spawned() | script_spawned()
	missing = defaultdict(list)
	for quest in sorted(loaded_quests()):
		for java in (quests / quest).glob("*.java"):
			src = java.read_text(encoding="utf-8", errors="replace")
			consts = {k: int(v) for k, v in CONSTANT.findall(src)}
			ids = set()
			for groups in call.findall(src):
				for token in re.findall(r"[A-Z][A-Z0-9_]*|\d+", "".join(groups)):
					ids.add(int(token) if token.isdigit() else consts.get(token, 0))
			for npc_id in ids:
				if npc_id in available or npc_id not in acis_spawned():
					continue
				template = npcs().get(npc_id)
				if template is not None and template[0].get("type") in core_spawned_types:
					continue
				missing[f"{npc_id} {template[0].get('name') if template else '?'}"].append(quest)
	return missing


def leader_minions():
	"""Leaders of the world carry the minions aCis gives them, and MinionSpawnManager knows them (build_minions.py)."""
	import build_minions

	spawned = world_spawned()
	by_script = script_spawned()
	in_template = build_minions.template_minions()
	manager = build_minions.MANAGER.read_text(encoding="utf-8")
	registered = {int(v) for v in re.findall(r"NPC\.add\((\d+)\);", manager)}
	problems = defaultdict(list)
	for leader, minions in build_minions.acis_privates().items():
		template = npcs().get(leader)
		if (leader not in spawned) or (template is None) or (template[0].get("type") in build_minions.BOSS_TYPES):
			continue
		wanted = {m for m in minions if (m in npcs()) and (m not in by_script)}
		if not wanted:
			continue
		missing = wanted - in_template.get(leader, set())
		if missing:
			problems[f"{leader} {template[0].get('name')}"].append("no minions " + ", ".join(str(m) for m in sorted(missing)))
		elif leader not in registered:
			problems[f"{leader} {template[0].get('name')}"].append("not in MinionSpawnManager")
	return problems


def spawn_zones():
	"""Territory spawns of the XML spawnlists point at an NpcSpawnTerritory that exists."""
	zones = {z.get("name") for f in ds._xml_files(DATA / "zones" / "npcSpawnTerritories") for z in ET.parse(f).getroot().findall("zone")}
	missing = defaultdict(list)
	for f in ds._xml_files(DATA / "spawnlist"):
		root = ET.parse(f).getroot()
		if root.get("enabled", "true") != "true":
			continue
		for spawn in root.findall("spawn"):
			zone = spawn.get("zone")
			if zone and zone not in zones:
				missing[zone].append("spawnlist/" + f.name)
	return missing


def recipes_interlude_items():
	"""Recipes, their materials and their products are Interlude or Kamael items."""
	allowed = allowed_items()
	problems = defaultdict(list)
	for recipe in ET.parse(DATA / "recipes.xml").getroot().findall("item"):
		where = "recipe " + (recipe.get("name") or recipe.get("id"))
		ids = {int(recipe.get("itemId"))} if recipe.get("itemId") else set()
		for entry in recipe.iter():
			if entry.tag in ("ingredient", "production") and entry.get("id"):
				ids.add(int(entry.get("id")))
		for item_id in sorted(ids - allowed):
			problems[_item_name(item_id)].append(where)
	return problems


def loader_classes():
	"""Every script class registered in QuestLoader and AILoader has a source file."""
	missing = defaultdict(list)
	script = ds.GAME / "script"
	for loader in ("com/l2jserver/datapack/quests/QuestLoader.java", "com/l2jserver/datapack/ai/AILoader.java"):
		for cls in re.findall(r"^import (com\.l2jserver\.datapack\.[\w.]+);", (script / loader).read_text(encoding="utf-8"), re.M):
			if not (script / (cls.replace(".", "/") + ".java")).exists():
				missing[cls].append(loader)
	return missing


def xml_schemas():
	"""XML files under game/data and game/config are valid against the XSD they declare."""
	from lxml import etree

	xsi = "{http://www.w3.org/2001/XMLSchema-instance}noNamespaceSchemaLocation"
	schemas = {}
	problems = defaultdict(list)
	files = [f for f in DATA.rglob("*.xml") if "geodata" not in f.parts] + list(CONFIG.glob("*.xml"))
	for f in files:
		rel = f.relative_to(ds.GAME).as_posix()
		try:
			doc = etree.parse(str(f))
		except etree.XMLSyntaxError as e:
			problems[rel].append(f"not well-formed: {e}")
			continue
		location = doc.getroot().get(xsi)
		if not location:
			continue
		xsd = (f.parent / location).resolve()
		if xsd not in schemas:
			schemas[xsd] = etree.XMLSchema(etree.parse(str(xsd))) if xsd.exists() else None
		if schemas[xsd] is None:
			problems[rel].append(f"schema {location} not found")
		elif not schemas[xsd].validate(doc):
			problems[rel].append(str(schemas[xsd].error_log.last_error))
	return problems


# ---------------------------------------------------------------- Interlude rules

def shops_interlude_items():
	"""Shops and multisells sell and take only Interlude or Kamael items."""
	late = defaultdict(list)
	for item_id, place in list(_buylist_entries()) + list(_multisell_entries()):
		if item_id not in allowed_items():
			late[_item_name(item_id)].append(place)
	return late


def drops_interlude_items():
	"""Interlude NPCs drop only Interlude or Kamael items."""
	late = defaultdict(list)
	for npc_id, (n, _) in npcs().items():
		if npc_id in acis_npcs():
			for it in n.iter("item"):
				if int(it.get("id")) not in allowed_items():
					late[_item_name(int(it.get("id")))].append(f"npc {npc_id}")
	return late


def manor_interlude_items():
	"""Castle manors sell seeds and buy crops of Interlude items only (build_items.py)."""
	import build_items

	allowed = allowed_items()
	problems = defaultdict(list)
	for castle in ET.parse(build_items.SEEDS).getroot().findall("castle"):
		for crop in castle.findall("crop"):
			for attr in ("id", "seedId", "mature_Id", "reward1", "reward2"):
				item_id = int(crop.get(attr))
				if item_id not in allowed:
					problems[_item_name(item_id)].append(f"castle {castle.get('id')} {attr}")
	return problems


def quest_rewards():
	"""Loaded quests hand out only Interlude or Kamael items."""
	quests = ds.GAME / "script" / "com" / "l2jserver" / "datapack" / "quests"
	loaded = set(re.findall(r"^\t+(Q\d{5}_\w+)\.class", (quests / "QuestLoader.java").read_text(encoding="utf-8"), re.M))
	# giveItems(player, ID, count), st.rewardItems(ID, count), new ItemHolder(ID, count)
	call = re.compile(r"(?:give|reward)Items\(\s*(?:player|killer|qs\.getPlayer\(\))?\s*,?\s*([A-Z][A-Z0-9_]*|\d+)\s*[,)\[]")
	holder = re.compile(r"new ItemHolder\(\s*([A-Z][A-Z0-9_]*|\d+)\s*,")
	const = re.compile(r"\b([A-Z][A-Z0-9_]*)\s*=\s*(\d+)\s*;")
	array = re.compile(r"\b([A-Z][A-Z0-9_]*)\s*=\s*\{(.*?)\};", re.S)
	problems = defaultdict(list)
	for quest in sorted(loaded):
		folder = quests / quest
		if not folder.is_dir():
			continue
		source = "".join(f.read_text(encoding="utf-8", errors="replace") for f in folder.glob("*.java"))
		numbers = {name: [int(value)] for name, value in const.findall(source)}
		arrays = {}
		for name, body in array.findall(source):
			if "ItemHolder" in body:
				continue
			# {{id, count}, {id, count}}: only the first number of a pair is an item.
			pairs = re.findall(r"\{([^{}]*)\}", body)
			if pairs:
				arrays[name] = [int(re.findall(r"\d+", pair)[0]) for pair in pairs if re.findall(r"\d+", pair)]
			else:
				arrays[name] = [int(value) for value in re.findall(r"\b(\d{2,5})\b", body)]
		ids = set()
		for token in call.findall(source) + holder.findall(source):
			if token.isdigit():
				ids.add(int(token))
			else:
				ids.update(numbers.get(token, ()))
				ids.update(arrays.get(token, ()))
		for item_id in ids:
			if (item_id in items()) and (item_id not in allowed_items()):
				problems[_item_name(item_id)].append(quest)
	return problems


def teleports_interlude():
	"""Teleports cost adena or Interlude items, and the reviewed ones land and cost as in Interlude (fix_teleports.py)."""
	import fix_teleports

	allowed = allowed_items()
	problems = defaultdict(list)
	rows = {}
	for line in fix_teleports.TELEPORT_SQL.read_text(encoding="utf-8").split("\n"):
		m = fix_teleports._ROW.match(line)
		if not m:
			continue
		tid, item = int(m.group(2)), int(m.group(8))
		rows[tid] = m
		if item not in allowed:
			problems[f"{tid} {m.group(1)}"].append(f"paid with {_item_name(item)}")
	for tid, (npc, desc) in fix_teleports.MOVED.items():
		x, y, z, _ = fix_teleports.acis_point(npc, desc)
		m = rows.get(tid)
		if m and (int(m.group(3)), int(m.group(4)), int(m.group(5))) != (x, y, z):
			problems[f"{tid} {m.group(1)}"].append(f"lands off the Interlude point {x},{y},{z}")
	for tid, (npc, desc) in fix_teleports.PRICES.items():
		price = fix_teleports.acis_point(npc, desc)[3]
		m = rows.get(tid)
		if m and int(m.group(6)) != price:
			problems[f"{tid} {m.group(1)}"].append(f"costs {m.group(6)} instead of {price}")
	return problems


def spawns_interlude_npcs():
	"""Spawns (spawnlist.sql outside the Isle of Souls, enabled XML spawnlists, bosses) are Interlude or Kamael NPCs."""
	allowed = acis_npcs() | kamael_npcs()
	late = defaultdict(list)

	def name(npc_id):
		return f"{npc_id} {npcs()[npc_id][0].get('name')}" if npc_id in npcs() else str(npc_id)

	region = None
	for line in (ds.GAME / "sql" / "spawnlist.sql").read_text(encoding="utf-8").splitlines():
		if m := re.match(r"-- \[(\d+_\d+)\]", line):
			region = m.group(1)
		elif (m := ds._SPAWN_ROW.match(line)) and region not in kamael.REGIONS and int(m.group(1)) not in allowed:
			late[name(int(m.group(1)))].append("spawnlist.sql")
	for npc_id, fname in enabled_xml_spawns():
		if npc_id not in allowed:
			late[name(npc_id)].append("spawnlist/" + fname)
	for table in ("raidboss_spawnlist.sql", "grandboss_data.sql"):
		for npc_id in sql_ids(table, BOSS_ROW):
			if npc_id not in acis_npcs():
				late[name(npc_id)].append(table)
	return late


def interlude_skill_trees():
	"""Class skill trees 0-118 match aCis (skill, level, required level); Kamael trees stop at level 80 without S80/S84 Expertise."""
	acis = build_skill_trees.acis_class_entries()
	problems = defaultdict(list)
	root = ET.parse(DATA / "skillTrees" / "classSkillTree.xml").getroot()
	for tree in root.findall("skillTree"):
		class_id = int(tree.get("classId"))
		for sk in tree.findall("skill"):
			skill_id, lvl, get_level = int(sk.get("skillId")), int(sk.get("skillLvl")), int(sk.get("getLevel"))
			key = f"class {class_id}: skill {skill_id} lvl {lvl}"
			if class_id in INTERLUDE_CLASS_IDS:
				if not any(e[0] == skill_id and e[1] == lvl and e[3] == get_level for e in acis.get(class_id, ())):
					problems[key].append(f"getLevel {get_level} not in aCis")
			elif get_level > MAX_LEVEL:
				problems[key].append(f"getLevel {get_level} above {MAX_LEVEL}")
			elif skill_id == build_skill_trees.EXPERTISE and lvl > 5:
				problems[key].append("Expertise S80/S84")
	for late_tree in ("highFiveNewClassSkillTree.xml", "highFiveCrossClassSkillTree.xml", "transferSkillTree.xml", "subClassSkillTree.xml",
		"collectSkillTree.xml", "forgottenSkillTree.xml", "subPledgeSkillTree.xml", "transformSkillTree.xml"):
		if (DATA / "skillTrees" / late_tree).exists():
			problems[late_tree].append("post-Interlude skill tree is back")
	return problems


def interlude_enchant_routes():
	"""Skills can be enchanted only as in Interlude: the same skills and the same number of routes (build_enchant_skills.py)."""
	import build_enchant_skills

	acis = build_enchant_skills.acis_routes()
	problems = defaultdict(list)
	for f in ds._xml_files(DATA / "stats" / "skills"):
		for s in ET.parse(f).getroot().findall("skill"):
			routes = len([k for k in s.attrib if k.startswith("enchantGroup")])
			expected = acis.get(int(s.get("id")), 0)
			if routes != expected:
				problems[f"{s.get('id')} {s.get('name')}"].append(f"{routes} routes, Interlude has {expected}")
	return problems


def interlude_enchant_costs():
	"""Enchanting a skill costs SP and experience, no adena, with the Interlude chances of its tier (build_enchant_costs.py)."""
	import build_enchant_costs

	tables, routes = build_enchant_costs.acis_tables()
	groups = {int(g.get("id")): g.findall("enchant") for g in ET.parse(build_enchant_costs.GROUPS).getroot().findall("group")}
	problems = defaultdict(list)
	for f in ds._xml_files(build_enchant_costs.SKILLS):
		for skill in ET.parse(f).getroot().findall("skill"):
			for attr, value in skill.attrib.items():
				if not attr.startswith("enchantGroup"):
					continue
				route = int(attr[len("enchantGroup"):])
				key = f"skill {skill.get('id')} route {route}"
				index = routes.get((int(skill.get("id")), route))
				if index is None:
					problems[key].append("no Interlude costs")
					continue
				levels = groups.get(int(value))
				if levels is None:
					problems[key].append(f"group {value} missing")
					continue
				for lvl, (enchant, wanted) in enumerate(zip(levels, tables[index]), start=1):
					got = (int(enchant.get("adena")), int(enchant.get("exp")), int(enchant.get("sp")), int(enchant.get("chance76")), int(enchant.get("chance80")))
					if got != (0, wanted["exp"], wanted["sp"], wanted["rate76"], wanted["rate80"]):
						problems[key].append(f"group {value} +{lvl}: adena/exp/sp/chance76/chance80 {got}")
						break
				if len(levels) != len(tables[index]):
					problems[key].append(f"group {value} has {len(levels)} levels")
	return problems


def interlude_residence_skills():
	"""Only fortresses give residence clan skills: none for castles (not in Interlude) or Territory War territories (build_residence_skills.py)."""
	import build_residence_skills

	problems = defaultdict(list)
	for skill in ET.parse(build_residence_skills.TREE).getroot().iter("skill"):
		if skill.get("residenceSkill") != "true":
			continue
		for residence in skill.findall("residenceId"):
			if int(residence.text) in build_residence_skills.REMOVED_RESIDENCES:
				problems[f"{skill.get('skillId')} {skill.get('skillName')}"].append(f"residence {residence.text}")
	return problems


def interlude_npc_stats():
	"""Monsters and bosses of Interlude fight with the Interlude level, stats, experience and modifier passives (build_npc_stats.py)."""
	import build_npc_stats

	monsters = build_npc_stats.acis_monsters()
	problems = defaultdict(list)
	for path in sorted(build_npc_stats.NPCS.glob("*.xml")):
		text = path.read_text(encoding="utf-8")
		for npc_id, m in build_npc_stats.npc_blocks(text):
			if npc_id in monsters and "<stats " in m.group(0) and build_npc_stats.with_stats(m.group(0), *monsters[npc_id]) != m.group(0):
				problems[str(npc_id)].append(path.name)
	return problems


def kamael_isle():
	"""The Isle of Souls has its services and a way to the mainland: village masters, shops, warehouse and gatekeepers whose dialogs open the teleports."""
	spawned = world_spawned()
	problems = defaultdict(list)
	# the services a Kamael needs on the island
	services = {
		32139: "village master", 32140: "village master", 32146: "village master",
		32163: "gatekeeper Ragara", 32189: "gatekeeper Largo of the harbour",
		32164: "grocer", 32170: "warehouse keeper", 32135: "newbie guide", 32138: "Kekropus",
	}
	for npc_id, what in services.items():
		if npc_id not in spawned:
			problems[f"{npc_id} {what}"].append("not spawned on the isle")
	# Ragara offers the mainland, the mainland gatekeepers offer the isle
	rows = {int(m.group(2)) for line in (ds.GAME / "sql" / "teleport.sql").read_text(encoding="utf-8").split("\n")
		if (m := re.match(r"^\('((?:[^'\\]|\\.)*)',(\d+),", line))}
	linked = defaultdict(set)
	for rel, text in html_texts():
		for tid in re.findall(r"npc_%objectId%_goto (\d+)", text):
			linked[int(tid)].add(rel)
	# Kamael Village -> the seven towns, and the towns -> Kamael Village
	for tid in list(range(1122, 1128)) + [1139] + list(range(12050, 12057)):
		if tid not in rows:
			problems[f"teleport {tid}"].append("missing from teleport.sql")
		elif not linked.get(tid):
			problems[f"teleport {tid}"].append("no dialog opens it")
	return problems


def interlude_config():
	"""Server switches keep Interlude values: level cap, disabled later systems, Olympiad rewards."""
	expected = {
		"character.properties": {"MaxPlayerLevel": "80", "MaxSubclassLevel": "80", "MaxPetLevel": "81"},
		"chronicle.properties": {"EnableInstances": "False", "EnableGracia": "False", "EnableHellbound": "False",
			"EnableTerritoryWar": "False", "EnableAttributes": "False"},
		"vitality.properties": {"Enabled": "False"},
		"general.properties": {"AllowMail": "False", "AllowAttachments": "False", "EnableBlockCheckerEvent": "False",
			"EnableBotReportButton": "False"},
		"olympiad.properties": {"CompetitionRewardItem": "6651"},
		# H5 gives every NPC a random weapon glow (+4..+21); Interlude NPCs have plain weapons.
		"npc.properties": {"RandomEnchantEffect": "False"},
	}
	problems = defaultdict(list)
	for file, keys in expected.items():
		actual = properties(file)
		for key, value in keys.items():
			if actual.get(key, "<missing>").lower() != value.lower():
				problems[f"{file} {key}"].append(f"is {actual.get(key, '<missing>')}, expected {value}")
	olympiad = properties("olympiad.properties")
	for key in ("ClassedReward", "NonClassedReward", "TeamReward"):
		for pair in filter(None, olympiad.get(key, "").split(";")):
			if int(pair.split(",")[0]) not in allowed_items():
				problems[f"olympiad.properties {key}"].append(f"rewards non-Interlude item {pair}")
	return problems


def server_rates():
	"""The server runs at x44, and the rates multiply chances, not the size of a single drop (rates.properties)."""
	problems = defaultdict(list)
	rates = properties("rates.properties")
	for key in ("RateXp", "RateSp", "RatePartyXp", "RatePartySp", "RateDropManor", "PetXpRate", "SinEaterXpRate",
		"DeathDropChanceMultiplier", "CorpseDropChanceMultiplier", "RaidDropChanceMultiplier",
		"QuestDropChanceMultiplier", "RateQuestRewardXP", "RateQuestRewardSP", "RateQuestRewardAdena"):
		if rates.get(key) != str(RATE):
			problems[f"rates.properties {key}"].append(f"is {rates.get(key, '<missing>')}, expected {RATE}")
	# A monster dropping fifteen swords comes from multiplying the amount: the amounts stay as Interlude wrote them.
	for key in ("DeathDropAmountMultiplier", "CorpseDropAmountMultiplier", "RaidDropAmountMultiplier",
		"QuestDropAmountMultiplier"):
		if rates.get(key) != "1":
			problems[f"rates.properties {key}"].append(f"is {rates.get(key, '<missing>')}, the amount of a drop is not multiplied")
	# adena is the one item that comes in a bigger pile instead of dropping more often
	if rates.get("DropAmountMultiplierByItemId") != f"57,{RATE}":
		problems["rates.properties DropAmountMultiplierByItemId"].append(f"expected 57,{RATE} (adena)")
	if rates.get("DropChanceMultiplierByItemId") != "57,1":
		problems["rates.properties DropChanceMultiplierByItemId"].append("expected 57,1 (adena drops as often as in Interlude)")
	# with False the quest multiplier also applies to a rewarded weapon, and the player gets 44 of them
	if rates.get("UseQuestRewardMultipliers", "").lower() != "true":
		problems["rates.properties UseQuestRewardMultipliers"].append("must be True, or quest reward gear is multiplied too")
	if properties("general.properties").get("PreciseDropMultipliesStackableOnly", "").lower() != "true":
		problems["general.properties PreciseDropMultipliesStackableOnly"].append("must be True, or a chance over 100% drops several swords")
	return problems


def interlude_loaders():
	"""Quests and AI scripts of later chronicles stay out of QuestLoader and AILoader."""
	problems = defaultdict(list)
	script = ds.GAME / "script" / "com" / "l2jserver" / "datapack"
	ai = (script / "ai" / "AILoader.java").read_text(encoding="utf-8")
	for name in sorted(build_ai.REMOVED):
		if re.search(rf"^\t+{name}\.class", ai, re.M):
			problems[name].append("AILoader")
	interlude = {int(m.group(1)) for f in build_quests.ACIS_QUESTS.glob("Q*.java") if (m := re.match(r"Q(\d+)_", f.name))}
	keep = interlude | kamael.QUESTS | set(range(201, 209))
	quests = (script / "quests" / "QuestLoader.java").read_text(encoding="utf-8")
	for qid in sorted({int(v) for v in re.findall(r"^\t+Q(\d{5})_\w+\.class", quests, re.M)} - keep):
		problems[f"Q{qid}"].append("QuestLoader")
	return problems


def starting_equipment():
	"""Only Kamael classes start with Adventurer's Scrolls of Escape and Kamael Village scrolls."""
	problems = defaultdict(list)
	root = ET.parse(DATA / "stats" / "initialEquipment.xml").getroot()
	for eq in root.findall("equipment"):
		class_id = int(eq.get("classId"))
		for it in eq.findall("item"):
			if class_id not in ds.KAMAEL_CLASS_IDS and int(it.get("id")) in (10650, 12753):
				problems[f"class {class_id}"].append(_item_name(int(it.get("id"))))
	return problems


# ---------------------------------------------------------------- phantom players (bots)

PHANTOMS = DATA / "phantoms"
# Slots PhantomFacts can fill; keep in sync with its value() method.
PHANTOM_SLOTS = {"me", "me.class", "me.level", "me.race", "town", "player", "zone", "zone.low", "zone.any", "grade",
	"weapon", "weapon.other", "armor", "price", "raid", "raid.dead", "epic", "castle", "castle.owner", "dawn", "online", "need",
	"subject", "object", "extra", "ago"}
# Event kinds PhantomNews records; [seen <kind>] and [news <kind>] sections use them.
PHANTOM_KINDS = {"raidkill", "pvp", "death", "levelup", "profession", "login", "logout", "clan", "olympiad", "siegestart", "siegeend", "owner"}
PHANTOM_GRADES = {"NONE", "D", "C", "B", "A", "S"}


def phantom_gear():
	"""Bots wear only Interlude or Kamael items (data/phantoms/gear.txt, built by build_phantom_gear.py)."""
	problems = defaultdict(list)
	for line in (PHANTOMS / "gear.txt").read_text(encoding="utf-8").splitlines():
		parts = line.split("#")[0].split()
		if not parts:
			continue
		if parts[1] not in PHANTOM_GRADES:
			problems[line.strip()].append("unknown grade")
			continue
		ids = [int(parts[4])] if parts[0] == "weapon" else [int(v) for v in parts[3:9]]
		for item_id in ids:
			if item_id == 0:
				continue
			if item_id not in items():
				problems[item_id].append("no item template")
			elif item_id not in allowed_items():
				problems[_item_name(item_id)].append("not an Interlude or Kamael item")
	return problems


def phantom_hunting():
	"""Every level from 20 to 80 has a hunting ground with monsters, and its zone comes from the client list (build_phantom_hunting.py)."""
	import build_phantom_hunting as hunting

	problems = defaultdict(list)
	names = {name for _level, _x, _y, _z, name in hunting.zones()}
	grounds = []
	for line in hunting.OUT.read_text(encoding="utf-8").splitlines():
		line = line.strip()
		if not line or line.startswith("#"):
			continue
		x, y, z, low, high, count, name = line.split(None, 6)
		grounds.append((int(low), int(high), int(count), name))
		if name not in names:
			problems[name].append("not a zone of the client list")
		if int(count) < hunting.MIN_MONSTERS:
			problems[name].append(f"only {count} monsters")
	for level in range(20, 81):
		if not [g for g in grounds if (g[0] - 2) <= level <= (g[1] + 5)]:
			problems[f"level {level}"].append("no hunting ground")
	return problems


def phantom_trade():
	"""The bot shops sell Interlude items a player may actually put in a store (build_phantom_trade.py)."""
	import build_phantom_trade as trade

	allowed = allowed_items()
	sellable = trade.prices()
	problems = defaultdict(list)
	for line in trade.OUT.read_text(encoding="utf-8").splitlines():
		line = line.strip()
		if not line or line.startswith("#"):
			continue
		item_id, price, _name = line.split(None, 2)
		item_id, price = int(item_id), int(price)
		if item_id not in allowed:
			problems[_item_name(item_id)].append("not an Interlude or Kamael item")
		if item_id not in sellable:
			problems[_item_name(item_id)].append("the core refuses it in a private store")
		if price <= 0:
			problems[_item_name(item_id)].append("no price")
	return problems


def phantom_phrases():
	"""Phrases of the bots use only slots the script can fill, and every section has lines."""
	problems = defaultdict(list)
	text = (PHANTOMS / "phrases.txt").read_text(encoding="utf-8")
	sections = defaultdict(int)
	section = None
	for raw in text.splitlines():
		line = raw.strip()
		if not line or line.startswith("#"):
			continue
		if line.startswith("[") and line.endswith("]"):
			section = line[1:-1].strip()
			sections[section] += 0
			continue
		sections[section] += 1
		if ((section == "dialog") or section.startswith("chain")) and ("=>" not in line):
			problems[line].append("a dialog or chain line needs '=>'")
	for slot in set(re.findall(r"\{([^}]*)\}", text)):
		if slot not in PHANTOM_SLOTS:
			problems["{" + slot + "}"].append("unknown slot, see PhantomFacts")
	for name, count in sections.items():
		if count == 0:
			problems[f"[{name}]"].append("section without lines")
		kind = name.split(" ", 1)
		if (kind[0] in ("seen", "news")) and ((len(kind) < 2) or (kind[1] not in PHANTOM_KINDS)):
			problems[f"[{name}]"].append("unknown event kind, see PhantomNews")
		elif kind[0] not in ("general", "trade", "shout", "dialog", "reply", "seen", "news", "chain", "leaving", "returning", "died"):
			problems[f"[{name}]"].append("unknown section")
	for required in ("general", "trade", "shout", "dialog"):
		if required not in sections:
			problems[f"[{required}]"].append("section missing")
	return problems


def phantom_config():
	"""The towns of config/phantoms.properties exist and the hunting zones parse."""
	problems = defaultdict(list)
	config = properties("phantoms.properties")
	regions = {f.stem for f in (DATA / "mapregion").glob("*.xml")}
	for town in config.get("Towns", "").split(","):
		if town.strip() and town.strip() not in regions:
			problems[town.strip()].append("no such region in data/mapregion")
	if int(config.get("Count", "0")) < 1:
		problems["Count"].append("must be at least 1")
	traders = int(config.get("Traders", "0"))
	if traders > int(config.get("Count", "0")):
		problems["Traders"].append("more traders than bots")
	if traders > 0:
		town = config.get("TradeTown", "").strip()
		if town not in regions:
			problems[town or "TradeTown"].append("no such region in data/mapregion")
	for line in (PHANTOMS / "zones.txt").read_text(encoding="utf-8").splitlines():
		parts = line.split("#")[0].split(None, 4)
		if not parts:
			continue
		numbers = parts[:4]
		if (len(parts) != 5) or not all(value.lstrip("-").isdigit() for value in numbers) or not parts[4].strip():
			problems[line.strip()].append("expected: <level> <x> <y> <z> <name>")
	return problems


# ---------------------------------------------------------------- database

def mysql_available():
	try:
		with socket.create_connection(("localhost", 3307), timeout=1):
			return True
	except OSError:
		return False


def mysql(query):
	out = subprocess.run([MYSQL, "--user=root", "--password=root", "--port=3307", "--host=localhost", "--protocol=TCP",
		"--batch", "--skip-column-names", "l2jdb_custom", "-e", query], capture_output=True, text=True, check=True)
	return out.stdout.split()


def database_tables():
	"""l2jdb_custom tables loaded from game/sql match the files."""
	problems = {}
	file_counts = {
		"spawnlist": len(ds.h5_spawns()),
		"teleport": len(sql_ids("teleport.sql", TELEPORT_ROW)),
		"raidboss_spawnlist": len(sql_ids("raidboss_spawnlist.sql", BOSS_ROW)),
	}
	for table, expected in file_counts.items():
		actual = int(mysql(f"SELECT COUNT(*) FROM {table}")[0])
		if actual != expected:
			problems[table] = [f"database {actual} rows, game/sql {expected} rows"]
	stale = {int(v) for v in mysql("SELECT boss_id FROM grandboss_data")} - set(sql_ids("grandboss_data.sql", BOSS_ROW))
	for boss in stale:
		problems[f"grandboss_data {boss}"] = ["boss removed from game/sql is still in the database"]
	return problems


# ---------------------------------------------------------------- geodata

FLOAT_LIMIT = 300


def floating_spawns():
	"""Spawns with fixed coordinates stand on the geodata floor, not more than 300 above it (the server corrects only 100)."""
	import geo

	points, places = [], []
	for m in re.finditer(r'^\("[^"]*", *\d+, *(\d+), *(-?\d+), *(-?\d+), *(-?\d+),', (ds.GAME / "sql" / "spawnlist.sql").read_text(encoding="utf-8"), re.M):
		points.append(tuple(int(v) for v in m.groups()[1:]))
		places.append((int(m.group(1)), "spawnlist.sql"))
	for m in re.finditer(r"^\((\d+),(-?\d+),(-?\d+),(-?\d+),", (ds.GAME / "sql" / "raidboss_spawnlist.sql").read_text(encoding="utf-8"), re.M):
		points.append(tuple(int(v) for v in m.groups()[1:]))
		places.append((int(m.group(1)), "raidboss_spawnlist.sql"))
	for f in ds._xml_files(DATA / "spawnlist"):
		root = ET.parse(f).getroot()
		if root.get("enabled", "true") == "true":
			for npc in root.iter("npc"):
				if npc.get("x") is not None:
					points.append((int(npc.get("x")), int(npc.get("y")), int(npc.get("z"))))
					places.append((int(npc.get("id")), "spawnlist/" + f.name))
	problems = defaultdict(list)
	for (x, y, z), (npc_id, place), floor in zip(points, places, geo.floors(points)):
		template = npcs().get(npc_id)
		if floor is None or z - floor <= FLOAT_LIMIT or (template and template[0].find(".//*[@flying='true']") is not None):
			continue
		problems[f"{npc_id} at {x},{y},{z}"].append(f"{place}, floor {floor}")
	return problems


# name -> function; database checks run only with a database, geodata checks only with geodata and a JDK.
GEO_CHECKS = {"floating_spawns": floating_spawns}
DATAPACK_CHECKS = {f.__name__: f for f in (
	item_references, npc_references, boss_positions, skill_references,
	html_multisell_links, html_buylist_links, html_teleport_links, html_quest_buttons, script_shop_calls, quest_dialog_links, quest_npcs, quest_kill_targets,
	spawn_zones, leader_minions, loader_classes, xml_schemas,
	shops_interlude_items, drops_interlude_items, recipes_interlude_items, manor_interlude_items, teleports_interlude, quest_rewards, spawns_interlude_npcs, interlude_skill_trees, interlude_enchant_routes, interlude_enchant_costs, interlude_residence_skills, interlude_npc_stats, kamael_isle, interlude_config, server_rates,
	interlude_loaders, starting_equipment, phantom_gear, phantom_hunting, phantom_trade, phantom_phrases, phantom_config,
)}
DATABASE_CHECKS = {"database_tables": database_tables}


def unexpected(name, problems):
	"""Problems of a check minus the KNOWN ones."""
	known = KNOWN.get(name, {})
	return {k: v for k, v in problems.items() if str(k) not in known}
