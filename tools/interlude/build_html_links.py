"""Removes NPC dialog buttons that lead nowhere.

Two kinds, both left behind by the rollback to Interlude:
1. multisells and buylists deleted by build_shops.py when the cleanup emptied them
   (NPC HTML in data/html and in script folders);
2. "bypass -h npc_%objectId%_Quest <script>" buttons of quests and AI scripts that
   build_quests.py and build_ai.py took out of the loaders, so nothing answers them
   (data/html only: the dialogs inside the folder of an unloaded script are never shown).

A link line "<a action="...">...</a><br>" is removed as a whole; a link inside a longer
line is cut out with its <br>. Safe to run again.

Usage: python tools/interlude/build_html_links.py [--dry-run]
"""

import re
import sys

import datasets as ds

LINK = re.compile(r'<a action="bypass -h npc_%objectId%_(multisell|exc_multisell|Buy) (\d+)"[^>]*>.*?</a>(?:<br1?>)?', re.I)
QUEST_LINK = re.compile(r'<a action="bypass -h npc_%objectId%_Quest ([\w-]+)[^"]*"[^>]*>.*?</a>(?:<br1?>)?', re.I)
LOADERS = ("com/l2jserver/datapack/quests/QuestLoader.java", "com/l2jserver/datapack/ai/AILoader.java")


def loaded_script_names():
	"""Names a loaded script answers to: its class name and the name it registers."""
	script = ds.GAME / "script"
	classes = set()
	for loader in LOADERS:
		classes |= set(re.findall(r"^\t+(\w+)\.class", (script / loader).read_text(encoding="utf-8"), re.M))
	names = set(classes)
	# One walk of the script tree, not one per class.
	for java in script.rglob("*.java"):
		if java.stem in classes:
			names |= set(re.findall(r'super\(\s*(?:\d+\s*,\s*)?"([\w-]+)"', java.read_text(encoding="utf-8", errors="replace")))
	return names


def _remove(text, pattern, is_broken):
	"""Returns (text without the links is_broken accepts, number removed)."""
	out, removed = [], 0
	for line in text.splitlines(keepends=True):
		matches = [m for m in pattern.finditer(line) if is_broken(m)]
		if not matches:
			out.append(line)
			continue
		removed += len(matches)
		rest = pattern.sub(lambda m: "" if is_broken(m) else m.group(0), line)
		if rest.strip():
			out.append(rest)
	return "".join(out), removed


def remove_links(text, is_broken):
	"""Returns (text without shop links for which is_broken(kind, id) is true, number removed)."""
	return _remove(text, LINK, lambda m: is_broken(m.group(1), int(m.group(2))))


def remove_quest_links(text, is_dead):
	"""Returns (text without quest buttons for which is_dead(script name) is true, number removed)."""
	return _remove(text, QUEST_LINK, lambda m: is_dead(m.group(1)))


def main():
	dry_run = "--dry-run" in sys.argv
	multisells = ds.multisell_ids()
	buylists = ds.buylist_ids()

	def is_broken(kind, list_id):
		return list_id not in (buylists if kind == "Buy" else multisells)

	names = loaded_script_names()
	html = ds.GAME / "data" / "html"

	files, links, buttons = 0, 0, 0
	for path in sorted(ds.html_files()):
		with open(path, encoding="utf-8", errors="surrogateescape", newline="") as f:
			text, removed = remove_links(f.read(), is_broken)
		dead = 0
		if path.is_relative_to(html):
			text, dead = remove_quest_links(text, lambda name: name not in names)
		if not removed and not dead:
			continue
		print(f"{path.relative_to(ds.GAME).as_posix()}: {removed} shop, {dead} quest")
		files += 1
		links += removed
		buttons += dead
		if not dry_run:
			with open(path, "w", encoding="utf-8", errors="surrogateescape", newline="") as f:
				f.write(text)
	print(f"{'would remove' if dry_run else 'removed'} {links} shop links and {buttons} quest buttons in {files} files")


if __name__ == "__main__":
	main()
