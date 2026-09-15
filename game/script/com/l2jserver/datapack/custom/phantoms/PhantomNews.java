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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.model.actor.L2Attackable;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.events.Containers;
import com.l2jserver.gameserver.model.events.EventType;
import com.l2jserver.gameserver.model.events.impl.character.CreatureKill;
import com.l2jserver.gameserver.model.events.impl.character.npc.attackable.AttackableKill;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerLevelChanged;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerLogin;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerLogout;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerProfessionChange;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerPvPKill;
import com.l2jserver.gameserver.model.events.impl.character.player.clan.PlayerClanCreate;
import com.l2jserver.gameserver.model.events.impl.olympiad.OlympiadMatchResult;
import com.l2jserver.gameserver.model.events.impl.sieges.castle.CastleSiegeFinish;
import com.l2jserver.gameserver.model.events.impl.sieges.castle.CastleSiegeOwnerChange;
import com.l2jserver.gameserver.model.events.impl.sieges.castle.CastleSiegeStart;
import com.l2jserver.gameserver.model.events.listeners.ConsumerEventListener;

/**
 * What happened on the server lately: raid bosses that fell, deaths, level ups,
 * sieges. Bots talk about these instead of empty small talk, and comment at once
 * on what happens next to them.
 */
public class PhantomNews {
	/**
	 * One event.
	 * @param kind matches the [seen kind] and [news kind] sections of phrases.txt
	 * @param subject who did it
	 * @param object what or whom
	 * @param extra a castle, a class, a zone
	 * @param global true when the whole server would know, false for what was seen on the spot
	 */
	public record Item(String kind, long time, int x, int y, String subject, String object, String extra, boolean global) {
	}

	private static final int MEMORY_SIZE = 60;
	/** How often the bots may bring the same event up, so one raid kill is not the only topic of the day. */
	private static final int MAX_MENTIONS_GLOBAL = 6;
	private static final int MAX_MENTIONS_LOCAL = 3;

	private final Deque<Item> _items = new ArrayDeque<>();
	private final java.util.Map<Item, Integer> _mentions = new java.util.IdentityHashMap<>();
	private final Consumer<Item> _onEvent;
	private final Object _owner;

	public PhantomNews(Object owner, Consumer<Item> onEvent) {
		_owner = owner;
		_onEvent = onEvent;
		listen(EventType.ATTACKABLE_KILL, (AttackableKill e) -> onAttackableKill(e));
		listen(EventType.CREATURE_KILL, (CreatureKill e) -> onCreatureKill(e));
		listen(EventType.PLAYER_PVP_KILL, (PlayerPvPKill e) -> add(new Item("pvp", now(), e.player().getX(), e.player().getY(), e.player().getName(), e.target().getName(), "", true)));
		listen(EventType.PLAYER_LEVEL_CHANGED, (PlayerLevelChanged e) -> onLevel(e));
		listen(EventType.PLAYER_PROFESSION_CHANGE, (PlayerProfessionChange e) -> add(new Item("profession", now(), e.player().getX(), e.player().getY(), e.player().getName(), PhantomClasses.readable(e.player().getClassId()), "", e.player().getLevel() >= 76)));
		listen(EventType.PLAYER_LOGIN, (PlayerLogin e) -> add(new Item("login", now(), e.player().getX(), e.player().getY(), e.player().getName(), "", "", false)));
		listen(EventType.PLAYER_LOGOUT, (PlayerLogout e) -> add(new Item("logout", now(), e.player().getX(), e.player().getY(), e.player().getName(), "", "", false)));
		listen(EventType.PLAYER_CLAN_CREATE, (PlayerClanCreate e) -> add(new Item("clan", now(), e.player().getX(), e.player().getY(), e.player().getName(), e.clan().getName(), "", true)));
		listen(EventType.OLYMPIAD_MATCH_RESULT, (OlympiadMatchResult e) -> add(new Item("olympiad", now(), 0, 0, e.winner().getName(), e.loser().getName(), "", true)));
		listen(EventType.CASTLE_SIEGE_START, (CastleSiegeStart e) -> add(new Item("siegestart", now(), 0, 0, "", "", e.siege().getCastle().getName(), true)));
		listen(EventType.CASTLE_SIEGE_FINISH, (CastleSiegeFinish e) -> add(new Item("siegeend", now(), 0, 0, "", "", e.siege().getCastle().getName(), true)));
		listen(EventType.CASTLE_SIEGE_OWNER_CHANGE, (CastleSiegeOwnerChange e) -> add(new Item("owner", now(), 0, 0, owner(e), "", e.siege().getCastle().getName(), true)));
	}

