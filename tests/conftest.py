import pytest

import checks
import datasets as ds


def pytest_addoption(parser):
	parser.addoption("--run-server", action="store_true", help="start login and game server and check the boot log")


def pytest_collection_modifyitems(config, items):
	skips = {
		"acis": None if ds.ACIS.exists() else f"aCis datapack not found at {ds.ACIS}",
		"db": None if checks.mysql_available() else "MySQL is not reachable on localhost:3307",
		"server": None if config.getoption("--run-server") else "boot test is opt-in: --run-server",
	}
	for item in items:
		for marker, reason in skips.items():
			if reason and marker in item.keywords:
				item.add_marker(pytest.mark.skip(reason=reason))


def format_problems(problems, limit=15):
	lines = [f"{len(problems)} problem(s):"]
	for key, places in sorted(problems.items(), key=lambda kv: str(kv[0]))[:limit]:
		where = sorted(set(places))
		lines.append(f"  {key}: {', '.join(where[:4])}{' ...' if len(where) > 4 else ''}")
	if len(problems) > limit:
		lines.append(f"  ... and {len(problems) - limit} more (python tools/interlude/validate.py --all)")
	return "\n".join(lines)
