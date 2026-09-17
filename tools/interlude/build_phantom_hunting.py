"""Hunting grounds for the bots: a zone of the client map plus the monsters this world puts there.

data/phantoms/zones.txt holds the zones of the client (name, recommended level, map point, read by
tools/client/hunting_zones.py). A bot also needs to know that the place is worth going to, so this
counts the monsters standing around each zone point in our own spawns and writes the zones that
hold enough of them to data/phantoms/hunting.txt:

    <x> <y> <z> <min level> <max level> <monsters> <zone name>

Zones of the later chronicles fall out by themselves: nothing of ours spawns there any more. The
height is taken from the monster nearest to the point, so a bot does not land under the floor.

Usage: python tools/interlude/build_phantom_hunting.py [--dry-run]
"""

import math
import sys
import xml.etree.ElementTree as ET

import datasets as ds

ZONES = ds.GAME / "data" / "phantoms" / "zones.txt"
OUT = ds.GAME / "data" / "phantoms" / "hunting.txt"
RADIUS = 2500  # monsters this close to the zone point count as its hunting ground
MIN_MONSTERS = 10
MIN_LEVEL, MAX_LEVEL = 18, 80


def zones():
	"""[(level, x, y, z, name)] of the client zones."""
	out = []
	for line in ZONES.read_text(encoding="utf-8").splitlines():
		line = line.strip()
		if line and not line.startswith("#"):
			level, x, y, z, name = line.split(None, 4)
			out.append((int(level), int(x), int(y), int(z), name.strip()))
	return out


def monsters():
	"""[(x, y, z, level, count)] of every monster this world spawns."""
	npcs = ds.h5_npcs()

	def monster(npc_id):
		template = npcs.get(npc_id)
		return template if template and template["type"] == "L2Monster" and MIN_LEVEL <= template["level"] <= MAX_LEVEL else None

	out = []
	for npc_id, x, y, z in ds.h5_spawns():
		template = monster(npc_id)
		if template:
			out.append((x, y, z, template["level"], 1))

	territories = {}
	for f in ds._xml_files(ds.GAME / "data" / "zones" / "npcSpawnTerritories"):
		for zone in ET.parse(f).getroot().findall("zone"):
			nodes = [(int(n.get("X")), int(n.get("Y"))) for n in zone.findall("node")]
			if nodes:
				middle_z = (int(zone.get("minZ")) + int(zone.get("maxZ"))) // 2
				territories[zone.get("name")] = (sum(n[0] for n in nodes) // len(nodes), sum(n[1] for n in nodes) // len(nodes), middle_z)
	for f in ds._xml_files(ds.GAME / "data" / "spawnlist"):
		root = ET.parse(f).getroot()
		if root.get("enabled", "true") != "true":
			continue
		for spawn in root.findall("spawn"):
			for npc in spawn.findall("npc"):
				template = monster(int(npc.get("id")))
				if not template:
					continue
				count = int(npc.get("count", 1))
				if npc.get("x"):
					out.append((int(npc.get("x")), int(npc.get("y")), int(npc.get("z")), template["level"], count))
				elif spawn.get("zone") in territories:
					x, y, z = territories[spawn.get("zone")]
					out.append((x, y, z, template["level"], count))
	return out


def grounds(zone_list, monster_list):
	"""[(x, y, z, min level, max level, monsters, name)] of the zones worth hunting in."""
	out = []
	for level, x, y, z, name in zone_list:
		near = [m for m in monster_list if math.hypot(m[0] - x, m[1] - y) <= RADIUS]
		total = sum(m[4] for m in near)
		if total < MIN_MONSTERS:
			continue
		# the height of the closest monster: the zone point of the client is only a map marker
		closest = min(near, key=lambda m: math.hypot(m[0] - x, m[1] - y))
		out.append((x, y, closest[2], min(m[3] for m in near), max(m[3] for m in near), total, name))
	out.sort(key=lambda ground: (ground[3], ground[6]))
	return out


def main():
	dry_run = "--dry-run" in sys.argv
	found = grounds(zones(), monsters())
	covered = {level for ground in found for level in range(ground[3], ground[4] + 1)}
	missing = [level for level in range(20, MAX_LEVEL + 1) if level not in covered]
	print(f"{len(found)} hunting grounds; levels 20-{MAX_LEVEL} without one: {missing or 'none'}")

	lines = ["# Where the bots hunt: a zone of the client map with the monsters this world spawns around it.",
		"# Built by tools/interlude/build_phantom_hunting.py, do not edit by hand.",
		"# <x> <y> <z> <min level> <max level> <monsters> <zone name>"]
	lines += [f"{x} {y} {z} {low} {high} {count} {name}" for x, y, z, low, high, count, name in found]
	if not dry_run:
		OUT.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
	main()
