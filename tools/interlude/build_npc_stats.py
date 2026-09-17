"""Monster and boss stats as in Interlude.

The two cores read an NPC template differently. aCis (Interlude) keeps a base value and
multiplies it in combat by the stat bonuses and the level: P. Atk. = base x STR x lvlMod,
M. Atk. = base x INT^2 x lvlMod^2, P. Def. = base x lvlMod, M. Def. = base x MEN x lvlMod,
crit = base x DEX x 10, attack and move speed x DEX, MP x MEN, regeneration x CON/MEN x lvlMod,
with lvlMod = (level + 89) / 100. The H5 core applies none of that to NPCs (only CON to HP) and
multiplies instead by the modifier passives 4408-4415 that H5 put into the templates.

So for every monster, raid boss and grand boss aCis defines, this writes into the H5 template
the values Interlude ends up with in combat, takes level, base stats, experience and SP from
aCis, and drops the modifier passives aCis does not give that NPC. HP stays a base value: both
cores multiply it by CON. Kamael monsters (absent from aCis) keep their H5 stats. Accuracy and
evasion are not read from H5 templates by the core and are left alone. Safe to run again.

Usage: python tools/interlude/build_npc_stats.py [--dry-run]
"""

import math
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter

import datasets as ds

NPCS = ds.GAME / "data" / "stats" / "npcs"
PORTED_TYPES = {"Monster", "RaidBoss", "GrandBoss", "FestivalMonster", "FeedableBeast", "Chest", "HalishaChest"}
MODIFIER_SKILLS = range(4408, 4416)  # HP, MP, P. Atk., M. Atk., P. Def., M. Def. modifiers, armor and weapon type


def _bonus(base, shift):
	return [math.floor(base ** (i - shift) * 100 + .5) / 100 for i in range(201)]


# Formulas.*_BONUS of aCis (the H5 statBonus.xml uses the same formulas)
STR, INT, DEX = _bonus(1.036, 34.845), _bonus(1.020, 31.375), _bonus(1.009, 19.360)
CON, MEN = _bonus(1.030, 27.632), _bonus(1.010, -0.060)


def number(value, digits=4):
	"""Compact decimal text: 12.5, 7, 0.3421."""
	text = f"{round(value, digits):.{digits}f}".rstrip("0").rstrip(".")
	return text if text not in ("", "-0") else "0"


def interlude_stats(s):
	"""What an aCis template (its <set> values) amounts to in combat, in H5 template terms."""
	level = int(s["level"])
	lvl_mod = (level + 89) / 100
	str_, int_, dex, con, men = (int(s[k]) for k in ("str", "int", "dex", "con", "men"))
	return {
		"level": level,
		"str": str_, "int": int_, "dex": dex, "wit": int(s["wit"]), "con": con, "men": men,
		"exp": float(s["exp"]), "sp": float(s["sp"]),
		"hp": float(s["hp"]),
		"hpRegen": float(s["hpRegen"]) * CON[con] * lvl_mod,
		"mp": float(s["mp"]) * MEN[men],
		"mpRegen": float(s["mpRegen"]) * MEN[men] * lvl_mod,
		"pAtk": float(s["pAtk"]) * STR[str_] * lvl_mod,
		"mAtk": float(s["mAtk"]) * INT[int_] ** 2 * lvl_mod ** 2,
		"pDef": float(s["pDef"]) * lvl_mod,
		"mDef": float(s["mDef"]) * MEN[men] * lvl_mod,
		"crit": round(float(s["crit"]) * DEX[dex] * 10),
		"atkSpd": round(float(s["atkSpd"]) * DEX[dex]),
		"walkSpd": float(s["walkSpd"]) * DEX[dex],
		"runSpd": float(s["runSpd"]) * DEX[dex],
	}


def acis_monsters():
	"""npc id -> (Interlude stats, {skill id: level} of the modifier passives aCis gives it)."""
	result = {}
	for f in ds._xml_files(ds.ACIS / "npcs"):
		for npc in ET.parse(f).getroot().findall("npc"):
			s = {e.get("name"): e.get("val") for e in npc.findall("set")}
			if s.get("type") not in PORTED_TYPES:
				continue
			modifiers = {int(sk.get("id")): int(sk.get("level")) for sk in npc.iter("skill") if int(sk.get("id")) in MODIFIER_SKILLS}
			result[int(npc.get("id"))] = (interlude_stats(s), modifiers)
	return result


