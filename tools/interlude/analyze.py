"""Prints how much High Five content is absent from the aCis Interlude datapack.

Usage: python tools/interlude/analyze.py
"""

import xml.etree.ElementTree as ET
from collections import Counter

import datasets as ds


def item_refs_in(folder, tag_attr_pairs):
	refs = Counter()
	for f in sorted(folder.rglob("*.xml")):
		root = ET.parse(f).getroot()
		for tag, attr in tag_attr_pairs:
			for e in root.iter(tag):
				if e.get(attr, "").lstrip("-").isdigit():
					refs[(f.relative_to(ds.GAME).as_posix(), int(e.get(attr)))] += 1
	return refs


def main():
	items = ds.h5_items()
	acis_items = ds.acis_ids("items")
	new_items = {i: it for i, it in items.items() if i not in acis_items}
	kamael_items = {i: it for i, it in new_items.items() if ds.is_kamael_item(it) and it["grade"] in ds.INTERLUDE_GRADES}
	print(f"Items: H5 {len(items)}, aCis {len(acis_items)}, only in H5 {len(new_items)} (Kamael gear up to S: {len(kamael_items)})")
	print("  only-in-H5 by grade:", dict(Counter(it["grade"] for it in new_items.values()).most_common()))
	print("  only-in-H5 by type:", dict(Counter(it["type"] for it in new_items.values()).most_common()))

	npcs = ds.h5_npcs()
	acis_npcs = ds.acis_ids("npcs")
	new_npcs = {i for i in npcs if i not in acis_npcs}
	print(f"NPCs: H5 {len(npcs)}, aCis {len(acis_npcs)}, only in H5 {len(new_npcs)}")

	spawns = ds.h5_spawns()
	new_spawns = [s for s in spawns if s[0] in new_npcs]
	print(f"Spawns: {len(spawns)} rows, {len(new_spawns)} of NPCs absent in aCis ({len({s[0] for s in new_spawns})} distinct NPCs)")

	skills = ds.h5_skills()
	acis_skills = ds.acis_ids("skills")
	print(f"Skills: H5 {len(skills)}, aCis {len(acis_skills)}, only in H5 {len(set(skills) - acis_skills)}")

	acis_tree = ds.acis_class_skills()
	tree = ET.parse(ds.GAME / "data" / "skillTrees" / "classSkillTree.xml").getroot()
	extra = Counter()
	missing = Counter()
	for st in tree.findall("skillTree"):
		cid = int(st.get("classId"))
		if cid in ds.KAMAEL_CLASS_IDS or cid not in acis_tree:
			continue
		h5 = {(int(s.get("skillId")), int(s.get("skillLvl"))) for s in st.findall("skill")}
		extra[cid] = len(h5 - acis_tree[cid])
		missing[cid] = len(acis_tree[cid] - h5)
	print(f"classSkillTree (non-Kamael classes): {sum(extra.values())} H5 entries not in aCis, {sum(missing.values())} aCis entries not in H5")

	for folder, pairs in (("buylists", [("item", "id")]), ("multisell", [("ingredient", "id"), ("production", "id")])):
		refs = item_refs_in(ds.GAME / "data" / folder, pairs)
		bad = {k: v for k, v in refs.items() if k[1] in new_items and k[1] not in kamael_items}
		print(f"{folder}: {len(refs)} item refs, {len(bad)} to post-Interlude items in {len({k[0] for k in bad})} files")


if __name__ == "__main__":
	main()
