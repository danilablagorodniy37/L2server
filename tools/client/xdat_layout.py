"""Works out the structure of system/Interface.xdat from the file itself, so a cut can be trusted.

xdat.py reads the file loosely: it knows a record starts with the name of a kind of widget, and it
carries a list of the kinds it has seen. That list is a guess, and a guess is what the first cut
broke on - a kind that is not in it makes the reader run two records into one, so the bytes it
then takes out are not the bytes of a widget and the client closes itself at the start.

This module does not guess. Three things about the file are unknown - which strings are kinds,
which kind is the one that holds the others, and where in a holder's record its number of children
stands - and all three are read out of the file by trying them and keeping what fits:

    a file is understood when every holder's number of children is the number of records that
    really follow it

That number is the measure. A kind that does not exist adds records inside a holder and the numbers
come out too high; a kind that is missing swallows records and they come out too low. So the reader
starts with every string that repeats, drops the ones whose leaving makes more holders agree, and
reports how many agree in the end. At a hundred percent the file is accounted for byte by byte, the
records are where the client puts them, and taking whole records out and lowering the number that
counts them leaves a file of the same shape.

Nothing here writes to a file; it returns bytes and leaves the writing to the caller.
"""

from collections import Counter, defaultdict

from xdat import KINDS as KNOWN_KINDS, strings

# a string has to repeat at least this often before it is taken for the name of a kind
MIN_TIMES = 3
# how far into a record its number of children can stand: so many bytes from the end of the record,
# or so many past the last string in it. Four bytes at a time, as the numbers of a record are.
COUNT_REACH = 64
COUNT_SPOTS = tuple([f"end-{n}" for n in range(4, COUNT_REACH + 1, 4)]
	+ [f"after-strings+{n}" for n in range(0, COUNT_REACH + 1, 4)])
# a count may or may not take the holder itself in
COUNT_BIAS = (0, 1)
# a kind is followed by the name of its widget this often at least
NEAR_ENOUGH = 0.95
# and this seldom at most does a string come glued to the one before it, as a name does
GLUED_LEFT = 0.05
# how many strings that did not look like kinds are offered back to the reading
MOST_TRIED = 40
# how many record places are looked for when asking whether the file points at its own records
SAMPLED = 200


class Record:
	"""One record: the kind, the name, the strings in it and where it starts and ends."""

	__slots__ = ("kind", "name", "start", "end", "texts", "text_end")

	def __init__(self, kind, name, start, text_end):
		self.kind = kind
		self.name = name
		self.start = start
		self.end = start
		self.texts = [name]
		self.text_end = text_end

	def __repr__(self):
		return f"{self.kind} {self.name} at 0x{self.start:x}-0x{self.end:x}"


def read(blob, kinds, tokens=None):
	"""
	Every record of the file, in order, as the given set of kinds makes them out.
	<p>
	The string right after a kind is the name of the widget and is never read as a kind itself,
	so a widget called Button does not open a record of its own.
	"""
	tokens = tokens if tokens is not None else strings(blob)
	out = []
	i = 0
	while i < len(tokens):
		offset, text = tokens[i]
		if (text in kinds) and ((i + 1) < len(tokens)):
			name_offset, name = tokens[i + 1]
			out.append(Record(text, name, offset, name_offset + 2 + len(name)))
			i += 2
			continue
		if out:
			out[-1].texts.append(text)
			out[-1].text_end = offset + 2 + len(text)
		i += 1
	for one, following in zip(out, out[1:]):
		one.end = following.start
	if out:
		out[-1].end = len(blob)
	return out


def count_place(record, spot):
	"""Where in the file a holder's number of children would stand, for one way of placing it."""
	if spot.startswith("end-"):
		return record.end - int(spot[4:])
	if spot.startswith("after-strings+"):
		return record.text_end + int(spot[len("after-strings+"):])
	if spot == "after-strings":
		return record.text_end
	raise ValueError(spot)


def claimed(blob, record, spot):
	"""What a holder says its number of children is, or None when the place is outside the record."""
	at = count_place(record, spot)
	if (at < record.start) or ((at + 4) > record.end) or ((at + 4) > len(blob)):
		return None
	return int.from_bytes(blob[at:at + 4], "little")


