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

import static com.l2jserver.gameserver.config.Configuration.rates;

import java.util.StringTokenizer;

import com.l2jserver.datapack.custom.phantoms.Phantoms;
import com.l2jserver.gameserver.GameServer;
import com.l2jserver.gameserver.handler.IAdminCommandHandler;
import com.l2jserver.gameserver.instancemanager.GrandBossManager;
import com.l2jserver.gameserver.instancemanager.RaidBossSpawnManager;
import com.l2jserver.gameserver.model.StatsSet;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * One page that says how this server is doing and takes the GM where the work is: how long it has
 * been up and who is on it, every epic of Interlude with the time left on it, how many raid bosses
 * are standing, what the bots are doing, and the handful of commands a GM uses all day.
 * <ul>
 * <li>//panel - the page</li>
 * <li>//panel go &lt;boss id&gt; - fly to that boss, alive or not</li>
 * </ul>
 */
public class AdminPanel implements IAdminCommandHandler {
	private static final String[] ADMIN_COMMANDS = {
		"admin_panel"
	};

	/** The epics this server has, in the order of the chronicle. */
	private record Epic(int id, String name) {
	}

	private static final Epic[] EPICS = {
		new Epic(29001, "Queen Ant"),
		new Epic(29006, "Core"),
		new Epic(29014, "Orfen"),
		new Epic(29022, "Zaken"),
		new Epic(29020, "Baium"),
		new Epic(29045, "Frintezza"),
		new Epic(29068, "Antharas"),
		new Epic(29028, "Valakas"),
	};

	private static final String BUTTON = "back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"";

	@Override
	public boolean useAdminCommand(String command, L2PcInstance activeChar) {
		final StringTokenizer parts = new StringTokenizer(command);
		parts.nextToken();
		if (parts.hasMoreTokens() && "go".equals(parts.nextToken()) && parts.hasMoreTokens()) {
			go(activeChar, parts.nextToken());
			return true;
		}
		final NpcHtmlMessage page = new NpcHtmlMessage();
		page.setHtml(page(activeChar));
		activeChar.sendPacket(page);
		return true;
	}

	/** Takes the GM to the lair of a boss, whether it is standing there or not. */
	private void go(L2PcInstance activeChar, String id) {
		final StatsSet info = statsSet(id);
		if (info == null) {
			activeChar.sendMessage("No such boss in grandboss_data.");
			return;
		}
		activeChar.teleToLocation(info.getInt("loc_x"), info.getInt("loc_y"), info.getInt("loc_z"), true);
	}

	private static StatsSet statsSet(String id) {
		try {
			return GrandBossManager.getInstance().getStatsSet(Integer.parseInt(id));
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private String page(L2PcInstance activeChar) {
		final StringBuilder out = new StringBuilder(4096);
		out.append("<html><title>Server Panel</title><body>");
		out.append("<center><table width=280 border=0 bgcolor=\"444444\"><tr>");
		out.append(tab("Panel", "admin_panel")).append(tab("Bots", "admin_bots"));
		out.append(tab("Main", "admin_admin")).append(tab("Server", "admin_admin4"));
		out.append("</tr></table></center>");

		int players = 0;
		int bots = 0;
		for (L2PcInstance player : L2World.getInstance().getPlayers()) {
			if (player.isPhantom()) {
				bots++;
			} else {
				players++;
			}
		}
		final Runtime runtime = Runtime.getRuntime();
		final long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
		out.append("<table width=280>");
		out.append(row("Up for", since(GameServer.dateTimeServerStarted.getTimeInMillis())));
		out.append(row("Playing", players + " players and " + bots + " bots"));
		out.append(row("Rates", "xp x" + (int) rates().getRateXp() + ", sp x" + (int) rates().getRateSp()
			+ ", drop x" + (int) rates().getDeathDropChanceMultiplier()));
		out.append(row("Memory", used + " of " + (runtime.maxMemory() / (1024 * 1024)) + " MB"));
		out.append("</table><br>");

		out.append("<center>Epic bosses</center>");
		out.append("<table width=280>");
		for (Epic epic : EPICS) {
			out.append("<tr><td width=80>").append(epic.name()).append("</td><td width=150>");
			out.append(state(epic)).append("</td><td width=45>");
			out.append("<button value=\"Go\" action=\"bypass -h admin_panel go ").append(epic.id());
			out.append("\" width=40 height=20 ").append(BUTTON).append("></td></tr>");
		}
		out.append("</table>");
		final int standing = RaidBossSpawnManager.getInstance().getBosses().size();
		final int known = RaidBossSpawnManager.getInstance().getSpawns().size();
		out.append("Raid bosses standing: <font color=\"LEVEL\">").append(standing);
		out.append("</font> of ").append(known).append("<br>");

		final String summary = Phantoms.summary();
		out.append("<br>Bots: <font color=\"LEVEL\">").append(summary == null ? "not running" : summary).append("</font><br>");

		out.append("<center>Right now</center><table width=280><tr>");
		out.append(cell("Speed", "admin_gmspeed 4")).append(cell("Normal", "admin_gmspeed 0"));
		out.append(cell(activeChar.isInvisible() ? "Show" : "Hide", activeChar.isInvisible() ? "admin_vis" : "admin_invis"));
		out.append(cell("Invul", "admin_invul"));
		out.append("</tr><tr>");
		out.append(cell("Heal", "admin_heal")).append(cell("Res", "admin_res"));
		out.append(cell("Reload", "admin_reload html")).append(cell("Grounds", "admin_html teleports/HuntingGrounds.htm"));
		out.append("</tr></table>");
		return out.append("</body></html>").toString();
	}

	private static String state(Epic epic) {
		final StatsSet info = GrandBossManager.getInstance().getStatsSet(epic.id());
		if (info == null) {
			return "<font color=\"888888\">not in the database</font>";
		}
		final var boss = GrandBossManager.getInstance().getBoss(epic.id());
		if ((boss != null) && !boss.isDead()) {
			return "<font color=\"00AA00\">in the world</font>";
		}
		final long left = info.getLong("respawn_time", 0) - System.currentTimeMillis();
		if (left > 0) {
			return "<font color=\"LEVEL\">back in " + span(left) + "</font>";
		}
		return "<font color=\"AAAA00\">due</font>";
	}

	/** How long ago something happened, as hours and minutes. */
	private static String since(long when) {
		return span(System.currentTimeMillis() - when);
	}

	private static String span(long millis) {
		final long minutes = Math.max(0, millis) / 60000;
		if (minutes < 60) {
			return minutes + "m";
		}
		final long hours = minutes / 60;
		return (hours >= 24 ? (hours / 24) + "d " + (hours % 24) + "h" : hours + "h " + (minutes % 60) + "m");
	}

	private static String row(String name, String value) {
		return "<tr><td width=80>" + name + "</td><td width=200><font color=\"LEVEL\">" + value + "</font></td></tr>";
	}

	private static String tab(String name, String bypass) {
		return "<td><button value=\"" + name + "\" action=\"bypass -h " + bypass + "\" width=68 height=21 " + BUTTON + "></td>";
	}

	private static String cell(String name, String bypass) {
		return "<td><button value=\"" + name + "\" action=\"bypass -h " + bypass + "\" width=68 height=21 " + BUTTON + "></td>";
	}

	@Override
	public String[] getAdminCommandList() {
		return ADMIN_COMMANDS;
	}
}
