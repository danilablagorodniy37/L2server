"""Gives the leaders of the world their Interlude minions.

aCis spawns a leader together with its <privates>; H5 keeps the same mechanic in the NPC
template (<minions name="Privates"> read by the datapack script MinionSpawnManager), but
for many Interlude leaders the block is gone, so the minions appear nowhere in the world.

This copies the aCis privates into the H5 templates of the leaders that our world spawns
and registers those leaders in MinionSpawnManager, which spawns the block on leader spawn.
Leaders that already carry minions are left alone, as are minions already in the block.

Usage: python tools/interlude/build_minions.py [--dry-run]
"""

import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict

import checks
import datasets as ds

NPCS = ds.GAME / "data" / "stats" / "npcs"
MANAGER = ds.GAME / "script" / "com" / "l2jserver" / "datapack" / "ai" / "group_template" / "MinionSpawnManager.java"
# Minions spawn with their leader in aCis, so no respawn delay of their own.
RESPAWN_TIME = 0
# Bosses keep their own AI script, which spawns their minions where and when it wants.
BOSS_TYPES = {"L2GrandBoss", "L2RaidBoss", "L2FlyRaidBoss"}


def acis_privates():
	"""leader id -> {minion id: (count, weight)}, the largest group aCis gives the leader."""
	result = defaultdict(dict)
	for f in ds._xml_files(ds.ACIS.parent / "xml" / "spawnlist"):
		for npc in ET.parse(f).getroot().iter("npc"):
			privates = npc.find("privates")
			if privates is None:
				continue
			group = Counter()
			weights = {}
			for private in privates.findall("private"):
				minion_id = int(private.get("id"))
				group[minion_id] += 1
				weights[minion_id] = int(private.get("weight") or 1)
			leader = int(npc.get("id"))
			for minion_id, count in group.items():
				known = result[leader].get(minion_id, (0, 1))
				result[leader][minion_id] = (max(known[0], count), weights[minion_id])
	return result


def template_minions():
	"""leader id -> minion ids already in the H5 template."""
	result = {}
	for f in ds._xml_files(NPCS):
		for npc in ET.parse(f).getroot().findall("npc"):
			ids = {int(entry.get("id")) for block in npc.findall("parameters/minions") for entry in block.findall("npc")}
			if ids:
				result[int(npc.get("id"))] = ids
	return result


def npc_block(text, npc_id):
	"""(start, end) of the <npc id="..."> ... </npc> element in the file text."""
	start = re.search(rf'^\t<npc id="{npc_id}"[ >]', text, re.M)
	if not start:
		raise SystemExit(f"npc {npc_id}: template not found")
	end = text.index("</npc>", start.start())
	return start.start(), end


def with_minions(text, npc_id, minions, names):
	"""The file text with a Privates block added to the template of npc_id."""
	start, end = npc_block(text, npc_id)
	block = ['\t\t\t<minions name="Privates">']
	for minion_id, (count, weight) in sorted(minions.items()):
		block.append(f'\t\t\t\t<npc id="{minion_id}" count="{count}" respawnTime="{RESPAWN_TIME}" weightPoint="{weight}" />'
			f'\t<!-- {names.get(minion_id, "")} -->')
	block.append("\t\t\t</minions>")
	added = "\n".join(block)

	head = text[start:end]
	params = re.search(r"^\t\t<parameters>[^\n]*\n", head, re.M)
	if params:
		at = start + params.end()
		return text[:at] + added + "\n" + text[at:]
	# No parameters element yet: open one right after the <npc ...> line.
	first_line = text.index("\n", start) + 1
	return text[:first_line] + "\t\t<parameters>\n" + added + "\n\t\t</parameters>\n" + text[first_line:]


def register(leaders):
	"""Adds the leaders to the NPC set of MinionSpawnManager."""
	text = MANAGER.read_text(encoding="utf-8").replace("\r\n", "\n")
	known = {int(v) for v in re.findall(r"NPC\.add\((\d+)\);", text)}
	new = sorted(set(leaders) - known)
	if not new:
		return 0
	names = {npc_id: element.get("name") for npc_id, (element, _) in checks.npcs().items()}
	lines = "".join(f"\t\tNPC.add({npc_id}); // {names.get(npc_id, '')}\n" for npc_id in new)
	marker = "\t}\n\t\n\tprivate static final NpcStringId[] ON_ATTACK_MSG"
	if marker not in text:
		raise SystemExit("MinionSpawnManager: end of the static block not found")
	text = text.replace(marker, f"\t\t// Interlude leaders whose privates were missing (tools/interlude/build_minions.py)\n{lines}{marker}", 1)
	MANAGER.write_text(text, encoding="utf-8", newline="\n")
	return len(new)


def main():
	dry_run = "--dry-run" in sys.argv
	privates = acis_privates()
	in_template = template_minions()
	spawned = checks.world_spawned()
	by_script = checks.script_spawned()
	names = {npc_id: element.get("name") for npc_id, (element, _) in checks.npcs().items()}
	types = {npc_id: element.get("type") for npc_id, (element, _) in checks.npcs().items()}
	templates = {npc_id: file for npc_id, (_, file) in checks.npcs().items()}

	todo = defaultdict(dict)
	for leader, minions in privates.items():
		if leader not in spawned or leader not in templates or types[leader] in BOSS_TYPES:
			continue
		for minion_id, value in minions.items():
			# A minion some script already spawns would end up in the world twice.
			if minion_id in templates and minion_id not in in_template.get(leader, ()) and minion_id not in by_script:
				todo[leader][minion_id] = value

	by_file = defaultdict(list)
	for leader in sorted(todo):
		by_file[templates[leader]].append(leader)

	total = sum(len(v) for v in todo.values())
	print(f"{len(todo)} leaders get {total} minion entries ({len({m for v in todo.values() for m in v})} distinct monsters)")
	for leader in sorted(todo):
		entries = " ".join(f"{m}x{c}" for m, (c, _) in sorted(todo[leader].items()))
		print(f"  {leader} {names.get(leader, '')}: {entries}")
	if dry_run:
		return

	for file, leaders in sorted(by_file.items()):
		path = NPCS / file
		text = path.read_text(encoding="utf-8").replace("\r\n", "\n")
		for leader in sorted(leaders, reverse=True):
			text = with_minions(text, leader, todo[leader], names)
		path.write_text(text, encoding="utf-8", newline="\n")
	print(f"written: {len(by_file)} template files, MinionSpawnManager got {register(todo)} new leaders")


if __name__ == "__main__":
	main()
