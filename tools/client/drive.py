"""Starts the client, looks at it and presses its keys - so a change to the interface is seen, not guessed.

Everything goes through the Windows API (ctypes) and PIL; nothing else needs installing.

    python tools/client/drive.py start                  starts L2.exe and waits for its window
    python tools/client/drive.py shot <file.png>        a picture of the client window
    python tools/client/drive.py type <text>            types into the client (ASCII)
    python tools/client/drive.py cmd <//command ...>    sends a chat line, e.g. a GM command (//admin)
    python tools/client/drive.py key <name> [times]     enter, tab, esc, up, down, left, right, f1..f12, alt+x...
    python tools/client/drive.py click <x> <y> [right]  a click at a point of the client area
    python tools/client/drive.py dblclick <x> <y>       a double click (uses an item, equips gear)
    python tools/client/drive.py login <account> <password> [shots dir]   start, log in, pick the server,
                                                        enter the world with the last character (the database
                                                        of the server says when it is in)
    python tools/client/drive.py alive                  is the client still running
    python tools/client/drive.py stop                   closes the client

The client of this build lives in S:/Programming/L2client/HighFive/system and runs windowed
(Option.ini StartupFullScreen=False), so its window can be captured while other windows stay usable.
"""

import ctypes
import ctypes.wintypes as wt
import subprocess
import sys
import time
from pathlib import Path

CLIENT = Path("S:/Programming/L2client/HighFive/system/L2.exe")
MYSQL = Path("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe")
user32 = ctypes.WinDLL("user32", use_last_error=True)
kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
user32.SetProcessDPIAware()

WNDENUMPROC = ctypes.WINFUNCTYPE(wt.BOOL, wt.HWND, wt.LPARAM)
INPUT_MOUSE, INPUT_KEYBOARD = 0, 1
KEYEVENTF_KEYUP, KEYEVENTF_UNICODE, KEYEVENTF_SCANCODE = 0x2, 0x4, 0x8
MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP = 0x2, 0x4
MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP = 0x8, 0x10


class MOUSEINPUT(ctypes.Structure):
	_fields_ = [("dx", wt.LONG), ("dy", wt.LONG), ("mouseData", wt.DWORD), ("dwFlags", wt.DWORD),
		("time", wt.DWORD), ("dwExtraInfo", ctypes.c_size_t)]


class KEYBDINPUT(ctypes.Structure):
	_fields_ = [("wVk", wt.WORD), ("wScan", wt.WORD), ("dwFlags", wt.DWORD), ("time", wt.DWORD),
		("dwExtraInfo", ctypes.c_size_t)]


class _INPUTUNION(ctypes.Union):
	_fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT), ("pad", ctypes.c_byte * 32)]


class INPUT(ctypes.Structure):
	_fields_ = [("type", wt.DWORD), ("u", _INPUTUNION)]


# virtual key -> scan code; games that read DirectInput only see scan codes
VK = {"enter": 0x0D, "tab": 0x09, "esc": 0x1B, "space": 0x20, "back": 0x08, "up": 0x26, "down": 0x28,
	"left": 0x25, "right": 0x27, "alt": 0x12, "ctrl": 0x11, "shift": 0x10, "home": 0x24, "end": 0x23}
for _n in range(1, 13):
	VK[f"f{_n}"] = 0x6F + _n


def pids():
	out = subprocess.run(["tasklist", "/FI", "IMAGENAME eq L2.exe", "/FO", "CSV", "/NH"], capture_output=True,
		text=True).stdout
	return [int(line.split('","')[1]) for line in out.splitlines() if line.startswith('"L2.exe"')]


def window():
	"""The biggest visible top-level window of L2.exe, or None."""
	wanted = set(pids())
	found = []

	def callback(hwnd, _lparam):
		pid = wt.DWORD()
		user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
		if (pid.value in wanted) and user32.IsWindowVisible(hwnd):
			rect = wt.RECT()
			user32.GetClientRect(hwnd, ctypes.byref(rect))
			found.append((rect.right * rect.bottom, hwnd))
		return True

	user32.EnumWindows(WNDENUMPROC(callback), 0)
	return max(found)[1] if found else None


