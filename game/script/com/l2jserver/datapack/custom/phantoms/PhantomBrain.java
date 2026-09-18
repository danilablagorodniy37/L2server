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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The head of a bot: a small language model running on this machine answers for it.<br>
 * Ollama speaks over plain HTTP, so nothing but the model has to be installed:
 * <code>ollama run llama3.2</code> and the bots start talking for themselves.
 * <p>
 * The phrase book stays: when the model is busy, slow or switched off, the bot falls back to it,
 * so the chat never waits for an answer that may not come.
 */
public class PhantomBrain {
	private static final Logger LOG = LoggerFactory.getLogger(PhantomBrain.class);

	/** How the bot is told to write: the model is a player typing in a hurry, not an assistant. */
	private static final String STYLE = "Answer in character as this player, in one line of at most %d words. "
		+ "Write the way a player types in chat: lowercase, short, no punctuation at the end, no quotes, no emoji, no roleplay asterisks. "
		+ "Never mention being an AI or a model. If you do not know something, say so in a few words.";

	private final HttpClient _http;
	private final String _url;
	private final String _model;
	private final int _words;
	private final Duration _timeout;
	private final Semaphore _slots;
	private final AtomicInteger _answered = new AtomicInteger();
	private final AtomicInteger _missed = new AtomicInteger();
	private volatile boolean _enabled;

	public PhantomBrain(String url, String model, int timeoutMillis, int slots, int words) {
		_url = url;
		_model = model;
		_words = words;
		_timeout = Duration.ofMillis(timeoutMillis);
		_slots = new Semaphore(Math.max(1, slots));
		_http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
		_enabled = true;
	}

	public boolean enabled() {
		return _enabled;
	}

	/** How many lines came from the model, and how many fell back to the phrase book. */
	public int answered() {
		return _answered.get();
	}

	public int missed() {
		return _missed.get();
	}

	/**
	 * What the bot says back.
	 * @param who the bot in a line or two: name, level, class, what it is doing
	 * @param heard what the player said
	 * @param said the bot's own last line, so it does not repeat itself, or null
	 * @return the answer, or null when the model is busy, slow or not there - the caller then uses the phrase book
	 */
	public String reply(String who, String heard, String said) {
		if (!_enabled || !_slots.tryAcquire()) {
			_missed.incrementAndGet();
			return null;
		}
		try {
			final String answer = ask(prompt(who, heard, said));
			if (answer == null) {
				_missed.incrementAndGet();
				return null;
			}
			_answered.incrementAndGet();
			return answer;
		} finally {
			_slots.release();
		}
	}

	private String prompt(String who, String heard, String said) {
		final StringBuilder out = new StringBuilder();
		out.append(who).append('\n');
		if ((said != null) && !said.isBlank()) {
			out.append("A moment ago you said: ").append(said).append('\n');
		}
		out.append("Another player says to you: ").append(heard).append('\n');
		out.append(String.format(STYLE, _words));
		return out.toString();
	}

	private String ask(String prompt) {
		final JsonObject options = new JsonObject();
		options.addProperty("num_predict", 48);
		options.addProperty("temperature", 0.8);
		final JsonObject body = new JsonObject();
		body.addProperty("model", _model);
		body.addProperty("prompt", prompt);
		body.addProperty("stream", false);
		body.add("options", options);

		try {
			final HttpRequest request = HttpRequest.newBuilder(URI.create(_url)) //
				.timeout(_timeout) //
				.header("Content-Type", "application/json") //
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())) //
				.build();
			final HttpResponse<String> response = _http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				LOG.warn("the bot model answered with {}", response.statusCode());
				return null;
			}
			final JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			return clean(json.has("response") ? json.get("response").getAsString() : null);
		} catch (Exception ex) {
			// A model that is not running must not stop the bots from talking.
			LOG.debug("the bot model did not answer: {}", ex.getMessage());
			return null;
		}
	}

	/** One line of chat out of whatever the model wrote. */
	static String clean(String answer) {
		if (answer == null) {
			return null;
		}
		String line = answer.strip();
		final int newLine = line.indexOf('\n');
		if (newLine > 0) {
			line = line.substring(0, newLine).strip();
		}
		line = line.replace("\"", "").replace("*", "").replace("\r", "").strip();
		if (line.length() > 120) {
			line = line.substring(0, 120).strip();
		}
		return line.isEmpty() ? null : line;
	}

	/** Checks the model is there; when it is not, the bots quietly keep to the phrase book. */
	public boolean awake() {
		_enabled = ask("say ok") != null;
		return _enabled;
	}

	@Override
	public String toString() {
		return _model + " at " + _url;
	}
}
