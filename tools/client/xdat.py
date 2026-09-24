"""Reads and writes the client's interface layout (system/Interface.xdat), field by field.

The file is not encrypted. Its layout follows the High Five schema of acmi's xdat_editor (the
"ct26" one, github.com/acmi/xdat_editor, MIT):

    shortcuts    int count, then key bindings: name, state, modifiable, items, special items
    windows      int count, then top-level Window records (no class name in front of them)
    int          always 1
    wndDefPos    default positions of windows
    fonts        font sets
    styles       text styles
    chat         the colour of every chat channel
    tail         20 bytes, when present

A widget inside a window is written as its class name and then its fields; the first fields are the
same for every widget (DefaultProperty: name, parent, size, anchor, font, tooltip), then come the
fields of its class. A Window, and a ScrollArea, end with the list of their own children, so
windows nest - that is what a flat reading of the file got wrong.

Strings are an Unreal compact length (the NUL counts) and the text; a negative length means UTF-16.
A boolean is four bytes (0, 1, or -1 for "not set"). Every number is four bytes little endian.

The reader keeps every value as it was read, so write(read(file)) gives the same bytes back - the
tests check that on the real file before anything is changed.

Usage:
    python tools/client/xdat.py                          the windows, biggest first
    python tools/client/xdat.py --window InventoryWnd     the widget tree of one window
    python tools/client/xdat.py --find talisman           every widget whose name matches
    python tools/client/xdat.py --check                   read, write back and compare
"""

import struct
import sys
from pathlib import Path

DEFAULT = Path("S:/Programming/L2client/HighFive/system/Interface.xdat")


class Text(str):
	"""A string the file stores as UTF-16."""


