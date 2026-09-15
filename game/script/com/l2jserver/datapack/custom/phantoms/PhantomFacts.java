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
import java.util.Map;

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.SevenSigns;
import com.l2jserver.gameserver.data.sql.impl.ClanTable;
import com.l2jserver.gameserver.datatables.ItemTable;
import com.l2jserver.gameserver.instancemanager.CastleManager;
import com.l2jserver.gameserver.instancemanager.GrandBossManager;
import com.l2jserver.gameserver.instancemanager.MapRegionManager;
import com.l2jserver.gameserver.instancemanager.RaidBossSpawnManager;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.entity.Castle;
import com.l2jserver.gameserver.model.items.L2Item;

/**
 * Values behind the slots of phrases.txt. Everything is read from the running
 * server: zones for the bot's level, raid bosses that are alive, castle owners,
 * the Seven Signs, item prices. That is what makes the bots talk about this server.
 */
public class PhantomFacts {
	private record Zone(int min, int max, String name) {
	}

	private static final String[] EPICS = {
		"Antharas", "Valakas", "Baium", "Queen Ant", "Core", "Orfen"
	};
	private static final int[] EPIC_IDS = {
		29068, 29028, 29020, 29001, 29006, 29014
	};
	private static final String[] ROLES = {
		"healer", "tank", "spoiler", "buffer", "nuker", "bishop", "prophet", "warcryer", "dagger", "archer"
	};

	private final List<Zone> _zones = new ArrayList<>();
	private final PhantomFactory.PhantomGear _gear;

	public PhantomFacts(Path zonesFile, PhantomFactory.PhantomGear gear) throws IOException {
		_gear = gear;
		for (String line : Files.readAllLines(zonesFile)) {
			final String text = line.split("#")[0].trim();
			if (text.isEmpty()) {
				continue;
			}
			final String[] p = text.split("\\s+", 3);
			if (p.length == 3) {
				_zones.add(new Zone(Integer.parseInt(p[0]), Integer.parseInt(p[1]), p[2]));
			}
		}
	}

