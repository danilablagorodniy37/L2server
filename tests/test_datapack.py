"""Datapack consistency: broken references and Interlude rules (tools/interlude/checks.py)."""

import pytest

import checks
from conftest import format_problems

pytestmark = pytest.mark.acis


@pytest.fixture(scope="module")
def results():
	"""Runs each check once; the checks share cached datapack loaders."""
	cache = {}

	def run(name):
		if name not in cache:
			cache[name] = checks.DATAPACK_CHECKS[name]()
		return cache[name]

	return run


@pytest.mark.parametrize("name", checks.DATAPACK_CHECKS)
def test_check(name, results):
	problems = checks.unexpected(name, results(name))
	assert not problems, f"{checks.DATAPACK_CHECKS[name].__doc__.strip()}\n{format_problems(problems)}"


@pytest.mark.parametrize("name,key", [(name, key) for name, known in checks.KNOWN.items() for key in known])
def test_known_problem_still_exists(name, key, results):
	"""A fixed known problem must be removed from checks.KNOWN, so it cannot come back silently."""
	assert key in {str(k) for k in results(name)}, f"{key} is fixed, remove it from checks.KNOWN['{name}']"
