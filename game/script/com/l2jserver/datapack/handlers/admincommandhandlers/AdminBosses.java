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
import static com.l2jserver.datapack.handlers.admincommandhandlers.AdminPanelHtml.link;
import static com.l2jserver.datapack.handlers.admincommandhandlers.AdminPanelHtml.number;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.l2jserver.gameserver.data.xml.impl.NpcData;
import com.l2jserver.gameserver.handler.IAdminCommandHandler;
import com.l2jserver.gameserver.instancemanager.GrandBossManager;
import com.l2jserver.gameserver.instancemanager.RaidBossSpawnManager;
import com.l2jserver.gameserver.model.L2Spawn;
import com.l2jserver.gameserver.model.StatsSet;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2GrandBossInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.actor.instance.L2RaidBossInstance;
import com.l2jserver.gameserver.model.actor.templates.L2NpcTemplate;

/**
 * The bosses panel: every epic boss and every raid boss, alive or when it comes back, with a way to get there and to
 * bring a dead raid boss back now.
 * <ul>
 * <li>//bosses - the epic bosses</li>
 * <li>//bosses raid &lt;all|alive|dead&gt; [page] - the raid bosses by level</li>
 * <li>//bosses find &lt;part of a name&gt; - raid bosses whose name has it</li>
 * <li>//bosses go &lt;npc id&gt; - the GM goes to the boss, or to its spawn when it is dead</li>
 * <li>//bosses now &lt;npc id&gt; [filter page] - a dead raid boss spawns right away</li>
 * </ul>
 */
public class AdminBosses implements IAdminCommandHandler {
	private static final String[] ADMIN_COMMANDS = {
		"admin_bosses"
	};

	/** The epic bosses that have their own page in //grandboss. */
	private static final Set<Integer> MANAGED = Set.of(29001, 29006, 29014, 29020, 29028, 29068);

	@Override
	public boolean useAdminCommand(String command, L2PcInstance gm) {
		final String[] args = command.trim().split("\\s+");
		final String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
		switch (action) {
			case "raid" -> raids(gm, args.length > 2 ? args[2] : "all", args.length > 3 ? number(args[3], 0) : 0);
			case "find" -> find(gm, args.length > 2 ? args[2] : "");
			case "go" -> go(gm, args.length > 2 ? number(args[2], 0) : 0);
			case "now" -> {
				final int id = args.length > 2 ? number(args[2], 0) : 0;
				if (RaidBossSpawnManager.getInstance().spawnNow(id)) {
					gm.sendMessage(name(id) + " is back.");
				} else {
					gm.sendMessage(name(id) + " is not waiting for its respawn.");
				}
				raids(gm, args.length > 3 ? args[3] : "dead", args.length > 4 ? number(args[4], 0) : 0);
			}
			default -> epics(gm);
		}
		return true;
	}

	private static void epics(L2PcInstance gm) {
		final StringBuilder out = AdminPanelHtml.page("Bosses");
		out.append("<center>Epic bosses</center><table width=270>");
		final GrandBossManager manager = GrandBossManager.getInstance();
		final List<Integer> ids = new ArrayList<>(manager.getBossIds());
		ids.sort(Comparator.comparingInt(AdminBosses::level));
		final long now = System.currentTimeMillis();
		for (int id : ids) {
			final L2GrandBossInstance boss = manager.getBoss(id);
			final StatsSet info = manager.getStatsSet(id);
			final long respawn = info == null ? 0 : info.getLong("respawn_time", 0);
			final String state;
			if ((boss != null) && boss.isVisible() && !boss.isDead()) {
				state = color("alive", "66FF66") + " " + hp(boss);
			} else if (respawn > now) {
				state = color("back in " + duration(respawn - now), "FF6666");
			} else {
				// not spawned and not waiting for a respawn: asleep until someone wakes it (Baium's statue, a dormant nest)
				state = color("asleep", "FFCC66");
			}
			out.append("<tr><td width=30>").append(level(id)).append("</td><td width=90>").append(name(id)).append("</td>");
			out.append("<td width=90>").append(state).append("</td><td width=25>").append(link("Go", "admin_bosses go " + id));
			out.append("</td><td width=40>").append(MANAGED.contains(id) ? link("Set", "admin_grandboss " + id) : "").append("</td></tr>");
		}
		out.append("</table>").append(line());
		final RaidBossSpawnManager raids = RaidBossSpawnManager.getInstance();
		final int alive = (int) raids.getSpawns().keySet().stream().filter(id -> raids.getRespawnTime(id) == 0).count();
		out.append("<center>Raid bosses: ").append(color(String.valueOf(alive), "66FF66")).append(" alive, ");
		out.append(color(String.valueOf(raids.getSpawns().size() - alive), "FF6666")).append(" dead</center>");
		out.append("<center><table><tr><td>").append(button("All", "admin_bosses raid all", 80)).append("</td><td>");
		out.append(button("Alive", "admin_bosses raid alive", 80)).append("</td><td>");
		out.append(button("Dead", "admin_bosses raid dead", 80)).append("</td></tr></table>");
		out.append("<table><tr><td><edit var=\"boss\" width=150 height=15></td><td>");
		out.append(button("Find", "admin_bosses find $boss", 80)).append("</td></tr></table></center>");
		AdminPanelHtml.send(gm, out);
	}

