"""Removes post-Interlude items from NPC drops/spoils, recipes and starting equipment.

Allowed items are those defined by aCis plus Kamael items (see build_shops.py).
- data/stats/npcs: drop/spoil entries of other items are removed, empty groups
  and empty drop lists too. Remaining chances are left as they are, so a
  removed item simply stops dropping.
- data/recipes.xml: recipes whose recipe item, ingredients or product are not
  allowed are removed.
- data/stats/initialEquipment.xml: non-Kamael classes keep only aCis items,
  Kamael classes keep only allowed items.
- data/seeds.xml: manor crops whose seed, crop, mature crop or rewards are not allowed
  are removed (H5 added the level 79-82 Coba seeds to the castle manors).

Usage: python tools/interlude/build_items.py
"""

import re

import datasets as ds
from build_shops import allowed_ids

NPCS = ds.GAME / "data" / "stats" / "npcs"
SEEDS = ds.GAME / "data" / "seeds.xml"
_CROP = re.compile(r'[ \t]*<crop [^>]*/>\r?\n')
_CROP_IDS = re.compile(r'\b(?:id|seedId|mature_Id|reward1|reward2)="(\d+)"')
RECIPES = ds.GAME / "data" / "recipes.xml"
INITIAL_EQUIPMENT = ds.GAME / "data" / "stats" / "initialEquipment.xml"

_DROP_ITEM = re.compile(r'\n[ \t]*<item id="(\d+)"[^>]*/>[^\n]*')
_EMPTY_GROUP = re.compile(r'\n[ \t]*<group\b[^>]*>\s*</group>')
_EMPTY_LIST = re.compile(r'\n[ \t]*<(death|corpse)>\s*</\1>')
_EMPTY_DROPLISTS = re.compile(r'\n[ \t]*<dropLists>\s*</dropLists>')
_DROPLISTS = re.compile(r"<dropLists>.*?</dropLists>", re.S)
_RECIPE = re.compile(r'\n[ \t]*<item id="\d+" recipeId="(\d+)".*?</item>', re.S)
_RECIPE_IDS = re.compile(r'<(?:ingredient|production)\b[^>]*\bid="(\d+)"')
_RARE_PRODUCTION = re.compile(r'\n[ \t]*<productionRare id="(\d+)"[^>]*/>')
_EQUIPMENT = re.compile(r'(<equipment classId="(\d+)">)(.*?)(</equipment>)', re.S)
_EQUIPMENT_ITEM = re.compile(r'\n[ \t]*<item id="(\d+)"[^>]*/>[^\n]*')


def clean_drops(allowed):
	removed = 0
	files = 0
	for path in sorted(NPCS.glob("*.xml")):
		text = path.read_text(encoding="utf-8").replace("\r\n", "\n")
		counter = [0]

		def drop_block(match):
			def item(m):
				if int(m.group(1)) in allowed:
					return m.group(0)
				counter[0] += 1
				return ""

			block = _DROP_ITEM.sub(item, match.group(0))
			block = _EMPTY_GROUP.sub("", block)
			return _EMPTY_LIST.sub("", block)

		new_text = _EMPTY_DROPLISTS.sub("", _DROPLISTS.sub(drop_block, text))
		if counter[0]:
			path.write_text(new_text, encoding="utf-8", newline="\n")
			removed += counter[0]
			files += 1
	print(f"npc drops: removed {removed} entries in {files} files")


def clean_recipes(allowed):
	text = RECIPES.read_text(encoding="utf-8").replace("\r\n", "\n")
	counts = [0, 0, 0]

	def rare(match):
		if int(match.group(1)) in allowed:
			return match.group(0)
		counts[2] += 1
		return ""

	def recipe(match):
		counts[0] += 1
		ids = [int(match.group(1))] + [int(v) for v in _RECIPE_IDS.findall(match.group(0))]
		if all(i in allowed for i in ids):
			# H5 added rare products (Foundation items) to Interlude recipes: drop only those.
			return _RARE_PRODUCTION.sub(rare, match.group(0))
		counts[1] += 1
		return ""

	RECIPES.write_text(_RECIPE.sub(recipe, text), encoding="utf-8", newline="\n")
	print(f"recipes: removed {counts[1]} of {counts[0]}, {counts[2]} post-Interlude rare products dropped")


def clean_initial_equipment(allowed):
	acis_items = ds.interlude_item_ids()
	text = INITIAL_EQUIPMENT.read_text(encoding="utf-8").replace("\r\n", "\n")
	removed = []

	def equipment(match):
		class_id = int(match.group(2))
		keep = allowed if class_id in ds.KAMAEL_CLASS_IDS else acis_items

		def item(m):
			if int(m.group(1)) in keep:
				return m.group(0)
			removed.append((class_id, int(m.group(1))))
			return ""

		return match.group(1) + _EQUIPMENT_ITEM.sub(item, match.group(3)) + match.group(4)

	INITIAL_EQUIPMENT.write_text(_EQUIPMENT.sub(equipment, text), encoding="utf-8", newline="\n")
	print(f"initial equipment: removed {len(removed)} entries, items {sorted({i for _, i in removed})}")


def without_late_crops(text, allowed):
	"""(seeds.xml text without crops that involve items outside allowed, removed seed ids)."""
	removed = []

	def crop(match):
		ids = [int(v) for v in _CROP_IDS.findall(match.group(0))]
		if all(i in allowed for i in ids):
			return match.group(0)
		removed.append(int(re.search(r'seedId="(\d+)"', match.group(0)).group(1)))
		return ""

	return _CROP.sub(crop, text), removed


def clean_manor(allowed):
	raw = SEEDS.read_bytes()
	text, removed = without_late_crops(raw.decode("utf-8"), allowed)
	if removed:
		SEEDS.write_bytes(text.encode("utf-8"))
	print(f"manor: removed {len(removed)} crops, seeds {sorted(set(removed))}")


def main():
	allowed = allowed_ids()
	clean_drops(allowed)
	clean_recipes(allowed)
	clean_initial_equipment(allowed)
	clean_manor(allowed)


if __name__ == "__main__":
	main()
