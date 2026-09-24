"""Reads the Unreal packages of the Lineage II client (system/*.u) and takes them apart.

A client package is a Lineage2VerNNN file (see l2dat.py - Interface.u, Engine.u and Core.u are Ver111,
a plain XOR with 0xAC) around an ordinary Unreal Engine 2 package, version 123, licensee 37:

    header       tag 9E2A83C1, version, licensee, flags, then count and offset of the names,
                 the exports and the imports
    names        a compact length, the text with its NUL, four bytes of flags
    imports      class package, class name (compact name indexes), the package the object sits in
                 (int32 object reference), the object name
    exports      class, super (compact object references), package (int32), name (compact),
                 flags (int32), size and, when the size is not zero, offset (both compact)

An object reference is 0 for none, -n for the n-th import and +n for the n-th export.

Interface.u keeps the UnrealScript source of every class it holds: each class has a TextBuffer
export called ScriptText - no properties (a None name), two int32 (Pos, Top) and the text as a
compact length string. So the interface can be read as source, not guessed from bytecode.

Usage:
    python tools/client/upackage.py <file.u>                     what is inside
    python tools/client/upackage.py <file.u> --decrypt <out.u>   the plain Unreal package
    python tools/client/upackage.py <file.u> --sources <dir>     every class as <dir>/<Class>.uc
    python tools/client/upackage.py <file.u> --class <Class>     the source of one class
"""

import struct
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import l2dat  # noqa: E402

TAG = 0x9E2A83C1


class Export:
	"""One object stored in the package."""

	def __init__(self, index, cls, super_, package, name, flags, size, offset):
		self.index = index
		self.cls = cls
		self.super = super_
		self.package = package
		self.name = name
		self.flags = flags
		self.size = size
		self.offset = offset

	def __repr__(self):
		return f"export {self.index} {self.name} ({self.size} bytes at 0x{self.offset:x})"


class Package:
	"""The name, import and export tables of an Unreal package, and a way to reach any object."""

	def __init__(self, data):
		self.data = data
		tag, self.version, self.licensee, self.flags, names, names_at, exports, exports_at, imports, imports_at = \
			struct.unpack_from("<IHHIIIIIII", data, 0)
		if tag != TAG:
			raise ValueError(f"not an Unreal package: tag 0x{tag:08x}")
		self.names = []
		pos = names_at
		for _ in range(names):
			length, pos = l2dat.read_compact(data, pos)
			self.names.append(data[pos:pos + length].decode("latin-1").rstrip(chr(0)))
			pos += length + 4
		self.imports = []
		pos = imports_at
		for _ in range(imports):
			class_package, pos = l2dat.read_compact(data, pos)
			class_name, pos = l2dat.read_compact(data, pos)
			package = struct.unpack_from("<i", data, pos)[0]
			pos += 4
			name, pos = l2dat.read_compact(data, pos)
			self.imports.append((self.names[class_package], self.names[class_name], package, self.names[name]))
		self.exports = []
		pos = exports_at
		for index in range(1, exports + 1):
			cls, pos = l2dat.read_compact(data, pos)
			super_, pos = l2dat.read_compact(data, pos)
			package = struct.unpack_from("<i", data, pos)[0]
			pos += 4
			name, pos = l2dat.read_compact(data, pos)
			flags = struct.unpack_from("<I", data, pos)[0]
			pos += 4
			size, pos = l2dat.read_compact(data, pos)
			offset = 0
			if size > 0:
				offset, pos = l2dat.read_compact(data, pos)
			self.exports.append(Export(index, cls, super_, package, self.names[name], flags, size, offset))

	@classmethod
	def open(cls, path, name=None):
		"""A package from a client file, encrypted or not; name as in l2dat.decode."""
		raw = Path(path).read_bytes()
		if struct.unpack_from("<I", raw, 0)[0] == TAG:
			return cls(raw)
		data, _key = l2dat.decode(path, name)
		return cls(data)

	def object_name(self, ref):
		if ref < 0:
			return self.imports[-ref - 1][3]
		if ref > 0:
			return self.exports[ref - 1].name
		return "None"

	def class_of(self, export):
		"""The name of the class of an export; a class itself has none and reads as Class."""
		return self.object_name(export.cls) if export.cls else "Class"

	def body(self, export):
		return self.data[export.offset:export.offset + export.size]

	def classes(self):
		return [e for e in self.exports if e.cls == 0]

	def sources(self):
		"""class name -> the UnrealScript source the package keeps for it."""
		out = {}
		for export in self.exports:
			if (export.name != "ScriptText") or (self.class_of(export) != "TextBuffer"):
				continue
			blob = self.body(export)
			pos = 0
			# the TextBuffer carries no properties: a single None name ends the list
			none, pos = l2dat.read_compact(blob, pos)
			if self.names[none] != "None":
				continue
			pos += 8
			text, _pos = l2dat.read_string(blob, pos)
			out[self.object_name(export.package)] = text.replace("\r\n", "\n")
		return out


def main():
	if len(sys.argv) < 2:
		print(__doc__)
		return
	path = sys.argv[1]
	package = Package.open(path)
	args = sys.argv[2:]
	if "--decrypt" in args:
		out = Path(args[args.index("--decrypt") + 1])
		out.write_bytes(package.data)
		print(f"{out}: {len(package.data)} bytes, Unreal package {package.version}/{package.licensee}")
		return
	if "--sources" in args:
		out = Path(args[args.index("--sources") + 1])
		out.mkdir(parents=True, exist_ok=True)
		sources = package.sources()
		for name, text in sources.items():
			(out / f"{name}.uc").write_text(text, encoding="utf-8", newline="\n")
		print(f"{len(sources)} classes written to {out}")
		return
	if "--class" in args:
		wanted = args[args.index("--class") + 1].lower()
		for name, text in package.sources().items():
			if name.lower() == wanted:
				print(text)
				return
		print(f"no class {wanted} in {path}")
		return
	kinds = Counter(package.class_of(e) for e in package.exports)
	print(f"{path}: Unreal package {package.version}/{package.licensee}, {len(package.names)} names, "
		f"{len(package.imports)} imports, {len(package.exports)} exports")
	for kind, count in kinds.most_common():
		print(f"  {kind:20} {count:6}")


if __name__ == "__main__":
	main()
