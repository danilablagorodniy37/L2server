"""Teleports as in Interlude: currency of the noble teleports, landing points and prices.

build_teleports.py removes the destinations of later places; this fixes the ones that stay.
- Noble teleports paid with the Olympiad Token (13722, a later item) cost a Noblesse Gate Pass
  (6651) as in Interlude, and the dialogs that name the token name the pass.
- H5 moved the landing points of some Interlude places (mostly the noble hunting zones):
  MOVED teleports get the aCis point back, and so does every other row that landed on the
  same H5 point. Town squares that moved a little are left alone.
- PRICES take the aCis adena price where H5 raised or lowered it. Ketra and Varka teleports
  keep theirs: aCis prices them by alliance level.

The lists were reviewed by hand: matching by name alone confuses places (Dragon Valley and
Enchanted Valley, Ivory Tower and Tower of Insolence). All aCis points stand on the H5 geodata.
Safe to run again. After a run reload the table: mysql l2jdb_custom < game/sql/teleport.sql

Usage: python tools/interlude/fix_teleports.py [--dry-run]
"""

import re
import sys
import xml.etree.ElementTree as ET

import datasets as ds

TELEPORT_SQL = ds.GAME / "sql" / "teleport.sql"
HTML = ds.GAME / "data" / "html"
OLYMPIAD_TOKEN = 13722
GATE_PASS = 6651
# teleport id -> (gatekeeper npc, aCis description) of the Interlude landing point
MOVED = {
	9052: (31320, "Cursed Village"),
	9066: (31275, "Shrine of Loyalty"),
	9080: (30059, "Execution Ground"),
	9081: (30059, "Floran Agricultural Area"),
	9101: (30146, "The Center of the Neutral Zone"),
	9102: (30134, "The Center of the Dark Forest"),
	9105: (30576, "Immortal Plateau, Northern Region"),
	9120: (30899, "Field of Silence (Western Section)"),
	1097: (31275, "Monastery of Silence"),
	1103: (31320, "Wild Beast Pastures"),
}
# teleport id -> (gatekeeper npc, aCis description) whose adena price is taken
PRICES = {
	63: (30878, "The Town of Giran"),
	97: (30177, "Plains of the Lizardmen"),
	107: (30878, "Heine"),
	419: (30540, "Mithril Mines"),
	1103: (31320, "Wild Beast Pastures"),
	1116: (31964, "Den of Evil"),
	1119: (31964, "Crypts of Disgrace"),
	1120: (31964, "Pavel Ruins"),
}
_ROW = re.compile(r"^\('((?:[^'\\]|\\.)*)',(\d+),(-?\d+),(-?\d+),(-?\d+),(\d+),(\d+),(\d+)\)")


def acis_point(npc_id, desc):
	"""(x, y, z, adena price) of an aCis teleport; the standard or adena entry when there are several."""
	for telepos in ET.parse(ds.ACIS / "teleports.xml").getroot().findall("telPosList"):
		if int(telepos.get("npcId")) != npc_id:
			continue
		found = [loc for loc in telepos.findall("loc") if loc.get("desc") == desc]
		found.sort(key=lambda loc: loc.get("priceId") != "57")
		if found:
			loc = found[0]
			return int(loc.get("x")), int(loc.get("y")), int(loc.get("z")), int(loc.get("priceCount"))
	raise SystemExit(f"aCis has no teleport {desc!r} at npc {npc_id}")


def fixed_rows(text):
	"""(teleport.sql text as in Interlude, number of rows changed)."""
	rows = {int(m.group(2)): m for line in text.split("\n") if (m := _ROW.match(line))}
	new_points = {}
	for tid, (npc, desc) in MOVED.items():
		old = rows.get(tid)
		if old is None:
			continue
		x, y, z, _ = acis_point(npc, desc)
		new_points[(old.group(3), old.group(4), old.group(5))] = (x, y, z)
		# a second run finds the rows already moved
		new_points[(str(x), str(y), str(z))] = (x, y, z)
	prices = {tid: acis_point(npc, desc)[3] for tid, (npc, desc) in PRICES.items()}

	changed = 0
	out = []
	for line in text.split("\n"):
		m = _ROW.match(line)
		if not m:
			out.append(line)
			continue
		desc, tid = m.group(1), int(m.group(2))
		x, y, z = new_points.get((m.group(3), m.group(4), m.group(5)), (m.group(3), m.group(4), m.group(5)))
		price, item = int(m.group(6)), int(m.group(8))
		if item == OLYMPIAD_TOKEN:
			item = GATE_PASS
		desc = desc.replace("Olympiad Token", "Noblesse Gate Pass")
		if tid in prices:
			price = prices[tid]
		row = f"('{desc}',{tid},{x},{y},{z},{price},{m.group(7)},{item})"
		if row != m.group(0):
			changed += 1
		out.append(row + line[m.end():])
	return "\n".join(out), changed


def renamed_currency(text):
	return text.replace("Olympiad Tokens", "Noblesse Gate Passes").replace("Olympiad Token", "Noblesse Gate Pass")


def main():
	dry_run = "--dry-run" in sys.argv
	text, changed = fixed_rows(TELEPORT_SQL.read_text(encoding="utf-8"))
	print(f"teleport.sql: {changed} rows {'would change' if dry_run else 'changed'}")
	if not dry_run and changed:
		TELEPORT_SQL.write_text(text, encoding="utf-8", newline="\n")

	files = 0
	for path in sorted(HTML.rglob("*.htm")):
		raw = path.read_text(encoding="utf-8", errors="surrogateescape")
		new = renamed_currency(raw)
		if new != raw:
			files += 1
			if not dry_run:
				path.write_text(new, encoding="utf-8", errors="surrogateescape", newline="")
	print(f"dialogs naming the Olympiad Token: {files} {'would change' if dry_run else 'changed'}")


if __name__ == "__main__":
	main()
