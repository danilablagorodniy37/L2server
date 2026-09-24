"""Reads and changes the pictures of the client's interface (SysTextures/*.utx).

A texture package is a Lineage2Ver121 file (l2dat.py) around an Unreal package (upackage.py). A Texture
export holds its tagged properties (Format, USize, VSize...), a few Lineage II additions and then its mip
levels, biggest first; the last level ends the export. Every level is

    int32    where the level ends in the package (the offset right after its pixels)
    compact  size of the pixels
    bytes    the pixels
    int32    USize, int32 VSize, byte UBits, byte VBits

and a compact count of the levels stands in front of the first one. The levels are found from the end
of the export backwards, each half the size of the one before.

Formats handled: 5 RGBA8 (stored as BGRA), 3 DXT1, 7 DXT3, 8 DXT5. Any change keeps the size of every
level, so the new pixels go exactly where the old ones were and nothing else in the package moves.
Pixels can be replaced from a picture for RGBA8; a DXT texture can be blanked (made fully transparent)
without an encoder, because its alpha sits apart in every 4x4 block.

Usage:
    python tools/client/utexture.py <package.utx> <Group.Name> --png <out.png>    saves a texture as PNG
    python tools/client/utexture.py <package.utx> --list <word>                    textures whose name has it
"""

import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import l2dat  # noqa: E402
import upackage  # noqa: E402

RGBA8, DXT1, DXT3, DXT5 = 5, 3, 7, 8
TAIL = 10
FOURCC = {DXT1: b"DXT1", DXT3: b"DXT3", DXT5: b"DXT5"}


def properties(package, blob):
	"""{name: raw bytes} of the tagged properties at the start of an export, and where they end."""
	out = {}
	pos = 0
	while True:
		index, pos = l2dat.read_compact(blob, pos)
		name = package.names[index]
		if name == "None":
			return out, pos
		info = blob[pos]
		pos += 1
		kind, size_code, array = info & 0x0F, (info >> 4) & 7, info & 0x80
		if kind == 10:
			_struct, pos = l2dat.read_compact(blob, pos)
		size = {0: 1, 1: 2, 2: 4, 3: 12, 4: 16}.get(size_code)
		if size_code == 5:
			size, pos = blob[pos], pos + 1
		elif size_code == 6:
			size, pos = struct.unpack_from("<H", blob, pos)[0], pos + 2
		elif size_code == 7:
			size, pos = struct.unpack_from("<I", blob, pos)[0], pos + 4
		if kind == 3:
			# a boolean keeps its value in the array bit and has no body
			out[name] = bytes([1 if array else 0])
			continue
		if array:
			pos += 1
		out[name] = blob[pos:pos + size]
		pos += size


