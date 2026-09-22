"""Crash test: a running login and game server survive broken and hostile clients.

Each test throws one kind of bad traffic at the servers (garbage, broken packet lengths,
half-sent packets, a flood of parallel connections, fuzzed packets behind the game crypt)
and then checks that the game server still answers the protocol handshake, that no socket
is left hanging and that the game server logged no exception.

Opt-in and needs the servers of this build running (it does not start them):
python -m pytest --run-crash tests/test_crash.py

Both servers ban an IP that connects too fast: the game server after 4 connections within
a second (IPv4Filter of l2j-commons, silent, lifted only after 5 minutes without attempts),
the login server after 15 (FastConnectionLimit in login/config/server.properties). So every
connection of this test comes from its own loopback address 127.1.x.y and 127.0.0.1, the
address of your client, is never banned by the test.
"""

import itertools
import random
import re
import socket
import struct
import subprocess
import threading
import time
from concurrent.futures import ThreadPoolExecutor

import pytest

import datasets as ds

pytestmark = pytest.mark.crash

HOST = "127.0.0.1"
LOGIN_PORT = 2106
GAME_PORT = 7777
PROTOCOL = 273
STATIC_KEY = bytes.fromhex("c8279301a16c3197")
SERVER_LOG = ds.GAME / "logs" / "server.log"
ERROR_LOG = ds.GAME / "logs" / "error" / "error.log"
_sources = (f"127.1.{i >> 8}.{i & 0xFF}" for i in itertools.count(random.randrange(1, 60000)))
_sources_lock = threading.Lock()
TRACE = re.compile(r"^\[ERROR\]|^\s+at [\w.$]+\(|Exception|OutOfMemory|StackOverflow")


def next_source():
	"""A loopback address nobody used recently, so the flood filters see a new client."""
	with _sources_lock:
		return next(_sources)


def port_open(port):
	try:
		with socket.create_connection((HOST, port), timeout=2, source_address=(next_source(), 0)):
			return True
	except OSError:
		return False


def connect(port, timeout=5, source=None):
	s = socket.create_connection((HOST, port), timeout=timeout, source_address=(source or next_source(), 0))
	s.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
	return s


def frame(body):
	return struct.pack("<H", len(body) + 2) + body


def recv_packet(s):
	"""One server packet (without the length header), or None if the server closed the socket."""
	head = recv_exact(s, 2)
	if head is None:
		return None
	size = struct.unpack("<H", head)[0]
	return recv_exact(s, size - 2)


def recv_exact(s, n):
	data = b""
	while len(data) < n:
		chunk = s.recv(n - len(data))
		if not chunk:
			return None
		data += chunk
	return data


def closed_by_server(s, timeout=10):
	"""True if the server drops the connection (reads until EOF or reset)."""
	s.settimeout(timeout)
	try:
		while s.recv(4096):
			pass
		return True
	except ConnectionError:
		return True
	except socket.timeout:
		return False


class GameCrypt:
	"""Client side of core GameCrypt: XOR chain with a 16-byte key whose bytes 8-11 count the traffic."""

	def __init__(self, key8):
		self.key = bytearray(key8 + STATIC_KEY)

	def encrypt(self, raw):
		out = bytearray(len(raw))
		temp = 0
		for i, b in enumerate(raw):
			temp = b ^ self.key[i & 15] ^ temp
			out[i] = temp
		counter = (struct.unpack_from("<I", self.key, 8)[0] + len(raw)) & 0xFFFFFFFF
		struct.pack_into("<I", self.key, 8, counter)
		return bytes(out)


def handshake(protocol=PROTOCOL):
	"""Sends ProtocolVersion; returns (socket, KeyPacket body)."""
	s = connect(GAME_PORT)
	s.sendall(frame(b"\x0e" + struct.pack("<i", protocol)))
	return s, recv_packet(s)


def assert_game_alive():
	s, key = handshake()
	with s:
		assert key is not None, "game server closed the connection instead of answering ProtocolVersion"
		assert key[0] == 0x2E and key[1] == 1, f"unexpected KeyPacket {key[:12].hex()}"


def assert_login_alive():
	with connect(LOGIN_PORT) as s:
		init = recv_packet(s)
		assert init, "login server did not send its Init packet"
		assert len(init) > 100, f"Init packet is only {len(init)} bytes"