def client_rect(hwnd):
	"""(left, top, right, bottom) of the client area on the screen."""
	rect = wt.RECT()
	user32.GetClientRect(hwnd, ctypes.byref(rect))
	point = wt.POINT(0, 0)
	user32.ClientToScreen(hwnd, ctypes.byref(point))
	return point.x, point.y, point.x + rect.right, point.y + rect.bottom


def focus(hwnd):
	"""
	Brings the client to the front, or refuses to go on. Windows keeps the foreground for the person at the
	computer: when the switch is refused, every key and click would land in their window (an editor, a terminal) -
	so nothing is sent then.
	"""
	# a foreground switch from a background process needs the ALT trick
	user32.keybd_event(0x12, 0, 0, 0)
	user32.keybd_event(0x12, 0, KEYEVENTF_KEYUP, 0)
	user32.ShowWindow(hwnd, 9)
	user32.SetForegroundWindow(hwnd)
	time.sleep(0.3)
	if user32.GetForegroundWindow() != hwnd:
		raise RuntimeError("the client is not in front - someone is using the computer; no keys or clicks sent")


def check_front(hwnd):
	"""Every key and click checks again: the person at the computer may have taken the foreground meanwhile."""
	if user32.GetForegroundWindow() != hwnd:
		raise RuntimeError("the client lost the front; stopping before a key or click lands elsewhere")


def send(inputs):
	array = (INPUT * len(inputs))(*inputs)
	user32.SendInput(len(inputs), array, ctypes.sizeof(INPUT))


def key_input(vk, up=False):
	scan = user32.MapVirtualKeyW(vk, 0)
	flags = KEYEVENTF_SCANCODE | (KEYEVENTF_KEYUP if up else 0)
	if vk in (0x25, 0x26, 0x27, 0x28, 0x24, 0x23):
		flags |= 0x1  # extended key
	return INPUT(type=INPUT_KEYBOARD, u=_INPUTUNION(ki=KEYBDINPUT(wVk=vk, wScan=scan, dwFlags=flags)))


def press(name, times=1):
	check_front(window())
	parts = name.lower().split("+")
	mods = [VK[p] for p in parts[:-1]]
	last = parts[-1]
	vk = VK[last] if last in VK else user32.VkKeyScanW(ord(last)) & 0xFF
	for _ in range(times):
		send([key_input(m) for m in mods] + [key_input(vk)])
		time.sleep(0.05)
		send([key_input(vk, True)] + [key_input(m, True) for m in reversed(mods)])
		time.sleep(0.15)


def type_text(text):
	hwnd = window()
	for ch in text:
		check_front(hwnd)
		code = user32.VkKeyScanW(ord(ch))
		vk, shift = code & 0xFF, bool(code & 0x100)
		down = ([key_input(0x10)] if shift else []) + [key_input(vk)]
		up = [key_input(vk, True)] + ([key_input(0x10, True)] if shift else [])
		send(down)
		time.sleep(0.03)
		send(up)
		time.sleep(0.06)


def click(hwnd, x, y, right=False, times=1):
	check_front(hwnd)
	left, top, _r, _b = client_rect(hwnd)
	user32.SetCursorPos(left + x, top + y)
	time.sleep(0.15)
	down, up = (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP) if right else (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP)
	for _ in range(times):
		send([INPUT(type=INPUT_MOUSE, u=_INPUTUNION(mi=MOUSEINPUT(dwFlags=down)))])
		time.sleep(0.05)
		send([INPUT(type=INPUT_MOUSE, u=_INPUTUNION(mi=MOUSEINPUT(dwFlags=up)))])
		time.sleep(0.08)
	time.sleep(0.2)


def shot(hwnd, path):
	from PIL import ImageGrab
	focus(hwnd)
	image = ImageGrab.grab(bbox=client_rect(hwnd), all_screens=True)
	image.save(path)
	return image.size


def start(timeout=90):
	if pids():
		return window()
	subprocess.Popen([str(CLIENT)], cwd=str(CLIENT.parent))
	deadline = time.time() + timeout
	while time.time() < deadline:
		time.sleep(2)
		if not pids():
			raise RuntimeError("the client closed itself while starting")
		hwnd = window()
		if hwnd:
			left, top, right, bottom = client_rect(hwnd)
			if (right - left) > 640:
				return hwnd
	raise RuntimeError("no client window")


