"""Floor heights from the server geodata (game/data/geodata), via tools/geo/GeoHeight.java.

The Java helper uses the geodata driver jar of the server, so heights match what
the server sees. It is compiled on first use into tools/geo/build (not in git).
"""

import shutil
import subprocess
from functools import cache

import datasets as ds

GEO_DIR = ds.ROOT / "tools" / "geo"
BUILD = GEO_DIR / "build"
GEODATA = ds.GAME / "data" / "geodata"


def available():
	return shutil.which("java") is not None and shutil.which("javac") is not None and any(GEODATA.glob("*.l2j"))


@cache
def _classpath():
	driver = next((ds.GAME / "libs").glob("l2j-server-geo-driver-*.jar"))
	source = GEO_DIR / "GeoHeight.java"
	compiled = BUILD / "GeoHeight.class"
	if not compiled.exists() or compiled.stat().st_mtime < source.stat().st_mtime:
		BUILD.mkdir(exist_ok=True)
		subprocess.run(["javac", "-d", str(BUILD), "-cp", str(driver), str(source)], check=True, capture_output=True)
	return f"{BUILD};{driver}"


def floors(points):
	"""[(x, y, z)] -> [nearest geodata floor to z, or None where there is no geodata]."""
	if not points:
		return []
	stdin = "\n".join(f"{x} {y} {z}" for x, y, z in points)
	out = subprocess.run(["java", "-cp", _classpath(), "GeoHeight", str(GEODATA)], input=stdin, capture_output=True, text=True, check=True).stdout
	result = [None if line.split()[3] == "-" else int(line.split()[3]) for line in out.splitlines()]
	assert len(result) == len(points)
	return result
