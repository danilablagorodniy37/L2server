"""Database l2jdb_custom matches game/sql. After editing an SQL file reload its table:
mysql -uroot -proot -P3307 l2jdb_custom < game/sql/<file>.sql
"""

import pytest

import checks
from conftest import format_problems

pytestmark = pytest.mark.db


def test_tables_match_sql_files():
	problems = checks.database_tables()
	assert not problems, format_problems(problems)


def test_gm_trigger_gives_master_access():
	"""Characters created on the GM account get access level 8 (trigger gm_blago1)."""
	triggers = checks.mysql("SHOW TRIGGERS WHERE `Trigger` = 'gm_blago1'")
	if not triggers:
		pytest.skip("trigger gm_blago1 is not installed")
	levels = checks.mysql("SELECT DISTINCT accesslevel FROM characters WHERE account_name = 'blago1'")
	assert set(levels) <= {"8"}, f"blago1 characters have access levels {levels}"
