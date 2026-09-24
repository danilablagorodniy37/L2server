"""Builds the interface of this server's client: High Five's layout without the systems Interlude never had.

The client keeps its untouched files next to the working ones (Interface.xdat.orig, Interface.u.orig);
this script always starts from those, applies the edits listed below and writes Interface.xdat, so
running it twice gives the same file and an edit is taken back by deleting it from the list.

The rules the edits follow come from trying them on the client (tools/client/drive.py):
  - a widget no script asks for by name can go; the interface sources inside Interface.u tell which
    ones are asked for (upackage.py reads them)
  - a widget a script does ask for stays in the file: the native code behind a handle does not check
    for a missing widget (an AddItem into a deleted item window is a General protection fault in
    NCItemWnd::ResizeScrollBar)
  - except a text box: SetText on a missing one does nothing, and a text box cannot be hidden - it draws
    its text past its own size and past its window, and ignores the alpha of its colour
  - any other such widget is hidden, not zeroed: a texture loses its picture, a window gets hidden=1,
    anything else size 0x0. Sizes are never zeroed on a widget others extend (extendsName) - eighteen equipment
    slots take their size from EquipItem_Underwear, and zeroing it emptied all of them
  - an item window with no item draws nothing; the frames of the equipment slots are a picture behind
    them (Inventory_DF_EquipSlot), so a slot the server never fills disappears with its frame

Usage:
    python tools/client/build_interface.py            writes the client's Interface.xdat
    python tools/client/build_interface.py --dry-run  says what would change
"""

import re
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import l2dat  # noqa: E402
import upackage  # noqa: E402
import utexture  # noqa: E402
import xdat  # noqa: E402

SYSTEM = Path("S:/Programming/L2client/HighFive/system")
TEXTURES_DIR = SYSTEM.parent / "SysTextures"
ORIGINAL = SYSTEM / "Interface.xdat.orig"
TARGET = SYSTEM / "Interface.xdat"
SCRIPTS = SYSTEM / "Interface.u.orig"


class Interface:
	"""The layout being edited and the script sources that say what may be taken out of it."""

	def __init__(self, layout, sources):
		self.layout = layout
		self.sources = sources
		self.log = []

	def window(self, name):
		found = xdat.find(self.layout, name)
		if found is None:
			raise KeyError(f"no window {name}")
		return found

	def widget(self, window, name):
		for _depth, node, parent in xdat.walk([self.window(window)]):
			if node["name"] == name:
				return node, parent
		raise KeyError(f"no widget {name} in {window}")

	def asked_for(self, name):
		"""
		The classes whose source looks this widget up: by its path ("Window.name", also built as
		m_WindowName $ ".name") or by a bare name handed to a Get...Handle call. A name in a case label of
		OnClickButton is only compared with, not looked up.
		"""
		lookup = re.compile(rf'\.{re.escape(name)}"|Handle\s*\(\s*"{re.escape(name)}"', re.IGNORECASE)
		return sorted(cls for cls, text in self.sources.items() if lookup.search(text))

	def remove(self, window, *names, reason):
		"""
		Takes widgets out of a window. A widget a script looks up can only go when it is a text box: SetText
		on a text box that is not there does nothing (tried on the character window), while other handles
		reach native code that does not check (AddItem into a missing item window crashes the client).
		"""
		for name in names:
			node, parent = self.widget(window, name)
			users = self.asked_for(name)
			if users and node["class"] != "TextBox":
				raise ValueError(f"{window}.{name} is asked for by {', '.join(users)} - hide it instead")
			parent["children"].remove(node)
		self.log.append(f"{window}: removed {', '.join(names)} - {reason}")

	def templates(self, window):
		"""Names of the widgets of a window that other widgets extend."""
		return {node.get("extendsName") for _depth, node, _parent in xdat.walk([self.window(window)])}

	def hide(self, window, *names, reason):
		"""Leaves widgets in place for the scripts that ask for them, but with nothing to draw."""
		templates = self.templates(window)
		for name in names:
			node, _parent = self.widget(window, name)
			if node["class"] == "Texture":
				node["file"] = None
			elif node["class"] == "Window":
				node["hidden"] = 1
			elif node["class"] in ("BarCtrl", "StatusBar"):
				# a bar is drawn from its pictures; without them SetValue has nothing to draw
				for field in node:
					if field.endswith(("Texture", "Tex")):
						node[field] = None
			elif node["class"] == "TextBox":
				# a text box draws its text past its size and its window, whatever the alpha of its
				# colour: the only way to hide one is to remove it
				raise ValueError(f"{window}.{name} is a text box - remove it instead")
			elif name in templates:
				raise ValueError(f"{window}.{name} is extended by other widgets - its size is theirs too")
			else:
				node["size"] = 1
				node["sizeAbsolute"] = 1
				node["width"] = 0
				node["height"] = 0
		self.log.append(f"{window}: hid {', '.join(names)} - {reason}")

	def has(self, window, name):
		return any(node["name"] == name for _depth, node, _parent in xdat.walk([self.window(window)]))

	def place(self, window, name, x, y):
		node, _parent = self.widget(window, name)
		node["x"] = float(x)
		node["y"] = float(y)

	def move(self, window, names, dy=0, dx=0):
		for name in names:
			node, _parent = self.widget(window, name)
			node["x"] += dx
			node["y"] += dy

	def resize(self, window, name=None, width=None, height=None):
		node = self.window(window) if name is None else self.widget(window, name)[0]
		if width is not None:
			node["width"] = width
		if height is not None:
			node["height"] = height


