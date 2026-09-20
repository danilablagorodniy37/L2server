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
    python tools/client/xdat.py --check              can the file be written to at all
    python tools/client/xdat.py --dump --json out.json   everything the reader sees, for a report
    python tools/client/xdat.py --cut Wnd1,Wnd2 --out new.xdat    a copy without those windows
    python tools/client/xdat.py --cut-systems --out new.xdat      a copy without the systems above

Read --check first. A cut only holds if every window in the file is made of the records the
reader found; where the counts disagree, something in the file is not understood yet and the
client closes itself at the start, whatever the cut looks like.
"""

import json
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
		# where the last string of this record ends: everything after it, up to the next record, is numbers
		self.text_end = offset

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
			# the kind and the name that follows it
			current.text_end = tokens[i + 1][0] + 2 + len(tokens[i + 1][1])
			out.append(current)
			continue
		if current is None:
			continue
		current.texts.append(text)
		current.text_end = offset + 2 + len(text)
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


def children(blob, items=None):
	"""
	window name -> (offset of the count, how many children it says it has, the child widgets).
	<p>
	A window is written as its own record, then a four byte count of the widgets inside it, then
	those widgets one after another. Cutting a widget without lowering that count leaves the client
	reading into the next window and it closes itself, so the count is what makes a cut safe.
	"""
	items = items if items is not None else widgets(blob)
	out = {}
	for window, group in by_window(items).items():
		# the record of the window itself is not one of its children
		group = [w for w in group if w.name != window]
		if (window == "(no window)") or not group:
			continue
		first = min(group, key=lambda w: w.offset)
		if first.offset < 4:
			continue
		said = int.from_bytes(blob[first.offset - 4:first.offset], "little")
		out[window] = (first.offset - 4, said, sorted(group, key=lambda w: w.offset))
	return out


def header(blob, items=None):
	"""The bytes before the first record: whatever the file says about itself."""
	items = items if items is not None else widgets(blob)
	return blob[:items[0].offset] if items else blob


def trailer(blob, items=None):
	"""The bytes after the last record, if the reader stopped before the end."""
	items = items if items is not None else widgets(blob)
	if not items:
		return b""
	last = max(items, key=lambda w: w.offset)
	return blob[last.offset + last.size:]


def shape(widget):
	"""
	(how many strings, how many bytes of numbers) - the shape of one record.
	<p>
	Records of one kind are written by the same code, so they should share a shape. Where they do
	not, the reader has run two records together or taken numbers for a string, and the file is
	not understood well enough to be written to.
	"""
	# texts already holds the name, so the kind is the only string not in it
	return (1 + len(widget.texts), max(0, (widget.offset + widget.size) - widget.text_end))


def shapes(items):
	"""kind -> [((strings, numbers), how many records), ...], the most common shape first."""
	out = defaultdict(lambda: defaultdict(int))
	for widget in items:
		out[widget.kind][shape(widget)] += 1
	return {kind: sorted(found.items(), key=lambda row: -row[1]) for kind, found in out.items()}


def mismatches(blob, items=None):
	"""
	[(window, what the file says, what the reader sees)] for every window whose count is wrong.
	<p>
	An empty list is the one thing that makes a cut safe: it means every window in the file is
	made of the records the reader found, with nothing between them it has not seen.
	"""
	items = items if items is not None else widgets(blob)
	rows = []
	for window, (_where, said, group) in children(blob, items).items():
		if said != len(group):
			rows.append((window, said, len(group)))
	return sorted(rows)


def dump(blob, items=None):
	"""Everything the reader knows about the file, as plain data: for a report or for JSON."""
	items = items if items is not None else widgets(blob)
	return {
		"bytes": len(blob),
		"widgets": len(items),
		"windows": len(by_window(items)),
		"header": header(blob, items).hex(),
		"trailer": trailer(blob, items)[:256].hex(),
		"trailer_bytes": len(trailer(blob, items)),
		"counts_wrong": [{"window": w, "said": said, "seen": seen} for w, said, seen in mismatches(blob, items)],
		"records": [{
			"offset": w.offset,
			"size": w.size,
			"kind": w.kind,
			"name": w.name,
			"window": w.window,
			"strings": w.texts,
			"numbers": (w.offset + w.size) - w.text_end,
			"raw": blob[w.text_end:w.offset + w.size].hex(),
		} for w in items],
	}


def cut(blob, names, force=False):
	"""
	Takes whole widget records out of the file and lowers the count of the window they sat in.
	<p>
	Only a window whose count matches what is really there can be cut: anywhere else the reader has
	miscounted, and changing the file on a guess is how a client stops starting.
	@param blob the file as it is
	@param names the windows to empty, by name
	@param force write even though the reader cannot account for every window
	@return (the new file, how many widgets went, how many bytes went, the windows left alone)
	"""
	wanted = {name.lower() for name in names}
	items = widgets(blob)
	counts = children(blob, items)
	# one window may count right while the file as a whole is misread; then the records that move
	# up into the hole are not the ones the client expects and it closes itself at the start
	wrong = mismatches(blob, items)
	if wrong and not force:
		named = "; ".join(f"{window} says {said}, the reader sees {seen}" for window, said, seen in wrong[:3])
		return blob, 0, 0, [f"the file is not understood, {len(wrong)} windows count wrong: {named}"
			f"{' and more' if len(wrong) > 3 else ''} - start with --check (--force writes anyway)"]
	out = bytearray(blob)
	gone = 0
	removed = 0
	skipped = []
	# from the back, so the offsets of what is still to come do not move
	for window in sorted(counts, key=lambda name: -counts[name][0]):
		if window.lower() not in wanted:
			continue
		where, said, group = counts[window]
		if said != len(group):
			skipped.append(f"{window}: the file says {said} widgets, the reader sees {len(group)}")
			continue
		start = group[0].offset
		end = group[-1].offset + group[-1].size
		del out[start:end]
		out[where:where + 4] = (0).to_bytes(4, "little")
		gone += end - start
		removed += len(group)
	return bytes(out), removed, gone, skipped


def main():
	# a value that follows an option is not the file to read
	args = []
	skip = False
	for arg in sys.argv[1:]:
		if skip:
			skip = False
			continue
		if arg in ("--window", "--find", "--cut", "--out", "--json"):
			skip = True
			continue
		if not arg.startswith("--"):
			args.append(arg)
	path = Path(args[0]) if args else DEFAULT
	blob = path.read_bytes()
	items = widgets(blob)
	print(f"{path}: {len(blob)} bytes, {len(items)} widgets in {len(by_window(items))} windows\n")

	if "--check" in sys.argv:
		head = header(blob, items)
		rest = trailer(blob, items)
		print(f"  header: {len(head)} bytes before the first record  {head[:32].hex(' ')}")
		print(f"  trailer: {len(rest)} bytes after the last one  {rest[:32].hex(' ')}")
		wrong = mismatches(blob, items)
		print(f"\n  {len(wrong)} of {len(children(blob, items))} windows count wrong:")
		for window, said, seen in wrong[:20]:
			print(f"    {window:34} says {said:5}, the reader sees {seen:5}")
		print(f"\n  {'kind':22} {'records':>8}  shapes (strings, numbers) x how many")
		for kind, found in sorted(shapes(items).items(), key=lambda row: -sum(n for _s, n in row[1])):
			total = sum(number for _one, number in found)
			text = ", ".join(f"({one[0]},{one[1]}) x{number}" for one, number in found[:3])
			print(f"  {kind:22} {total:8}  {len(found):3} shapes: {text}")
		print("\n  a cut is safe only when no window counts wrong and every kind has one shape.")
		return

	if "--dump" in sys.argv:
		data = dump(blob, items)
		where = Path(sys.argv[sys.argv.index("--json") + 1]) if "--json" in sys.argv else None
		if where is not None:
			where.write_text(json.dumps(data, indent=1), encoding="utf-8")
			print(f"  written to {where}: {len(data['records'])} records")
		for record in data["records"][:40]:
			print(f"  0x{record['offset']:06x} {record['size']:6} {record['kind']:16} {record['name']:34} "
				f"{record['numbers']:5} numbers  {record['raw'][:32]}")
		if where is None:
			print(f"  ... {len(data['records'])} records in all; --json <file> writes them out")
		return

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

	if ("--cut" in sys.argv) or ("--cut-systems" in sys.argv):
		if "--cut-systems" in sys.argv:
			names = sorted({window for _label, _count, _size, found in systems(items) for window in found})
			print(f"  the windows of the systems this server does not run: {len(names)}")
		else:
			names = sys.argv[sys.argv.index("--cut") + 1].split(",")
		where = Path(sys.argv[sys.argv.index("--out") + 1]) if "--out" in sys.argv else None
		fixed, count, gone, skipped = cut(blob, names, force="--force" in sys.argv)
		print(f"  {count} widgets, {gone} bytes would go: {', '.join(names)}")
		for line in skipped:
			print(f"  left alone, {line}")
		if where is None:
			print("  no --out given, nothing written")
			return
		if count == 0:
			print("  nothing to write")
			return
		where.write_bytes(fixed)
		again = widgets(fixed)
		print(f"  written to {where}: {len(again)} widgets left in {len(by_window(again))} windows")
		print("  back up system/Interface.xdat first (tools\\client\\backup.bat) and start the client once.")
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