	/**
	 * Fills the slots of one line. Values are remembered per line, so {@code {weapon}} and
	 * {@code {price}} in the same line belong to the same item.
	 * @param text the line
	 * @param bot the speaking bot
	 * @param playerName name for the {player} slot, may be null
	 * @return the line with slots replaced
	 */
	public String fill(String text, L2PcInstance bot, String playerName) {
		if (text.indexOf('{') < 0) {
			return text;
		}
		final Map<String, String> line = new HashMap<>();
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			if (c != '{') {
				sb.append(c);
				continue;
			}
			final int end = text.indexOf('}', i);
			if (end < 0) {
				sb.append(c);
				continue;
			}
			final String slot = text.substring(i + 1, end);
			String filled = line.get(slot);
			if (filled == null) {
				// value() may remember helper values (the price of the item, the chosen castle),
				// so it must not run inside computeIfAbsent on the same map.
				filled = value(slot, bot, playerName, line);
				line.put(slot, filled);
			}
			sb.append(filled);
			i = end;
		}
		return sb.toString();
	}

	private String value(String slot, L2PcInstance bot, String playerName, Map<String, String> line) {
		switch (slot) {
			case "me":
				return bot.getName();
			case "me.class":
				return PhantomClasses.readable(bot.getClassId());
			case "me.level":
				return String.valueOf(bot.getLevel());
			case "me.race":
				return readable(bot.getRace().name());
			case "town":
				return town(bot);
			case "player":
				return (playerName != null) ? playerName : "mate";
			case "zone":
				return zone(bot.getLevel());
			case "zone.low":
				return zone(Math.max(20, bot.getLevel() - 15));
			case "zone.any":
				return _zones.isEmpty() ? "Cruma Tower" : _zones.get(Rnd.get(_zones.size())).name();
			case "grade":
				return PhantomFactory.PhantomGear.gradeForLevel(bot.getLevel());
			case "weapon":
			case "weapon.other":
				return item(_gear.randomWeaponOfGrade(PhantomFactory.PhantomGear.gradeForLevel(bot.getLevel())), line);
			case "armor":
				return item(_gear.randomSetChestOfGrade(PhantomFactory.PhantomGear.gradeForLevel(bot.getLevel())), line);
			case "price":
				return price(line);
			case "raid":
				return raid(true);
			case "raid.dead":
				return raid(false);
			case "epic":
				return epic();
			case "castle":
				return castle(line)[0];
			case "castle.owner":
				return castle(line)[1];
			case "dawn":
				return SevenSigns.getCabalName(SevenSigns.getInstance().getCabalHighestScore());
			case "online":
				return String.valueOf(onlinePlayers());
			case "need":
				return ROLES[Rnd.get(ROLES.length)];
			default:
				return "";
		}
	}

	private String town(L2PcInstance bot) {
		final var region = MapRegionManager.getInstance().getMapRegion(bot);
		return ((region == null) || (region.getTown() == null)) ? "town" : region.getTown();
	}

	private String zone(int level) {
		final List<Zone> fit = new ArrayList<>();
		for (Zone zone : _zones) {
			if ((level >= zone.min()) && (level <= zone.max())) {
				fit.add(zone);
			}
		}
		if (fit.isEmpty()) {
			return _zones.isEmpty() ? "Cruma Tower" : _zones.get(_zones.size() - 1).name();
		}
		return fit.get(Rnd.get(fit.size())).name();
	}

	private String item(Integer itemId, Map<String, String> line) {
		if (itemId == null) {
			return "something";
		}
		final L2Item template = ItemTable.getInstance().getTemplate(itemId);
		if (template == null) {
			return "something";
		}
		line.putIfAbsent("#price", String.valueOf(template.getReferencePrice()));
		return template.getName();
	}

	private String price(Map<String, String> line) {
		final String known = line.get("#price");
		long value = (known != null) ? Long.parseLong(known) : Rnd.get(50, 5000) * 1000L;
		value = (value * Rnd.get(70, 130)) / 100;
		if (value >= 1000000) {
			return (value / 1000000) + "kk";
		} else if (value >= 1000) {
			return (value / 1000) + "k";
		}
		return String.valueOf(value);
	}

	private String raid(boolean alive) {
		final List<String> names = new ArrayList<>();
		for (var entry : RaidBossSpawnManager.getInstance().getBosses().entrySet()) {
			final boolean isAlive = (entry.getValue() != null) && !entry.getValue().isDead();
			if (isAlive == alive) {
				names.add(entry.getValue().getName());
			}
		}
		if (names.isEmpty()) {
			return alive ? "some raid boss" : "that raid boss";
		}
		return names.get(Rnd.get(names.size()));
	}

	private String epic() {
		final int index = Rnd.get(EPICS.length);
		final int status = GrandBossManager.getInstance().getBossStatus(EPIC_IDS[index]);
		return EPICS[index] + (((status == 0) || (status == 2)) ? " is alive" : " is dead, wait for respawn");
	}

	private String[] castle(Map<String, String> line) {
		final List<Castle> castles = CastleManager.getInstance().getCastles();
		if (castles.isEmpty()) {
			return new String[] {
				"Aden",
				"nobody"
			};
		}
		line.putIfAbsent("#castle", String.valueOf(Rnd.get(castles.size())));
		final int index = Integer.parseInt(line.get("#castle"));
		final Castle castle = castles.get(index);
		final var clan = (castle.getOwnerId() > 0) ? ClanTable.getInstance().getClan(castle.getOwnerId()) : null;
		return new String[] {
			castle.getName(),
			(clan != null) ? clan.getName() : "nobody"
		};
	}

	private int onlinePlayers() {
		int count = 0;
		for (L2PcInstance player : L2World.getInstance().getPlayers()) {
			if ((player.getClient() != null) && !player.getClient().isDetached()) {
				count++;
			}
		}
		return count;
	}

	private static String readable(String name) {
		return name.charAt(0) + name.substring(1).toLowerCase().replace('_', ' ');
	}
}