def _set_attrs(tag, values):
	for name, value in values.items():
		tag, n = re.subn(rf'(\s{name}=")[^"]*(")', rf"\g<1>{value}\g<2>", tag, count=1)
		if n == 0:
			# H5 leaves out attributes it has no value for (a monster without experience)
			tag = re.sub(r"\s*(/?>)$", rf' {name}="{value}" \1', tag, count=1)
	return tag


def with_stats(block, stats, modifiers):
	"""An H5 <npc> block carrying the Interlude stats and modifier passives."""
	def tag(pattern, values):
		nonlocal block
		m = re.search(pattern, block)
		if m:
			block = block[:m.start()] + _set_attrs(m.group(0), values) + block[m.end():]
		return m is not None

	level = stats["level"]
	tag(r"<npc [^>]*>", {"level": level})
	tag(r"<acquire [^>]*/>", {"expRate": number(stats["exp"] / (level * level)), "sp": number(stats["sp"])})
	tag(r"<stats [^>]*>", {k: stats[k] for k in ("str", "int", "dex", "wit", "con", "men")})
	tag(r"<vitals [^>]*/>", {"hp": number(stats["hp"]), "hpRegen": number(stats["hpRegen"]), "mp": number(stats["mp"]), "mpRegen": number(stats["mpRegen"])})
	tag(r"<attack [^>]*/>", {"physical": number(stats["pAtk"]), "magical": number(stats["mAtk"]), "critical": stats["crit"], "attackSpeed": stats["atkSpd"]})
	tag(r"<defence [^>]*/>", {"physical": number(stats["pDef"]), "magical": number(stats["mDef"])})
	tag(r"<walk [^>]*/>", {"ground": number(stats["walkSpd"], 2)})
	tag(r"<run [^>]*/>", {"ground": number(stats["runSpd"], 2)})

	def skill(m):
		sid = int(m.group(1))
		if sid not in MODIFIER_SKILLS:
			return m.group(0)
		if sid in modifiers:
			return re.sub(r'level="\d+"', f'level="{modifiers[sid]}"', m.group(0), count=1)
		return ""

	block = re.sub(r'[ \t]*<skill id="(\d+)" level="\d+" />[^\n]*\n', skill, block)

	# Modifiers aCis gives the NPC and H5 does not
	present = {int(v) for v in re.findall(r'<skill id="(\d+)" level="\d+" />', block)}
	missing = "".join(f'\t\t\t<skill id="{sid}" level="{lvl}" />\n' for sid, lvl in sorted(modifiers.items()) if sid not in present)
	if missing:
		if "<skillList>" in block:
			block = block.replace("\t\t<skillList>\n", "\t\t<skillList>\n" + missing, 1)
		else:
			block = re.sub(r"(\t\t</stats>\n(?:\t\t<status [^\n]*\n)?)", lambda m: m.group(1) + "\t\t<skillList>\n" + missing + "\t\t</skillList>\n", block, count=1)
	return block


def npc_blocks(text):
	"""(npc id, match) of every <npc> block in a templates file."""
	for m in re.finditer(r'^\t<npc id="(\d+)"[^\n]*\n.*?^\t</npc>\n', text, re.S | re.M):
		yield int(m.group(1)), m


def main():
	dry_run = "--dry-run" in sys.argv
	monsters = acis_monsters()
	counts = Counter()
	for path in sorted(NPCS.glob("*.xml")):
		raw = path.read_bytes()
		newline = "\r\n" if b"\r\n" in raw else "\n"
		text = raw.decode("utf-8").replace("\r\n", "\n")
		out, pos = [], 0
		for npc_id, m in npc_blocks(text):
			if npc_id not in monsters or "<stats " not in m.group(0):
				continue
			new = with_stats(m.group(0), *monsters[npc_id])
			if new != m.group(0):
				counts["changed"] += 1
			counts["ported"] += 1
			out.append(text[pos:m.start()])
			out.append(new)
			pos = m.end()
		out.append(text[pos:])
		new_text = "".join(out)
		if new_text != text:
			counts["files"] += 1
			if not dry_run:
				path.write_bytes(new_text.replace("\n", newline).encode("utf-8"))
	print(f"{counts['ported']} monsters and bosses of Interlude, {counts['changed']} {'would change' if dry_run else 'changed'} in {counts['files']} files")


if __name__ == "__main__":
	main()