def groups(records, container):
	"""[(holder, [the records that follow it])] - a holder holds everything up to the next holder."""
	out = []
	current = None
	held = []
	for record in records:
		if record.kind == container:
			if current is not None:
				out.append((current, held))
			current, held = record, []
			continue
		if current is not None:
			held.append(record)
	if current is not None:
		out.append((current, held))
	return out


def agreement(blob, records, container, spot, bias=0):
	"""(holders whose number of children is right, holders in all) for one reading of the file."""
	right = 0
	found = groups(records, container)
	for holder, held in found:
		if claimed(blob, holder, spot) == (len(held) + bias):
			right += 1
	return right, len(found)


def best_reading(blob, records, containers=None):
	"""
	(container kind, where the count stands, bias, holders that agree, holders in all).
	<p>
	Which kind holds the others, where it keeps their number and whether that number counts the
	holder itself are all read off the file: the reading that makes the most holders agree is the
	one the client wrote.
	"""
	kinds = Counter(record.kind for record in records)
	choices = containers if containers is not None else [kind for kind, number in kinds.items() if number >= 2]
	best = (None, None, 0, 0, 0)
	for container in choices:
		# the holders are found once and every place for the count is tried against them
		found = groups(records, container)
		if not found:
			continue
		held = [len(children) for _holder, children in found]
		for spot in COUNT_SPOTS:
			said = [claimed(blob, holder, spot) for holder, _children in found]
			for bias in COUNT_BIAS:
				right = sum(1 for one, many in zip(said, held) if one == (many + bias))
				if right and (quality(right, len(found)) > quality(best[3], best[4])):
					best = (container, spot, bias, right, len(found))
	return best


def quality(right, total):
	"""How good a reading is: first how many holders agree, then what share of them."""
	return (right, (right / total) if total else 0)


def adjacency(tokens):
	"""
	text -> (how often another string follows it with nothing in between, how often one comes before).
	<p>
	This is what tells a kind from a name without knowing either. A record is a kind, the name of
	the widget, sometimes more strings, then its numbers - so a kind always has a string glued to
	it on the right and never one on the left, because the numbers of the record before it are
	there. A name is the other way round: glued on the left, numbers or another record on the right.
	"""
	after = Counter()
	before = Counter()
	seen = Counter()
	for (offset, text), (next_offset, next_text) in zip(tokens, tokens[1:]):
		seen[text] += 1
		if next_offset == (offset + 2 + len(text)):
			after[text] += 1
			before[next_text] += 1
	if tokens:
		seen[tokens[-1][1]] += 1
	return {text: (after[text] / seen[text], before[text] / seen[text]) for text in seen}


def kind_like(near, text):
	"""How much a string behaves like a kind: a name glued after it and nothing glued before it."""
	after, before = near.get(text, (0, 1))
	return after - before


def candidates(tokens):
	"""
	The strings that could be kinds: they repeat, a name is glued to them, nothing is glued in front.
	"""
	times = Counter(text for _offset, text in tokens)
	near = adjacency(tokens)
	return {text for text, number in times.items()
		if (number >= MIN_TIMES) and (near.get(text, (0, 1))[0] >= NEAR_ENOUGH) and (near.get(text, (0, 1))[1] <= GLUED_LEFT)}


