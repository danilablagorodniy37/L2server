"""Removes raid and grand bosses absent from aCis (Interlude) from their spawn tables.

Usage: python tools/interlude/build_bosses.py
"""

import re

import datasets as ds

TABLES = ["raidboss_spawnlist.sql", "grandboss_data.sql"]
_ROW = re.compile(r"^\((\d+),")
_ROW_PARTS = re.compile(r"^(\(.*?\))\s*[,;]?(?=\s*(?:--|$))(\s*--.*)?\s*$")


def main():
	interlude = ds.acis_ids("npcs")
	npcs = ds.h5_npcs()
	for table in TABLES:
		path = ds.GAME / "sql" / table
		lines = path.read_text(encoding="utf-8").replace("\r\n", "\n").split("\n")
		removed = [int(m.group(1)) for line in lines if (m := _ROW.match(line)) and int(m.group(1)) not in interlude]
		lines = [line for line in lines if not ((m := _ROW.match(line)) and int(m.group(1)) not in interlude)]
		rows = [i for i, line in enumerate(lines) if _ROW.match(line)]
		for n, i in enumerate(rows):
			last = n + 1 == len(rows) or any(lines[j].lstrip().upper().startswith("INSERT") for j in range(i + 1, rows[n + 1]))
			parts = _ROW_PARTS.match(lines[i])
			lines[i] = parts.group(1) + (";" if last else ",") + (parts.group(2) or "")
		path.write_text("\n".join(lines), encoding="utf-8", newline="\n")
		print(f"{table}: removed {', '.join(npcs[i]['name'] for i in removed)}")


if __name__ == "__main__":
	main()
