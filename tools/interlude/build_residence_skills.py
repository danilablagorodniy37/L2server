"""Residence clan skills as in Interlude, with the fortresses kept as in H5.

H5 gives the owning clan residence skills (data/skillTrees/pledgeSkillTree.xml, residenceSkill="true"):
castles 1-9 and fortresses 101-121 get skills 590-610, the Territory War territories 81-89 get
848-856. Interlude castles had no such skills and there is no Territory War here, while the
fortresses stay as in H5 (TASKS decision). This removes the castle and territory residenceId
entries and drops a residence skill left without any residence. Safe to run again.

Usage: python tools/interlude/build_residence_skills.py [--dry-run]
"""

import re
import sys

import datasets as ds

TREE = ds.GAME / "data" / "skillTrees" / "pledgeSkillTree.xml"
CASTLES = range(1, 10)
TERRITORIES = range(81, 90)
REMOVED_RESIDENCES = set(CASTLES) | set(TERRITORIES)
_SKILL = re.compile(r'([ \t]*)<skill [^>]*residenceSkill="true"[^>]*>\n(.*?)\1</skill>\n', re.S)
_RESIDENCE = re.compile(r"[ \t]*<residenceId>(\d+)</residenceId>\n")


def without_residences(text):
	"""(tree text without castle and territory residence skills, entries removed, skills removed)."""
	entries, skills = 0, 0

	def fix_skill(match):
		nonlocal entries, skills
		body = match.group(2)
		kept = _RESIDENCE.sub(lambda r: "" if int(r.group(1)) in REMOVED_RESIDENCES else r.group(0), body)
		entries += len(_RESIDENCE.findall(body)) - len(_RESIDENCE.findall(kept))
		if not _RESIDENCE.search(kept):
			skills += 1
			return ""
		return match.group(0).replace(body, kept)

	return _SKILL.sub(fix_skill, text), entries, skills


def main():
	dry_run = "--dry-run" in sys.argv
	raw = TREE.read_bytes()
	newline = "\r\n" if b"\r\n" in raw else "\n"
	text, entries, skills = without_residences(raw.decode("utf-8").replace("\r\n", "\n"))
	print(f"{'would remove' if dry_run else 'removed'} {entries} castle/territory residence entries, {skills} skills left without a residence")
	if not dry_run and entries:
		TREE.write_bytes(text.replace("\n", newline).encode("utf-8"))


if __name__ == "__main__":
	main()