def discover(blob, tokens=None, passes=2):
	"""
	Reads the structure out of the file: (kinds, container, where the count stands, bias, right, all).
	<p>
	The strings that look like kinds are tried as kinds, then dropped one by one where dropping them
	makes more holders count right, then the ones left over are offered back one by one where adding
	them helps. What is left is the set the client itself wrote with, as far as the file can show it.
	<p>
	Which kind holds the others and where it keeps their number are looked for once, over the whole
	file, and then held still while the set of kinds is worked on; they are looked for again at the
	end, in case the set that came out reads better another way.
	"""
	tokens = tokens if tokens is not None else strings(blob)
	if not tokens:
		return set(), None, None, 0, 0, 0
	times = Counter(text for _offset, text in tokens)
	near = adjacency(tokens)
	kinds = candidates(tokens)
	rest = {text for text, number in times.items() if (number >= 2) and (text not in kinds)}
	if not kinds:
		kinds, rest = set(rest), set()

	container, spot, bias, right, total = best_reading(blob, read(blob, kinds, tokens))
	if container is None:
		# nothing counted right with those strings; try the kinds the tool already knows
		for fallback in (KNOWN_KINDS & set(times), set(rest) | kinds):
			if not fallback:
				continue
			container, spot, bias, right, total = best_reading(blob, read(blob, fallback, tokens))
			if container is not None:
				kinds, rest = set(fallback), {text for text, number in times.items() if (number >= 2) and (text not in fallback)}
				break
	if container is None:
		return kinds, None, None, 0, 0, 0

	def score(candidate):
		return agreement(blob, read(blob, candidate, tokens), container, spot, bias)

	# the least kind-like first when dropping, the most kind-like first when adding
	going = sorted(kinds, key=lambda text: (kind_like(near, text), times[text]))
	coming = sorted(rest, key=lambda text: (-kind_like(near, text), -times[text]))[:MOST_TRIED]
	for _ in range(passes):
		better = False
		for text in going:
			if text not in kinds:
				continue
			hits, all_of_them = score(kinds - {text})
			if quality(hits, all_of_them) > quality(right, total):
				kinds, right, total = kinds - {text}, hits, all_of_them
				better = True
		for text in coming:
			if text in kinds:
				continue
			hits, all_of_them = score(kinds | {text})
			if quality(hits, all_of_them) > quality(right, total):
				kinds, right, total = kinds | {text}, hits, all_of_them
				better = True
		if not better:
			break
	# where a holder is still short, the kind that is missing is inside it
	kinds, right, total = repair(blob, tokens, kinds, container, spot, bias, right, total)
	# the set that came out may read better with another holder or another place for the count
	again = best_reading(blob, read(blob, kinds, tokens))
	if (again[0] is not None) and (quality(again[3], again[4]) > quality(right, total)):
		return kinds, again[0], again[1], again[2], again[3], again[4]
	return kinds, container, spot, bias, right, total


def short_spans(blob, records, container, spot, bias):
	"""(from, to) of every holder that says it has more children than the reader found."""
	spans = []
	found = groups(records, container)
	for i, (holder, held) in enumerate(found):
		said = claimed(blob, holder, spot)
		if (said is None) or (said <= (len(held) + bias)):
			continue
		end = found[i + 1][0].start if (i + 1) < len(found) else len(blob)
		spans.append((holder.end, end))
	return spans


def suspects(blob, tokens, kinds, container, spot, bias):
	"""
	The strings that could be the kind the reader is missing, the most promising first.
	<p>
	A holder that says it holds more than the reader found is holding a record the reader walked
	past, and that record starts with a kind it does not know. So the strings to try are the ones
	standing inside such a holder with a name right after them.
	"""
	records = read(blob, kinds, tokens)
	spans = short_spans(blob, records, container, spot, bias)
	if not spans:
		return []
	found = Counter()
	for (offset, text), (next_offset, _next) in zip(tokens, tokens[1:]):
		if (text in kinds) or (next_offset != (offset + 2 + len(text))):
			continue
		if any(start <= offset < end for start, end in spans):
			found[text] += 1
	return [text for text, _times in found.most_common(MOST_TRIED)]


def repair(blob, tokens, kinds, container, spot, bias, right, total, rounds=6):
	"""
	Offers the reading the kinds it seems to be missing, where the file says a holder is short.
	<p>
	@return (kinds, holders that agree, holders in all) with every kind that helped taken in
	"""
	for _ in range(rounds):
		if (total > 0) and (right == total):
			break
		better = False
		for text in suspects(blob, tokens, kinds, container, spot, bias):
			hits, all_of_them = agreement(blob, read(blob, kinds | {text}, tokens), container, spot, bias)
			if quality(hits, all_of_them) > quality(right, total):
				kinds, right, total = kinds | {text}, hits, all_of_them
				better = True
		if not better:
			break
	return kinds, right, total


