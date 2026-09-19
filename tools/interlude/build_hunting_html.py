"""The GM page with the hunting grounds of the bots.

Typing coordinates into the chat is a chore and the client has no paste, so the admin teleport
menu gets a page of its own: one line per hunting ground, a click teleports the GM there.
The grounds come from data/phantoms/hunting.txt (tools/interlude/build_phantom_hunting.py).

Usage: python tools/interlude/build_hunting_html.py [--dry-run]
"""

import sys

import datasets as ds

GROUNDS = ds.GAME / "data" / "phantoms" / "hunting.txt"
CONFIG = ds.GAME / "config" / "phantoms.properties"
OUT = ds.GAME / "data" / "html" / "admin" / "teleports" / "HuntingGrounds.htm"


def grounds():
	"""[(x, y, z, min level, max level, monsters, name)] of the bot hunting grounds."""
	out = []
	for line in GROUNDS.read_text(encoding="utf-8").splitlines():
		line = line.strip()
		if not line or line.startswith("#"):
			continue
		x, y, z, low, high, monsters, name = line.split(None, 6)
		out.append((int(x), int(y), int(z), int(low), int(high), int(monsters), name))
	return out


def camp():
	"""(x, y, z, name) of the place the standing parties of bots hold, from config/phantoms.properties."""
	config = {}
	for line in CONFIG.read_text(encoding="utf-8").splitlines():
		line = line.strip()
		if line and not line.startswith("#") and ("=" in line):
			key, value = line.split("=", 1)
			config[key.strip()] = value.strip()
	if int(config.get("Squads", "0")) < 1:
		return None
	return (int(config["SquadX"]), int(config["SquadY"]), int(config["SquadZ"]),
		config.get("SquadPlace", "the bot parties"), int(config.get("Squads", "0")), int(config.get("SquadSize", "9")))


def page(rows):
	"""The admin html: a title, a line per ground, a way back."""
	lines = ['<html><title>Bot Hunting Grounds</title><body>',
		'<center>',
		'<table width=256><tr>',
		'<td align=center><button value="Main" action="bypass -h admin_admin" width=40 height=21 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td>',
		'<td align=center width=176>Where the bots hunt</td>',
		'<td align=center><button value="Back" action="bypass -h admin_html teleports.htm" width=40 height=21 back="L2UI_CT1.Button_DF_Down" fore="L2UI_CT1.Button_DF"></td>',
		'</tr></table>',
		'</center>',
		'<br>']
	standing = camp()
	if standing:
		x, y, z, name, squads, size = standing
		lines.append(f'<a action="bypass -h admin_move_to {x} {y} {z}">{name}</a> '
			f'<font color="LEVEL">{squads} parties of {size}</font> camp here<br1>')
		lines.append('<br1>')
	for x, y, z, low, high, monsters, name in rows:
		lines.append(f'<a action="bypass -h admin_move_to {x} {y} {z}">{name}</a> '
			f'<font color="LEVEL">{low}-{high}</font>, {monsters} mobs<br1>')
	lines.append('</body></html>')
	return "\n".join(lines) + "\n"


def main():
	rows = sorted(grounds(), key=lambda row: row[3])
	print(f"{len(rows)} hunting grounds on the GM page")
	if "--dry-run" not in sys.argv:
		OUT.parent.mkdir(parents=True, exist_ok=True)
		OUT.write_text(page(rows), encoding="utf-8", newline="\n")


if __name__ == "__main__":
	main()