def stop():
	"""Closes every client and waits until they are gone."""
	for pid in pids():
		subprocess.run(["taskkill", "/PID", str(pid), "/F"], capture_output=True)
	deadline = time.time() + 30
	while pids():
		if time.time() > deadline:
			raise RuntimeError("the client does not close")
		time.sleep(1)


def wait(seconds):
	"""Sleeps while checking that the client is still alive."""
	deadline = time.time() + seconds
	while time.time() < deadline:
		time.sleep(1)
		if not pids():
			raise RuntimeError("the client closed itself")


def online(account):
	"""Whether a character of the account is in the game: the server marks it the moment one is chosen."""
	if not account.isalnum():
		raise ValueError("an account name is letters and digits")
	query = f"SELECT COUNT(*) FROM characters WHERE account_name='{account}' AND online=1"
	out = subprocess.run([str(MYSQL), "-uroot", "-proot", "-P3307", "-h127.0.0.1", "-N", "-e", query, "l2jdb_custom"],
		capture_output=True, text=True).stdout
	return out.strip().splitlines()[-1:] != ["0"] if out.strip() else False


def login(account, password, shots=None):
	"""
	From a fresh start into the world with the last character of the account.
	<p>
	The client remembers the server and the character and sometimes goes on into the world by itself. Every key
	after the licence is therefore sent only while the database says no character of the account is in the game:
	a blind Enter or click lands on the world instead (it once used a shortcut and walked a GM into a bot camp).
	"""
	shots = Path(shots) if shots else None
	# always from a fresh start: the clicks below aim at the login screens, and on a client that is already
	# in the world they land on whatever is open there (a GM teleport list, once)
	stop()
	hwnd = start()
	wait(20)
	focus(hwnd)

	def picture(name):
		if shots:
			shots.mkdir(parents=True, exist_ok=True)
			shot(hwnd, shots / f"{name}.png")

	# the login box and the licence page sit in the middle of the window
	left, top, right, bottom = client_rect(hwnd)
	cx, cy = (right - left) // 2, (bottom - top) // 2
	picture("1-login")
	click(hwnd, cx + 20, cy + 4)
	type_text(account)
	press("tab")
	type_text(password)
	press("enter")
	wait(6)
	picture("2-licence")
	if not online(account):
		click(hwnd, cx - 46, cy + 204)
	# the server list and the character list both take Enter
	for _ in range(8):
		wait(5)
		if online(account):
			break
		press("enter")
	else:
		raise RuntimeError("the client did not get into the game")
	wait(20)
	picture("3-world")
	return hwnd


def main():
	if len(sys.argv) < 2:
		print(__doc__)
		return
	command, args = sys.argv[1], sys.argv[2:]
	if command == "start":
		hwnd = start()
		print(f"client window {hwnd}, client area {client_rect(hwnd)}")
	elif command == "alive":
		print("running" if pids() else "not running")
	elif command == "stop":
		stop()
	elif command == "login":
		login(args[0], args[1], args[2] if len(args) > 2 else None)
		print("in the world" if pids() else "client closed")
	else:
		hwnd = window()
		if not hwnd:
			print("the client is not running")
			sys.exit(1)
		if command == "shot":
			print(shot(hwnd, args[0]))
		elif command == "type":
			focus(hwnd)
			type_text(" ".join(args))
		elif command == "cmd":
			# with OldChatting=True a line goes to the chat - unless a window holds the keyboard (an admin page with
			# an edit box, the GM inventory): close it first with a click on its X, Esc does not
			focus(hwnd)
			# the client takes the first slash of a line for itself: //admin needs a third one
			line = " ".join(args)
			type_text(("/" + line) if line.startswith("/") else line)
			press("enter")
		elif command == "key":
			focus(hwnd)
			press(args[0], int(args[1]) if len(args) > 1 else 1)
		elif command == "click":
			focus(hwnd)
			click(hwnd, int(args[0]), int(args[1]), len(args) > 2)
		elif command == "dblclick":
			focus(hwnd)
			click(hwnd, int(args[0]), int(args[1]), times=2)


if __name__ == "__main__":
	main()