def server_sockets(port):
	"""Server-side sockets on the port that are not LISTENING (count by state)."""
	out = subprocess.run(["netstat", "-ano", "-p", "TCP"], capture_output=True, text=True).stdout
	states = {}
	for line in out.splitlines():
		parts = line.split()
		if len(parts) >= 4 and parts[1].endswith(f":{port}") and parts[3] != "LISTENING":
			states[parts[3]] = states.get(parts[3], 0) + 1
	return states


def wait_sockets_released(port, timeout=30):
	deadline = time.monotonic() + timeout
	while time.monotonic() < deadline:
		left = {k: v for k, v in server_sockets(port).items() if k in ("ESTABLISHED", "CLOSE_WAIT")}
		if not left:
			return {}
		time.sleep(1)
	return left


@pytest.fixture(scope="module", autouse=True)
def servers():
	busy = {p: port_open(p) for p in (LOGIN_PORT, GAME_PORT)}
	if not all(busy.values()):
		pytest.skip(f"start the login and game server first (open ports: {busy})")
	baseline = {p: server_sockets(p).get("ESTABLISHED", 0) for p in (GAME_PORT,)}
	if baseline[GAME_PORT]:
		pytest.skip(f"{baseline[GAME_PORT]} client(s) are connected to the game server: run the crash test on an empty server")
	yield


@pytest.fixture(autouse=True)
def new_log_lines():
	"""Fails the test if the game server logged an error or an exception while it ran."""
	start = SERVER_LOG.stat().st_size if SERVER_LOG.exists() else 0
	error_start = ERROR_LOG.stat().st_size if ERROR_LOG.exists() else 0
	yield
	time.sleep(1)
	lines = []
	if SERVER_LOG.exists():
		with SERVER_LOG.open("rb") as f:
			f.seek(start)
			lines = f.read().decode("utf-8", "replace").splitlines()
	bad = [line for line in lines if TRACE.search(line)]
	if ERROR_LOG.exists() and ERROR_LOG.stat().st_size > error_start:
		with ERROR_LOG.open("rb") as f:
			f.seek(error_start)
			bad += [line for line in f.read().decode("utf-8", "replace").splitlines() if TRACE.search(line)][:20]
	assert not bad, "game server logged:\n" + "\n".join(bad[:30])


# --- game server --------------------------------------------------------------------------

def test_flood_filter_bans_only_the_flooding_address():
	"""IPv4Filter: the 5th connection within a second from one address is refused,
	other addresses keep getting in."""
	flooder = next_source()
	answers = []
	for _ in range(6):
		try:
			with connect(GAME_PORT, source=flooder) as s:
				s.sendall(frame(b"\x0e" + struct.pack("<i", PROTOCOL)))
				answers.append(recv_packet(s) is not None)
		except ConnectionError:
			answers.append(False)
	assert answers[:4] == [True] * 4, f"first connections were refused: {answers}"
	assert not answers[-1], f"flood from one address was not stopped: {answers}"
	assert_game_alive()


def test_handshake_answers_right_and_wrong_protocol():
	assert_game_alive()
	s, key = handshake(protocol=1)
	with s:
		assert key is not None and key[:2] == b"\x2e\x00", "wrong protocol must be refused with KeyPacket 0"
		assert closed_by_server(s), "connection with a wrong protocol stays open"


@pytest.mark.parametrize("payload", [
	pytest.param(b"", id="connect-and-close"),
	pytest.param(b"\x00\x00", id="length-0"),
	pytest.param(b"\x01\x00", id="length-1"),
	pytest.param(b"\x02\x00", id="length-2-empty-packet"),
	pytest.param(b"\xff\xff" + b"A" * 1000, id="length-65535-short-body"),
	pytest.param(b"\x07\x00\x0e\x11", id="truncated-protocol-version"),
	pytest.param(b"\x03\x00\x0e", id="protocol-version-without-int"),
	pytest.param(frame(b"\x2b" + b"\x41\x00" * 40), id="auth-login-string-without-end"),
	pytest.param(frame(b"\x2b"), id="auth-login-empty"),
	pytest.param(frame(b"\xd0\x36\x00"), id="extended-opcode-before-auth"),
	pytest.param(frame(b"\x0e" + struct.pack("<i", -2)), id="ping"),
	pytest.param(bytes(random.Random(1).getrandbits(8) for _ in range(4096)), id="random-4k"),
])
def test_game_survives_broken_packets(payload):
	with connect(GAME_PORT) as s:
		try:
			s.sendall(payload)
			s.shutdown(socket.SHUT_WR)
			closed_by_server(s, timeout=3)
		except ConnectionError:
			pass
	assert_game_alive()


