"""Cleans the client's system/L2.ini of what the previous owner left in it.

The client of this server came from another assembly, and its settings still point there: an item
mall behind somebody's URL, that server's home page, an auto login with their account, and switches
of systems this server does not run. This puts our own values in and leaves everything else alone.

The file is a Lineage2Ver413 one; tools/client/l2dat.py reads and writes it, CRC and all. The
original is kept next to it as L2.ini.orig the first time this runs.

Usage: python tools/client/clean_ini.py [<client system folder>] [--dry-run]
"""

import sys
from pathlib import Path

import l2dat

DEFAULT_SYSTEM = Path("S:/Programming/L2client/HighFive/system")

# key -> what it should say, per section. A key that is not there is added to its section.
SETTINGS = {
	"URL": {
		# the item mall and the pages of the assembly this client came from
		"GamePointURL": "",
		"L2HomeURL": "",
		# our own server
		"ServerAddr": "127.0.0.1",
		# the replay manager is not part of this server; a second window stays on, a GM needs it
		"L2UseReplayManager": "false",
	},
	"AutoLogOn": {
		"IsL2AutoLogOn": "False",
		"L2ID": "",
		"L2Passwd": "",
	},
	# the item mall of the later chronicles: no such shop here
	"PrimeShop": {
		"UsePrimeShop": "false",
	},
}


def clean(text):
	"""The ini with our settings in it; returns (new text, [what changed])."""
	changed = []
	lines = text.split("\r\n")
	section = None
	seen = {name: set() for name in SETTINGS}
	out = []
	for line in lines:
		stripped = line.strip()
		if stripped.startswith("[") and stripped.endswith("]"):
			# before leaving a section, add the keys it was missing
			out += missing(section, seen, changed)
			section = stripped[1:-1]
		elif (section in SETTINGS) and ("=" in stripped) and not stripped.startswith(";"):
			key = stripped.split("=", 1)[0].strip()
			if key in SETTINGS[section]:
				seen[section].add(key)
				wanted = SETTINGS[section][key]
				if stripped.split("=", 1)[1].strip() != wanted:
					changed.append(f"{section}.{key}: {stripped.split('=', 1)[1].strip()} -> {wanted or '(empty)'}")
					line = f"{key}={wanted}"
		out.append(line)
	out += missing(section, seen, changed)
	return "\r\n".join(out), changed


def missing(section, seen, changed):
	"""The keys of a section that were not in the file."""
	if section not in SETTINGS:
		return []
	lines = []
	for key, value in SETTINGS[section].items():
		if key not in seen[section]:
			seen[section].add(key)
			changed.append(f"{section}.{key}: added as {value or '(empty)'}")
			lines.append(f"{key}={value}")
	return lines


def main():
	args = [a for a in sys.argv[1:] if not a.startswith("--")]
	system = Path(args[0]) if args else DEFAULT_SYSTEM
	dry_run = "--dry-run" in sys.argv
	path = system / "L2.ini"
	raw = path.read_bytes()
	data, key = l2dat.decode(path)
	text = data.decode("cp1251")
	fixed, changed = clean(text)
	print(f"{path} ({key} key), {len(changed)} setting(s) to change")
	for line in changed:
		print("  " + line)
	if dry_run or not changed:
		return
	backup = path.with_suffix(".ini.orig")
	if not backup.exists():
		backup.write_bytes(raw)
		print(f"the original is kept as {backup.name}")
	path.write_bytes(l2dat.encode_413(fixed.encode("cp1251")))
	# read it back, the way the client will
	again, _ = l2dat.decode(path)
	assert again.decode("cp1251") == fixed, "the file did not come back the way it went in"
	print("written and read back")


if __name__ == "__main__":
	main()
