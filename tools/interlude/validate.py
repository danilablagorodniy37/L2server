"""Consistency checks of the Interlude + Kamael datapack. Changes nothing.

1. Broken references: items, NPCs, skills, buylists, multisells and teleports
   used by shops, drops, recipes, skill trees, spawns and NPC HTML must exist.
2. Interlude rules: shops, multisells, drops and enabled spawns contain only
   aCis (Interlude) or Kamael content.
3. Database: tables loaded from game/sql match the files (optional, needs mysql).

Usage: python tools/interlude/validate.py [--db] [--all]
  --db   also compare the l2jdb_custom tables with game/sql
  --all  list every problem, not only the first ones
Exit code 1 when any check fails.
"""

import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

import build_ai
import datasets as ds
import kamael

DATA = ds.GAME / "data"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
_TELEPORT_ROW = r"^\('(?:[^'\\]|\\.)*', *(\d+),"
SHOWN = 10**9 if "--all" in sys.argv else 8

_failed = []


def report(title, problems):
	"""problems: dict key -> list of places."""
	if not problems:
		print(f"[ OK ] {title}")
		return
	_failed.append(title)
	print(f"[FAIL] {title}: {len(problems)}")
	for key, places in sorted(problems.items(), key=lambda kv: str(kv[0]))[:SHOWN]:
		where = sorted(set(places))
		shown = where if SHOWN > 8 else where[:4]
		print(f"         {key}: {', '.join(shown)}{' ...' if len(where) > len(shown) else ''}")
	if len(problems) > SHOWN:
		print(f"         ... and {len(problems) - SHOWN} more")


def skill_levels():
	"""id -> number of levels (without enchant routes)."""
	levels = {}
	for f in ds._xml_files(DATA / "stats" / "skills"):
		for s in ET.parse(f).getroot().findall("skill"):
			levels[int(s.get("id"))] = int(s.get("levels") or 1)
	return levels


def npc_templates():
	"""id -> (element, file name)"""
	result = {}
	for f in ds._xml_files(DATA / "stats" / "npcs"):
		for n in ET.parse(f).getroot().findall("npc"):
			result[int(n.get("id"))] = (n, f.name)
	return result


def enabled_xml_spawns():
	"""(npc_id, file name) of <list enabled="true"> spawnlists."""
	for f in ds._xml_files(DATA / "spawnlist"):
		root = ET.parse(f).getroot()
		if root.get("enabled", "true") == "true":
			for npc in root.iter("npc"):
				yield int(npc.get("id")), f.name


def sql_ids(file, pattern):
	return [int(v) for v in re.findall(pattern, (ds.GAME / "sql" / file).read_text(encoding="utf-8"), re.M)]


def check_references(items, npcs, skills):
	missing_items = defaultdict(list)
	missing_npcs = defaultdict(list)
	missing_skills = defaultdict(list)

	for f in ds._xml_files(DATA / "buylists"):
		root = ET.parse(f).getroot()
		for it in root.iter("item"):
			if int(it.get("id")) not in items:
				missing_items[int(it.get("id"))].append("buylist " + f.stem)
		for npc in root.iter("npc"):
			if int(npc.text) not in npcs:
				missing_npcs[int(npc.text)].append("buylist " + f.stem)

	for f in ds._xml_files(DATA / "multisell"):
		for e in ET.parse(f).getroot().iter():
			if e.tag in ("ingredient", "production") and int(e.get("id")) > 0 and int(e.get("id")) not in items:
				missing_items[int(e.get("id"))].append("multisell " + f.stem)

	for npc_id, (n, fname) in npcs.items():
		for it in n.iter("item"):
			if int(it.get("id")) not in items:
				missing_items[int(it.get("id"))].append(f"npc {npc_id}")
		for sk in n.iter("skill"):
			if sk.get("id") is None:
				continue
			sid, lvl = int(sk.get("id")), int(sk.get("level") or 1)
			if skills.get(sid, 0) < lvl:
				missing_skills[f"{sid} lvl {lvl}"].append(f"npc {npc_id}")

	for r in ET.parse(DATA / "recipes.xml").getroot().findall("item"):
		ids = [int(r.get("recipeId"))] + [int(e.get("id")) for e in r.iter() if e.tag in ("ingredient", "production", "productionRare")]
		for i in ids:
			if i not in items:
				missing_items[i].append("recipe " + r.get("name"))

	for f in ds._xml_files(DATA / "skillTrees"):
		for tree in ET.parse(f).getroot().findall("skillTree"):
			label = f"{tree.get('type')} {tree.get('classId') or ''}".strip()
			for sk in tree.findall("skill"):
				sid, lvl = int(sk.get("skillId")), int(sk.get("skillLvl"))
				if skills.get(sid, 0) < lvl:
					missing_skills[f"{sid} lvl {lvl}"].append(label)
				for it in sk.findall("item"):
					if int(it.get("id")) not in items:
						missing_items[int(it.get("id"))].append(label)

	for it in ET.parse(DATA / "stats" / "initialEquipment.xml").getroot().iter("item"):
		if int(it.get("id")) not in items:
			missing_items[int(it.get("id"))].append("initialEquipment")

	for npc_id in ds.h5_spawns():
		if npc_id[0] not in npcs:
			missing_npcs[npc_id[0]].append("spawnlist.sql")
	for npc_id, fname in enabled_xml_spawns():
		if npc_id not in npcs:
			missing_npcs[npc_id].append("spawnlist/" + fname)
	for table in ("raidboss_spawnlist.sql", "grandboss_data.sql"):
		for npc_id in sql_ids(table, r"^\((\d+),"):
			if npc_id not in npcs:
				missing_npcs[npc_id].append(table)

	report("items referenced by shops, multisells, drops, recipes, skill trees, starting gear exist", missing_items)
	report("NPCs referenced by spawns, bosses, buylists exist", missing_npcs)
	report("skills referenced by skill trees and NPCs exist with that level", missing_skills)


