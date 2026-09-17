"""Hunting zones of the client: name, recommended level and the point the map shows.

The client keeps them in system/HuntingZone-e.dat. The bots use the list to pick a place to
hunt and to name it in chat, so it is written into the datapack as data/phantoms/zones.txt.

The client is not part of this repository: pass its system folder, or set L2_CLIENT.

Usage: python tools/client/hunting_zones.py [<client system folder>] [--write]
"""

import os
import struct
import sys
from pathlib import Path

import l2dat

DEFAULT_CLIENT = Path(os.environ.get("L2_CLIENT", r"S:\Programming\L2client\HighFive\system"))
OUT = Path(__file__).resolve().parents[2] / "game" / "data" / "phantoms" / "zones.txt"


def zones(path):
	"""[(id, level, x, y, z, name)] of the HuntingZone table."""
	data, _ = l2dat.decode(path)
	count = struct.unpack_from("<I", data, 0)[0]
	pos, out = 4, []
	for _ in range(count):
		zone_id, _unknown, level, _unknown2 = struct.unpack_from("<IIII", data, pos)
		pos += 16
		x, y, z = struct.unpack_from("<fff", data, pos)
		pos += 12
		_extra, pos = l2dat.read_string(data, pos)
		pos += 4
		name, pos = l2dat.read_string(data, pos)
		out.append((zone_id, level, round(x), round(y), round(z), name))
	return out


def main():
	args = [a for a in sys.argv[1:] if not a.startswith("--")]
	system = Path(args[0]) if args else DEFAULT_CLIENT
	found = [z for z in zones(system / "HuntingZone-e.dat") if z[5] and z[1] > 0 and (z[2] or z[3])]
	found.sort(key=lambda z: (z[1], z[5]))
	print(f"{len(found)} zones with a name, a level and a point")
	lines = ["# Hunting zones of the bots: name, recommended level and the point the client map shows.",
		"# Read from the client with tools/client/hunting_zones.py; do not edit by hand.",
		"# <level> <x> <y> <z> <name>"]
	lines += [f"{level} {x} {y} {z} {name}" for _id, level, x, y, z, name in found]
	if "--write" in sys.argv:
		OUT.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
		print("written to", OUT)
	else:
		print("\n".join(lines[3:13]), "\n...")


if __name__ == "__main__":
	main()
