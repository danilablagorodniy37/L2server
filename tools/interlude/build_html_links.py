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

_LINK = re.compile(r'<a action="bypass -h npc_%objectId%_(multisell|exc_multisell|Buy) (\d+)"[^>]*>.*?</a>(?:<br1?>)?', re.I)


def main():
	dry_run = "--dry-run" in sys.argv
	multisells = ds.multisell_ids()
	buylists = ds.buylist_ids()

	def broken(m):
		ids = buylists if m.group(1) == "Buy" else multisells
		return int(m.group(2)) not in ids

	files, links = 0, 0
	for path in sorted(ds.html_files()):
		with open(path, encoding="utf-8", errors="surrogateescape", newline="") as f:
			text = f.read()
		if not any(broken(m) for m in _LINK.finditer(text)):
			continue
		out, removed = [], 0
		for line in text.splitlines(keepends=True):
			matches = [m for m in _LINK.finditer(line) if broken(m)]
			if not matches:
				out.append(line)
				continue
			removed += len(matches)
			rest = _LINK.sub(lambda m: "" if broken(m) else m.group(0), line)
			if rest.strip():
				out.append(rest)
		print(f"{path.relative_to(ds.GAME).as_posix()}: {removed}")
		files += 1
		links += removed
		if not dry_run:
			with open(path, "w", encoding="utf-8", errors="surrogateescape", newline="") as f:
				f.write("".join(out))
	print(f"{'would remove' if dry_run else 'removed'} {links} links in {files} files")


if __name__ == "__main__":
	main()
