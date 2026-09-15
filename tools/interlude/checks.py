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
	"quest_rewards": {
		"14362 White Cloth": "quest 234 was reworked in H5 around the white cloth step; porting the aCis version is a separate task",
		"14854 Recipe - Spiteful Soul Energy": "quest 503 got the Spiteful Soul step in H5; porting the aCis version is a separate task",
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
	# Dialogs the scripts open by name (event case labels are not files). Tutorial pages live in data/html.
	literal = re.compile(r'"(\w[\w-]*-[\w-]*\.html?)"')
	for quest in loaded:
		for java in (quests / quest).glob("*.java"):
			for line in java.read_text(encoding="utf-8", errors="replace").splitlines():
				if line.lstrip().startswith("case ") or "showTutorialHTML" in line:
					continue
				for dialog in literal.findall(line):
					if not (quests / quest / dialog).exists():
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
		elif kind[0] not in ("general", "trade", "shout", "dialog", "reply", "seen", "news", "chain"):
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
	for line in (PHANTOMS / "zones.txt").read_text(encoding="utf-8").splitlines():
		parts = line.split("#")[0].split(None, 2)
		if not parts:
			continue
		if (len(parts) != 3) or not parts[0].isdigit() or not parts[1].isdigit() or (int(parts[0]) > int(parts[1])):
			problems[line.strip()].append("expected: <min level> <max level> <name>")
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
	html_multisell_links, html_buylist_links, html_teleport_links, script_shop_calls, quest_dialog_links, quest_npcs, loader_classes, xml_schemas,
	shops_interlude_items, drops_interlude_items, quest_rewards, spawns_interlude_npcs, interlude_skill_trees, interlude_enchant_routes, interlude_config,
	interlude_loaders, starting_equipment, phantom_gear, phantom_phrases, phantom_config,
)}
DATABASE_CHECKS = {"database_tables": database_tables}


def unexpected(name, problems):
	"""Problems of a check minus the KNOWN ones."""
	known = KNOWN.get(name, {})
	return {k: v for k, v in problems.items() if str(k) not in known}
