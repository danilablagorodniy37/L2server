"""Game scripts (game/script) compile against the core jar, as the server does at boot (~10 s)."""

import subprocess

import pytest

import datasets as ds


@pytest.mark.skipif(ds.jdk("javac") is None, reason="no javac (JDK 21) in JAVA_HOME or on PATH")
def test_scripts_compile(tmp_path):
	sources = sorted(str(p.relative_to(ds.GAME)) for p in (ds.GAME / "script").rglob("*.java"))
	(tmp_path / "sources.txt").write_text("\n".join(f'"{s}"'.replace("\\", "/") for s in sources), encoding="utf-8")
	result = subprocess.run(
		[ds.jdk("javac"), "-proc:none", "-nowarn", "-encoding", "UTF-8", "-d", str(tmp_path / "classes"),
			"-cp", "l2jserver.jar;libs/*", f"@{tmp_path / 'sources.txt'}"],
		cwd=ds.GAME, capture_output=True, text=True)
	errors = [line for line in (result.stdout + result.stderr).splitlines() if not line.startswith("Note:")]
	assert result.returncode == 0, "\n".join(errors[:60])
