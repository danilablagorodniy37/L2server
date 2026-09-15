"""Interlude spellbooks: skills need a book to learn, shops sell books again.

- classSkillTree.xml: for Interlude classes, level 1 of every skill listed in
  the aCis spellbooks.xml requires its book (as in aCis SpellbookData).
  Divine Inspiration already requires its books in the H5 data.
- buylists: books sold by an NPC in the aCis buylists are added, with the
  aCis price, to the H5 buylist of that NPC sharing the most products with it.

Idempotent.

Usage: python tools/interlude/build_spellbooks.py
"""

import re
import xml.etree.ElementTree as ET
from collections import defaultdict

import datasets as ds

TREE = ds.GAME / "data" / "skillTrees" / "classSkillTree.xml"
BUYLISTS = ds.GAME / "data" / "buylists"

_TREE_BLOCK = re.compile(r'(<skillTree type="classSkillTree" classId="(\d+)"[^>]*>)(.*?)(\t</skillTree>)', re.S)
_SKILL_SELF_CLOSED = re.compile(r'^(\t\t)<skill (skillName="[^"]*" skillId="(\d+)" skillLvl="1" [^>]*?) />$', re.M)
_BUYLIST_ITEM = re.compile(r'<item id="(\d+)"')


def spellbooks():
	root = ET.parse(ds.ACIS / "spellbooks.xml").getroot()
	return {int(b.get("skillId")): int(b.get("itemId")) for b in root.findall("book")}


def add_tree_books(books, items):
	interlude_classes = set(ds.acis_class_skills())
	text = TREE.read_text(encoding="utf-8").replace("\r\n", "\n")
	added = [0]

	def block(match):
		if int(match.group(2)) not in interlude_classes:
			return match.group(0)

		def skill(m):
			book = books.get(int(m.group(3)))
			if book is None or book not in items:
				return m.group(0)
			added[0] += 1
			name = items[book]["name"]
			return f'{m.group(1)}<skill {m.group(2)}>\n{m.group(1)}\t<item id="{book}" count="1" /> <!-- {name} -->\n{m.group(1)}</skill>'

		return match.group(1) + _SKILL_SELF_CLOSED.sub(skill, match.group(3)) + match.group(4)

	TREE.write_text(_TREE_BLOCK.sub(block, text), encoding="utf-8", newline="\n")
	print(f"skill trees: {added[0]} skills now require a spellbook")


def h5_buylists_by_npc():
	by_npc = defaultdict(list)
	for path in BUYLISTS.glob("*.xml"):
		text = path.read_text(encoding="utf-8")
		npcs = {int(v) for v in re.findall(r"<npc>(\d+)</npc>", text)} or {int(path.stem) // 100}
		for npc in npcs:
			by_npc[npc].append(path)
	return by_npc


def add_shop_books(books, items):
	book_ids = set(books.values()) | {8618, 8619, 8620, 8621}
	by_npc = h5_buylists_by_npc()
	added, no_shop = 0, set()
	for buylist in ET.parse(ds.ACIS / "buyLists.xml").getroot().findall("buyList"):
		products = {int(p.get("id")): p.get("price") for p in buylist.findall("product")}
		sold_books = {i: price for i, price in products.items() if i in book_ids and i in items}
		if not sold_books:
			continue
		npc = int(buylist.get("npcId"))
		candidates = by_npc.get(npc)
		if not candidates:
			no_shop.add(npc)
			continue
		best = max(candidates, key=lambda p: len(set(map(int, _BUYLIST_ITEM.findall(p.read_text(encoding="utf-8")))) & set(products)))
		text = best.read_text(encoding="utf-8").replace("\r\n", "\n")
		present = set(map(int, _BUYLIST_ITEM.findall(text)))
		new_lines = [f'\t<item id="{i}" price="{price}" /> <!-- {items[i]["name"]} -->' for i, price in sorted(sold_books.items()) if i not in present]
		if new_lines:
			text = text.replace("</list>", "\n".join(new_lines) + "\n</list>")
			best.write_text(text, encoding="utf-8", newline="\n")
			added += len(new_lines)
	print(f"buylists: {added} spellbooks added" + (f", NPCs without an H5 buylist: {sorted(no_shop)}" if no_shop else ""))


def main():
	books = spellbooks()
	items = ds.h5_items()
	add_tree_books(books, items)
	add_shop_books(books, items)


if __name__ == "__main__":
	main()
