"""Puts spawns that hang in the air back on the ground.

The server lowers a spawn to the geodata floor only when it is at most 100
units above it, so NPCs placed higher stay in the air. For rows of
spawnlist.sql and raidboss_spawnlist.sql more than FLOAT_LIMIT above the
nearest geodata floor:
- raid bosses that aCis spawns in a territory move to the centre of that
  territory, on its floor (their H5 coordinates are rough placeholders);
- other rows keep x/y and get the floor height.
Flying NPC templates are left alone. Safe to run again.

Usage: python tools/interlude/fix_floating_spawns.py [--dry-run]
"""

import re
import sys
import xml.etree.ElementTree as ET

import checks
import datasets as ds
import geo

FLOAT_LIMIT = 300
SPAWN_ROW = re.compile(r'^(\("[^"]*", *\d+, *(\d+), *(-?\d+), *(-?\d+), *)(-?\d+)(,.*)$')
BOSS_ROW = re.compile(r"^(\((\d+),)(-?\d+),(-?\d+),(-?\d+)(,.*)$")


def flying(npc_id):
	template = checks.npcs().get(npc_id)
	return template is not None and template[0].find(".//*[@flying='true']") is not None


def territory_centres():
	"""npc id -> (x, y, minZ, maxZ) of the aCis territory its maker spawns it in."""
	result = {}
	for f in ds._xml_files(ds.ACIS.parent / "xml" / "spawnlist"):
		root = ET.parse(f).getroot()
		territories = {t.get("name"): t for t in root.findall("territory")}
		for maker in root.findall("npcmaker"):
			territory = territories.get(maker.get("territory"))
			if territory is None:
				continue
			nodes = [(int(n.get("x")), int(n.get("y"))) for n in territory.findall("node")]
			area = cx = cy = 0
			for (x1, y1), (x2, y2) in zip(nodes, nodes[1:] + nodes[:1]):
				cross = x1 * y2 - x2 * y1
				area += cross
				cx += (x1 + x2) * cross
				cy += (y1 + y2) * cross
			if area == 0:
				continue
			for npc in maker.findall("npc"):
				if not npc.get("pos"):
					result[int(npc.get("id"))] = (int(cx / (3 * area)), int(cy / (3 * area)), int(territory.get("minZ")), int(territory.get("maxZ")))
	return result


def fix_spawnlist(dry_run):
	path = ds.GAME / "sql" / "spawnlist.sql"
	lines = path.read_text(encoding="utf-8").split("\n")
	rows = [(i, m) for i, line in enumerate(lines) if (m := SPAWN_ROW.match(line))]
	points = [(int(m.group(3)), int(m.group(4)), int(m.group(5))) for _, m in rows]
	changed = 0
	for (i, m), (x, y, z), floor in zip(rows, points, geo.floors(points)):
		if floor is not None and z - floor > FLOAT_LIMIT and not flying(int(m.group(2))):
			lines[i] = f"{m.group(1)}{floor}{m.group(6)}"
			changed += 1
	if changed and not dry_run:
		path.write_text("\n".join(lines), encoding="utf-8", newline="\n")
	print(f"spawnlist.sql: {changed} floating rows lowered to the floor")


def fix_bosses(dry_run):
	path = ds.GAME / "sql" / "raidboss_spawnlist.sql"
	lines = path.read_text(encoding="utf-8").split("\n")
	rows = [(i, m) for i, line in enumerate(lines) if (m := BOSS_ROW.match(line))]
	points = [(int(m.group(3)), int(m.group(4)), int(m.group(5))) for _, m in rows]
	floating = [(i, m) for (i, m), (x, y, z), floor in zip(rows, points, geo.floors(points)) if floor is not None and z - floor > FLOAT_LIMIT]
	centres = territory_centres()
	for i, m in floating:
		boss = int(m.group(2))
		if boss in centres:
			x, y, min_z, max_z = centres[boss]
			z = geo.floors([(x, y, max_z)])[0]
			if z is None or not (min_z - 100 <= z <= max_z + 100):
				print(f"  boss {boss}: no floor inside its aCis territory, skipped")
				continue
			how = "aCis territory centre"
		else:
			x, y = int(m.group(3)), int(m.group(4))
			z = geo.floors([(x, y, int(m.group(5)))])[0]
			how = "floor"
		lines[i] = f"{m.group(1)}{x},{y},{z}{m.group(6)}"
		print(f"  boss {boss} {checks.npcs()[boss][0].get('name')}: ({m.group(3)},{m.group(4)},{m.group(5)}) -> ({x},{y},{z}) {how}")
	if floating and not dry_run:
		path.write_text("\n".join(lines), encoding="utf-8", newline="\n")
	print(f"raidboss_spawnlist.sql: {len(floating)} floating bosses")


def main():
	dry_run = "--dry-run" in sys.argv
	fix_spawnlist(dry_run)
	fix_bosses(dry_run)


if __name__ == "__main__":
	main()
