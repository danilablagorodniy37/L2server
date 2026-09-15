"""Interlude skill values: copies numbers from aCis skills into the H5 skill files.

H5 keeps its own skill structure (effect handlers, conditions, targets); only
values with an unambiguous counterpart are replaced, per level:
- sets: mpConsume -> mpConsume2, mpInitialConsume -> mpConsume1, hpConsume,
  reuseDelay, hitTime, coolTime, castRange, effectRange
- power of damage/heal skills -> the single power="..." of the H5 effects
- effect time -> abnormalTime (when all aCis effects share one time)
- stat modifiers (mul/add/sub/div/basemul by stat) that occur exactly once
  on both sides
When H5 has more levels than aCis, the extra H5 levels keep their values.
Skills with fewer H5 levels than aCis, and Kamael-only skills, are untouched.
Idempotent.

Usage: python tools/interlude/build_skill_stats.py [--dry-run]
"""

import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter

import datasets as ds

H5_SKILLS = ds.GAME / "data" / "stats" / "skills"
SET_MAP = {
	"mpConsume": "mpConsume2",
	"mpInitialConsume": "mpConsume1",
	"hpConsume": "hpConsume",
	"reuseDelay": "reuseDelay",
	"hitTime": "hitTime",
	"coolTime": "coolTime",
	"castRange": "castRange",
	"effectRange": "effectRange",
}
STAT_OPS = ("mul", "add", "sub", "div", "basemul")
# aCis skill types whose "power" is damage or healing (for others it is a land rate).
POWER_TYPES = {"PDAM", "MDAM", "BLOW", "CHARGEDAM", "HEAL", "HEAL_PERCENT", "DRAIN", "MANADAM", "MANAHEAL", "MANAHEAL_PERCENT", "COMBATPOINTHEAL", "DEATHLINK", "FATAL", "CPDAMPERCENT"}
PREFIX = "#il_"

_SKILL_BLOCK = re.compile(r'\t<skill id="(\d+)" levels="(\d+)"[^>]*>.*?\n\t</skill>', re.S)
_TABLE = re.compile(r'<table name="(#[^"]+)">([^<]*)</table>')


def per_level(value, tables, levels):
	"""Resolves a scalar or #table value to one string per level, None if unusable."""
	if value is None:
		return None
	value = value.strip()
	if value.startswith("#"):
		values = tables.get(value)
		return values[:levels] if values is not None and len(values) >= levels else None
	return [value] * levels


def acis_skills():
	result = {}
	for f in ds._xml_files(ds.ACIS / "skills"):
		for skill in ET.parse(f).getroot().findall("skill"):
			levels = int(skill.get("levels"))
			tables = {t.get("name"): t.text.split() for t in skill.findall("table")}
			sets = {s.get("name"): per_level(s.get("val"), tables, levels) for s in skill.findall("set")}
			effects = [e for e in skill.findall("for/effect")]
			times = {e.get("time") for e in effects if e.get("time") is not None}
			ops = Counter()
			op_values = {}
			for for_node in skill.findall("for"):
				for node in for_node.iter():
					if node.tag in STAT_OPS and node.get("stat"):
						key = (node.tag, node.get("stat"))
						ops[key] += 1
						op_values[key] = per_level(node.get("val"), tables, levels)
			result[int(skill.get("id"))] = {
				"levels": levels,
				"sets": sets,
				"type": (sets.get("skillType") or [None])[0],
				"time": per_level(times.pop(), tables, levels) if len(times) == 1 and len(effects) else None,
				"ops": {k: v for k, v in op_values.items() if ops[k] == 1 and v is not None},
			}
	return result


