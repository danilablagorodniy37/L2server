"""Boot smoke test: login and game server start, the game server registers on the
login server and logs no warnings or errors. Takes about a minute.

Opt-in: python -m pytest --run-server tests/test_server_boot.py
The servers of this build must not be running (the test needs ports 2106, 7777, 9014).
"""

import re
import socket
import subprocess
import time

import pytest

import datasets as ds

pytestmark = pytest.mark.server

LOGIN = ds.ROOT / "login"
GAME = ds.GAME
PORTS = (2106, 7777, 9014)
BOOT_TIMEOUT = 600
LOADED = re.compile(r"Server \w+ loaded in \d+ seconds")


def port_open(port):
	try:
		with socket.create_connection(("127.0.0.1", port), timeout=1):
			return True
	except OSError:
		return False


def wait_for(condition, timeout, process):
	deadline = time.monotonic() + timeout
	while time.monotonic() < deadline:
		if condition():
			return True
		if process.poll() is not None:
			return False
		time.sleep(1)
	return False


def stop(process):
	if process and process.poll() is None:
		subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True)
		process.wait(timeout=30)


@pytest.fixture(scope="module")
def boot_log():
	busy = [p for p in PORTS if port_open(p)]
	if busy:
		pytest.skip(f"ports {busy} are in use: stop the running login/game server first")
	log = GAME / "logs" / "server.log"
	started = time.time()
	login = game = None
	try:
		login = subprocess.Popen(["java", "-Xms1g", "-Xmx4g", "-jar", "l2jlogin.jar"], cwd=LOGIN,
			stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
		assert wait_for(lambda: port_open(9014), 120, login), "login server did not open port 9014"
		game = subprocess.Popen(["java", "-Xms4g", "-Xmx12g", "-cp", "l2jserver.jar;libs/*", "com.l2jserver.gameserver.GameServer"],
			cwd=GAME, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

		def loaded():
			return log.exists() and log.stat().st_mtime >= started and LOADED.search(log.read_text(encoding="utf-8", errors="replace"))

		booted = wait_for(loaded, BOOT_TIMEOUT, game)
		text = log.read_text(encoding="utf-8", errors="replace") if log.exists() else ""
		assert booted, f"game server did not finish loading, log tail:\n{text[-3000:]}"
		error_log = GAME / "logs" / "error" / "error.log"
		yield text, error_log.read_text(encoding="utf-8", errors="replace") if error_log.exists() else ""
	finally:
		stop(game)
		stop(login)


def test_game_server_loads(boot_log):
	text, _ = boot_log
	assert LOADED.search(text)


def test_registers_on_login_server(boot_log):
	text, _ = boot_log
	assert "Registered on login as Server" in text


def test_no_warnings_or_errors(boot_log):
	text, error_log = boot_log
	bad = [line for line in text.splitlines() if re.match(r"\[(WARN|ERROR)\]", line)]
	assert not bad, "\n".join(bad[:30])
	assert not error_log.strip(), error_log[:3000]


def test_script_loaders_ran(boot_log):
	"""Script compile errors are logged as WARN/ERROR; here the loaders must also have finished."""
	text, _ = boot_log
	assert re.search(r"QuestLoader: Loaded \d+ quests", text)
	assert re.search(r"AILoader: Loaded \d+ AI scripts", text)
