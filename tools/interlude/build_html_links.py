"""Removes NPC dialog links to multisells and buylists that no longer exist.

build_shops.py deletes multisells and buylists left empty after the cleanup;
the buttons opening them stay in NPC HTML (data/html and script folders) and
lead nowhere. A link line "<a action="bypass -h npc_%objectId%_multisell N">...</a><br>"
is removed as a whole; a link inside a longer line is cut out with its <br>.
Safe to run again.

Usage: python tools/interlude/build_html_links.py [--dry-run]
"""

import re
import sys

import datasets as ds

LINK = re.compile(r'<a action="bypass -h npc_%objectId%_(multisell|exc_multisell|Buy) (\d+)"[^>]*>.*?</a>(?:<br1?>)?', re.I)


def remove_links(text, is_broken):
	"""Returns (text without links for which is_broken(kind, id) is true, number removed)."""
	out, removed = [], 0
	for line in text.splitlines(keepends=True):
		matches = [m for m in LINK.finditer(line) if is_broken(m.group(1), int(m.group(2)))]
		if not matches:
			out.append(line)
			continue
		removed += len(matches)
		rest = LINK.sub(lambda m: "" if is_broken(m.group(1), int(m.group(2))) else m.group(0), line)
		if rest.strip():
			out.append(rest)
	return "".join(out), removed


def main():
	dry_run = "--dry-run" in sys.argv
	multisells = ds.multisell_ids()
	buylists = ds.buylist_ids()

	def is_broken(kind, list_id):
		return list_id not in (buylists if kind == "Buy" else multisells)

	files, links = 0, 0
	for path in sorted(ds.html_files()):
		with open(path, encoding="utf-8", errors="surrogateescape", newline="") as f:
			text, removed = remove_links(f.read(), is_broken)
		if not removed:
			continue
		print(f"{path.relative_to(ds.GAME).as_posix()}: {removed}")
		files += 1
		links += removed
		if not dry_run:
			with open(path, "w", encoding="utf-8", errors="surrogateescape", newline="") as f:
				f.write(text)
	print(f"{'would remove' if dry_run else 'removed'} {links} links in {files} files")


if __name__ == "__main__":
	main()
