"""Spawn positions against the server geodata (tools/interlude/geo.py, ~2 s)."""

import pytest

import checks
import geo
from conftest import format_problems

pytestmark = pytest.mark.skipif(not geo.available(), reason="no geodata in game/data/geodata or no JDK")


def test_floor_heights_are_known():
	"""Sanity check of the helper: Black Lily stands on a floor at about -3180 (Giran)."""
	assert abs(geo.floors([(92544, 115232, -3100)])[0] - -3180) < 50


@pytest.mark.parametrize("name", checks.GEO_CHECKS)
def test_geo_check(name):
	problems = checks.unexpected(name, checks.GEO_CHECKS[name]())
	assert not problems, f"{checks.GEO_CHECKS[name].__doc__.strip()}\n{format_problems(problems)}\nFix: python tools/interlude/fix_floating_spawns.py"
