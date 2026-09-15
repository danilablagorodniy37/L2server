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
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
TELEPORT_ROW = r"^\('(?:[^'\\]|\\.)*', *(\d+),"
BOSS_ROW = r"^\((\d+),"

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
	return ds.acis_ids("items")


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


def _html_links(kinds):
	link = re.compile(r"bypass -h npc_%objectId%_(multisell|exc_multisell|Buy|goto) (\d+)")
	for f in ds.html_files():
		rel = f.relative_to(ds.GAME).as_posix()
		for kind, value in link.findall(f.read_text(encoding="utf-8", errors="replace")):
			if kind in kinds:
				yield int(value), rel


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


# name -> function; database checks run only with a database.
DATAPACK_CHECKS = {f.__name__: f for f in (
	item_references, npc_references, skill_references,
	html_multisell_links, html_buylist_links, html_teleport_links, script_shop_calls, loader_classes, xml_schemas,
	shops_interlude_items, drops_interlude_items, spawns_interlude_npcs, interlude_skill_trees, interlude_config,
	interlude_loaders, starting_equipment,
)}
DATABASE_CHECKS = {"database_tables": database_tables}


def unexpected(name, problems):
	"""Problems of a check minus the KNOWN ones."""
	known = KNOWN.get(name, {})
	return {k: v for k, v in problems.items() if str(k) not in known}