	private <T extends com.l2jserver.gameserver.model.events.impl.BaseEvent> void listen(EventType type, Consumer<T> callback) {
		Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), type, callback, _owner));
	}

	private static long now() {
		return System.currentTimeMillis();
	}

	private static String owner(CastleSiegeOwnerChange event) {
		final var clan = event.siege().getCastle().getOwner();
		return (clan != null) ? clan.getName() : "nobody";
	}

	private void onAttackableKill(AttackableKill event) {
		final L2Attackable target = event.target();
		final L2PcInstance killer = event.attacker();
		if ((target == null) || (killer == null) || !target.isRaid()) {
			return;
		}
		add(new Item("raidkill", now(), target.getX(), target.getY(), killer.getName(), target.getName(), String.valueOf(target.getLevel()), true));
	}

	private void onCreatureKill(CreatureKill event) {
		// A player killed by a monster: the bots around saw it.
		if ((event.target() instanceof L2PcInstance victim) && (event.attacker() instanceof L2Attackable monster)) {
			add(new Item("death", now(), victim.getX(), victim.getY(), victim.getName(), monster.getName(), "", false));
		}
	}

	private void onLevel(PlayerLevelChanged event) {
		if (event.newLevel() <= event.oldLevel()) {
			return;
		}
		add(new Item("levelup", now(), event.player().getX(), event.player().getY(), event.player().getName(), String.valueOf(event.newLevel()), "", event.newLevel() >= 76));
	}

	public void add(Item item) {
		synchronized (_items) {
			_items.addFirst(item);
			while (_items.size() > MEMORY_SIZE) {
				_mentions.remove(_items.removeLast());
			}
		}
		if (_onEvent != null) {
			_onEvent.accept(item);
		}
	}

	/** Something that happened next to this spot and is still worth mentioning. */
	public Item seenNear(int x, int y, int radius, long maxAge) {
		return pick(false, x, y, radius, maxAge);
	}

	/** Server news: everybody would know about it. */
	public Item recentGlobal(long maxAge) {
		return pick(true, 0, 0, 0, maxAge);
	}

	/** Counts that the bots talked about this event. */
	public void mention(Item item) {
		synchronized (_items) {
			_mentions.merge(item, 1, Integer::sum);
		}
	}
	
	private Item pick(boolean global, int x, int y, int radius, long maxAge) {
		final List<Item> fit = new ArrayList<>();
		final long oldest = now() - maxAge;
		final int limit = global ? MAX_MENTIONS_GLOBAL : MAX_MENTIONS_LOCAL;
		synchronized (_items) {
			for (Item item : _items) {
				if ((item.time() < oldest) || (item.global() != global) || (_mentions.getOrDefault(item, 0) >= limit)) {
					continue;
				}
				if (!global) {
					final long dx = item.x() - x;
					final long dy = item.y() - y;
					if (((dx * dx) + (dy * dy)) > ((long) radius * radius)) {
						continue;
					}
				}
				fit.add(item);
			}
		}
		return fit.isEmpty() ? null : fit.get(Rnd.get(fit.size()));
	}

	/** "just now", "5 minutes ago". */
	public static String ago(long time) {
		final long minutes = (now() - time) / 60000;
		if (minutes < 1) {
			return "just now";
		} else if (minutes == 1) {
			return "a minute ago";
		} else if (minutes < 60) {
			return minutes + " minutes ago";
		}
		return "a while ago";
	}
}
