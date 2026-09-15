"""Replaces the dialogs of an H5 quest with the dialogs of its aCis (Interlude) version.

The quest Java class is ported by hand; this copies the aCis HTML files into the
H5 quest folder, renames the quest in "bypass -h Quest" links and removes the
old H5 dialogs.

Usage: python tools/interlude/port_quest_html.py <quest id> [<quest id> ...]
"""

import re
import sys

import datasets as ds

ACIS_HTML = ds.ACIS.parent / "html" / "script" / "quest"
H5_QUESTS = ds.GAME / "script" / "com" / "l2jserver" / "datapack" / "quests"


def folders(quest_id):
	acis = [d for d in ACIS_HTML.iterdir() if re.fullmatch(rf"Q0*{quest_id}_\w+", d.name)]
	h5 = [d for d in H5_QUESTS.iterdir() if d.is_dir() and d.name.startswith(f"Q{quest_id:05d}_")]
	if len(acis) != 1 or len(h5) != 1:
		raise SystemExit(f"quest {quest_id}: aCis {[d.name for d in acis]}, H5 {[d.name for d in h5]}")
	return acis[0], h5[0]


def port(quest_id):
	acis, h5 = folders(quest_id)
	for old in list(h5.glob("*.htm")) + list(h5.glob("*.html")):
		old.unlink()
	count = 0
	for src in sorted(acis.glob("*.htm*")):
		text = src.read_text(encoding="utf-8", errors="surrogateescape")
		text = re.sub(rf"(bypass -h Quest ){re.escape(acis.name)}\b", rf"\g<1>{h5.name}", text).rstrip() + "\n"
		(h5 / src.name).write_text(text, encoding="utf-8", errors="surrogateescape", newline="\n")
		count += 1
	print(f"{h5.name}: {count} dialogs from {acis.name}")


if __name__ == "__main__":
	for arg in sys.argv[1:]:
		port(int(arg))
