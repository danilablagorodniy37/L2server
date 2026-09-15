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

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.GeoData;
import com.l2jserver.gameserver.ai.CtrlIntention;
import com.l2jserver.gameserver.model.Location;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.serverpackets.SocialAction;

/**
 * One bot: walks around its town square, stands, sits down and uses emotes.
 * Chat is decided by {@link Phantoms}, which can pair two bots into a dialog.
 */
public class Phantom {
	/** How a bot types, so the town chat does not look like one person talking. */
	public enum Style {
		/** Plain text. */
		PLAIN,
		/** No capitals, no dots: the usual town chat. */
		LAZY,
		/** Short words: u, r, ty, pls. */
		SHORT,
		/** Adds a lol or a hm at the end. */
		CHATTY;

		private static final String[] TAILS = {
			" lol", " xd", " hm", "", "", ""
		};

		public String apply(String text) {
			switch (this) {
				case LAZY:
					// No dots and no question marks: someone typing in a hurry. Names keep their capitals.
					return text.replace(".", "").replace("?", "");
				case SHORT:
					return text.replace(" you ", " u ").replace(" are ", " r ").replace("thanks", "ty").replace("please", "pls");
				case CHATTY:
					return text + TAILS[Rnd.get(TAILS.length)];
				default:
					return text;
			}
		}
	}

	private static final int[] EMOTES = {
		2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13
	};

	private final L2PcInstance _player;
	private final Location _home;
	private final Location _spawn;
	private final int _radius;
	private final Style _style = Style.values()[Rnd.get(Style.values().length)];
	private long _nextAction;
	private long _nextChat;
	private long _nextReaction;

	public Phantom(L2PcInstance player, Location home, int radius) {
		_player = player;
		_home = home;
		_radius = radius;
		_spawn = new Location(player.getX(), player.getY(), player.getZ());
		_nextAction = System.currentTimeMillis() + Rnd.get(2000, 20000);
	}

	public L2PcInstance player() {
		return _player;
	}

	public Style style() {
		return _style;
	}

	/** Bots do not comment on every single event they see. */
	public boolean canReact(long now) {
		return now >= _nextReaction;
	}

	public void reacted(long now) {
		_nextReaction = now + Rnd.get(45000, 120000);
	}

	/** True when the bot has left the place it was spawned at. */
	public boolean hasMoved() {
		return (_player != null) && (_player.calculateDistance(_spawn, false, false) > 100);
	}

	public boolean chatDue(long now) {
		return now >= _nextChat;
	}

	public void chatDone(long now, int intervalSeconds) {
		_nextChat = now + (Rnd.get(intervalSeconds / 2, (intervalSeconds * 3) / 2) * 1000L);
	}

	/** Delays the next line, so an answer in a dialog does not collide with it. */
	public void delayChat(long millis) {
		_nextChat = Math.max(_nextChat, System.currentTimeMillis() + millis);
	}

	public void act(long now) {
		if ((_player == null) || !_player.isVisible() || (now < _nextAction)) {
			return;
		}

		if (_player.isMoving()) {
			// Still walking, look again in a moment.
			_nextAction = now + 2000;
			return;
		}

		final int roll = Rnd.get(100);
		if (_player.isSitting()) {
			_player.standUp();
			_nextAction = now + Rnd.get(2000, 6000);
		} else if (roll < 60) {
			walk(now);
		} else if (roll < 75) {
			_player.sitDown();
			_nextAction = now + Rnd.get(20000, 60000);
		} else if (roll < 85) {
			_player.broadcastPacket(new SocialAction(_player.getObjectId(), EMOTES[Rnd.get(EMOTES.length)]));
			_nextAction = now + Rnd.get(5000, 15000);
		} else {
			_nextAction = now + Rnd.get(5000, 20000);
		}
	}

	private void walk(long now) {
		for (int i = 0; i < 6; i++) {
			final int x = _home.getX() + Rnd.get(-_radius, _radius);
			final int y = _home.getY() + Rnd.get(-_radius, _radius);
			final int z = GeoData.getInstance().getHeight(x, y, _home.getZ());
			if (Math.abs(z - _home.getZ()) > 500) {
				continue;
			}
			if (GeoData.getInstance().canMove(_player, x, y, z)) {
				_player.setRunning();
				_player.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, new Location(x, y, z));
				_nextAction = now + Rnd.get(4000, 12000);
				return;
			}
		}
		_nextAction = now + Rnd.get(3000, 8000);
	}
}