# field kinds: s string, i int, b boolean, f float, c colour, e enum stored as int, n enum stored as
# string; ("list", Struct) a list of records with an int count
FIELDS = {
	"BarCtrl": "foreTexture:s foreLeftTexture:s foreRightTexture:s backTexture:s backLeftTexture:s "
		"backRightTexture:s uSize:i vSize:i",
	"Button": "normalTex:s pushedTex:s highlightTex:s dropTex:s buttonName:i buttonNameText:s noHighlight:b "
		"defaultSoundOn:b disableTime:i",
	"CharacterViewportWindow": "characterScale:f characterOffsetX:i characterOffsetY:i cameraDistMax:i "
		"cameraDistMin:i defaultCameraPitch:i defaultCameraYaw:i zoomRate:i rotationRate:i backgroundTex:s npcID:i",
	"ChatWindow": "lineGap:i nextLineOffsetX:i",
	"CheckBox": "titleIndex:i titleText:s checked:b leftAligned:b max_width:i checkTexture:s unCheckTexture:s "
		"disableTexture:s disableCheckTexture:s",
	"ComboBox": "values:[ComboBoxElement]",
	"DrawPanel": "autoSize:b",
	"EditBox": "type:e maxLength:i showCursor:b chatMarkOn:b offsetX:i candidateBoxShowUpPos:b autoCompletionType:e",
	"EffectButton": "type:e normalTex:s pushedTex:s highlightTex:s effectTex1:s effectTex2:s",
	"FishViewportWindow": "texBack:s texClock:s texFishHPBar:s texFishHPBarBack:s texFishFakeHPBarWarning:s "
		"texFishingEffect:s texIconPumping:s texIconReeling:s",
	"FlashCtrl": "flashFile:s",
	"HtmlCtrl": "viewType:s",
	"InvenWeight": "target:s textureWidth:i textureHeight:i textureStepLeft:s textureStepMid:s textureStepRight:s "
		"textureWarnLeft:s textureWarnMid:s textureWarnRight:s textureAddedLeft:s textureAddedMid:s "
		"textureAddedRight:s textureBackLeft:s textureBackMid:s textureBackRight:s gaugeText:s fontWidth:i fontHeight:i",
	"ItemWindow": "wndType:n col:i row:i maxItemNum:i iconWidth:i iconHeight:i gapX:i gapY:i offsetX:i offsetY:i "
		"backgroundItemWidth:i backgroundItemHeight:i backgroundItemTex:s selectedItemWidth:i selectedItemHeight:i "
		"selectedItemTex:s unselectedItemWidth:i unselectedItemHeight:i unselectedItemTex:s blankItemWidth:i "
		"blankItemHeight:i blankItemTex:s noSelectItem:b noItemDrag:b buttonClick:b useCoolTime:b noScroll:b "
		"showIconFrame:b outLineUp:s outLineDown:s buttonTypePrevButtonPosX:i buttonTypePrevButtonPosY:i "
		"buttonTypeNextButtonPosX:i buttonTypeNextButtonPosY:i expandItem:[ItemWindowInner]",
	"ListBox": "maxRow:i showRow:i showLastLine:b",
	"ListCtrl": "maxRow:i showRow:i useVScroll:b contentsHeight:i headerHeight:i values:[ListElement]",
	"MinimapCtrl": "showTime:b showTown:b showGrid:b showMyLocMark:b showMyLocText:b showSSQText:b",
	"MoviePlayerCtrl": "unk100:s unk101:i",
	"MultiEdit": "maxRow:i showRow:i",
	"MultiSellItemInfo": "",
	"MultiSellNeededItem": "",
	"NameCtrl": "",
	"Progress": "unk100:s backLeftTexture:s backTexture:s backRightTexture:s barLeftTexture:s barTexture:s "
		"barRightTexture:s gap:i textureSize:i type:e",
	"PropertyController": "",
	"Radar": "",
	"RadarMapCtrl": "mapTextureName:s layer:f radarMapCtrlObjectType:[RadarMapCtrlObjectType]",
	"RadioButton": "sysstring:i radioGroupID:i isChecked:b",
	"ShortcutItemWindow": "alwaysShowOutline:b useReservedShortcut:b",
	"SliderCtrl": "numOfTick:i currTick:i thumbBtnWidth:i thumbBtnHeight:i backTexture:s disableBackTexture:s "
		"thumbBtnNormalTexture:s thumbBtnDownTexture:s pushBtnWidth:i pushBtnHeight:i pushBtnAutoHitTime:i "
		"strDecreaseBtnNormalTexture:s strDecreaseBtnDownTexture:s strIncreaseBtnNormalTexture:s "
		"strIncreaseBtnDownTexture:s tickTexture:s thumbBtnInclusive:b",
	"StatusBar": "title:s barWidth:i barHeight:i foreTex:s foreLeftTex:s foreRightTex:s backTex:s backLeftTex:s "
		"backRightTex:s regenLeftTex:s regenTex:s regenRightTex:s warnTex:s warnLeftTex:s warnRightTex:s "
		"gaugeFontTextureName:s gaugeFontSizeX:i gaugeFontSizeY:i",
	"StatusIconCtrl": "noClip:b noTooltip:b",
	"Tab": "tabs:[TabElement]",
	"TextBox": "text:s textAlign:e textVAlign:e fontType:e backTex:s sysstring:i systemMsg:i textColor:c "
		"emoticon:b autoSize:b",
	"TextListBox": "maxRow:i showRow:i lineGap:i isShowScroll:b",
	"Texture": "file:s alphaMask:s type:e layer:e u:f v:f uSize:f vSize:f alpha:i isAnimTex:b autoRotate:n "
		"maskLayer:f colorModify:c",
	"TreeCtrl": "saveExpandedNode:b multiExpand:b",
	# records that are not widgets
	"ComboBoxElement": "sysString:i systemMsg:i text:s reserved:i",
	"ItemWindowInner": "width:i height:i num:i texture:s",
	"ListElement": "textStringId:i width:i bAscend:b bClickEnable:b bNumber:b",
	"RadarMapCtrlObjectType": "typeName:s normalTexName:s pushedTexName:s overTexName:s width:i height:i top:i",
	"TabElement": "buttonName:i buttonNameText:s target:s width:i height:i normalTex:s pushedTex:s movable:b gap:i "
		"tooltip:i noHighlight:i",
	"Shortcut": "name:s state:s modifiable:i shortcutItems:[ShortcutItem] shortcutSpecialItems:[ShortcutSpecialItem]",
	"ShortcutItem": "key:e subKey1:e subKey2:e command:s action:e",
	"ShortcutSpecialItem": "key:e command:s",
	"WndDefPos": "wnd:s alignment:e x:i y:i moveParent:b width:i height:i",
	"Style": "name:s fontName:s line:s color:c",
	"ChatChannelDefinition": "chatType:e chatColor:c",
	"WindowState": "unk148:s",
}
SCHEMA = {}
for _cls, _spec in FIELDS.items():
	SCHEMA[_cls] = [tuple(item.split(":", 1)) for item in _spec.split()]

