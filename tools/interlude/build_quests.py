"""Removes quests that are absent from aCis (Interlude) from QuestLoader.java,
except Kamael quests (see kamael.QUESTS). Quest sources stay in place.

Usage: python tools/interlude/build_quests.py
"""

import re

import datasets as ds
import kamael

ACIS_QUESTS = ds.ROOT.parent / "acis_public-master" / "aCis_gameserver" / "java" / "net" / "sf" / "l2j" / "gameserver" / "scripting" / "quest"
LOADER = ds.GAME / "script" / "com" / "l2jserver" / "datapack" / "quests" / "QuestLoader.java"
QUEST_CLASS = re.compile(r"\bQ(\d{5})_\w+")


def main():
	interlude = {int(m.group(1)) for f in ACIS_QUESTS.glob("Q*.java") if (m := re.match(r"Q(\d+)_", f.name))}
	# 201-208: dummy quests holding newbie tutorial state for the tutorial quest.
	keep = interlude | kamael.QUESTS | set(range(201, 209))

	removed = set()
	lines = []
	for line in LOADER.read_text(encoding="utf-8").replace("\r\n", "\n").split("\n"):
		m = QUEST_CLASS.search(line)
		if m and (line.startswith("import ") or line.strip().endswith((".class,", ".class"))) and int(m.group(1)) not in keep:
			removed.add(m.group(0))
			continue
		lines.append(line)

	text = "\n".join(lines)
	# The last array element may have lost its trailing element, drop a dangling comma.
	text = re.sub(r",(\s*\n\t\};)", r"\1", text)
	LOADER.write_text(text, encoding="utf-8", newline="\n")
	print(f"Removed {len(removed)} quests from QuestLoader:")
	print(" ".join(sorted(removed)))


if __name__ == "__main__":
	main()
