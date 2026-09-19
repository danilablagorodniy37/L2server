"""Reads the client's interface file (system/Interface.xdat) and says what is inside it.

The file is not encrypted: it is a flat list of widget records, each one a few length-prefixed
strings followed by its numbers. A string is one byte of length (the NUL counts) and then the
characters:

    14 "AttributeEnchantWnd\\0"

A record starts with the kind of widget (Window, Button, Texture, TextBox, ...), then its name,
and somewhere in it stands the window it belongs to. That is enough to answer the question this
tool exists for: which windows the interface has, how big each one is, and what a window is made of.

Nothing here writes to the file.

Usage:
    python tools/client/xdat.py                      the windows, biggest first
    python tools/client/xdat.py --window InventoryWnd    what one window is made of
    python tools/client/xdat.py --find talisman      every widget whose name matches
    python tools/client/xdat.py --systems            what the systems of the later chronicles take up
"""

import sys
from collections import defaultdict
from pathlib import Path

DEFAULT = Path("S:/Programming/L2client/HighFive/system/Interface.xdat")
PRINTABLE = set(range(0x20, 0x7F))

# the kinds of widget the client knows; a record starts with one of them
KINDS = {
	"Window", "Texture", "TextBox", "Button", "CheckBox", "EditBox", "ListCtrl", "ComboBox",
	"ItemWindow", "BarCtrl", "StatusIconCtrl", "Tab", "TreeCtrl", "RadarCtrl", "MinimapCtrl",
	"HtmlCtrl", "InvenWeightCtrl", "ChatWindow", "ShortCut", "Progress", "Slider", "NameCtrl",
	"MultiSellItemInfo", "DrawPanel", "FlashCtrl", "EffectViewportCtrl", "SkillTree", "Timer",
}

# the systems High Five brought and this server does not run
SYSTEMS = {
	"attributes": ("attribute", "elemental"),
	"vitality": ("vitality",),
	"territory war": ("territory",),
	"talisman, cloak, belt, bracelet": ("talisman", "cloak", "belt", "bracelet"),
	"item mall": ("primeshop", "productlist", "br_", "brgift"),
	"airship": ("airship",),
	"instance zones": ("instancezone", "kamaloka"),
}


def strings(blob):
	"""[(offset, text)] of every length-prefixed, NUL-terminated string in the file."""
	out = []
	pos = 0
	end = len(blob)
	while pos < end:
		length = blob[pos]
		if 2 <= length <= 200:
			chunk = blob[pos + 1:pos + 1 + length]
			if (len(chunk) == length) and (chunk[-1] == 0) and all(b in PRINTABLE for b in chunk[:-1]):
				out.append((pos, chunk[:-1].decode("ascii")))
				pos += 1 + length
				continue
		pos += 1
	return out


class Widget:
	"""One record of the file: a kind, a name, the window it belongs to and where it lives."""

	def __init__(self, offset, kind, name):
		self.offset = offset
		self.kind = kind
		self.name = name
		self.window = None
		self.size = 0
		self.texts = []

	def __repr__(self):
		return f"{self.kind} {self.name} in {self.window} at 0x{self.offset:x} ({self.size} bytes)"


def widgets(blob):
	"""Every widget record, in the order the file holds them."""
	tokens = strings(blob)
	out = []
	current = None
	for i, (offset, text) in enumerate(tokens):
		if (text in KINDS) and (i + 1 < len(tokens)):
			if current is not None:
				current.size = offset - current.offset
			current = Widget(offset, text, tokens[i + 1][1])
			out.append(current)
			continue
		if current is None:
			continue
		current.texts.append(text)
		# the window a widget belongs to is named inside its record
		if text.endswith("Wnd") and (current.window is None) and (text != current.name):
			current.window = text
	if (current is not None) and out:
		current.size = len(blob) - current.offset
	# a window record names itself
	for widget in out:
		if (widget.window is None) and widget.name.endswith("Wnd"):
			widget.window = widget.name
	return out


def by_window(items):
	"""window name -> the widgets that belong to it."""
	out = defaultdict(list)
	for widget in items:
		out[widget.window or "(no window)"].append(widget)
	return out


def summary(items):
	"""[(window, widgets, bytes)] biggest first."""
	rows = []
	for window, group in by_window(items).items():
		rows.append((window, len(group), sum(w.size for w in group)))
	return sorted(rows, key=lambda row: -row[2])


def systems(items):
	"""What each system of the later chronicles takes up: [(name, widgets, bytes, windows)]."""
	rows = []
	for label, words in SYSTEMS.items():
		hit = [w for w in items if any(word in (w.name + " " + (w.window or "") + " " + " ".join(w.texts)).lower() for word in words)]
		windows = sorted({w.window for w in hit if w.window})
		rows.append((label, len(hit), sum(w.size for w in hit), windows))
	return rows


def main():
	# a value that follows --window or --find is not the file to read
	args = []
	skip = False
	for arg in sys.argv[1:]:
		if skip:
			skip = False
			continue
		if arg in ("--window", "--find"):
			skip = True
			continue
		if not arg.startswith("--"):
			args.append(arg)
	path = Path(args[0]) if args else DEFAULT
	blob = path.read_bytes()
	items = widgets(blob)
	print(f"{path}: {len(blob)} bytes, {len(items)} widgets in {len(by_window(items))} windows\n")

	if "--systems" in sys.argv:
		print(f"{'system':34} {'widgets':>8} {'bytes':>9}  windows")
		for label, count, size, windows in systems(items):
			print(f"{label:34} {count:8} {size:9}  {', '.join(windows[:4])}{' ...' if len(windows) > 4 else ''}")
		return

	if "--window" in sys.argv:
		wanted = sys.argv[sys.argv.index("--window") + 1].lower()
		for widget in items:
			if (widget.window or "").lower() == wanted:
				print(f"  0x{widget.offset:06x} {widget.size:6} {widget.kind:16} {widget.name}")
		return

	if "--find" in sys.argv:
		wanted = sys.argv[sys.argv.index("--find") + 1].lower()
		for widget in items:
			if wanted in widget.name.lower():
				print(f"  0x{widget.offset:06x} {widget.size:6} {widget.kind:16} {widget.name:40} {widget.window}")
		return

	print(f"{'window':34} {'widgets':>8} {'bytes':>9}")
	for window, count, size in summary(items)[:40]:
		print(f"{window:34} {count:8} {size:9}")


if __name__ == "__main__":
	main()
