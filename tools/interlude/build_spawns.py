"""Rebuilds world spawns for Interlude + Kamael.

1. game/sql/spawnlist.sql: rows of NPCs absent from aCis are removed, except on
   the Isle of Souls and for Kamael NPCs (see kamael.py).
2. Interlude monsters that are no longer spawned anywhere in H5 are imported
   from the aCis spawnlist (plain "default_maker" makers only; event, day/night
   and scripted makers are skipped):
   - territories -> game/data/zones/npcSpawnTerritories/interlude.xml
   - makers      -> game/data/spawnlist/interlude.xml

Works on the original H5 spawnlist.sql, run it once.

Usage: python tools/interlude/build_spawns.py
"""

import re
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from xml.sax.saxutils import escape, quoteattr

import datasets as ds
import kamael

SPAWNLIST_SQL = ds.GAME / "sql" / "spawnlist.sql"
ZONES_OUT = ds.GAME / "data" / "zones" / "npcSpawnTerritories" / "interlude.xml"
SPAWNS_OUT = ds.GAME / "data" / "spawnlist" / "interlude.xml"
ZONE_PREFIX = "il_"
_REGION = re.compile(r"-- \[(\d+_\d+)\]")
_DURATION = re.compile(r"^(\d+)(sec|min|hour)$")


def seconds(value):
	if not value:
		return 0
	m = _DURATION.match(value)
	if not m:
		raise ValueError(f"Unknown duration {value}")
	return int(m.group(1)) * {"sec": 1, "min": 60, "hour": 3600}[m.group(2)]


def read_sql():
	"""Lines of spawnlist.sql with (region, npc_id or None) per line."""
	region = None
	result = []
	for line in SPAWNLIST_SQL.read_text(encoding="utf-8").replace("\r\n", "\n").split("\n"):
		m = _REGION.match(line)
		if m:
			region = m.group(1)
		row = ds._SPAWN_ROW.match(line)
		result.append((line, region, int(row.group(1)) if row else None))
	return result


def other_h5_spawned_ids():
	"""NPCs spawned by H5 outside spawnlist.sql: XML spawnlists, raid and grand bosses."""
	ids = set()
	for f in (ds.GAME / "data" / "spawnlist").glob("*.xml"):
		if f.name != SPAWNS_OUT.name:
			ids |= {int(v) for v in re.findall(r'<npc id="(\d+)"', f.read_text(encoding="utf-8"))}
	for table in ("raidboss_spawnlist.sql", "grandboss_data.sql"):
		ids |= {int(v) for v in re.findall(r"^\((\d+),", (ds.GAME / "sql" / table).read_text(encoding="utf-8"), re.M)}
	return ids


def remove_rows(lines, keep_ids):
	out = []
	removed = Counter()
	pending_comment = None
	for line, region, npc_id in lines:
		if npc_id is not None and region not in kamael.REGIONS and npc_id not in keep_ids:
			removed[npc_id] += 1
			pending_comment = None
			continue
		if line.startswith("-- ") and not _REGION.match(line):
			# NPC name comment: emit it only if a row of this NPC survives.
			if pending_comment is not None:
				out.append(pending_comment)
			pending_comment = line
			continue
		if pending_comment is not None and npc_id is not None:
			out.append(pending_comment)
		pending_comment = None
		out.append(line)
	if pending_comment is not None:
		out.append(pending_comment)
	return "\n".join(fix_separators(out)), removed


def fix_separators(lines):
	"""Re-terminates rows (',' inside a statement, ';' at its end) and drops empty INSERT statements."""
	is_row = [ds._SPAWN_ROW.match(line) is not None for line in lines]
	result = []
	for i, line in enumerate(lines):
		if line.startswith("INSERT INTO"):
			j = i + 1
			while j < len(lines) and not is_row[j] and not lines[j].startswith("INSERT INTO"):
				j += 1
			if j >= len(lines) or not is_row[j]:
				continue
		if is_row[i]:
			j = i + 1
			while j < len(lines) and not is_row[j] and not lines[j].startswith("INSERT INTO"):
				j += 1
			last = j >= len(lines) or not is_row[j]
			line = line.rstrip().rstrip(",;") + (";" if last else ",")
		result.append(line)
	return result


