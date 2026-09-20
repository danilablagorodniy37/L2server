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
    python tools/client/xdat.py --check              what the file says about its own shape
    python tools/client/xdat.py --dump --json out.json   everything the reader sees, for a report
    python tools/client/xdat.py --cut Wnd1,Wnd2 --out new.xdat    a copy with those windows emptied
    python tools/client/xdat.py --cut-systems --out new.xdat      a copy without the systems above

The cut itself is xdat_layout's work: the kinds of record, which kind holds the others and where it
keeps their number are read out of the file, not guessed, and nothing is written unless every window
in the file counts its children right and the file that comes out reads back the same way.
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


def structure(blob):
	"""What the file says about its own shape (xdat_layout), for the report and for the JSON."""
	import xdat_layout

	layout = xdat_layout.Layout.of(blob)
	right, total = layout.agreement()
	return {
		"kinds": sorted(layout.kinds),
		"holder": layout.container,
		"count_at": layout.spot,
		"count_takes_the_holder_in": bool(layout.bias),
		"records": len(layout.records),
		"windows": total,
		"windows_counting_right": right,
		"understood": layout.understood(),
		"counts_wrong": [{"window": name, "said": said, "found": found} for name, said, found in layout.wrong()],
		"kinds_with_several_shapes": layout.loose_kinds(),
		"values_pointing_at_records": layout.offset_references(),
		"header_counters": [{"at": at, "counts": what} for at, what in layout.header_counters()],
	}


def dump(blob, items=None):
	"""Everything the reader knows about the file, as plain data: for a report or for JSON."""
	items = items if items is not None else widgets(blob)
	return {
		"structure": structure(blob),
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
	A copy of the file with the named windows emptied, or the reason it cannot be done.
	<p>
	The work is xdat_layout's: it reads the kinds, the holder and the place of the count out of the
	file, empties the windows in the tree and writes it back, then reads the result again and checks
	that every window still holds what it should. Nothing is written when that check does not pass.
	@param blob the file as it is
	@param names the windows to empty, by name
	@param force empty what can be emptied although the file as a whole does not add up
	@return (the new file, how many widgets went, how many bytes went, [what was left alone and why])
	"""
	import xdat_layout

	return xdat_layout.cut(blob, names, force=force)


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
		import xdat_layout

		head = header(blob, items)
		print(f"  header: {len(head)} bytes before the first record  {head[:32].hex(' ')}")
		layout = xdat_layout.Layout.of(blob)
		right, total = layout.agreement()
		print(f"\n  read out of the file itself:")
		print(f"    {len(layout.kinds)} kinds of widget, {len(layout.records)} records")
		print(f"    the holder is {layout.container}, its count of children stands at {layout.spot}"
			f"{' and takes the holder in' if layout.bias else ''}")
		print(f"    {right} of {total} windows count right"
			+ ("  - the file is accounted for" if layout.understood() else "  - the rest is not understood yet"))
		for name, said, seen in layout.wrong()[:20]:
			print(f"      {name:34} says {said:6}, the file has {seen:6}")
		loose = layout.loose_kinds()
		if loose:
			print(f"    kinds whose records are not all alike, which is usual: {', '.join(loose[:10])}")
		hits = layout.offset_references()
		print(f"    values that point at the start of a record: {hits}"
			+ ("  - records cannot be moved" if hits > (len(layout.records) // 5) else ""))
		counters = layout.header_counters()
		if counters:
			print(f"    the header counts the file: {', '.join(f'{what} at {at}' for at, what in counters)}")
		print(f"\n  kinds: {', '.join(sorted(layout.kinds)[:24])}{' ...' if len(layout.kinds) > 24 else ''}")
		print("\n  a cut runs when every window counts right; the tool reads the result back either way.")
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
		fixed, count, gone, notes = cut(blob, names, force="--force" in sys.argv)
		print(f"  {count} widgets, {gone} bytes out of {', '.join(names[:6])}"
			f"{f' and {len(names) - 6} more' if len(names) > 6 else ''}")
		for line in notes:
			print(f"  - {line}")
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
