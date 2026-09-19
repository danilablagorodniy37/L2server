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

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The compiled scripts, kept on disk between runs.<br>
 * The datapack is thirteen hundred Java files and compiling them again at every start costs the
 * better part of half a minute. They rarely change, so they are compiled into a folder once and the
 * classes are read from there until a source file, or the server itself, is newer than the folder.
 */
public class ScriptCache {
	private static final Logger LOG = LoggerFactory.getLogger(ScriptCache.class);

	/** What the cache was built from, written next to the classes. */
	private static final String STAMP = "sources.txt";

	private final Path _root;
	private final Path _cache;
	private URLClassLoader _loader;

	public ScriptCache(Path scriptRoot, Path cache) {
		_root = scriptRoot;
		_cache = cache;
	}

	/** Every source of the datapack, in a fixed order. */
	public List<File> sources() {
		final List<File> out = new ArrayList<>();
		try (Stream<Path> files = Files.walk(_root)) {
			files.filter(path -> path.toString().endsWith(".java")).sorted().forEach(path -> out.add(path.toFile()));
		} catch (IOException ex) {
			LOG.warn("could not read the scripts of {}!", _root, ex);
		}
		return out;
	}

	/**
	 * What the sources look like right now: their paths, sizes and times, in one line each.<br>
	 * The classpath of the server goes in as well, because a change in the core makes the classes stale too.
	 * @param sources the files
	 * @param classPath the classpath the scripts are compiled against
	 * @return the stamp of this state
	 */
	public static String stamp(List<File> sources, String classPath) {
		final StringBuilder out = new StringBuilder();
		for (File file : sources) {
			out.append(file.getPath()).append('\t').append(file.length()).append('\t').append(file.lastModified()).append('\n');
		}
		out.append("classpath\t").append(hash(classPath)).append('\n');
		return out.toString();
	}

	private static String hash(String text) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			final byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
			final StringBuilder out = new StringBuilder();
			for (byte b : bytes) {
				out.append(String.format("%02x", b));
			}
			return out.toString();
		} catch (Exception ex) {
			return String.valueOf(text.hashCode());
		}
	}

	/** True when the classes on disk were built from exactly these sources. */
	public boolean valid(String stamp) {
		final Path written = _cache.resolve(STAMP);
		if (!Files.isRegularFile(written)) {
			return false;
		}
		try {
			return Files.readString(written, StandardCharsets.UTF_8).equals(stamp);
		} catch (IOException ex) {
			return false;
		}
	}

	/**
	 * Compiles every script into the cache folder.
	 * @param sources the files
	 * @param classPath the classpath to compile against
	 * @param stamp what to write down once it worked
	 * @return true when everything compiled
	 */
	public boolean compile(List<File> sources, String classPath, String stamp) {
		final JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
		if (javac == null) {
			LOG.info("no java compiler in this runtime, the scripts are compiled in memory as before.");
			return false;
		}
		try {
			clear();
			Files.createDirectories(_cache);
		} catch (IOException ex) {
			LOG.warn("could not make the script cache {}!", _cache, ex);
			return false;
		}

		final DiagnosticCollector<JavaFileObject> problems = new DiagnosticCollector<>();
		try (StandardJavaFileManager files = javac.getStandardFileManager(problems, null, StandardCharsets.UTF_8)) {
			final List<String> options = List.of("-classpath", classPath, "-d", _cache.toString(), "-g", "-nowarn", "-proc:none");
			final boolean ok = javac.getTask(null, files, problems, options, null, files.getJavaFileObjectsFromFiles(sources)).call();
			if (!ok) {
				problems.getDiagnostics().stream() //
					.filter(problem -> problem.getKind() == javax.tools.Diagnostic.Kind.ERROR) //
					.limit(5) //
					.forEach(problem -> LOG.warn("script: {}", problem));
				return false;
			}
		} catch (Exception ex) {
			LOG.warn("could not compile the scripts!", ex);
			return false;
		}

		try {
			Files.writeString(_cache.resolve(STAMP), stamp, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			LOG.warn("could not write the script stamp!", ex);
			return false;
		}
		return true;
	}

	/** The class loader over the cache folder; every script sees every other. */
	public ClassLoader loader() {
		if (_loader == null) {
			try {
				_loader = new URLClassLoader(new java.net.URL[] {
					_cache.toUri().toURL()
				}, ScriptCache.class.getClassLoader());
			} catch (Exception ex) {
				LOG.warn("could not open the script cache {}!", _cache, ex);
			}
		}
		return _loader;
	}

	/**
	 * A class of the cache.
	 * @param name the full name of the class
	 * @return the class, or null when it is not there
	 */
	public Class<?> load(String name) {
		final ClassLoader loader = loader();
		if (loader == null) {
			return null;
		}
		try {
			return Class.forName(name, true, loader);
		} catch (Throwable ex) {
			LOG.warn("the script {} is not in the cache!", name);
			return null;
		}
	}

	/** Throws the compiled classes away, so the next start builds them again. */
	public void clear() throws IOException {
		if (!Files.isDirectory(_cache)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(_cache)) {
			paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
					// a file another process holds is left where it is; the compiler overwrites it
				}
			});
		}
	}

	@Override
	public String toString() {
		return "script cache at " + _cache;
	}
}