class Layout:
	"""The file as a structure: a header, records in order, holders that count their children."""

	def __init__(self, blob, kinds, container, spot, bias=0, records=None, tokens=None):
		self.blob = blob
		self.kinds = set(kinds)
		self.container = container
		self.spot = spot
		self.bias = bias
		self.records = records if records is not None else read(blob, self.kinds, tokens)
		self.groups = groups(self.records, container) if container else []

	@classmethod
	def of(cls, blob, tokens=None):
		"""The layout the file itself says it has."""
		tokens = tokens if tokens is not None else strings(blob)
		kinds, container, spot, bias, _right, _total = discover(blob, tokens)
		return cls(blob, kinds, container, spot, bias, tokens=tokens)

	@property
	def header(self):
		return self.blob[:self.records[0].start] if self.records else self.blob

	def agreement(self):
		if not self.container:
			return 0, 0
		return agreement(self.blob, self.records, self.container, self.spot, self.bias)

	def understood(self):
		"""True when every holder in the file counts its children right."""
		right, total = self.agreement()
		return (total > 0) and (right == total)

	def wrong(self):
		"""[(holder, what it says, what is there)] for the holders that do not add up."""
		rows = []
		for holder, held in self.groups:
			said = claimed(self.blob, holder, self.spot)
			if said != (len(held) + self.bias):
				rows.append((holder.name, said, len(held) + self.bias))
		return rows

	def offset_references(self):
		"""
		How many four byte values in the file point at the start of a record.
		<p>
		None is what a cut needs: a file that holds the places of its own records cannot have them
		moved, and everything after a hole moves. Above a few hundred records the places are looked
		for in a sample of them and the answer is scaled up, which is close enough for a threshold.
		"""
		starts = sorted(record.start for record in self.records if record.start >= 256)
		if not starts:
			return 0
		step = max(1, len(starts) // SAMPLED)
		sample = starts[::step]
		hits = sum(self.blob.count(start.to_bytes(4, "little")) for start in sample)
		return hits * step

	def header_counters(self):
		"""
		Places in the header that hold the number of records or the number of holders.
		<p>
		A value that happens to be the number of records is not always a count of them, so a place
		is only taken for one when it is the only place in the header holding that number.
		"""
		head = self.header
		found = defaultdict(list)
		for i in range(max(0, len(head) - 3)):
			value = int.from_bytes(head[i:i + 4], "little")
			if value == len(self.records):
				found["records"].append(i)
			elif value == len(self.groups):
				found["holders"].append(i)
		return [(places[0], what) for what, places in found.items() if len(places) == 1]

	def shapes(self):
		"""kind -> how many shapes (strings, numbers) its records come in; one means a fixed layout."""
		found = defaultdict(Counter)
		for record in self.records:
			found[record.kind][(len(record.texts) + 1, record.end - record.text_end)] += 1
		return {kind: counts for kind, counts in found.items()}

	def loose_kinds(self):
		"""Kinds whose records do not all have the same shape: the reader is unsure about those."""
		return sorted(kind for kind, counts in self.shapes().items() if len(counts) > 1)

	def rebuild(self, empty=()):
		"""
		The file again, with the named holders emptied of their children and their count set to zero.
		<p>
		Everything else is copied byte for byte, so a rebuild with nothing to empty gives the file back.
		"""
		wanted = {name.lower() for name in empty}
		# a holder whose count does not stand inside its own record is left alone; verify then says so
		emptied = {id(holder) for holder, _held in self.groups
			if (holder.name.lower() in wanted) and (claimed(self.blob, holder, self.spot) is not None)}
		dropped = {id(record) for holder, held in self.groups if id(holder) in emptied for record in held}
		out = bytearray(self.header)
		for record in self.records:
			if id(record) in dropped:
				continue
			raw = bytearray(self.blob[record.start:record.end])
			if id(record) in emptied:
				at = count_place(record, self.spot) - record.start
				raw[at:at + 4] = self.bias.to_bytes(4, "little")
			out += raw
		# a count of the records in the header is a count of fewer records now
		left = len(self.records) - len(dropped)
		for at, what in self.header_counters():
			if what == "records":
				out[at:at + 4] = left.to_bytes(4, "little")
		return bytes(out), len(dropped)

	def counting_right(self):
		"""The names of the holders whose number of children is the number that follows them."""
		return {holder.name for holder, held in self.groups
			if claimed(self.blob, holder, self.spot) == (len(held) + self.bias)}

	def verify(self, after, empty=()):
		"""
		Reads the cut file the same way and says what does not hold.
		<p>
		A window that did not add up before the cut is not held against the file afterwards - it was
		already like that, and the cut did not touch it. What must hold is that the holders are the
		same, that only the emptied ones lost anything, and that no window that counted right before
		counts wrong now.
		@param after the bytes the cut produced
		@param empty the holders that were emptied
		@return [] when nothing is out of place
		"""
		wanted = {name.lower() for name in empty}
		fresh = Layout(after, self.kinds, self.container, self.spot, self.bias)
		problems = []
		before = {holder.name: [record.name for record in held] for holder, held in self.groups}
		now = {holder.name: [record.name for record in held] for holder, held in fresh.groups}
		if set(before) != set(now):
			gone = sorted(set(before) - set(now))[:3]
			new = sorted(set(now) - set(before))[:3]
			problems.append(f"the holders are not the same any more: {len(before)} before, {len(now)} after"
				+ (f", missing {', '.join(gone)}" if gone else "") + (f", new {', '.join(new)}" if new else ""))
			return problems
		for name, held in before.items():
			expected = [] if name.lower() in wanted else held
			if now[name] != expected:
				problems.append(f"{name}: expected {len(expected)} widgets in it, the new file has {len(now[name])}")
		was_right = self.counting_right()
		for name, said, seen in fresh.wrong():
			if name in was_right:
				problems.append(f"{name}: counted right before the cut, now says {said} and has {seen}")
		return problems


def cut(blob, names, force=False):
	"""
	A copy of the file with the named windows emptied, or a reason why it cannot be done.
	<p>
	The window record itself stays where it is: its name is still in the file, nothing after it
	moves further than the widgets that were taken out, and the only number that changes is the one
	that counts them. A window is only emptied when the file's own count of its children is the
	number of records that really follow it; a window that does not add up is left alone and said
	so, because there the reader does not know where the widgets end. Whatever comes out is read
	back the same way before it is handed over, and that reading has to hold - force does not skip
	it, it only lets the rest of the file be less than fully understood.
	@param blob the file as it is
	@param names the windows to empty
	@param force empty what can be emptied although the file as a whole does not add up
	@return (the new file, widgets taken out, bytes taken out, [what was left alone and why])
	"""
	layout = Layout.of(blob)
	right, total = layout.agreement()
	if not total:
		return blob, 0, 0, ["nothing in the file reads as a window; --dump --json says what the reader sees"]
	notes = []
	if right != total:
		if not force:
			rows = layout.wrong()[:3]
			named = "; ".join(f"{name} says {said}, the file has {seen}" for name, said, seen in rows)
			return blob, 0, 0, [f"the file is not understood: {right} of {total} windows count right - {named}"
				" - --force empties the windows that do count right"]
		notes.append(f"{total - right} of {total} windows in the file do not add up; they are left alone")
	hits = layout.offset_references()
	if hits > (len(layout.records) // 5):
		return blob, 0, 0, [f"the file holds the places of its own records in {hits} spots; "
			"moving anything would point them at the wrong bytes"]

	known = {holder.name.lower(): holder.name for holder, _held in layout.groups}
	sound = layout.counting_right()
	targets = []
	for name in names:
		real = known.get(name.lower())
		if real is None:
			notes.append(f"no window called {name} in the file")
		elif real not in sound:
			notes.append(f"{real} does not add up itself, so it is left as it is")
		else:
			targets.append(real)
	if not targets:
		return blob, 0, 0, notes
	fixed, dropped = layout.rebuild(targets)
	problems = layout.verify(fixed, targets)
	if problems:
		return blob, 0, 0, problems
	return fixed, dropped, len(blob) - len(fixed), notes
