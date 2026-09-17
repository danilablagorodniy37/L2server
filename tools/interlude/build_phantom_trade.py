"""What the bots sell in their shops in Giran.

A player walking through Giran expects a row of private stores. The bots keep theirs stocked
with gear of Interlude: weapons and armour of every grade, priced from the item's own price with
a small markup, the way players do. The list is written to data/phantoms/trade.txt:

    <item id> <price> <name>

Only items this server allows (Interlude or Kamael) and only ones with a price of their own.

Usage: python tools/interlude/build_phantom_trade.py [--dry-run]
"""

import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

import datasets as ds
import kamael

OUT = ds.GAME / "data" / "phantoms" / "trade.txt"
MARKUP = 1.2
PER_GRADE = 12  # how many weapons and how many armour pieces of each grade the market offers
GRADES = ("NONE", "D", "C", "B", "A", "S")


def prices():
	"""item id -> price of the item template."""
	out = {}
	for f in ds._xml_files(ds.GAME / "data" / "stats" / "items"):
		for item in ET.parse(f).getroot().findall("item"):
			for s in item.findall("set"):
				if s.get("name") == "price":
					out[int(item.get("id"))] = int(s.get("val"))
	return out


def goods():
	"""[(item id, price, name)] of the gear the bots trade."""
	items = ds.h5_items()
	allowed = ds.interlude_item_ids() | kamael.item_ids(items)
	price_of = prices()
	by_kind = defaultdict(list)
	for item_id, item in items.items():
		if item_id not in allowed or item_id not in price_of or price_of[item_id] <= 0:
			continue
		if item["type"] not in ("Weapon", "Armor"):
			continue
		if item["quest_item"] or not item["name"] or item["name"].startswith("_"):
			continue
		# no event, PvP or rented copies: the market sells plain gear
		if any(mark in item["name"] for mark in ("Event", "PvP", "{", "(", "Rental")):
			continue
		by_kind[(item["grade"], item["type"])].append((item_id, int(price_of[item_id] * MARKUP), item["name"]))

	out = []
	for grade in GRADES:
		for kind in ("Weapon", "Armor"):
			found = sorted(by_kind.get((grade, kind), []), key=lambda good: good[1])
			# spread the choice over the price range instead of taking the cheapest twelve
			step = max(1, len(found) // PER_GRADE)
			out += found[::step][:PER_GRADE]
	return sorted(out)


def main():
	dry_run = "--dry-run" in sys.argv
	found = goods()
	print(f"{len(found)} items for the bot shops")
	lines = ["# What the bots sell in Giran, built by tools/interlude/build_phantom_trade.py.",
		"# <item id> <price> <name>"]
	lines += [f"{item_id} {price} {name}" for item_id, price, name in found]
	if not dry_run:
		OUT.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
	main()
