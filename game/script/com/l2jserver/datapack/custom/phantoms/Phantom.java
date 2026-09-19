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
import java.util.ArrayList;
import java.util.List;

import com.l2jserver.gameserver.model.Location;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.serverpackets.SocialAction;
import com.l2jserver.gameserver.network.serverpackets.ValidateLocation;
import com.l2jserver.gameserver.util.Util;

/**
 * One bot: lives in its town, travels to a hunting ground and comes back.
 * Chat is decided by {@link Phantoms}, which can pair two bots into a dialog.
 */
public class Phantom {
	/** How many lines of a conversation a bot keeps in mind. */
	private static final int TALK_LINES = 6;
	/** How long a bot keeps the lines of a conversation. */
	private static final long TALK_KEPT = 5 * 60 * 1000L;

	/** What the bot is busy with. */
	public enum State {
		/** Walking around the town square, sitting, chatting. */
		TOWN,
		/** On the way to a hunting ground. */
		TRAVEL,
		/** At the hunting ground. */
		HUNT,
		/** On the way home. */
		RETURN,
		/** Sitting in Giran with a private store. */
		TRADE
	}

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

	/** How long a bot remembers who talked to it, so a follow-up message keeps the same bot. */
	private static final long TALK_MEMORY = 90000;

	private static final int[] EMOTES = {
		2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13
	};

	private final L2PcInstance _player;
	private final Location _home;
	private final Location _spawn;
	private final int _radius;
	private final Style _style = Style.values()[Rnd.get(Style.values().length)];
	private State _state = State.TOWN;
	private PhantomHunting.Ground _ground;
	private long _stateSince;
	private long _stateUntil;
	private long _nextAction;
	private long _nextChat;
	private long _nextSkill;
	private long _restUntil;
	private int _kills;
	private int _deaths;
	private long _earned;
	private long _diedAt;
	private boolean _inSquad;
	private boolean _townOnly;
	private long _nextSupply;
	private long _nextRestock;
	/** The last few lines said to this bot and by it, so a conversation holds together. */
	private final List<String> _talk = new ArrayList<>();
	private String _talkingWith;
	private long _memoryUntil;
	private boolean _buying;
	private long _nextReaction;
	private String _talkingTo;
	private long _talkUntil;
	private String _lastAnswer;

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

	/** True while the bot is in the middle of a conversation with this player. */
	public boolean isTalkingTo(String playerName, long now) {
		return playerName.equals(_talkingTo) && (now < _talkUntil);
	}

	public void startTalking(String playerName, String answer, long now) {
		_talkingTo = playerName;
		_talkUntil = now + TALK_MEMORY;
		_lastAnswer = answer;
	}

	public String lastAnswer() {
		return _lastAnswer;
	}