def system_menu(ui):
	"""
	Mail is off on this server and the home page button opens NCsoft's site: both rows go and the rows
	below them move up. The striped backgrounds stay where they are - every second row still gets one.
	"""
	ui.remove("SystemMenuWnd", "btnPost", "txtPost", reason="mail is not part of Interlude")
	ui.remove("SystemMenuWnd", "btnHomepage", "txtHomepage", reason="the button opens NCsoft's site")
	for rows, dy in ((["BBS", "Macro", "HelpHtml", "Petition"], -38), (["Option", "Restart", "Quit"], -76)):
		ui.move("SystemMenuWnd", [f"btn{row}" for row in rows] + [f"txt{row}" for row in rows], dy=dy)
	ui.remove("SystemMenuWnd", "BackTexLineBack4", reason="one stripe for four rows less")
	ui.resize("SystemMenuWnd", height=376 - 76)


def login_menu(ui):
	"""
	The buttons of the login screen for a new account, a lost one and the home page lead to NCsoft's
	sites; Options, Credits and Replay stay and move up.
	"""
	ui.remove("LoginMenuWnd", "btnNewAccount", "btnLossAccount", "btnHomepage", reason="links to NCsoft's sites")
	for name, y in (("btnOption", 8), ("btnCredit", 34), ("btnReplay", 60)):
		node, _parent = ui.widget("LoginMenuWnd", name)
		node["y"] = float(y)
	ui.resize("LoginMenuWnd", height=175 - 78)


def detail_status(ui):
	"""The two groups of elemental attributes at the bottom of the character window go, and the window ends
	under the Social group."""
	heads = ["txtHeadAttribute", "txtHeadAttackAttrType", "txtHeadAttackAttrValue", "txtHeadDefenseAttribute",
		"txtHeadDefenseFire", "txtHeadDefenseWater", "txtHeadDefenseWind", "txtHeadDefenseEarth",
		"txtHeadDefenseHoly", "txtHeadDefenseUnHoly", "Divider4", "Divider5", "GroupBox5", "GroupBox6"]
	values = ["txtAttrAttackType", "txtAttrAttackValue", "txtAttrDefenseValFire", "txtAttrDefenseValWater",
		"txtAttrDefenseValWind", "txtAttrDefenseValEarth", "txtAttrDefenseValHoly", "txtAttrDefenseValUnholy"]
	ui.remove("DetailStatusWnd", *heads, *values, reason="Interlude has no elemental attributes")
	ui.resize("DetailStatusWnd", height=516 - 124)


def status(ui):
	"""
	The bar at the top left: the vitality icon left of the experience bar, the vitality bar under it and
	the gauge of Nevit's hunting bonus go (both systems are off), and the experience bar takes the
	width the icon had, lining up with CP, HP and MP.
	"""
	ui.hide("StatusWnd", "LifeForceTex", "LifeForceAnimTex_Left", "LifeForceAnimTex_Center",
		"LifeForceAnimTex_Right", "NavitGaugeLeft", "NavitGaugeMid", "NavitGaugeRight", "VpDetailBar",
		"Statustooltipwnd", reason="vitality and Nevit's hunting bonus are not part of Interlude")
	bar, _parent = ui.widget("StatusWnd", "EXPBar")
	bar["x"], bar["width"] = 19.0, -24
	ui.log.append("StatusWnd: the experience bar is as wide as CP, HP and MP")


