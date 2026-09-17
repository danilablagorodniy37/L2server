"""Reading and writing the data files of the Lineage II client (system/*.dat, *.ini).

A client file starts with a 28 byte header, "Lineage2VerNNN" in UTF-16, and the rest depends
on the version:
  Ver111  every byte is XORed with 0xAC (the .ini files use this);
  Ver413  RSA blocks of 128 bytes, then zlib. Each decrypted block keeps its payload size in
          byte 3 and the payload itself right-aligned; the blocks together are a 4 byte length
          and a zlib stream. The last 20 bytes of the file are a trailer whose bytes 12-16 hold
          the CRC32 of everything before it - the client crashes on a file without it.

This client is a modified one: its Ver413 files use the public l2encdec key, whose private
exponent is public as well, so the files can be written back.

Usage: python tools/client/l2dat.py <file> [<file> ...]   prints what each file is
"""

import struct
import sys
import zlib

HEADER = 28
NUL = bytes([0])
# (modulus, public exponent) of the retail client and of the l2encdec key used by modified ones
KEYS = {
	"retail": (int("97df398472ddf737ef0a0cd17e8d172f0fef1661a38a8ae1d6e829bc1c6e4c3cfc19292dda9ef90175e46e7394a18850b6417d03be6eea274d3ed1dde5b5d7bde72cc0a0b71d03608655633881793a02c9a67d9ef2b45eb7c08d4be329083ce450e68f7867b6749314d40511d09bc5744551baa86a89dc38123dc1668fd72d83", 16), 0x35),
	"l2encdec": (int("75b4d6de5c016544068a1acf125869f43d2e09fc55b8b1e289556daf9b8757635593446288b3653da1ce91c87bb1a5c18f16323495c55d7d72c0890a83f69bfd1fd9434eb1c02f3e4679edfa43309319070129c267c85604d87bb65bae205de3707af1d2108881abb567c3b3d069ae67c3a4c6a3aa93d26413d4c66094ae2039", 16), 0x1d),
}
# private exponent of the l2encdec key, the one this client reads
L2ENCDEC_PRIVATE = int("30b4c2d798d47086145c75063c8e841e719776e400291d7838d3e6c4405b504c6a07f8fca27f32b86643d2649d1d5f124cdd0bf272f0909dd7352fe10a77b34d831043d9ae541f8263c6fe3d1c14c2f04e43a7253a6dda9a8c1562cbd493c1b631a1957618ad5dfe5ca28553f746e2fc6f2db816c7db223ec91e955081c1de65", 16)
XOR_111 = 0xAC


def version(raw):
	return raw[:HEADER].decode("utf-16-le", "replace")


def decode_413(raw):
	"""(plain bytes, name of the key that fits)."""
	body = raw[HEADER:-20]
	for name, (modulus, exponent) in KEYS.items():
		out = bytearray()
		try:
			for i in range(0, len(body), 128):
				block = pow(int.from_bytes(body[i:i + 128], "big"), exponent, modulus).to_bytes(128, "big")
				size = block[3]
				pad = (124 - size) % 4
				out += block[128 - size - pad:128 - pad]
			data = zlib.decompress(bytes(out[4:]))
			if len(data) == int.from_bytes(out[:4], "little"):
				return data, name
		except (zlib.error, ValueError, OverflowError):
			continue
	raise ValueError("no Ver413 key fits this file")


def encode_413(data):
	"""A Ver413 file the modified client reads, CRC32 trailer included."""
	packed = struct.pack("<I", len(data)) + zlib.compress(data, 9)
	out = bytearray("Lineage2Ver413".encode("utf-16-le"))
	modulus = KEYS["l2encdec"][0]
	for i in range(0, len(packed), 124):
		chunk = packed[i:i + 124]
		size = len(chunk)
		pad = (124 - size) % 4
		block = bytearray(128)
		block[3] = size
		block[128 - size - pad:128 - pad] = chunk
		out += pow(int.from_bytes(block, "big"), L2ENCDEC_PRIVATE, modulus).to_bytes(128, "big")
	return bytes(out) + NUL * 12 + struct.pack("<I", zlib.crc32(bytes(out)) & 0xFFFFFFFF) + NUL * 4


def decode(path):
	"""(plain bytes, key name) of a client file."""
	raw = open(path, "rb").read()
	kind = version(raw)
	if kind == "Lineage2Ver413":
		return decode_413(raw)
	if kind == "Lineage2Ver111":
		return bytes(b ^ XOR_111 for b in raw[HEADER:]), "111"
	raise ValueError(f"unsupported file version {kind!r}")


def read_compact(data, pos):
	"""Unreal compact index: (value, new position). A negative value counts UTF-16 characters."""
	b = data[pos]
	pos += 1
	negative, value, shift, more = b & 0x80, b & 0x3F, 6, b & 0x40
	while more:
		b = data[pos]
		pos += 1
		value |= (b & 0x7F) << shift
		shift += 7
		more = b & 0x80
	return (-value if negative else value), pos


def write_compact(value):
	out = bytearray()
	out.append((value & 0x3F) | (0x40 if value >> 6 else 0))
	value >>= 6
	while value:
		b = value & 0x7F
		value >>= 7
		out.append(b | (0x80 if value else 0))
	return bytes(out)


def read_string(data, pos):
	"""A string of a dat table: (text, new position)."""
	length, pos = read_compact(data, pos)
	if length < 0:
		return data[pos:pos - 2 * length].decode("utf-16-le").rstrip(chr(0)), pos - 2 * length
	return data[pos:pos + length].decode("latin-1").rstrip(chr(0)), pos + length


def write_string(text):
	if not text:
		return NUL
	raw = text.encode("latin-1") + NUL
	return write_compact(len(raw)) + raw


if __name__ == "__main__":
	for path in sys.argv[1:]:
		try:
			data, key = decode(path)
			print(f"{path}: {len(data)} bytes, key {key}")
		except Exception as ex:
			print(f"{path}: {ex}")
