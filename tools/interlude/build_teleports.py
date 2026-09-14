"""Removes teleport destinations that did not exist in Interlude.

A destination from game/sql/teleport.sql is removed when no aCis (Interlude)
teleport lands within MAX_DISTANCE of it and its description names a
post-Interlude place (LATE_PLACES) or it is paid with Olympiad Tokens.
Isle of Souls destinations and fortress teleports are kept. Other far
destinations are only reported: H5 moved landing points of many Interlude
teleports.
Removed ids are also taken out of NPC HTML: lines that only hold a link to a
removed destination are deleted.

Usage: python tools/interlude/build_teleports.py [--dry-run]
"""

import math
import re
import sys

import datasets as ds
import kamael

TELEPORT_SQL = ds.GAME / "sql" / "teleport.sql"
HTML_ROOTS = [ds.GAME / "data" / "html", ds.GAME / "script"]
MAX_DISTANCE = 3000
LATE_PLACES = re.compile(r"Seed of|SOA|Airship|Krateis|Handys Block|Heart of Infinity|Infinity|Sel Mahum|Field of Silence|Field of Whispers|Photo Snow|Shyeed|Giant\\?'?s Cave|Mithril Mines|Olympiad Token|Enter Mines", re.I)

_ROW = re.compile(r"^\('((?:[^'\\]|\\.)*)',(\d+),(-?\d+),(-?\d+),(-?\d+),")
# A row, its separator and an optional trailing "-- comment".
_ROW_PARTS = re.compile(r"^(\(.*?\))\s*[,;]?(?=\s*(?:--|$))(\s*--.*)?\s*$")
_GOTO = re.compile(r"npc_%objectId%_goto (\d+)")
_LINK_LINE = re.compile(r"^\s*(<a [^>]*>.*?</a>\s*(<br1?>)?\s*)+$", re.I)


def region(x, y):
	return f"{math.floor(x / 32768) + 20}_{math.floor(y / 32768) + 18}"


def acis_destinations():
	points = []
	for name in ("teleports.xml", "instantTeleports.xml"):
		text = (ds.ACIS / name).read_text(encoding="utf-8")
		points += [(int(x), int(y)) for x, y in re.findall(r'x="(-?\d+)" y="(-?\d+)"', text)]
	return points


def late_teleports():
	"""(removed, far_but_kept) as id -> description."""
	acis = acis_destinations()
	late, far = {}, {}
	for line in TELEPORT_SQL.read_text(encoding="utf-8").split("\n"):
		m = _ROW.match(line)
		if not m:
			continue
		x, y = int(m.group(3)), int(m.group(4))
		if region(x, y) in kamael.REGIONS:
			continue
		if min(math.hypot(x - ax, y - ay) for ax, ay in acis) > MAX_DISTANCE:
			target = late if LATE_PLACES.search(m.group(1)) else far
			target[int(m.group(2))] = m.group(1)
	return late, far


def fix_separators(lines):
	"""Last row of each INSERT must end with ';', the others with ','."""
	rows = [i for i, line in enumerate(lines) if _ROW.match(line)]
	for n, i in enumerate(rows):
		last = n + 1 == len(rows) or any(lines[j].startswith("INSERT") for j in range(i + 1, rows[n + 1]))
		parts = _ROW_PARTS.match(lines[i])
		lines[i] = parts.group(1) + (";" if last else ",") + (parts.group(2) or "")
	return lines


def main():
	dry_run = "--dry-run" in sys.argv
	late, far = late_teleports()
	for tid, desc in sorted(late.items(), key=lambda kv: kv[1]):
		print(f"  {tid}: {desc}")
	print(f"{len(late)} teleport destinations removed")
	print(f"{len(far)} far from Interlude destinations but kept: " + "; ".join(sorted(far.values())))
	if dry_run:
		return

	lines = TELEPORT_SQL.read_text(encoding="utf-8").replace("\r\n", "\n").split("\n")
	lines = [line for line in lines if not ((m := _ROW.match(line)) and int(m.group(2)) in late)]
	TELEPORT_SQL.write_text("\n".join(fix_separators(lines)), encoding="utf-8", newline="\n")

	removed_links = 0
	kept_links = []
	for root in HTML_ROOTS:
		for path in list(root.rglob("*.htm")) + list(root.rglob("*.html")):
			text = path.read_text(encoding="utf-8", errors="surrogateescape")
			if "_goto" not in text:
				continue
			out = []
			changed = False
			for line in text.split("\n"):
				ids = [int(v) for v in _GOTO.findall(line)]
				if ids and all(i in late for i in ids):
					if _LINK_LINE.match(line.rstrip("\r")):
						removed_links += 1
						changed = True
						continue
					kept_links.append(path)
				out.append(line)
			if changed:
				path.write_text("\n".join(out), encoding="utf-8", errors="surrogateescape", newline="")
	print(f"removed {removed_links} HTML link lines; {len(set(kept_links))} files still link removed ids inside other markup")
	for path in sorted(set(kept_links))[:30]:
		print(f"  {path.relative_to(ds.GAME)}")


if __name__ == "__main__":
	main()