def main():
	npcs = ds.h5_npcs()
	acis_npcs = ds.acis_ids("npcs")
	lines = read_sql()

	by_region = defaultdict(set)
	for _, region, npc_id in lines:
		if npc_id is not None:
			by_region[region].add(npc_id)
	kamael_npcs = kamael.npc_ids(npcs, by_region)
	# Rows on the Isle of Souls are always kept (remove_rows); elsewhere only NPCs
	# of Kamael scripts, not everything that also stands on the island.
	keep_ids = acis_npcs | (kamael.referenced_ids() & set(npcs))

	text, removed = remove_rows(lines, keep_ids)
	SPAWNLIST_SQL.write_text(text, encoding="utf-8", newline="\n")

	still_spawned = {npc for line, region, npc in lines if npc is not None and (npc in keep_ids or region in kamael.REGIONS)}
	still_spawned |= other_h5_spawned_ids()

	territories = {}
	spawns = []
	skipped = Counter()
	for f in ds._xml_files(ds.ACIS.parent / "xml" / "spawnlist"):
		root = ET.parse(f).getroot()
		file_territories = {t.get("name"): t for t in root.findall("territory")}
		for maker in root.findall("npcmaker"):
			ai = maker.find("ai")
			ai_type = ai.get("type") if ai is not None else None
			if ai_type != "default_maker" or maker.get("event") or maker.get("spawnTime") or maker.get("ban"):
				skipped["maker " + str(ai_type)] += 1
				continue
			entries = []
			for npc in maker.findall("npc"):
				npc_id = int(npc.get("id"))
				template = npcs.get(npc_id)
				if template is None:
					skipped["no H5 template"] += 1
				elif npc_id in still_spawned:
					skipped["already spawned in H5"] += 1
				elif template["type"] != "L2Monster":
					skipped["not a monster: " + template["type"]] += 1
				else:
					entries.append((npc, template))
			if not entries:
				continue
			territory = file_territories.get(maker.get("territory"))
			if territory is None:
				skipped["territory not found"] += 1
				continue
			zone_name = ZONE_PREFIX + territory.get("name")
			territories[zone_name] = territory
			spawns.append((f.stem, maker.get("name"), zone_name, entries))

	write_zones(territories)
	write_spawns(spawns)

	print(f"spawnlist.sql: removed {sum(removed.values())} rows of {len(removed)} NPCs, Kamael NPC whitelist {len(kamael_npcs)}")
	print(f"imported from aCis: {len(spawns)} makers, {len(territories)} territories, {sum(len(e) for *_, e in spawns)} npc entries")
	for key, count in skipped.most_common():
		print(f"  skipped {key}: {count}")


def write_zones(territories):
	out = ['<?xml version="1.0" encoding="UTF-8"?>',
		'<list enabled="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="../../xsd/zones.xsd">',
		"\t<!-- Interlude spawn territories imported from aCis by tools/interlude/build_spawns.py -->"]
	for name, t in sorted(territories.items()):
		out.append(f'\t<zone name={quoteattr(name)} type="NpcSpawnTerritory" shape="NPoly" minZ="{t.get("minZ")}" maxZ="{t.get("maxZ")}">')
		for node in t.findall("node"):
			out.append(f'\t\t<node X="{node.get("x")}" Y="{node.get("y")}" />')
		out.append("\t</zone>")
	out.append("</list>")
	ZONES_OUT.write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")


def write_spawns(spawns):
	out = ['<?xml version="1.0" encoding="UTF-8"?>',
		'<list enabled="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="../xsd/spawnlist.xsd">',
		"\t<!-- Interlude monsters imported from aCis by tools/interlude/build_spawns.py -->"]
	region = None
	for file_region, maker_name, zone_name, entries in spawns:
		if file_region != region:
			region = file_region
			out.append(f"\t<!-- [{region}] -->")
		out.append(f"\t<spawn name={quoteattr(maker_name)} zone={quoteattr(zone_name)}>")
		for npc, template in entries:
			attrs = f'id="{npc.get("id")}"'
			if npc.get("pos"):
				x, y, z, heading = npc.get("pos").split(";")
				attrs += f' x="{x}" y="{y}" z="{z}" heading="{heading}"'
			attrs += f' count="{npc.get("total")}" respawnDelay="{seconds(npc.get("respawn"))}"'
			if npc.get("respawnRand"):
				attrs += f' respawnRandom="{seconds(npc.get("respawnRand"))}"'
			out.append(f"\t\t<npc {attrs} />\t<!-- {escape(template['name'] or '')} -->")
		out.append("\t</spawn>")
	out.append("</list>")
	SPAWNS_OUT.write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
	main()
