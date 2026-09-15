"""Prints the datapack consistency checks from checks.py. Changes nothing.

The same checks run as tests: python -m pytest (see tests/README.md).

Usage: python tools/interlude/validate.py [--db] [--all]
  --db   also compare the l2jdb_custom tables with game/sql
  --all  list every problem, not only the first ones
Exit code 1 when any check fails; problems listed in checks.KNOWN do not fail.
"""

import sys

import checks
import geo


def report(name, func, shown):
	problems = func()
	failing = checks.unexpected(name, problems)
	title = (func.__doc__ or name).strip().splitlines()[0]
	if not problems:
		print(f"[ OK ] {title}")
		return True
	print(f"[{'FAIL' if failing else 'KNOWN'}] {title}: {len(problems)}")
	for key, places in sorted(problems.items(), key=lambda kv: str(kv[0]))[:shown]:
		where = sorted(set(places))
		listed = where if shown > 8 else where[:4]
		reason = checks.KNOWN.get(name, {}).get(str(key))
		print(f"         {key}: {', '.join(listed)}{' ...' if len(where) > len(listed) else ''}{f'  (known: {reason})' if reason else ''}")
	if len(problems) > shown:
		print(f"         ... and {len(problems) - shown} more")
	return not failing


def main():
	shown = 10**9 if "--all" in sys.argv else 8
	selected = dict(checks.DATAPACK_CHECKS)
	if geo.available():
		selected.update(checks.GEO_CHECKS)
	else:
		print("[SKIP] geodata checks: no geodata in game/data/geodata or no JDK")
	if "--db" in sys.argv:
		selected.update(checks.DATABASE_CHECKS)
	failed = [name for name, func in selected.items() if not report(name, func, shown)]
	print(f"\n{len(failed)} check(s) failed" if failed else "\nall checks passed")
	sys.exit(1 if failed else 0)


if __name__ == "__main__":
	main()