# widgets whose records end with a list of children of their own
CONTAINERS = ("Window", "ScrollArea")


class Reader:
	def __init__(self, data):
		self.data = data
		self.pos = 0

	def int(self):
		value = struct.unpack_from("<i", self.data, self.pos)[0]
		self.pos += 4
		return value

	def float(self):
		value = struct.unpack_from("<f", self.data, self.pos)[0]
		self.pos += 4
		return value

	def compact(self):
		b = self.data[self.pos]
		self.pos += 1
		negative, value, more = b & 0x80, b & 0x3F, b & 0x40
		shift = 6
		while more:
			b = self.data[self.pos]
			self.pos += 1
			value |= (b & 0x7F) << shift
			shift += 7
			more = (b & 0x80) and (shift < 34)
		return -value if negative else value

	def str(self):
		"""None for an empty string (length 0), a Text for UTF-16, a str otherwise."""
		length = self.compact()
		if length == 0:
			return None
		if length < 0:
			raw = self.data[self.pos:self.pos - 2 * length]
			self.pos -= 2 * length
			return Text(raw[:-2].decode("utf-16-le"))
		raw = self.data[self.pos:self.pos + length]
		self.pos += length
		return raw[:-1].decode("latin-1")

	def value(self, kind):
		if kind in ("s", "n"):
			return self.str()
		if kind == "f":
			return self.float()
		if kind.startswith("["):
			return [self.record(kind[1:-1]) for _ in range(self.int())]
		return self.int()

	def record(self, cls):
		node = {"class": cls}
		self.fields(node, cls)
		return node

	def fields(self, node, cls):
		for name, kind in SCHEMA[cls]:
			node[name] = self.value(kind)

	def base(self, node):
		"""The fields every widget starts with (DefaultProperty)."""
		for name in ("name", "extendsName"):
			node[name] = self.str()
		node["alwaysOnTop"] = self.int()
		node["alwaysOnBack"] = self.int()
		for name in ("parentName", "preOrder", "postOrder"):
			node[name] = self.str()
		node["anchored"] = self.int()
		node["superChild"] = self.int()
		node["size"] = self.int()
		if node["size"]:
			node["sizeAbsolute"] = self.int()
			if not node["sizeAbsolute"]:
				node["sizePercentWindow"] = self.str()
				node["sizePercentWidth"] = self.float()
				node["sizePercentHeight"] = self.float()
			node["width"] = self.int()
			node["height"] = self.int()
		node["usePosition"] = self.int()
		if node["usePosition"]:
			node["relativePoint"] = self.int()
			node["anchorPoint"] = self.int()
			node["relativeTo"] = self.str()
			node["x"] = self.float()
			node["y"] = self.float()
		node["useFont"] = self.int()
		if node["useFont"]:
			node["styleName"] = self.str()
			node["fontName"] = self.str()
			node["fontColor"] = self.int()
			node["fontLine"] = self.int()
		node["virtual"] = self.int()
		node["unk24"] = self.int()
		node["tooltipType"] = self.str()
		for name in ("tooltipText", "tooltipAppearanceTime", "scrollBarOffsetX", "scrollBarOffsetY",
				"scrollBarOffsetHeight"):
			node[name] = self.int()

	def window(self, node):
		for name in ("parent", "backTex", "script", "state"):
			node[name] = self.str()
		for name in ("frame", "iconable", "stuckable", "hidden", "alwaysFullAlpha", "savePosition", "saveSize",
				"title", "resizeFrame", "frameSize", "frameDirection", "exitbutton", "draggable",
				"resizeFrameDirection"):
			node[name] = self.int()
		for name in ("resizeFrameX", "resizeFrameY", "resizeFrameWidth", "resizeFrameHeight"):
			node[name] = self.float()
		node["resizeMaxIncrease"] = self.int()
		node["drawerDirection"] = self.int()
		# offsetX, offsetY, directionFixed - read the same way whether a drawer is set or not
		node["drawer"] = [self.int(), self.int(), self.int()]
		node["ownerWindow"] = self.str()
		node["showAnimType"] = self.int()
		node["hideAnimType"] = self.int()
		# direction and seconds of the animation; the seconds are a float only when it is used
		node["showAnim"] = [self.int(), self.float() if node["showAnimType"] > 0 else self.int()]
		node["hideAnim"] = [self.int(), self.float() if node["hideAnimType"] > 0 else self.int()]
		node["iconName"] = self.str()
		node["tooltipIdx"] = self.int()
		node["hookKeyInput"] = self.int()
		for name in ("workingConfiguration", "leftTextureName", "midTextureName", "rightTextureName",
				"minimizeBtnTextureNormal", "minimizeBtnTexturePushed", "closeBtnTextureNormal",
				"closeBtnTexturePushed"):
			node[name] = self.str()
		node["leftBackTextureWidth"] = self.int()
		node["midBackTextureWidth"] = self.int()
		node["additionalState"] = [self.record("WindowState") for _ in range(self.compact())]
		node["useParentClipRect"] = self.int()
		node["children"] = self.children()

	def widget(self, cls):
		node = {"class": cls}
		self.base(node)
		if cls == "Window":
			self.window(node)
		elif cls == "ScrollArea":
			node["areaHeight"] = self.int()
			node["children"] = self.children()
		elif cls in SCHEMA:
			self.fields(node, cls)
		else:
			raise ValueError(f"unknown widget class {cls!r} at 0x{self.pos:x}")
		return node

	def children(self):
		return [self.widget(self.str()) for _ in range(self.int())]

	def font(self):
		node = {"class": "Font", "defaultFontName": self.str(), "defaultColor": self.int(), "fonts": []}
		for _ in range(self.int()):
			data = {"name": self.str(), "file": self.str(), "location": self.str()}
			for name in ("size", "index", "indexOn", "shadow", "shadowX", "shadowY", "stroke"):
				data[name] = self.int()
			# a stroke is one boolean (large or not) when set, an unused int when not - four bytes either way
			for name in ("strokeLarge", "lineGap", "underlineOffset"):
				data[name] = self.int()
			node["fonts"].append(data)
		return node