class Block:
	"""Text of one H5 skill, with helpers to rewrite per-level values."""

	def __init__(self, text, levels):
		self.text = text
		self.levels = levels

	def tables(self):
		return {m.group(1): m.group(2).split() for m in _TABLE.finditer(self.text)}

	def h5_values(self, value):
		return per_level(value, self.tables(), self.levels)

	def put_values(self, span, old_value, values):
		"""Replaces the value found at span (a scalar or a #table reference)."""
		old = self.h5_values(old_value)
		if old is None:
			return False
		merged = values + old[len(values):]
		if merged == old:
			return False
		if len(set(merged)) == 1 and not old_value.startswith(PREFIX):
			new_ref = merged[0]
		else:
			name = old_value.strip("#") if old_value.startswith("#") else "value"
			new_ref = old_value if old_value.startswith(PREFIX) else PREFIX + name
		start, end = span
		self.text = self.text[:start] + new_ref + self.text[end:]
		if new_ref.startswith("#"):
			self.set_table(new_ref, merged)
		return True

	def set_table(self, name, values):
		line = f'<table name="{name}"> {" ".join(values)} </table>'
		existing = re.search(rf'<table name="{re.escape(name)}">[^<]*</table>', self.text)
		if existing:
			self.text = self.text[:existing.start()] + line + self.text[existing.end():]
		else:
			first_line_end = self.text.index("\n")
			self.text = self.text[:first_line_end] + "\n\t\t" + line + self.text[first_line_end:]

	def replace_set(self, name, values):
		m = re.search(rf'<set name="{name}" val="([^"]*)" />', self.text)
		if m is None:
			if all(v in ("0", "0.0") for v in values):
				return False
			ref = values[0] if len(set(values)) == 1 else PREFIX + name
			anchor = self.text.index("<set ")
			self.text = self.text[:anchor] + f'<set name="{name}" val="{ref}" />\n\t\t' + self.text[anchor:]
			if ref.startswith("#"):
				self.set_table(ref, values)
			return True
		return self.put_values(m.span(1), m.group(1), values)

	def effects_span(self):
		m = re.search(r"<effects>.*?</effects>", self.text, re.S)
		return (m.start(), m.end()) if m else (0, 0)

	def replace_unique_attr(self, pattern, values):
		start, end = self.effects_span()
		matches = list(re.finditer(pattern, self.text[start:end]))
		if len(matches) != 1:
			return False
		m = matches[0]
		return self.put_values((start + m.start(1), start + m.end(1)), m.group(1), values)


def main():
	dry_run = "--dry-run" in sys.argv
	acis = acis_skills()
	stats = Counter()
	for path in sorted(H5_SKILLS.glob("*.xml")):
		text = path.read_text(encoding="utf-8").replace("\r\n", "\n")

		def skill(match):
			skill_id, levels = int(match.group(1)), int(match.group(2))
			il = acis.get(skill_id)
			if il is None:
				stats["not in aCis"] += 1
				return match.group(0)
			if levels < il["levels"]:
				stats["fewer levels in H5"] += 1
				return match.group(0)
			block = Block(match.group(0), levels)
			changed = False
			for acis_name, h5_name in SET_MAP.items():
				values = il["sets"].get(acis_name)
				if values is not None and block.replace_set(h5_name, values):
					stats["set " + h5_name] += 1
					changed = True
			if il["type"] in POWER_TYPES and il["sets"].get("power") is not None and block.replace_unique_attr(r'\bpower="([^"]+)"', il["sets"]["power"]):
				stats["power"] += 1
				changed = True
			if il["time"] is not None and re.search(r'<set name="abnormalTime"', block.text) and block.replace_set("abnormalTime", il["time"]):
				stats["abnormalTime"] += 1
				changed = True
			for (tag, stat), values in il["ops"].items():
				if block.replace_unique_attr(rf'<{tag} stat="{stat}" val="([^"]+)"', values):
					stats["stat " + tag] += 1
					changed = True
			stats["skills changed" if changed else "skills already Interlude"] += 1
			return block.text

		new_text = _SKILL_BLOCK.sub(skill, text)
		if new_text != text and not dry_run:
			path.write_text(new_text, encoding="utf-8", newline="\n")
	for key, count in sorted(stats.items()):
		print(f"{key}: {count}")


if __name__ == "__main__":
	main()
