"""Rebuilds game/data/skillTrees/classSkillTree.xml for Interlude + Kamael.

- Classes present in aCis (0-118): skill list, required level and SP are taken
  from aCis. cost=0 becomes autoGet, cost=-1 keeps the H5 item requirement.
  Attributes such as the skill name come from the H5 data.
- Kamael classes (123-136): H5 tree, capped at the Interlude level cap and
  without Expertise S80/S84.

Entries whose skill level has no H5 skill template are skipped and reported.
The script is idempotent only against the original H5 file, run it once.

Usage: python tools/interlude/build_skill_trees.py
"""

import re
import xml.etree.ElementTree as ET
from collections import defaultdict
from xml.sax.saxutils import quoteattr

import datasets as ds

MAX_LEVEL = 80
EXPERTISE = 239
TREE = ds.GAME / "data" / "skillTrees" / "classSkillTree.xml"


def h5_skill_levels():
	levels = {}
	for f in ds._xml_files(ds.GAME / "data" / "stats" / "skills"):
		for s in ET.parse(f).getroot().findall("skill"):
			levels[int(s.get("id"))] = (s.get("name"), int(s.get("levels")))
	return levels


def acis_class_entries():
	"""classId -> ordered list of (skillId, lvl, cost, minLvl)."""
	result = {}
	for f in ds._xml_files(ds.ACIS / "classes"):
		for c in ET.parse(f).getroot().findall("class"):
			class_id = next(int(s.get("id")) for s in c.findall("set") if s.get("id") is not None)
			result[class_id] = [(int(s.get("id")), int(s.get("lvl")), int(s.get("cost")), int(s.get("minLvl"))) for s in c.find("skills").findall("skill")]
	return result


def skill_line(name, skill_id, lvl, get_level, sp=None, auto_get=False, items=()):
	attrs = f'skillName={quoteattr(name)} skillId="{skill_id}" skillLvl="{lvl}" getLevel="{get_level}"'
	if auto_get:
		attrs += ' autoGet="true"'
	else:
		if sp:
			attrs += f' levelUpSp="{sp}"'
		attrs += ' learnedByNpc="true"'
	if not items:
		return [f"\t\t<skill {attrs} />"]
	return [f"\t\t<skill {attrs}>"] + [f'\t\t\t<item id="{i}" count="{n}" />' for i, n in items] + ["\t\t</skill>"]


def main():
	text = TREE.read_text(encoding="utf-8").replace("\r\n", "\n")
	root = ET.fromstring(text)
	templates = h5_skill_levels()
	acis = acis_class_entries()

	# Item requirements known by H5 for (skillId, lvl), wherever they are declared.
	h5_items = {}
	for st in root.findall("skillTree"):
		for s in st.findall("skill"):
			items = [(int(i.get("id")), int(i.get("count"))) for i in s.findall("item")]
			if items:
				h5_items[(int(s.get("skillId")), int(s.get("skillLvl")))] = items

	report = defaultdict(list)
	blocks = {}
	for st in root.findall("skillTree"):
		class_id = int(st.get("classId"))
		lines = []
		if class_id in acis:
			for skill_id, lvl, cost, min_lvl in acis[class_id]:
				template = templates.get(skill_id)
				if template is None or lvl > template[1]:
					report["no H5 template"].append((class_id, skill_id, lvl))
					continue
				if cost < 0:
					items = h5_items.get((skill_id, lvl))
					if not items:
						report["book skill without H5 item"].append((class_id, skill_id, lvl))
						continue
					lines += skill_line(template[0], skill_id, lvl, min_lvl, items=items)
				elif cost == 0:
					lines += skill_line(template[0], skill_id, lvl, min_lvl, auto_get=True)
				else:
					lines += skill_line(template[0], skill_id, lvl, min_lvl, sp=cost)
		elif class_id in ds.KAMAEL_CLASS_IDS:
			for s in st.findall("skill"):
				skill_id, lvl, get_level = int(s.get("skillId")), int(s.get("skillLvl")), int(s.get("getLevel"))
				if get_level > MAX_LEVEL or (skill_id == EXPERTISE and lvl > 5):
					report["Kamael entry removed"].append((class_id, skill_id, lvl))
					continue
				items = [(int(i.get("id")), int(i.get("count"))) for i in s.findall("item")]
				lines += skill_line(s.get("skillName"), skill_id, lvl, get_level, sp=int(s.get("levelUpSp", "0")), auto_get=s.get("autoGet") == "true", items=items)
		else:
			report["class kept as is"].append(class_id)
			continue
		blocks[class_id] = lines

	# Replace the body of each <skillTree> block, keeping the file's comments and layout.
	def replace(match):
		class_id = int(match.group(2))
		if class_id not in blocks:
			return match.group(0)
		return match.group(1) + "\n" + "\n".join(blocks[class_id]) + "\n\t</skillTree>"

	pattern = re.compile(r'(\t<skillTree type="classSkillTree" classId="(\d+)"[^>]*>(?:\n\t\t<!--[^\n]*-->)?)(.*?)\t</skillTree>', re.S)
	new_text, count = pattern.subn(replace, text)
	new_text = new_text.replace("Confirmed CT2.5 and Updated to H5", "Interlude (aCis) skill list")
	TREE.write_text(new_text, encoding="utf-8", newline="\n")

	print(f"Rewrote {count} skill trees, {sum(len(v) for v in blocks.values())} lines")
	for key, values in report.items():
		print(f"{key}: {len(values)}")
		for v in values[:40]:
			print(f"  {v}")


if __name__ == "__main__":
	main()