class Writer:
	def __init__(self):
		self.out = bytearray()

	def int(self, value):
		self.out += struct.pack("<i", value)

	def float(self, value):
		self.out += struct.pack("<f", value)

	def compact(self, value):
		negative = value < 0
		value = abs(value)
		first = (value & 0x3F) | (0x80 if negative else 0)
		value >>= 6
		if value:
			first |= 0x40
		self.out.append(first)
		while value:
			b = value & 0x7F
			value >>= 7
			self.out.append(b | (0x80 if value else 0))

	def str(self, text):
		if text is None:
			self.compact(0)
			return
		if isinstance(text, Text) or any(ord(ch) > 0xFF for ch in text):
			raw = (text + "\0").encode("utf-16-le")
			self.compact(-(len(raw) // 2))
		else:
			raw = (text + "\0").encode("latin-1")
			self.compact(len(raw))
		self.out += raw

	def value(self, kind, value):
		if kind in ("s", "n"):
			self.str(value)
		elif kind == "f":
			self.float(value)
		elif kind.startswith("["):
			self.int(len(value))
			for item in value:
				self.fields(item, kind[1:-1])
		else:
			self.int(value)

	def fields(self, node, cls):
		for name, kind in SCHEMA[cls]:
			self.value(kind, node[name])

	def base(self, node):
		self.str(node["name"])
		self.str(node["extendsName"])
		self.int(node["alwaysOnTop"])
		self.int(node["alwaysOnBack"])
		for name in ("parentName", "preOrder", "postOrder"):
			self.str(node[name])
		self.int(node["anchored"])
		self.int(node["superChild"])
		self.int(node["size"])
		if node["size"]:
			self.int(node["sizeAbsolute"])
			if not node["sizeAbsolute"]:
				self.str(node["sizePercentWindow"])
				self.float(node["sizePercentWidth"])
				self.float(node["sizePercentHeight"])
			self.int(node["width"])
			self.int(node["height"])
		self.int(node["usePosition"])
		if node["usePosition"]:
			self.int(node["relativePoint"])
			self.int(node["anchorPoint"])
			self.str(node["relativeTo"])
			self.float(node["x"])
			self.float(node["y"])
		self.int(node["useFont"])
		if node["useFont"]:
			self.str(node["styleName"])
			self.str(node["fontName"])
			self.int(node["fontColor"])
			self.int(node["fontLine"])
		self.int(node["virtual"])
		self.int(node["unk24"])
		self.str(node["tooltipType"])
		for name in ("tooltipText", "tooltipAppearanceTime", "scrollBarOffsetX", "scrollBarOffsetY",
				"scrollBarOffsetHeight"):
			self.int(node[name])

	def window(self, node):
		for name in ("parent", "backTex", "script", "state"):
			self.str(node[name])
		for name in ("frame", "iconable", "stuckable", "hidden", "alwaysFullAlpha", "savePosition", "saveSize",
				"title", "resizeFrame", "frameSize", "frameDirection", "exitbutton", "draggable",
				"resizeFrameDirection"):
			self.int(node[name])
		for name in ("resizeFrameX", "resizeFrameY", "resizeFrameWidth", "resizeFrameHeight"):
			self.float(node[name])
		self.int(node["resizeMaxIncrease"])
		self.int(node["drawerDirection"])
		for value in node["drawer"]:
			self.int(value)
		self.str(node["ownerWindow"])
		self.int(node["showAnimType"])
		self.int(node["hideAnimType"])
		for key, kind in (("showAnim", "showAnimType"), ("hideAnim", "hideAnimType")):
			direction, seconds = node[key]
			self.int(direction)
			if node[kind] > 0:
				self.float(seconds)
			else:
				self.int(seconds)
		self.str(node["iconName"])
		self.int(node["tooltipIdx"])
		self.int(node["hookKeyInput"])
		for name in ("workingConfiguration", "leftTextureName", "midTextureName", "rightTextureName",
				"minimizeBtnTextureNormal", "minimizeBtnTexturePushed", "closeBtnTextureNormal",
				"closeBtnTexturePushed"):
			self.str(node[name])
		self.int(node["leftBackTextureWidth"])
		self.int(node["midBackTextureWidth"])
		self.compact(len(node["additionalState"]))
		for state in node["additionalState"]:
			self.fields(state, "WindowState")
		self.int(node["useParentClipRect"])
		self.children(node["children"])

	def widget(self, node, named=True):
		if named:
			self.str(node["class"])
		self.base(node)
		cls = node["class"]
		if cls == "Window":
			self.window(node)
		elif cls == "ScrollArea":
			self.int(node["areaHeight"])
			self.children(node["children"])
		else:
			self.fields(node, cls)

	def children(self, nodes):
		self.int(len(nodes))
		for node in nodes:
			self.widget(node)

	def font(self, node):
		self.str(node["defaultFontName"])
		self.int(node["defaultColor"])
		self.int(len(node["fonts"]))
		for data in node["fonts"]:
			for name in ("name", "file", "location"):
				self.str(data[name])
			for name in ("size", "index", "indexOn", "shadow", "shadowX", "shadowY", "stroke", "strokeLarge",
					"lineGap", "underlineOffset"):
				self.int(data[name])


def read(data):
	"""The whole file as a dict of plain Python values."""
	r = Reader(data)
	xdat = {"shortcuts": [r.record("Shortcut") for _ in range(r.int())]}
	count = r.int()
	windows = []
	for _ in range(count):
		node = {"class": "Window"}
		r.base(node)
		r.window(node)
		windows.append(node)
	xdat["windows"] = windows
	xdat["separator"] = r.int()
	xdat["wndDefPos"] = [r.record("WndDefPos") for _ in range(r.int())]
	xdat["fonts"] = [r.font() for _ in range(r.int())]
	xdat["styles"] = [r.record("Style") for _ in range(r.int())]
	xdat["chatChannels"] = [r.record("ChatChannelDefinition") for _ in range(r.int())]
	xdat["tail"] = bytes(data[r.pos:])
	return xdat


def write(xdat):
	w = Writer()
	w.int(len(xdat["shortcuts"]))
	for item in xdat["shortcuts"]:
		w.fields(item, "Shortcut")
	w.int(len(xdat["windows"]))
	for node in xdat["windows"]:
		w.widget(node, named=False)
	w.int(xdat["separator"])
	for key, cls in (("wndDefPos", "WndDefPos"), ("fonts", None), ("styles", "Style"), ("chatChannels", "ChatChannelDefinition")):
		w.int(len(xdat[key]))
		for item in xdat[key]:
			if cls is None:
				w.font(item)
			else:
				w.fields(item, cls)
	w.out += xdat["tail"]
	return bytes(w.out)


BASE_STRINGS = ("name", "extendsName", "parentName", "preOrder", "postOrder", "tooltipType")
BASE_INTS = ("alwaysOnTop", "alwaysOnBack", "anchored", "superChild", "size", "usePosition", "useFont", "virtual", "unk24",
	"tooltipText", "tooltipAppearanceTime", "scrollBarOffsetX", "scrollBarOffsetY", "scrollBarOffsetHeight")
WINDOW_STRINGS = ("parent", "backTex", "script", "state", "ownerWindow", "iconName", "workingConfiguration",
	"leftTextureName", "midTextureName", "rightTextureName", "minimizeBtnTextureNormal", "minimizeBtnTexturePushed",
	"closeBtnTextureNormal", "closeBtnTexturePushed")
WINDOW_INTS = ("frame", "iconable", "stuckable", "hidden", "alwaysFullAlpha", "savePosition", "saveSize", "title",
	"resizeFrame", "frameSize", "frameDirection", "exitbutton", "draggable", "resizeFrameDirection", "resizeMaxIncrease",
	"drawerDirection", "showAnimType", "hideAnimType", "tooltipIdx", "hookKeyInput", "leftBackTextureWidth",
	"midBackTextureWidth", "useParentClipRect")


def new(cls, name, **fields):
	"""
	A widget of that class with every field empty (0, no string, no children), then the given fields: enough to
	write a valid record, and the start of a widget an edit adds to a window. With size or usePosition set, the
	fields they bring (width and height, the anchor and x and y) have to be given too.
	"""
	node = {"class": cls}
	for key in BASE_STRINGS:
		node[key] = None
	for key in BASE_INTS:
		node[key] = 0
	node["name"] = name
	if cls == "Window":
		for key in WINDOW_STRINGS:
			node[key] = None
		for key in WINDOW_INTS:
			node[key] = 0
		for key in ("resizeFrameX", "resizeFrameY", "resizeFrameWidth", "resizeFrameHeight"):
			node[key] = 0.0
		node["drawer"] = [0, 0, 0]
		node["showAnim"] = [0, 0]
		node["hideAnim"] = [0, 0]
		node["additionalState"] = []
		node["children"] = []
	elif cls == "ScrollArea":
		node["areaHeight"] = 0
		node["children"] = []
	else:
		for key, kind in SCHEMA[cls]:
			node[key] = None if kind in ("s", "n") else 0.0 if kind == "f" else [] if kind.startswith("[") else 0
	node.update(fields)
	return node


def walk(nodes, depth=0, parent=None):
	"""(depth, widget, the widget it sits in) for every widget of the tree, parents first."""
	for node in nodes:
		yield depth, node, parent
		yield from walk(node.get("children", ()), depth + 1, node)


def size(node):
	"""How many bytes a widget takes in the file, its children included."""
	w = Writer()
	w.widget(node)
	return len(w.out)


def find(xdat, name):
	"""The top-level window with that name, or None."""
	for node in xdat["windows"]:
		if node["name"].lower() == name.lower():
			return node
	return None


def main():
	args = [a for a in sys.argv[1:] if not a.startswith("--")]
	flags = [a for a in sys.argv[1:] if a.startswith("--")]
	value = None
	if flags and flags[0] in ("--window", "--find"):
		value = args.pop(0) if args else None
	path = Path(args[0]) if args else DEFAULT
	data = path.read_bytes()
	xdat = read(data)
	widgets = sum(1 for _ in walk(xdat["windows"]))
	print(f"{path}: {len(data)} bytes, {len(xdat['windows'])} windows, {widgets} widgets, "
		f"{len(xdat['shortcuts'])} shortcut sets, {len(xdat['wndDefPos'])} default positions")

	if "--check" in flags:
		again = write(xdat)
		print("  written back byte for byte" if again == data else f"  DIFFERS: {len(again)} bytes written")
		return
	if "--window" in flags:
		node = find(xdat, value)
		if node is None:
			print(f"  no window {value}")
			return
		for depth, widget, _parent in walk([node]):
			hidden = " hidden" if widget.get("hidden") == 1 else ""
			print(f"  {'  ' * depth}{widget['class']:16} {widget['name']}{hidden}")
		return
	if "--find" in flags:
		for window in xdat["windows"]:
			for depth, widget, _parent in walk([window]):
				if value.lower() in (widget["name"] or "").lower():
					print(f"  {window['name']:30} {widget['class']:16} {widget['name']}")
		return
	rows = sorted(((size(node), node) for node in xdat["windows"]), key=lambda row: -row[0])
	print(f"{'window':34} {'widgets':>8} {'bytes':>9}  script")
	for length, node in rows[:40]:
		count = sum(1 for _ in walk([node]))
		print(f"{node['name']:34} {count:8} {length:9}  {node.get('script') or ''}")


if __name__ == "__main__":
	main()
