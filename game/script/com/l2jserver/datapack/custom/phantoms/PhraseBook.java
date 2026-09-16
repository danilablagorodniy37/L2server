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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.l2jserver.commons.util.Rnd;

/**
 * The phrases of data/phantoms/phrases.txt: lines per channel, bot to bot dialogs
 * and answers to players, picked by keywords.
 */
public class PhraseBook {
	/** An answer group: the player writes one of the words, the bot answers with one of the lines. */
	public record Intent(String name, List<String> keywords, List<String> replies) {
	}

	/** A short exchange: one bot says the line, another answers. */
	public record Dialog(String line, String answer) {
	}

	private final Map<String, List<String>> _lines = new HashMap<>();
	private final Map<String, List<List<String>>> _chains = new HashMap<>();
	private final List<Dialog> _dialogs = new ArrayList<>();
	private final List<Intent> _intents = new ArrayList<>();

	public PhraseBook(Path file) throws IOException {
		String section = "general";
		Intent intent = null;
		for (String raw : Files.readAllLines(file)) {
			final String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			if (line.startsWith("[") && line.endsWith("]")) {
				final String header = line.substring(1, line.length() - 1).trim();
				if (header.startsWith("reply ") && header.contains(":")) {
					final String[] parts = header.substring(6).split(":", 2);
					final List<String> keywords = new ArrayList<>();
					for (String keyword : (parts.length > 1 ? parts[1] : "").split("\\|")) {
						if (!keyword.isBlank()) {
							keywords.add(keyword.trim().toLowerCase(Locale.ROOT));
						}
					}
					intent = new Intent(parts[0].trim(), keywords, new ArrayList<>());
					_intents.add(intent);
					section = "reply";
				} else {
					section = header;
					intent = null;
				}
				continue;
			}
			if (section.startsWith("chain")) {
				final String topic = section.length() > 5 ? section.substring(5).trim() : "general";
				final List<String> turns = new ArrayList<>();
				for (String turn : line.split("=>")) {
					if (!turn.isBlank()) {
						turns.add(turn.trim());
					}
				}
				if (turns.size() > 1) {
					_chains.computeIfAbsent(topic, s -> new ArrayList<>()).add(turns);
				}
				continue;
			}
			switch (section) {
				case "reply" -> {
					if (intent != null) {
						intent.replies().add(line);
					}
				}
				case "dialog" -> {
					final String[] parts = line.split("=>", 2);
					if (parts.length == 2) {
						_dialogs.add(new Dialog(parts[0].trim(), parts[1].trim()));
					}
				}
				default -> _lines.computeIfAbsent(section, s -> new ArrayList<>()).add(line);
			}
		}
	}

	public String random(String section) {
		final List<String> lines = _lines.get(section);
		return ((lines == null) || lines.isEmpty()) ? null : lines.get(Rnd.get(lines.size()));
	}

	/** A conversation of three or four turns on a topic; falls back to the general ones. */
	public List<String> randomChain(String topic) {
		List<List<String>> chains = _chains.get(topic);
		if ((chains == null) || chains.isEmpty()) {
			chains = _chains.get("general");
		}
		return ((chains == null) || chains.isEmpty()) ? null : chains.get(Rnd.get(chains.size()));
	}

	public boolean has(String section) {
		final List<String> lines = _lines.get(section);
		return (lines != null) && !lines.isEmpty();
	}

	public Dialog randomDialog() {
		return _dialogs.isEmpty() ? null : _dialogs.get(Rnd.get(_dialogs.size()));
	}

	/**
	 * @param text what a player said
	 * @param botName the bot's name, for the "name" intent
	 * @return an answer or {@code null} when nothing matches
	 */
	public String answerTo(String text, String botName) {
		final String lower = text.toLowerCase(Locale.ROOT);
		final List<String> answers = new ArrayList<>();
		for (Intent intent : _intents) {
			for (String keyword : intent.keywords()) {
				final String needle = keyword.equals("{me}") ? botName.toLowerCase(Locale.ROOT) : keyword;
				if (lower.contains(needle)) {
					answers.addAll(intent.replies());
					break;
				}
			}
		}
		return answers.isEmpty() ? null : answers.get(Rnd.get(answers.size()));
	}

	public int size() {
		int count = _dialogs.size();
		for (List<String> lines : _lines.values()) {
			count += lines.size();
		}
		for (Intent intent : _intents) {
			count += intent.replies().size();
		}
		for (List<List<String>> chains : _chains.values()) {
			for (List<String> chain : chains) {
				count += chain.size();
			}
		}
		return count;
	}
}
