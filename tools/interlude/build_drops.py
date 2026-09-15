"""Interlude drops: replaces NPC drop and spoil lists with the aCis ones.

For every NPC that aCis defines with drops, the H5 <dropLists> is replaced:
- CURRENCY, DROP and HERB categories -> <death> groups (same chance model:
  group chance, then item chance inside the group)
- SPOIL category -> <corpse> items, chance = category chance * item chance / 100
NPCs without aCis drops (Kamael and other H5-only NPCs) keep their current lists.
Items missing from the H5 item templates are skipped. Idempotent.

Usage: python tools/interlude/build_drops.py
"""

import re
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

import datasets as ds

NPCS = ds.GAME / "data" / "stats" / "npcs"
_NPC_BLOCK = re.compile(r'(\t<npc id="(\d+)".*?)(\n\t</npc>)', re.S)
_DROPLISTS = re.compile(r"\n\t\t<dropLists>.*?</dropLists>", re.S)


def acis_drops():
	"""npc id -> list of (type, chance, [(item, min, max, chance)])"""
	result = {}
	for f in ds._xml_files(ds.ACIS / "npcs"):
		for npc in ET.parse(f).getroot().iter("npc"):
			drops = npc.find("drops")
			if drops is None:
				continue
			result[int(npc.get("id"))] = [
				(c.get("type"), float(c.get("chance")), [(int(d.get("itemid")), int(d.get("min")), int(d.get("max")), float(d.get("chance"))) for d in c.findall("drop")])
				for c in drops.findall("category")
			]
	return result


def number(value):
	return f"{value:.4f}".rstrip("0").rstrip(".")


def render(categories, items, skipped):
	def item_line(indent, item_id, low, high, chance):
		return f'{indent}<item id="{item_id}" min="{low}" max="{high}" chance="{number(chance)}" /> <!-- {escape(items[item_id]["name"] or "")} -->'

	death, corpse = [], []
	for kind, chance, drops in categories:
		drops = [d for d in drops if d[0] in items or skipped.add(d[0])]
		if not drops:
			continue
		if kind == "SPOIL":
			corpse += [item_line("\t\t\t\t", i, lo, hi, chance * c / 100) for i, lo, hi, c in drops]
		else:
			death.append(f'\t\t\t\t<group chance="{number(chance)}">')
			death += [item_line("\t\t\t\t\t", i, lo, hi, c) for i, lo, hi, c in drops]
			death.append("\t\t\t\t</group>")
	if not death and not corpse:
		return ""
	out = ["", "\t\t<dropLists>"]
	if death:
		out += ["\t\t\t<death>"] + death + ["\t\t\t</death>"]
	if corpse:
		out += ["\t\t\t<corpse>"] + corpse + ["\t\t\t</corpse>"]
	out.append("\t\t</dropLists>")
	return "\n".join(out)


def main():
	drops = acis_drops()
	items = ds.h5_items()
	skipped = set()
	replaced = 0
	for path in sorted(NPCS.glob("*.xml")):
		text = path.read_text(encoding="utf-8").replace("\r\n", "\n")

		def npc(match):
			nonlocal replaced
			npc_id = int(match.group(2))
			if npc_id not in drops:
				return match.group(0)
			replaced += 1
			body = _DROPLISTS.sub("", match.group(1))
			return body + render(drops[npc_id], items, skipped) + match.group(3)

		new_text = _NPC_BLOCK.sub(npc, text)
		if new_text != text:
			path.write_text(new_text, encoding="utf-8", newline="\n")
	print(f"drop lists replaced for {replaced} NPCs; skipped items missing in H5: {sorted(skipped)}")


if __name__ == "__main__":
	main()