	/** Stops walking and turns to the player who is talking to the bot. */
	public void lookAt(L2PcInstance player) {
		if ((_player == null) || !_player.isVisible()) {
			return;
		}
		if (_player.isSitting()) {
			_player.standUp();
		}
		_player.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		_player.setHeading(Util.calculateHeadingFrom(_player, player));
		_player.broadcastPacket(new ValidateLocation(_player));
		// Stay put while the conversation lasts.
		_nextAction = System.currentTimeMillis() + Rnd.get(15000, 25000);
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

	public State state() {
		return _state;
	}

	public PhantomHunting.Ground ground() {
		return _ground;
	}

	/** The town square the bot lives on. */
	public Location home() {
		return _home;
	}

	/** True when the bot has been in this state longer than it planned. */
	public boolean stateOver(long now) {
		return now >= _stateUntil;
	}

	public long stateSince() {
		return _stateSince;
	}

	/**
	 * Puts the bot into a state for a while.
	 * @param state what it does now
	 * @param ground the hunting ground it travels to or hunts in, null in town
	 * @param millis how long it stays in this state before deciding again
	 */
	public void enter(State state, PhantomHunting.Ground ground, long millis) {
		_state = state;
		_ground = ground;
		_stateSince = System.currentTimeMillis();
		_stateUntil = _stateSince + millis;
		_nextAction = _stateSince;
	}

	public boolean skillDue(long now) {
		return now >= _nextSkill;
	}

	public void skillUsed(long now) {
		_nextSkill = now + Rnd.get(4000, 9000);
	}

	public boolean resting(long now) {
		return now < _restUntil;
	}

	public void rest(long millis) {
		_restUntil = System.currentTimeMillis() + millis;
	}

	public void stopResting() {
		_restUntil = 0;
	}

	public int kills() {
		return _kills;
	}

	public void killed() {
		_kills++;
	}

	public int deaths() {
		return _deaths;
	}

	public void died() {
		_deaths++;
	}
	
	/** The adena the bot made selling what it brought back from hunting. */
	public long earned() {
		return _earned;
	}
	
	public void earned(long adena) {
		_earned += adena;
	}

	/** When the bot fell, so its party knows how long it has been lying there. */
	public long diedAt() {
		return _diedAt;
	}

	public void diedAt(long when) {
		_diedAt = when;
	}

	/** True when the bot hunts in a standing party and not on its own. */
	public boolean inSquad() {
		return _inSquad;
	}

	public void inSquad(boolean value) {
		_inSquad = value;
	}

	/** True for a bot that lives in the towns and never goes out to hunt. */
	public boolean townOnly() {
		return _townOnly;
	}

	public void townOnly(boolean value) {
		_townOnly = value;
	}

	/** Shots run out after a few thousand swings: true when it is time to fill the pouch again. */
	public boolean supplyDue(long now) {
		return now > _nextSupply;
	}

	public void supplied(long now) {
		_nextSupply = now + 60000L;
	}

	/**
	 * Keeps what was said, so the next answer follows the one before it.
	 * @param who the player talking to the bot
	 * @param heard what they said
	 * @param answer what the bot answered
	 * @param now the time
	 */
	public void remember(String who, String heard, String answer, long now) {
		if ((who == null) || !who.equals(_talkingWith) || (now > _memoryUntil)) {
			_talk.clear();
			_talkingWith = who;
		}
		_talkUntil = now + TALK_MEMORY;
		if (heard != null) {
			_talk.add(who + ": " + heard);
		}
		if (answer != null) {
			_talk.add("you: " + answer);
		}
		while (_talk.size() > TALK_LINES) {
			_talk.remove(0);
		}
	}

	/**
	 * The conversation so far with this player, a few lines of it.
	 * @param who the player
	 * @param now the time
	 * @return the lines, or null when there is nothing to remember
	 */
	public String talk(String who, long now) {
		if (_talk.isEmpty() || (now > _memoryUntil) || (who == null) || !who.equals(_talkingWith)) {
			return null;
		}
		return String.join("\n", _talk);
	}

	/** True for a shopkeeper that buys gear instead of selling it. */
	public boolean buying() {
		return _buying;
	}

	public void buying(boolean value) {
		_buying = value;
	}

	/** True when the shop has stood with the same goods long enough. */
	public boolean restockDue(long now, int minutes) {
		return now > (_nextRestock + (minutes * 60000L));
	}

	public void restocked(long now) {
		_nextRestock = now;
	}

	/** Where the bot walks around: its town square, or the hunting ground it travels to. */
	private Location anchor() {
		return ((_state == State.HUNT) && (_ground != null)) ? new Location(_ground.x(), _ground.y(), _ground.z()) : _home;
	}

	public void act(long now) {
		if ((_player == null) || !_player.isVisible() || (now < _nextAction) || (_state == State.TRADE)) {
			return;
		}
		if ((_state == State.HUNT) && (_player.getTarget() != null) && _player.isInCombat()) {
			// busy fighting, the hunting tick drives it
			_nextAction = now + 2000;
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
		final Location around = anchor();
		for (int i = 0; i < 6; i++) {
			final int x = around.getX() + Rnd.get(-_radius, _radius);
			final int y = around.getY() + Rnd.get(-_radius, _radius);
			final int z = GeoData.getInstance().getHeight(x, y, around.getZ());
			if (Math.abs(z - around.getZ()) > 500) {
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
