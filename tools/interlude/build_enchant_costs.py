"""Skill enchanting costs and chances as in Interlude.

H5 prices every enchant level in SP and adena and gives chances up to level 85;
Interlude prices it in SP and experience, with its own chances for levels 76-80.
aCis has four such tables, one per tier of skill (the SP of +1 tells them apart),
while H5 shares two groups between all skills. This adds the four Interlude tables
to data/enchantSkillGroups.xml as groups 101-104 (the H5 groups stay, unused) and
points every enchant route of a skill at the table aCis gives that skill.

Experience is paid by the core (RequestExEnchantSkill); levels 81-85 get the chances
of level 80. Safe to run again.

Usage: python tools/interlude/build_enchant_costs.py [--dry-run]
"""

import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

import datasets as ds

GROUPS = ds.GAME / "data" / "enchantSkillGroups.xml"
SKILLS = ds.GAME / "data" / "stats" / "skills"
ACIS_ENCHANTS = ds.ACIS / "skillstrees" / "enchantSkills.xml"
FIRST_GROUP_ID = 101
LEVELS = 30
ROUTE_SPAN = 40  # aCis routes: 101-130, 141-170
_GROUP_BLOCK = re.compile(r'\t<group id="(\d+)">.*?\t</group>\n', re.S)
_SKILL_TAG = re.compile(r'<skill id="(\d+)"[^>]*>')
_ROUTE = re.compile(r'enchantGroup(\d+)="(\d+)"')


def acis_tables():
	"""(tables, routes): tables is [levels] sorted from the cheapest tier, each level a dict of
	exp, sp and rate76..rate80; routes maps (skill id, route) to the index of its table."""
	per_route = defaultdict(dict)
	for e in ET.parse(ACIS_ENCHANTS).getroot().findall("enchantSkill"):
		offset = int(e.get("lvl")) - 101
		key = (int(e.get("id")), offset // ROUTE_SPAN + 1)
		per_route[key][offset % ROUTE_SPAN + 1] = {
			"exp": int(e.get("exp")),
			"sp": int(e.get("sp")),
			**{f"rate{lvl}": int(e.get(f"rate{lvl}")) for lvl in range(76, 81)},
		}
	distinct = {}
	for key, levels in per_route.items():
		if sorted(levels) != list(range(1, LEVELS + 1)):
			raise SystemExit(f"aCis skill {key[0]} route {key[1]}: expected {LEVELS} levels, got {len(levels)}")
		table = tuple(tuple(sorted(levels[lvl].items())) for lvl in range(1, LEVELS + 1))
		distinct.setdefault(table, []).append(key)
	tables = sorted(distinct, key=lambda table: dict(table[0])["sp"])
	routes = {key: index for index, table in enumerate(tables) for key in distinct[table]}
	return [[dict(level) for level in table] for table in tables], routes


def groups_xml(tables):
	out = []
	for index, table in enumerate(tables):
		out.append(f'\t<group id="{FIRST_GROUP_ID + index}">\n')
		out.append(f"\t\t<!-- Interlude tier {index + 1}: +1 costs {table[0]['sp']} SP and {table[0]['exp']} exp (build_enchant_costs.py) -->\n")
		for lvl, level in enumerate(table, start=1):
			chances = " ".join(f'chance{c}="{level[f"rate{min(c, 80)}"]}"' for c in range(76, 86))
			out.append(f'\t\t<enchant level="{lvl}" adena="0" exp="{level["exp"]}" sp="{level["sp"]}" {chances} />\n')
		out.append("\t</group>\n")
	return "".join(out)


def with_groups(text, tables):
	"""The groups file with the Interlude groups (re)written at its end."""
	ours = range(FIRST_GROUP_ID, FIRST_GROUP_ID + len(tables))
	text = _GROUP_BLOCK.sub(lambda m: "" if int(m.group(1)) in ours else m.group(0), text)
	end = text.rindex("</list>")
	return text[:end] + groups_xml(tables) + text[end:]


def with_routes(text, routes):
	"""(skills file text pointing the enchant routes at the Interlude groups, routes changed, routes unknown to aCis)."""
	changed, unknown = 0, []

	def fix_tag(tag_match):
		nonlocal changed
		skill_id = int(tag_match.group(1))

		def fix_route(route_match):
			nonlocal changed
			route = int(route_match.group(1))
			index = routes.get((skill_id, route))
			if index is None:
				unknown.append((skill_id, route))
				return route_match.group(0)
			new = f'enchantGroup{route}="{FIRST_GROUP_ID + index}"'
			changed += new != route_match.group(0)
			return new

		return _ROUTE.sub(fix_route, tag_match.group(0))

	return _SKILL_TAG.sub(fix_tag, text), changed, unknown


def main():
	dry_run = "--dry-run" in sys.argv
	tables, routes = acis_tables()
	print(f"aCis: {len(tables)} cost tables for {len(routes)} skill routes")

	total, unknown = 0, []
	for path in sorted(SKILLS.glob("*.xml")):
		text = path.read_text(encoding="utf-8")
		new_text, changed, missing = with_routes(text, routes)
		unknown += missing
		if changed:
			total += changed
			if not dry_run:
				path.write_text(new_text, encoding="utf-8", newline="\n")
	if unknown:
		raise SystemExit(f"skill routes without aCis costs (run build_enchant_skills.py first): {unknown[:10]}")
	if not dry_run:
		newline = "\r\n" if b"\r\n" in GROUPS.read_bytes() else "\n"
		GROUPS.write_text(with_groups(GROUPS.read_text(encoding="utf-8"), tables), encoding="utf-8", newline=newline)
	print(f"{'would point' if dry_run else 'pointed'} {total} routes at groups {FIRST_GROUP_ID}-{FIRST_GROUP_ID + len(tables) - 1}")


if __name__ == "__main__":
	main()
