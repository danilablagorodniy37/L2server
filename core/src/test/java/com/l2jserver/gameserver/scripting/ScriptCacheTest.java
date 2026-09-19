/*
 * Copyright © 2004-2025 L2J Server
 *
 * This file is part of L2J Server.
 *
 * L2J Server is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * L2J Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jserver.gameserver.scripting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Script cache test: what makes the compiled classes stale.
 */
class ScriptCacheTest {

	@Test
	void theStampFollowsTheSources(@TempDir Path root) throws IOException {
		final Path script = root.resolve("Quest.java");
		Files.writeString(script, "class Quest {}", StandardCharsets.UTF_8);
		final ScriptCache cache = new ScriptCache(root, root.resolve("cache"));

		final List<java.io.File> sources = cache.sources();
		assertEquals(1, sources.size());
		final String before = ScriptCache.stamp(sources, "core.jar");

		// the same files and the same classpath mean the same stamp
		assertEquals(before, ScriptCache.stamp(cache.sources(), "core.jar"));
		// a new core, and the classes have to be built again
		assertNotEquals(before, ScriptCache.stamp(cache.sources(), "core.jar;new.jar"));

		Files.writeString(script, "class Quest { void more() {} }", StandardCharsets.UTF_8);
		Files.setLastModifiedTime(script, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
		assertNotEquals(before, ScriptCache.stamp(cache.sources(), "core.jar"), "an edited script must make the cache stale");
	}

	@Test
	void anEmptyCacheIsNotValid(@TempDir Path root) throws IOException {
		Files.writeString(root.resolve("Quest.java"), "class Quest {}", StandardCharsets.UTF_8);
		final Path folder = root.resolve("cache");
		final ScriptCache cache = new ScriptCache(root, folder);
		final String stamp = ScriptCache.stamp(cache.sources(), "core.jar");

		assertFalse(cache.valid(stamp), "nothing is compiled yet");

		Files.createDirectories(folder);
		Files.writeString(folder.resolve("sources.txt"), stamp, StandardCharsets.UTF_8);
		assertTrue(cache.valid(stamp));
		assertFalse(cache.valid(stamp + "one more script\n"), "another set of sources is another cache");
	}

	@Test
	void aScriptTreeIsListedInOneOrder(@TempDir Path root) throws IOException {
		Files.createDirectories(root.resolve("ai"));
		Files.writeString(root.resolve("ai").resolve("Beast.java"), "class Beast {}", StandardCharsets.UTF_8);
		Files.writeString(root.resolve("Alpha.java"), "class Alpha {}", StandardCharsets.UTF_8);
		Files.writeString(root.resolve("notes.txt"), "not a script", StandardCharsets.UTF_8);
		final ScriptCache cache = new ScriptCache(root, root.resolve("cache"));

		final List<java.io.File> sources = cache.sources();

		assertEquals(2, sources.size(), "only the java files count");
		assertEquals(sources, cache.sources(), "the order does not change between runs");
	}
}
