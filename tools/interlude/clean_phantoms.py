"""Throws away the bot characters nobody plays any more.

Every time the make-up of the standing parties changes, the factory creates the characters it needs
and leaves the ones that no longer fit in the database. After a few rounds the bot account holds
thousands of characters while the server uses a few hundred, and their bags hold tens of thousands
of items nobody will ever see.

This keeps the characters the current phantoms.properties would use - the oldest ones by charId,
because that is the order the factory reads them in - and deletes the rest with everything that
hangs off them.

Run it with the game server stopped: it is writing to the world the server keeps in memory.

Usage:
    python tools/interlude/clean_phantoms.py --dry-run     what would go
    python tools/interlude/clean_phantoms.py               do it
"""

import subprocess
import sys

import checks

ACCOUNT = "phantoms"
# every table that points at a character, and the column it points with
TABLES = (
	("character_contacts", "charId"), ("character_friends", "charId"), ("character_hennas", "charId"),
	("character_instance_time", "charId"), ("character_item_reuse_save", "charId"), ("character_macroses", "charId"),
	("character_offline_trade", "charId"), ("character_offline_trade_items", "charId"),
	("character_premium_items", "charId"), ("character_quest_global_data", "charId"), ("character_quests", "charId"),
	("character_raid_points", "charId"), ("character_recipebook", "charId"), ("character_recipeshoplist", "charId"),
	("character_reco_bonus", "charId"), ("character_shortcuts", "charId"), ("character_skills", "charId"),
	("character_skills_save", "charId"), ("character_subclasses", "charId"), ("character_tpbookmark", "charId"),
	("character_ui_actions", "charId"), ("character_ui_categories", "charId"), ("character_variables", "charId"),
	("items", "owner_id"), ("olympiad_nobles", "charId"), ("olympiad_nobles_eom", "charId"),
	("seven_signs", "charId"), ("heroes", "charId"), ("characters", "charId"),
)


def wanted():
	"""How many bot characters the settings ask for."""
	config = checks.properties("phantoms.properties")
	return int(config.get("Count", "0"))


def run(query):
	subprocess.run([checks.MYSQL, "--user=root", "--password=root", "--port=3307", "--host=localhost",
		"--protocol=TCP", "--batch", "--skip-column-names", "l2jdb_custom", "-e", query], check=True,
		capture_output=True, text=True)


def main():
	dry_run = "--dry-run" in sys.argv
	keep = wanted()
	total = int(checks.mysql(f"SELECT COUNT(*) FROM characters WHERE account_name = '{ACCOUNT}'")[0])
	doomed = total - keep
	if doomed <= 0:
		print(f"{total} bot characters, the settings ask for {keep}: nothing to throw away")
		return

	# the factory reads them oldest first, so the newest ones beyond the count are the spare ones
	ids = checks.mysql(f"SELECT charId FROM characters WHERE account_name = '{ACCOUNT}' ORDER BY charId LIMIT {doomed} OFFSET {keep}")
	if not ids:
		print("no spare characters found")
		return
	items = int(checks.mysql(f"SELECT COUNT(*) FROM items WHERE owner_id IN ({','.join(ids)})")[0])
	print(f"{total} bot characters, the settings ask for {keep}: {len(ids)} to go, with {items} items")
	if dry_run:
		print("dry run, nothing was deleted")
		return

	in_list = ",".join(ids)
	for table, column in TABLES:
		try:
			run(f"DELETE FROM {table} WHERE {column} IN ({in_list})")
		except subprocess.CalledProcessError as ex:
			print(f"  {table}: {ex.stderr.strip().splitlines()[-1] if ex.stderr else ex}")
	left = int(checks.mysql(f"SELECT COUNT(*) FROM characters WHERE account_name = '{ACCOUNT}'")[0])
	print(f"{left} bot characters left")


if __name__ == "__main__":
	main()
