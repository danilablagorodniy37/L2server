"""Removes post-Interlude items from NPC shops (data/buylists) and multisells (data/multisell).

An item is allowed when aCis (Interlude) defines it or it is Kamael gear/quest
item (kamael.item_ids). Buylist entries of other items are dropped; a multisell
entry is dropped when any of its ingredients or products is not allowed.
Files left without entries are deleted. Special multisell currencies (negative
ids: clan reputation, fame) are kept.

Usage: python tools/interlude/build_shops.py
"""

import re

import datasets as ds
import kamael

BUYLISTS = ds.GAME / "data" / "buylists"
MULTISELL = ds.GAME / "data" / "multisell"
_BUYLIST_ITEM = re.compile(r'^\s*<item id="(\d+)"')
_MULTISELL_ENTRY = re.compile(r"\n?[ \t]*<item>.*?</item>", re.S)
_ENTRY_ID = re.compile(r'<(?:ingredient|production)\b[^>]*\bid="(-?\d+)"')


def allowed_ids():
	items = ds.h5_items()
	return ds.interlude_item_ids() | kamael.item_ids(items)


def filter_buylist(path, allowed):
	lines = path.read_text(encoding="utf-8").replace("\r\n", "\n").split("\n")
	kept, removed, total = [], 0, 0
	for line in lines:
		m = _BUYLIST_ITEM.match(line)
		if m:
			total += 1
			if int(m.group(1)) not in allowed:
				removed += 1
				continue
		kept.append(line)
	return "\n".join(kept), total, removed


def filter_multisell(path, allowed):
	text = path.read_text(encoding="utf-8").replace("\r\n", "\n")
	counts = {"total": 0, "removed": 0}

	def keep(match):
		counts["total"] += 1
		ids = [int(v) for v in _ENTRY_ID.findall(match.group(0))]
		if all(i < 0 or i in allowed for i in ids):
			return match.group(0)
		counts["removed"] += 1
		return ""

	return _MULTISELL_ENTRY.sub(keep, text), counts["total"], counts["removed"]


def process(folder, filter_fn, allowed):
	changed, deleted, entries, removed = 0, [], 0, 0
	for path in sorted(folder.glob("*.xml")):
		text, total, dropped = filter_fn(path, allowed)
		entries += total
		removed += dropped
		if not dropped:
			continue
		if dropped == total:
			path.unlink()
			deleted.append(path.stem)
		else:
			path.write_text(text, encoding="utf-8", newline="\n")
			changed += 1
	print(f"{folder.name}: removed {removed} of {entries} entries, {changed} files changed, {len(deleted)} files deleted")
	if deleted:
		print("  deleted: " + " ".join(deleted))


def main():
	allowed = allowed_ids()
	process(BUYLISTS, filter_buylist, allowed)
	process(MULTISELL, filter_multisell, allowed)


if __name__ == "__main__":
	main()
