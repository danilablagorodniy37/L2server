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

import static com.l2jserver.datapack.handlers.admincommandhandlers.AdminPanelHtml.button;
import static com.l2jserver.datapack.handlers.admincommandhandlers.AdminPanelHtml.color;
import static com.l2jserver.datapack.handlers.admincommandhandlers.AdminPanelHtml.duration;
import static com.l2jserver.datapack.handlers.admincommandhandlers.AdminPanelHtml.line;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Locale;

import com.l2jserver.gameserver.GameServer;
import com.l2jserver.gameserver.GameTimeController;
import com.l2jserver.gameserver.handler.IAdminCommandHandler;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

/**
 * The server panel: how the server is doing (uptime, who is online, memory, threads, processor, garbage collection)
 * and what a GM does to it - save everyone, reload data, announce, restart or stop with a countdown.
 * <ul>
 * <li>//srv - the page</li>
 * <li>//srv save - every player (not the bots) is written to the database now</li>
 * </ul>
 * Reloads, announcements and the countdown are the old commands (admin_reload, admin_announce..., admin_server_...),
 * so their access rights stay.
 */
public class AdminServerPanel implements IAdminCommandHandler {
	private static final String[] ADMIN_COMMANDS = {
		"admin_srv"
	};

	/** What //reload can reload, as the buttons name it and as the command wants it. */
	private static final String[][] RELOADS = {
		{
			"Config",
			"config"
		},
		{
			"HTML",
			"html"
		},
		{
			"Multisell",
			"multisell"
		},
		{
			"Buylist",
			"buylist"
		},
		{
			"Teleports",
			"teleport"
		},
		{
			"NPC",
			"npc"
		},
		{
			"Items",
			"item"
		},
		{
			"Skills",
			"skill"
		},
		{
			"Doors",
			"door"
		},
		{
			"Zones",
			"zone"
		},
		{
			"Access",
			"access"
		},
		{
			"Crests",
			"crest"
		},
	};

	@Override
	public boolean useAdminCommand(String command, L2PcInstance gm) {
		final String[] args = command.trim().split("\\s+");
		if ((args.length > 1) && "save".equals(args[1].toLowerCase(Locale.ROOT))) {
			int saved = 0;
			for (L2PcInstance player : L2World.getInstance().getPlayers()) {
				if (!player.isPhantom()) {
					player.storeMe();
					saved++;
				}
			}
			gm.sendMessage(saved + " players saved.");
		}
		page(gm);
		return true;
	}

	private static void page(L2PcInstance gm) {
		final StringBuilder out = AdminPanelHtml.page("Server");
		int people = 0;
		int bots = 0;
		int offline = 0;
		int gms = 0;
		for (L2PcInstance player : L2World.getInstance().getPlayers()) {
			if (player.isPhantom()) {
				bots++;
			} else if (player.isInOfflineMode()) {
				offline++;
			} else {
				people++;
				gms += player.isGM() ? 1 : 0;
			}
		}
		final Runtime runtime = Runtime.getRuntime();
		final long used = (runtime.totalMemory() - runtime.freeMemory()) >> 20;
		final long max = runtime.maxMemory() >> 20;
		long gcCount = 0;
		long gcMillis = 0;
		for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
			gcCount += Math.max(0, gc.getCollectionCount());
			gcMillis += Math.max(0, gc.getCollectionTime());
		}
		final GameTimeController time = GameTimeController.getInstance();
		out.append("<table width=270>");
		out.append(row("Up for", duration(System.currentTimeMillis() - GameServer.dateTimeServerStarted.getTimeInMillis())));
		out.append(row("Game time", String.format("%02d:%02d%s", time.getGameHour(), time.getGameMinute(), time.isNight() ? " (night)" : "")));
		out.append(row("Online", color(String.valueOf(people), "LEVEL") + " (" + gms + " GM), " + offline + " shops, " + bots + " bots"));
		out.append(row("Memory", color(used + " / " + max + " MB", used > (max * 0.85) ? "FF6666" : "66FF66")));
		out.append(row("Threads", ManagementFactory.getThreadMXBean().getThreadCount() + ", peak " + ManagementFactory.getThreadMXBean().getPeakThreadCount()));
		out.append(row("Processor", cpu()));
		out.append(row("GC", gcCount + " runs, " + duration(gcMillis) + " in all"));
		out.append("</table>").append(line());

		out.append("<table width=270><tr><td>").append(button("Save all", "admin_srv save", 84)).append("</td><td>");
		out.append(button("Refresh", "admin_srv", 84)).append("</td><td>").append(button("Old stats", "admin_stats", 84));
		out.append("</td></tr></table><br>");
		out.append("Reload<br1><table width=270><tr>");
		for (int i = 0; i < RELOADS.length; i++) {
			if ((i > 0) && ((i % 4) == 0)) {
				out.append("</tr><tr>");
			}
			out.append("<td>").append(button(RELOADS[i][0], "admin_reload " + RELOADS[i][1], 62)).append("</td>");
		}
		out.append("</tr></table>").append(line());

		out.append("Announce<br1><table width=270><tr><td colspan=3><multiedit var=\"text\" width=250 height=30></td></tr><tr>");
		out.append("<td>").append(button("To all", "admin_announce $text", 82)).append("</td>");
		out.append("<td>").append(button("Critical", "admin_announce_crit $text", 82)).append("</td>");
		out.append("<td>").append(button("On screen", "admin_announce_screen $text", 82)).append("</td>");
		out.append("</tr></table>").append(line());

		out.append("Restart or stop in (seconds)<br1><table width=270><tr><td><edit var=\"sec\" width=50 height=15></td>");
		out.append("<td>").append(button("Restart", "admin_server_restart $sec", 62)).append("</td>");
		out.append("<td>").append(button("Stop", "admin_server_shutdown $sec", 62)).append("</td>");
		out.append("<td>").append(button("Abort", "admin_server_abort", 62)).append("</td>");
		out.append("</tr></table>");
		AdminPanelHtml.send(gm, out);
	}

	/** The load of this process on the processor, when the platform tells it. */
	private static String cpu() {
		if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
			final double load = os.getProcessCpuLoad();
			if (load >= 0) {
				return String.format("%.0f%% of %d cores", load * 100, os.getAvailableProcessors());
			}
		}
		return "unknown";
	}

	private static String row(String what, String value) {
		return "<tr><td width=80>" + what + "</td><td>" + value + "</td></tr>";
	}

	@Override
	public String[] getAdminCommandList() {
		return ADMIN_COMMANDS;
	}
}
