"""Limits skill enchanting to the Interlude routes.

In H5 many more skills can be enchanted (526 against 256) and every skill has up
to seven routes, four of them attribute routes that do not exist in Interlude.
aCis is the reference: a skill keeps as many enchantGroup attributes as it has
enchantLevels routes there, and skills absent from aCis lose them all.
The enchant tables inside a skill are left alone, a route without a group is
simply not offered.

Usage: python tools/interlude/build_enchant_skills.py [--dry-run]
"""

import re
import sys
import xml.etree.ElementTree as ET

import datasets as ds

SKILLS = ds.GAME / "data" / "stats" / "skills"
_SKILL_TAG = re.compile(r"<skill\b[^>]*>")
_GROUP = re.compile(r'\s+enchantGroup(\d+)="[^"]*"')


def acis_routes():
	"""skill id -> how many enchant routes Interlude has for it."""
	routes = {}
	for f in ds._xml_files(ds.ACIS / "skills"):
		for s in ET.parse(f).getroot().iter("skill"):
			count = len([k for k in s.attrib if k.startswith("enchantLevels")])
			if count > 0:
				routes[int(s.get("id"))] = count
	return routes


def main():
	dry_run = "--dry-run" in sys.argv
	routes = acis_routes()
	removed_skills = 0
	removed_routes = 0
	changed_files = 0

	for path in ds._xml_files(SKILLS):
		text = path.read_text(encoding="utf-8")
		changed = False

		def fix(match):
			nonlocal changed, removed_skills, removed_routes
			tag = match.group(0)
			groups = [int(m.group(1)) for m in _GROUP.finditer(tag)]
			if not groups:
				return tag
			skill_id = int(re.search(r'id="(\d+)"', tag).group(1))
			allowed = routes.get(skill_id, 0)
			extra = [g for g in groups if g > allowed]
			if not extra:
				return tag
			changed = True
			removed_routes += len(extra)
			if allowed == 0:
				removed_skills += 1
			return _GROUP.sub(lambda m: "" if int(m.group(1)) > allowed else m.group(0), tag)

		new_text = _SKILL_TAG.sub(fix, text)
		if changed:
			changed_files += 1
			if not dry_run:
				path.write_text(new_text, encoding="utf-8", newline="\n")

	print(f"{'would remove' if dry_run else 'removed'} {removed_routes} enchant routes; {removed_skills} skills are no longer enchantable; {changed_files} files")


if __name__ == "__main__":
	main()