def check_html_links():
	multisells = ds.multisell_ids()
	buylists = ds.buylist_ids()
	teleports = set(sql_ids("teleport.sql", _TELEPORT_ROW))
	broken = {"multisell": defaultdict(list), "buylist": defaultdict(list), "teleport": defaultdict(list)}
	link = re.compile(r"bypass -h npc_%objectId%_(multisell|exc_multisell|Buy|goto) (\d+)")
	for f in ds.html_files():
		rel = f.relative_to(ds.GAME).as_posix()
		for kind, value in link.findall(f.read_text(encoding="utf-8", errors="replace")):
			value = int(value)
			if kind in ("multisell", "exc_multisell") and value not in multisells:
				broken["multisell"][value].append(rel)
			elif kind == "Buy" and value not in buylists:
				broken["buylist"][value].append(rel)
			elif kind == "goto" and value not in teleports:
				broken["teleport"][value].append(rel)
	if not teleports:
		print("[WARN] teleport.sql ids were not parsed, teleport links not checked")
	report("NPC HTML links to multisells that exist", broken["multisell"])
	report("NPC HTML links to buylists that exist", broken["buylist"])
	report("NPC HTML links to teleports that exist", broken["teleport"])

	java = defaultdict(list)
	call = re.compile(r"(separateAndSend|showBuyWindow)\((?:player, *)?(\d+) *[,)]")
	disabled = build_ai.REMOVED | {"hellbound"}
	for f in (ds.GAME / "script").rglob("*.java"):
		if disabled & set(f.parts):
			continue
		for kind, value in call.findall(f.read_text(encoding="utf-8", errors="replace")):
			if int(value) not in (multisells if kind == "separateAndSend" else buylists):
				java[f"{kind} {value}"].append(f.relative_to(ds.GAME / "script").as_posix())
	report("scripts open multisells and buylists that exist (loaded scripts only)", java)


def check_interlude(items, npcs):
	allowed_items = ds.acis_ids("items") | kamael.item_ids(items)
	acis_npcs = ds.acis_ids("npcs")
	kamael_npcs = kamael.referenced_ids() & set(npcs)

	def name(i):
		return f"{i} {items[i]['name']}" if i in items else str(i)

	late_shop = defaultdict(list)
	for f in ds._xml_files(DATA / "buylists"):
		for it in ET.parse(f).getroot().iter("item"):
			if int(it.get("id")) not in allowed_items:
				late_shop[name(int(it.get("id")))].append("buylist " + f.stem)
	for f in ds._xml_files(DATA / "multisell"):
		for e in ET.parse(f).getroot().iter():
			if e.tag in ("ingredient", "production") and int(e.get("id")) > 0 and int(e.get("id")) not in allowed_items:
				late_shop[name(int(e.get("id")))].append("multisell " + f.stem)
	report("shops and multisells contain only Interlude or Kamael items", late_shop)

	late_drop = defaultdict(list)
	for npc_id, (n, fname) in npcs.items():
		if npc_id in acis_npcs:
			for it in n.iter("item"):
				if int(it.get("id")) not in allowed_items:
					late_drop[name(int(it.get("id")))].append(f"npc {npc_id}")
	report("drops of Interlude NPCs contain only Interlude or Kamael items", late_drop)

	late_spawn = defaultdict(list)
	for npc_id, fname in enabled_xml_spawns():
		if npc_id not in acis_npcs and npc_id not in kamael_npcs:
			late_spawn[f"{npc_id} {npcs[npc_id][0].get('name') if npc_id in npcs else ''}"].append("spawnlist/" + fname)
	report("enabled XML spawnlists spawn only Interlude or Kamael NPCs", late_spawn)


def mysql(query):
	out = subprocess.run([MYSQL, "--user=root", "--password=root", "--port=3307", "--host=localhost", "--protocol=TCP",
		"--batch", "--skip-column-names", "l2jdb_custom", "-e", query], capture_output=True, text=True, check=True)
	return out.stdout.split()


def check_database():
	problems = {}
	file_counts = {
		"spawnlist": len(ds.h5_spawns()),
		"teleport": len(sql_ids("teleport.sql", _TELEPORT_ROW)),
		"raidboss_spawnlist": len(sql_ids("raidboss_spawnlist.sql", r"^\((\d+),")),
	}
	for table, expected in file_counts.items():
		actual = int(mysql(f"SELECT COUNT(*) FROM {table}")[0])
		if actual != expected:
			problems[table] = [f"database {actual} rows, game/sql {expected} rows"]
	report("database tables match game/sql files", problems)

	bosses = set(sql_ids("grandboss_data.sql", r"^\((\d+),"))
	stale = {int(v) for v in mysql("SELECT boss_id FROM grandboss_data")} - bosses
	report("grandboss_data has no bosses removed from game/sql", {b: ["grandboss_data"] for b in stale})


def main():
	items = ds.h5_items()
	npcs = npc_templates()
	skills = skill_levels()
	print(f"datapack: {len(items)} items, {len(npcs)} NPCs, {len(skills)} skills\n")
	check_references(items, npcs, skills)
	check_html_links()
	check_interlude(items, npcs)
	if "--db" in sys.argv:
		check_database()
	print(f"\n{len(_failed)} check(s) failed" if _failed else "\nall checks passed")
	sys.exit(1 if _failed else 0)


if __name__ == "__main__":
	main()
