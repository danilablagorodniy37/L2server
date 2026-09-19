"""Takes the elemental attributes of High Five out of the NPC templates.

Interlude has no attribute system: no fire or water defence on a monster, no attribute on a weapon.
High Five gave every NPC a block like

    <attribute>
        <defence fire="20" water="20" wind="-5" earth="20" holy="20" dark="20" />
    </attribute>

which means nothing on this server and would start to mean something the moment the attribute
system were switched on again. This removes those blocks from game/data/stats/npcs.

The attributes of a skill (its element) are left alone: Interlude skills have those.

Usage: python tools/interlude/strip_attributes.py [--dry-run]
"""

import re
import sys

import datasets as ds

NPCS = ds.GAME / "data" / "stats" / "npcs"
# the whole block with the lines it sits on
BLOCK = re.compile(r"[ \t]*<attribute>\s*?\n(?:[^\n]*\n)*?[ \t]*</attribute>[ \t]*\n", re.M)
ONE_LINE = re.compile(r"[ \t]*<attribute>.*?</attribute>[ \t]*\n", re.S)


def strip(text):
	"""(text without the attribute blocks, how many were taken out)."""
	out, count = BLOCK.subn("", text)
	out, more = ONE_LINE.subn("", out)
	return out, count + more


def main():
	dry_run = "--dry-run" in sys.argv
	files = 0
	blocks = 0
	for f in sorted(NPCS.glob("*.xml")):
		text = f.read_text(encoding="utf-8")
		fixed, count = strip(text)
		if count == 0:
			continue
		files += 1
		blocks += count
		if not dry_run:
			f.write_text(fixed, encoding="utf-8", newline="\n")
	print(f"{blocks} attribute blocks in {files} files{' (dry run)' if dry_run else ''}")


if __name__ == "__main__":
	main()
