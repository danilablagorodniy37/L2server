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

import static com.l2jserver.gameserver.config.Configuration.server;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.handler.ChatHandler;
import com.l2jserver.gameserver.instancemanager.MapRegionManager;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.Location;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.events.Containers;
import com.l2jserver.gameserver.model.events.EventType;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerChat;
import com.l2jserver.gameserver.model.events.listeners.ConsumerEventListener;
import com.l2jserver.gameserver.network.L2GameClient;
import com.l2jserver.gameserver.network.clientpackets.Say2;

/**
 * Phantom players: characters without a client that live in the big towns, walk
 * around and chat about this server. Switched on in config/phantoms.properties.
 */
public class Phantoms {
	private static final Logger LOG = LoggerFactory.getLogger(Phantoms.class);
	/** Bot lines go to the usual chat log, so they can be read in game/logs/chat. */
	private static final Logger LOG_CHAT = LoggerFactory.getLogger("chat");

	private static final int DIALOG_RANGE = 700;
	private static final int REPLY_RANGE = 900;

	private final List<Phantom> _phantoms = new ArrayList<>();
	private final Set<Integer> _objectIds = new HashSet<>();
	private final Map<Integer, Long> _regionChat = new HashMap<>();

	private final Properties _config = new Properties();
	private PhraseBook _phrases;
	private PhantomFacts _facts;
	private PhantomFactory _factory;
	private String[] _towns;
	private int _chatInterval;
	private int _regionChatInterval;
	private int _radius;
	private boolean _replyToPlayers;

	public Phantoms() {
		final File root = server().getDatapackRoot();
		try (var in = new FileInputStream(new File(root, "config/phantoms.properties"))) {
			_config.load(in);
		} catch (Exception ex) {
			LOG.info("config/phantoms.properties not found, bots are off.");
			return;
		}

		if (!Boolean.parseBoolean(_config.getProperty("Enabled", "False").trim())) {
			return;
		}

		try {
			final Path data = new File(root, "data/phantoms").toPath();
			_phrases = new PhraseBook(data.resolve("phrases.txt"));
			_factory = new PhantomFactory(data.resolve("gear.txt"), property("Account", "phantoms"), number("MinLevel", 20), number("MaxLevel", 80));
			_facts = new PhantomFacts(data.resolve("zones.txt"), _factory.gear());
		} catch (Exception ex) {
			LOG.warn("could not read data/phantoms!", ex);
			return;
		}

		_towns = property("Towns", "giran_castle_town").split(",");
		_chatInterval = number("ChatInterval", 180);
		_regionChatInterval = number("RegionChatInterval", 40);
		_radius = number("WanderRadius", 1500);
		_replyToPlayers = Boolean.parseBoolean(_config.getProperty("ReplyToPlayers", "True").trim());

		final int count = number("Count", 100);
		ThreadPoolManager.getInstance().scheduleGeneral(() -> spawnAll(count), number("StartDelay", 30), TimeUnit.SECONDS);
	}

	private String property(String key, String fallback) {
		return _config.getProperty(key, fallback).trim();
	}

	private int number(String key, int fallback) {
		try {
			return Integer.parseInt(property(key, String.valueOf(fallback)));
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private void spawnAll(int count) {
		_factory.blockAccount();
		final List<Integer> stored = _factory.storedCharacters();
		int created = 0;
		for (int i = 0; i < count; i++) {
			L2PcInstance player = null;
			if (i < stored.size()) {
				player = L2PcInstance.load(stored.get(i));
			} else {
				player = _factory.create();
				created++;
			}
			if (player == null) {
				continue;
			}
			final Location home = townSquare(_towns[i % _towns.length]);
			if (home == null) {
				continue;
			}
			enterWorld(player, home);
			final Phantom phantom = new Phantom(player, home, _radius);
			// Spread the first lines over one chat interval instead of all at once.
			phantom.chatDone(System.currentTimeMillis(), _chatInterval);
			_phantoms.add(phantom);
			_objectIds.add(player.getObjectId());
		}

		if (_phantoms.isEmpty()) {
			LOG.warn("no bots could be spawned.");
			return;
		}

		ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(this::tick, 5, 2, TimeUnit.SECONDS);
		if (_replyToPlayers) {
			Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.PLAYER_CHAT, (PlayerChat event) -> onPlayerChat(event), this));
		}
		LOG.info("{} bots in the world ({} created), {} phrases.", _phantoms.size(), created, _phrases.size());
		ThreadPoolManager.getInstance().scheduleGeneral(() -> {
			final long moved = _phantoms.stream().filter(Phantom::hasMoved).count();
			LOG.info("{} of {} bots are walking around.", moved, _phantoms.size());
		}, 90, TimeUnit.SECONDS);
	}