def test_game_survives_many_packets_in_one_write():
	"""Hundreds of packets glued together, the way a buggy or hostile client sends them."""
	with connect(GAME_PORT) as s:
		try:
			s.sendall(frame(b"\x0e" + struct.pack("<i", PROTOCOL)) + frame(b"\x00") * 500)
			closed_by_server(s, timeout=5)
		except ConnectionError:
			pass
	assert_game_alive()


def test_game_survives_half_sent_packets():
	"""Clients that send half a packet and then hang must not block the others."""
	idle = []
	try:
		for i in range(30):
			s = connect(GAME_PORT)
			s.sendall(b"\x40\x00\x0e" if i % 2 else b"\x40")
			idle.append(s)
		assert_game_alive()
	finally:
		for s in idle:
			s.close()
	assert_game_alive()


def test_game_survives_reset_connections():
	"""Connections torn down with RST (SO_LINGER 0) right after the handshake."""
	for _ in range(50):
		s, key = handshake()
		assert key is not None
		s.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack("ii", 1, 0))
		s.close()
	assert_game_alive()


def test_game_survives_connection_flood():
	"""500 parallel clients: handshake, garbage, disconnect. Then no socket may stay open."""
	def client(seed):
		rnd = random.Random(seed)
		try:
			s, key = handshake()
			with s:
				if key is None:
					return "refused"
				s.sendall(bytes(rnd.getrandbits(8) for _ in range(rnd.randint(1, 300))))
			return "ok"
		except OSError as e:
			return type(e).__name__

	with ThreadPoolExecutor(max_workers=100) as pool:
		results = list(pool.map(client, range(500)))
	failed = {r: results.count(r) for r in set(results) if r != "ok"}
	assert results.count("ok") >= 450, f"too many clients failed: {failed}"
	assert_game_alive()
	left = wait_sockets_released(GAME_PORT)
	assert not left, f"server kept sockets after the clients left: {left}"


def test_game_survives_fuzzed_packets_behind_the_crypt():
	"""After the handshake every opcode 0x00-0xFF (and 0xD0 sub-opcodes) with random bodies,
	properly encrypted, so the packets reach the packet handler and the readers."""
	rnd = random.Random(273)
	cases = [bytes([op]) for op in range(256)] + [b"\xd0" + struct.pack("<H", sub) for sub in range(0x100)]
	for batch in range(0, len(cases), 32):
		s, key = handshake()
		with s:
			assert key is not None and key[1] == 1
			crypt = GameCrypt(key[2:10])
			try:
				for opcode in cases[batch:batch + 32]:
					body = opcode + bytes(rnd.getrandbits(8) for _ in range(rnd.choice((0, 1, 3, 17, 200))))
					s.sendall(frame(crypt.encrypt(body)))
			except ConnectionError:
				pass  # the server may drop a client that sends nonsense, that is fine
	assert_game_alive()
	left = wait_sockets_released(GAME_PORT)
	assert not left, f"server kept sockets after the clients left: {left}"


# --- login server -------------------------------------------------------------------------

@pytest.mark.parametrize("payload", [
	pytest.param(b"", id="connect-and-close"),
	pytest.param(b"\x00\x00", id="length-0"),
	pytest.param(b"\xff\xff" + b"B" * 500, id="length-65535-short-body"),
	pytest.param(frame(b"\x07" + bytes(40)), id="unaligned-body"),
	pytest.param(frame(bytes(range(256)) * 2), id="garbage-not-blowfish-aligned"),
	pytest.param(frame(bytes(random.Random(2).getrandbits(8) for _ in range(176))), id="random-auth-sized"),
])
def test_login_survives_broken_packets(payload):
	with connect(LOGIN_PORT) as s:
		try:
			assert recv_packet(s), "no Init packet"
			s.sendall(payload)
			closed_by_server(s, timeout=3)
		except ConnectionError:
			pass
	assert_login_alive()
	assert_game_alive()  # the game server keeps its link to the login server
