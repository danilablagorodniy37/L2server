/*
 * Copyright © 2004-2024 L2J DataPack
 *
 * This file is part of L2J DataPack.
 *
 * L2J DataPack is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * L2J DataPack is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jserver.datapack.custom.phantoms;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.l2jserver.commons.util.Rnd;

/**
 * Where the bots hunt: the zones of the client map that this world fills with monsters.<br>
 * Built into data/phantoms/hunting.txt by tools/interlude/build_phantom_hunting.py.
 */
public class PhantomHunting {
	/**
	 * One hunting ground.
	 * @param x the point a bot travels to
	 * @param y the point a bot travels to
	 * @param z the point a bot travels to
	 * @param minLevel level of the weakest monster around it
	 * @param maxLevel level of the strongest monster around it
	 * @param monsters how many monsters stand there
	 * @param name the name the client map gives the place
	 */
	public record Ground(int x, int y, int z, int minLevel, int maxLevel, int monsters, String name) {
		/** A bot fights monsters of about its own level: too low is a waste of time, too high is a death. */
		public boolean fits(int level) {
			return (level >= (minLevel - 2)) && (level <= (maxLevel + 5));
		}
	}

	private final List<Ground> _grounds = new ArrayList<>();

	public PhantomHunting(Path file) throws IOException {
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			final String text = line.trim();
			if (text.isEmpty() || text.startsWith("#")) {
				continue;
			}
			final String[] p = text.split("\\s+", 7);
			if (p.length == 7) {
				_grounds.add(new Ground(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
					Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]), p[6]));
			}
		}
	}

	public int size() {
		return _grounds.size();
	}

	/** The grounds a bot of this level can hunt in. */
	public List<Ground> forLevel(int level) {
		final List<Ground> out = new ArrayList<>();
		for (Ground ground : _grounds) {
			if (ground.fits(level)) {
				out.add(ground);
			}
		}
		return out;
	}

	/** A ground for a bot of this level, or null when its level has none. */
	public Ground pick(int level) {
		final List<Ground> fitting = forLevel(level);
		return fitting.isEmpty() ? null : fitting.get(Rnd.get(fitting.size()));
	}

	/** The name of a place a bot of this level would hunt in, for the chat. */
	public String name(int level) {
		final Ground ground = pick(level);
		return ground == null ? "" : ground.name();
	}

	@Override
	public String toString() {
		return _grounds.size() + " hunting grounds";
	}
}