def level_size(fmt, width, height):
	if fmt == RGBA8:
		return width * height * 4
	blocks = max(1, (width + 3) // 4) * max(1, (height + 3) // 4)
	return blocks * (8 if fmt == DXT1 else 16)


class Level:
	def __init__(self, offset, size, width, height):
		self.offset = offset
		self.size = size
		self.width = width
		self.height = height


class Texture:
	"""Where the pixels of every level of one texture sit in the plain package, and how to read them."""

	def __init__(self, package, export):
		self.package = package
		self.export = export
		blob = package.body(export)
		props, _end = properties(package, blob)
		self.format = props["Format"][0] if "Format" in props else None
		self.width = struct.unpack("<i", props["USize"])[0]
		self.height = struct.unpack("<i", props["VSize"])[0]
		if self.format not in (RGBA8, DXT1, DXT3, DXT5):
			raise ValueError(f"{export.name}: format {self.format} is not handled")
		levels = []
		end = len(blob)
		while end > TAIL:
			width, height = struct.unpack_from("<ii", blob, end - TAIL)
			size = level_size(self.format, width, height)
			start = end - TAIL - size
			prefix = l2dat.write_compact(size)
			head = start - len(prefix) - 4
			if (head < 0) or (blob[start - len(prefix):start] != prefix):
				break
			if struct.unpack_from("<I", blob, head)[0] != export.offset + start + size:
				break
			levels.append(Level(export.offset + start, size, width, height))
			if (width, height) == (self.width, self.height):
				count, _pos = l2dat.read_compact(blob, head - 1)
				if count != len(levels):
					raise ValueError(f"{export.name}: {len(levels)} levels found, the texture says {count}")
				break
			end = head
		if not levels or (levels[-1].width, levels[-1].height) != (self.width, self.height):
			raise ValueError(f"{export.name}: the levels of a {self.width}x{self.height} texture were not found")
		self.levels = list(reversed(levels))

	def image(self, level=0):
		"""A PIL image of one level."""
		from io import BytesIO
		from PIL import Image
		mip = self.levels[level]
		raw = self.package.data[mip.offset:mip.offset + mip.size]
		if self.format == RGBA8:
			return Image.frombytes("RGBA", (mip.width, mip.height), raw, "raw", "BGRA")
		# let PIL decode the blocks: a DDS header in front of them is all it needs
		header = struct.pack("<4sIIIIIII44xII4s20xI16x", b"DDS ", 124, 0x1007, mip.height, mip.width, mip.size, 0,
			1, 32, 0x4, FOURCC[self.format], 0x1000)
		return Image.open(BytesIO(header + raw)).convert("RGBA")


def find(package, name):
	"""The texture called Group.Name (or just Name) in the package."""
	for export in package.exports:
		if package.class_of(export) != "Texture":
			continue
		if name in (export.name, f"{package.object_name(export.package)}.{export.name}"):
			return Texture(package, export)
	raise KeyError(f"no texture {name}")


def blank_block(fmt):
	"""A 4x4 block every pixel of which is fully transparent."""
	if fmt == DXT1:
		# colour0 <= colour1 selects the three-colour mode, where index 3 is transparent
		return bytes(4) + b"\xff" * 4
	# DXT3: sixteen alphas of 0 (8 bytes), DXT5: alpha0 = alpha1 = 0 and all indexes 0 - then any colour
	return bytes(16)


def replace(package, textures):
	"""
	The plain package with new pixels: {Texture: PIL image of the texture's size, or None to blank it}.
	A new picture needs an RGBA8 texture; blanking works for every format.
	"""
	from PIL import Image
	data = bytearray(package.data)
	for texture, image in textures.items():
		for mip in texture.levels:
			if image is None:
				if texture.format == RGBA8:
					raw = bytes(mip.size)
				else:
					block = blank_block(texture.format)
					raw = block * (mip.size // len(block))
			else:
				if texture.format != RGBA8:
					raise ValueError(f"{texture.export.name}: a new picture needs RGBA8, not format {texture.format}")
				if image.size != (texture.width, texture.height):
					raise ValueError(f"{texture.export.name}: {image.size} instead of {texture.width}x{texture.height}")
				scaled = image if mip.width == texture.width else image.resize((mip.width, mip.height), Image.LANCZOS)
				raw = scaled.convert("RGBA").tobytes("raw", "BGRA")
			data[mip.offset:mip.offset + mip.size] = raw
	return bytes(data)


def main():
	if len(sys.argv) < 3:
		print(__doc__)
		return
	path = sys.argv[1]
	package = upackage.Package.open(path)
	if sys.argv[2] == "--list":
		word = sys.argv[3].lower() if len(sys.argv) > 3 else ""
		for export in package.exports:
			if (package.class_of(export) == "Texture") and (word in export.name.lower()):
				try:
					texture = Texture(package, export)
					print(f"  {package.object_name(export.package)}.{export.name}: {texture.width}x{texture.height}, "
						f"format {texture.format}, {len(texture.levels)} level(s)")
				except ValueError as ex:
					print(f"  {export.name}: {ex}")
		return
	texture = find(package, sys.argv[2])
	print(f"{sys.argv[2]}: {texture.width}x{texture.height}, format {texture.format}, {len(texture.levels)} level(s)")
	if "--png" in sys.argv:
		out = sys.argv[sys.argv.index("--png") + 1]
		texture.image().save(out)
		print(f"  saved to {out}")


if __name__ == "__main__":
	main()