def detail_status_vitality(ui):
	"""The vitality icon in front of the experience bar of the character window goes the same way."""
	ui.hide("DetailStatusWnd", "LifeForceTex", reason="vitality is not part of Interlude")
	bar, _parent = ui.widget("DetailStatusWnd", "texexp")
	bar["x"], bar["width"] = 40.0, 103
	ui.log.append("DetailStatusWnd: the experience bar is as wide as HP and MP")


def inventory(ui, window):
	"""
	Interlude dresses fourteen slots: seven down the left (head, chest, legs, gloves, feet, weapon,
	shield) and seven down the right (two hair slots, two earrings, necklace, two rings). The cloak,
	shirt, belt, both bracelets and the six talismans of High Five go; weapon and shield move up under
	the feet, where the cloak and the shirt were. The frames drawn behind the slots change with them
	(equip_slots below).
	"""
	marks = [f"Talisman{n}_Disable" for n in range(1, 7)] + ["CloakSlot_Disable", "TalismanAllow"]
	ui.hide(window, *[m for m in marks if ui.has(window, m)],
		reason="the script shows these 'slot closed' marks while no talisman or cloak can be worn")
	# the slots themselves stay for the script (it puts items into them) and never get one; they only
	# have to leave the places weapon and shield take
	ui.place(window, "EquipItem_Cloak", 8, 311)
	ui.place(window, "EquipItem_Underwear", 8, 351)
	ui.place(window, "EquipItem_RHand", 8, 232)
	ui.place(window, "EquipItem_LHand", 8, 272)
	ui.log.append(f"{window}: weapon and shield moved under the feet, cloak and shirt slots under them")


def inventories(ui):
	inventory(ui, "InventoryWnd")
	inventory(ui, "GMInventoryInspecotrWnd")


def equip_slots(image):
	"""
	The frames behind the paper doll (L2UI_CT1 InventoryWnd.Inventory_DF_EquipSlot, 256x512): the weapon
	frame moves to where the cloak was, the shield frame to where the shirt was, and the frames of the belt,
	the bracelets, the talismans and the old weapon and shield rows are cleared.
	"""
	from PIL import Image
	out = image.copy()
	weapon = image.crop((7, 323, 43, 359))
	shield = image.crop((161, 323, 197, 359))
	clear = Image.new("RGBA", (256, 512), (0, 0, 0, 0))
	for box in ((7, 204, 43, 359), (161, 283, 197, 359), (50, 320, 150, 362)):
		out.paste(clear.crop(box), box[:2])
	out.paste(weapon, (7, 204))
	out.paste(shield, (7, 244))
	return out


EDITS = [system_menu, login_menu, detail_status, detail_status_vitality, status, inventories]
# (package, texture, what to do with its picture: a function of the old one, or None to blank it)
TEXTURES = [
	("L2UI_CT1.utx", "InventoryWnd.Inventory_DF_EquipSlot", equip_slots),
	# the "Freya - The 2nd Throne" logo in the corner of the login screen
	("L2Font-e.utx", "mini_logo-e", None),
	("L2Font-ru.utx", "mini_logo-ru", None),
]


def build():
	layout = xdat.read(ORIGINAL.read_bytes())
	sources = upackage.Package.open(SCRIPTS).sources()
	ui = Interface(layout, sources)
	for edit in EDITS:
		edit(ui)
	return xdat.write(ui.layout), ui.log


def build_textures():
	"""{package file: its new encrypted bytes}, every package built from its .orig copy."""
	out = {}
	by_package = {}
	for package, texture, change in TEXTURES:
		by_package.setdefault(package, []).append((texture, change))
	for name, changes in by_package.items():
		target = TEXTURES_DIR / name
		original = original_of(target)
		package = upackage.Package.open(original, name)
		new = {}
		for texture_name, change in changes:
			texture = utexture.find(package, texture_name)
			new[texture] = None if change is None else change(texture.image())
		out[target] = l2dat.encode(utexture.replace(package, new), "121", name)
	return out


def original_of(path):
	"""The untouched copy of a client file, made the first time the file is built."""
	original = path.with_name(path.name + ".orig")
	if not original.exists():
		shutil.copy2(path, original)
	return original


def main():
	data, log = build()
	for line in log:
		print(f"  {line}")
	textures = build_textures()
	if "--dry-run" in sys.argv:
		print(f"  {len(data)} bytes would be written to {TARGET}")
		for path in textures:
			print(f"  {path} would be written")
		return
	TARGET.write_bytes(data)
	print(f"  {TARGET}: {len(data)} bytes (the original has {ORIGINAL.stat().st_size})")
	for path, blob in textures.items():
		path.write_bytes(blob)
		print(f"  {path}: written")


if __name__ == "__main__":
	main()