	private static void raids(L2PcInstance gm, String filter, int page) {
		final RaidBossSpawnManager raids = RaidBossSpawnManager.getInstance();
		final List<Integer> ids = new ArrayList<>();
		for (int id : raids.getSpawns().keySet()) {
			final boolean dead = raids.getRespawnTime(id) > 0;
			if ("all".equalsIgnoreCase(filter) || ("dead".equalsIgnoreCase(filter) == dead)) {
				ids.add(id);
			}
		}
		ids.sort(Comparator.comparingInt(AdminBosses::level).thenComparing(AdminBosses::name));
		final StringBuilder out = AdminPanelHtml.page("Bosses");
		out.append(ids.size()).append(" raid bosses, ").append(filter.toLowerCase(Locale.ROOT)).append("<br>");
		final int shown = Math.max(0, page);
		AdminPanelHtml.paged(out, ids, shown, id -> raidRow(id, filter, shown), "admin_bosses raid " + filter);
		out.append("<br>").append(button("Back", "admin_bosses", 80));
		AdminPanelHtml.send(gm, out);
	}

	private static void find(L2PcInstance gm, String text) {
		final String wanted = text.trim().toLowerCase(Locale.ROOT);
		final List<Integer> ids = new ArrayList<>();
		for (int id : RaidBossSpawnManager.getInstance().getSpawns().keySet()) {
			if (!wanted.isEmpty() && name(id).toLowerCase(Locale.ROOT).contains(wanted)) {
				ids.add(id);
			}
		}
		ids.sort(Comparator.comparingInt(AdminBosses::level));
		final StringBuilder out = AdminPanelHtml.page("Bosses");
		out.append(ids.size()).append(" raid bosses with \"").append(AdminPanelHtml.safe(text.trim())).append("\" in the name<br>");
		AdminPanelHtml.paged(out, ids.subList(0, Math.min(ids.size(), AdminPanelHtml.ROWS)), 0, id -> raidRow(id, "all", 0), "admin_bosses");
		out.append("<br>").append(button("Back", "admin_bosses", 80));
		AdminPanelHtml.send(gm, out);
	}

	private static String raidRow(int id, String filter, int page) {
		final RaidBossSpawnManager raids = RaidBossSpawnManager.getInstance();
		final long respawn = raids.getRespawnTime(id);
		final StringBuilder row = new StringBuilder("<tr><td width=30>").append(level(id)).append("</td><td width=120>");
		row.append(link(name(id), "admin_bosses go " + id)).append("</td><td width=80>");
		if (respawn > 0) {
			row.append(color(duration(respawn - System.currentTimeMillis()), "FF6666")).append("</td><td>");
			row.append(link("Now", "admin_bosses now " + id + " " + filter + " " + page));
		} else {
			final L2RaidBossInstance boss = raids.getBosses().get(id);
			row.append(boss == null ? color("?", "999999") : color("alive", "66FF66") + " " + hp(boss)).append("</td><td>");
		}
		return row.append("</td></tr>").toString();
	}

	private static void go(L2PcInstance gm, int id) {
		L2Npc boss = GrandBossManager.getInstance().getBoss(id);
		if (boss == null) {
			boss = RaidBossSpawnManager.getInstance().getBosses().get(id);
		}
		if ((boss != null) && boss.isVisible() && !boss.isDead()) {
			gm.teleToLocation(boss.getX(), boss.getY(), boss.getZ(), true);
			return;
		}
		final L2Spawn spawn = RaidBossSpawnManager.getInstance().getSpawns().get(id);
		if (spawn != null) {
			gm.teleToLocation(spawn.getX(), spawn.getY(), spawn.getZ(), true);
			return;
		}
		final StatsSet info = GrandBossManager.getInstance().getStatsSet(id);
		if ((info != null) && ((info.getInt("loc_x", 0) != 0) || (info.getInt("loc_y", 0) != 0))) {
			gm.teleToLocation(info.getInt("loc_x"), info.getInt("loc_y"), info.getInt("loc_z"), true);
			return;
		}
		gm.sendMessage("No place is known for " + name(id) + ".");
	}

	private static String hp(L2Npc boss) {
		return (int) ((boss.getCurrentHp() * 100) / Math.max(1, boss.getMaxHp())) + "%";
	}

	private static String name(int id) {
		final L2NpcTemplate template = NpcData.getInstance().getTemplate(id);
		return template == null ? String.valueOf(id) : template.getName();
	}

	private static int level(int id) {
		final L2NpcTemplate template = NpcData.getInstance().getTemplate(id);
		return template == null ? 0 : template.getLevel();
	}

	@Override
	public String[] getAdminCommandList() {
		return ADMIN_COMMANDS;
	}
}