	private Location townSquare(String region) {
		final var mapRegion = MapRegionManager.getInstance().getMapRegionByName(region.trim());
		if ((mapRegion == null) || mapRegion.getSpawns().isEmpty()) {
			LOG.warn("unknown town {}!", region);
			return null;
		}
		return mapRegion.getSpawns().get(Rnd.get(mapRegion.getSpawns().size()));
	}

	private void enterWorld(L2PcInstance player, Location home) {
		final L2GameClient client = new L2GameClient(null);
		client.setDetached(true);
		client.setActiveChar(player);
		player.setClient(client);
		player.setOnlineStatus(true, false);
		player.setXYZInvisible(home.getX() + Rnd.get(-200, 200), home.getY() + Rnd.get(-200, 200), home.getZ());
		L2World.getInstance().addPlayerToWorld(player);
		player.spawnMe(player.getX(), player.getY(), player.getZ());
		player.setRunning();
		player.broadcastUserInfo();
	}

	private void tick() {
		final long now = System.currentTimeMillis();
		for (Phantom phantom : _phantoms) {
			try {
				phantom.act(now);
				if (phantom.chatDue(now)) {
					phantom.chatDone(now, _chatInterval);
					speak(phantom, now);
				}
			} catch (Exception ex) {
				LOG.warn("{} failed to act!", phantom.player().getName(), ex);
			}
		}
	}

	private void speak(Phantom phantom, long now) {
		final Phantom listener = neighbour(phantom, DIALOG_RANGE);
		if ((listener != null) && (Rnd.get(100) < 35)) {
			final PhraseBook.Dialog dialog = _phrases.randomDialog();
			if (dialog != null) {
				say(phantom, Say2.ALL, dialog.line(), null);
				listener.delayChat(15000);
				ThreadPoolManager.getInstance().scheduleGeneral(() -> say(listener, Say2.ALL, dialog.answer(), null), Rnd.get(3000, 8000));
				return;
			}
		}

		final int roll = Rnd.get(100);
		if ((roll < 12) && regionFree(phantom, now)) {
			say(phantom, Say2.TRADE, _phrases.random("trade"), null);
		} else if ((roll < 20) && regionFree(phantom, now)) {
			say(phantom, Say2.SHOUT, _phrases.random("shout"), null);
		} else {
			say(phantom, Say2.ALL, _phrases.random("general"), null);
		}
	}

	/** Trade and shout are heard by a whole region, so they are rate limited per region. */
	private boolean regionFree(Phantom phantom, long now) {
		final int region = MapRegionManager.getInstance().getMapRegionLocId(phantom.player());
		final Long last = _regionChat.get(region);
		if ((last != null) && ((now - last) < (_regionChatInterval * 1000L))) {
			return false;
		}
		_regionChat.put(region, now);
		return true;
	}

	private Phantom neighbour(Phantom phantom, int range) {
		final List<Phantom> near = new ArrayList<>();
		for (Phantom other : _phantoms) {
			if ((other != phantom) && other.player().isVisible() && (other.player().calculateDistance(phantom.player(), false, false) < range)) {
				near.add(other);
			}
		}
		return near.isEmpty() ? null : near.get(Rnd.get(near.size()));
	}

	private void say(Phantom phantom, int type, String text, String playerName) {
		if ((text == null) || !phantom.player().isVisible()) {
			return;
		}
		final var handler = ChatHandler.getInstance().getHandler(type);
		if (handler != null) {
			final String line = _facts.fill(text, phantom.player(), playerName);
			handler.handleChat(type, phantom.player(), null, line);
			LOG_CHAT.info("[phantom] {} says [{}].", phantom.player().getName(), line);
		}
	}

	private void onPlayerChat(PlayerChat event) {
		final L2PcInstance player = event.player();
		if ((player == null) || _objectIds.contains(player.getObjectId()) || (event.chatType() != Say2.ALL)) {
			return;
		}

		final List<Phantom> near = new ArrayList<>();
		for (Phantom phantom : _phantoms) {
			if (phantom.player().isVisible() && (phantom.player().calculateDistance(player, false, false) < REPLY_RANGE)) {
				near.add(phantom);
			}
		}
		if (near.isEmpty()) {
			return;
		}

		final Phantom phantom = near.get(Rnd.get(near.size()));
		final String answer = _phrases.answerTo(event.text(), phantom.player().getName());
		if (answer != null) {
			phantom.delayChat(20000);
			ThreadPoolManager.getInstance().scheduleGeneral(() -> say(phantom, Say2.ALL, answer, player.getName()), Rnd.get(2000, 6000));
		}
	}

	public static void main(String[] args) {
		new Phantoms();
	}
}
