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
package com.l2jserver.datapack.handlers.admincommandhandlers;

import static com.l2jserver.datapack.handlers.admincommandhandlers.AdminPanelHtml.color;
import static com.l2jserver.datapack.handlers.admincommandhandlers.AdminPanelHtml.duration;
import static com.l2jserver.datapack.handlers.admincommandhandlers.AdminPanelHtml.link;

import com.l2jserver.datapack.custom.phantoms.Phantoms;
import com.l2jserver.gameserver.GameServer;
import com.l2jserver.gameserver.data.xml.impl.NpcData;
import com.l2jserver.gameserver.handler.IAdminCommandHandler;
import com.l2jserver.gameserver.instancemanager.GrandBossManager;
import com.l2jserver.gameserver.instancemanager.RaidBossSpawnManager;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.StatsSet;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

/**
 * //panel - one page that says how things stand, with the four panels (bots, bosses, players, server) a click away.
 */
public class AdminPanel implements IAdminCommandHandler {
	private static final String[] ADMIN_COMMANDS = {
		"admin_panel"
	};

	@Override
	public boolean useAdminCommand(String command, L2PcInstance gm) {
		final StringBuilder out = AdminPanelHtml.page("Panel");
		int people = 0;
		for (L2PcInstance player : L2World.getInstance().getPlayers()) {
			if (!player.isPhantom() && !player.isInOfflineMode()) {
				people++;
			}
		}
		final Runtime runtime = Runtime.getRuntime();
		out.append("<table width=270>");
		out.append(row(link("Players", "admin_players"), color(String.valueOf(people), "LEVEL") + " online"));
		final int bots = Phantoms.all().size();
		out.append(row(link("Bots", "admin_bots"), bots == 0 ? "off" : bots + (Phantoms.paused() ? color(" stopped", "FF6666") : " running")));

		final RaidBossSpawnManager raids = RaidBossSpawnManager.getInstance();
		final long dead = raids.getSpawns().keySet().stream().filter(id -> raids.getRespawnTime(id) > 0).count();
		out.append(row(link("Raid bosses", "admin_bosses raid all"), (raids.getSpawns().size() - dead) + " alive, " + dead + " dead"));
		// the epic boss that comes back first
		final long now = System.currentTimeMillis();
		int next = 0;
		long nextTime = Long.MAX_VALUE;
		for (int id : GrandBossManager.getInstance().getBossIds()) {
			final StatsSet info = GrandBossManager.getInstance().getStatsSet(id);
			final long respawn = info == null ? 0 : info.getLong("respawn_time", 0);
			if ((respawn > now) && (respawn < nextTime)) {
				next = id;
				nextTime = respawn;
			}
		}
		out.append(row(link("Epic bosses", "admin_bosses"), next == 0 ? "none dead" : NpcData.getInstance().getTemplate(next).getName() + " in " + duration(nextTime - now)));
		out.append(row(link("Server", "admin_srv"), "up " + duration(now - GameServer.dateTimeServerStarted.getTimeInMillis()) + ", "
			+ ((runtime.totalMemory() - runtime.freeMemory()) >> 20) + " MB"));
		out.append("</table>");
		AdminPanelHtml.send(gm, out);
		return true;
	}

	private static String row(String what, String value) {
		return "<tr><td width=100>" + what + "</td><td>" + value + "</td></tr>";
	}

	@Override
	public String[] getAdminCommandList() {
		return ADMIN_COMMANDS;
	}
}
