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
import static com.l2jserver.datapack.handlers.admincommandhandlers.AdminPanelHtml.safe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.l2jserver.gameserver.data.xml.impl.ClassListData;
import com.l2jserver.gameserver.datatables.ItemTable;
import com.l2jserver.gameserver.handler.IAdminCommandHandler;
import com.l2jserver.gameserver.instancemanager.MapRegionManager;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.items.L2Item;
import com.l2jserver.gameserver.network.serverpackets.GMHennaInfo;
import com.l2jserver.gameserver.network.serverpackets.GMViewCharacterInfo;
import com.l2jserver.gameserver.network.serverpackets.GMViewItemList;
import com.l2jserver.gameserver.network.serverpackets.GMViewSkillInfo;
import com.l2jserver.gameserver.network.serverpackets.GMViewWarehouseWithdrawList;

/**
 * The players panel: who is online (people, not bots), a card for each of them with what a GM does most - go there,
 * bring them over, look into their bag, warehouse and skills, jail, kick, silence - and items found by name and given.
 * <ul>
 * <li>//players [page] - who is online</li>
 * <li>//players find &lt;part of a name&gt; - online players whose name has it</li>
 * <li>//players card &lt;name&gt; - one player</li>
 * <li>//players look &lt;name&gt; &lt;status|bag|warehouse|skills&gt; - the GM windows of the client for that player</li>
 * <li>//players items &lt;name&gt; &lt;part of an item name&gt; - items to give the player</li>
 * <li>//players give &lt;name&gt; &lt;item id&gt; [count] - the player gets it</li>
 * </ul>
 * Jail, kick and silence are the old commands (admin_jail, admin_kick, admin_punishment_add), so their access rights stay.
 */
public class AdminPlayers implements IAdminCommandHandler {
	private static final String[] ADMIN_COMMANDS = {
		"admin_players"
	};

	@Override
	public boolean useAdminCommand(String command, L2PcInstance gm) {
		final String[] args = command.trim().split("\\s+");
		final String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
		switch (action) {
			case "find" -> list(gm, args.length > 2 ? args[2] : "", 0);
			case "card" -> card(gm, args.length > 2 ? args[2] : "");
			case "look" -> look(gm, args.length > 2 ? args[2] : "", args.length > 3 ? args[3] : "");
			case "items" -> items(gm, args.length > 2 ? args[2] : "", args.length > 3 ? String.join(" ", List.of(args).subList(3, args.length)) : "");
			case "give" -> {
				if (args.length > 3) {
					give(gm, args[2], number(args[3], 0), args.length > 4 ? number(args[4], 1) : 1);
				}
			}
			default -> list(gm, "", args.length > 1 ? number(args[1], 0) : 0);
		}
		return true;
	}

	/** People online: bots and shops left behind are counted apart. */
	private static void list(L2PcInstance gm, String text, int page) {
		final String wanted = text.trim().toLowerCase(Locale.ROOT);
		final List<L2PcInstance> people = new ArrayList<>();
		int bots = 0;
		int offline = 0;
		for (L2PcInstance player : L2World.getInstance().getPlayers()) {
			if (player.isPhantom()) {
				bots++;
				continue;
			}
			if (player.isInOfflineMode()) {
				offline++;
				continue;
			}
			if (wanted.isEmpty() || player.getName().toLowerCase(Locale.ROOT).contains(wanted)) {
				people.add(player);
			}
		}
		people.sort(Comparator.comparing(L2PcInstance::getName, String.CASE_INSENSITIVE_ORDER));
		final StringBuilder out = AdminPanelHtml.page("Players");
		out.append("Online: ").append(color(String.valueOf(people.size()), "LEVEL")).append(wanted.isEmpty() ? " people" : " found");
		out.append(", ").append(offline).append(" offline shops, ").append(bots).append(" bots<br>");
		out.append("<table width=270><tr><td><edit var=\"who\" width=150 height=15></td><td>");
		out.append(button("Find", "admin_players find $who", 80)).append("</td></tr></table>");
		AdminPanelHtml.paged(out, people, page, player -> "<tr><td width=110>" + link(player.getName(), "admin_players card " + player.getName())
			+ (player.isGM() ? color(" GM", "FF9900") : "") + "</td><td width=30>" + player.getLevel() + "</td><td>" + town(player)
			+ "</td></tr>", "admin_players");
		AdminPanelHtml.send(gm, out);
	}

	private static void card(L2PcInstance gm, String name) {
		final L2PcInstance player = L2World.getInstance().getPlayer(name);
		if (player == null) {
			gm.sendMessage(name + " is not online.");
			list(gm, "", 0);
			return;
		}
		final String n = player.getName();
		final StringBuilder out = AdminPanelHtml.page("Players");
		out.append("<table width=270><tr><td>").append(color(n, "LEVEL")).append(player.isGM() ? color(" (GM)", "FF9900") : "");
		out.append("</td><td align=right>").append(player.getLevel()).append(" ").append(cls(player)).append("</td></tr></table>");
		out.append("<table width=270>");
		out.append(row("Account", safe(player.getAccountName())));
		out.append(row("Clan", player.getClan() == null ? "-" : safe(player.getClan().getName())));
		out.append(row("Where", town(player) + " (" + player.getX() + ", " + player.getY() + ")"));
		out.append(row("Online", duration(player.getUptime())));
		out.append(row("Karma / PvP / PK", player.getKarma() + " / " + player.getPvpKills() + " / " + player.getPkKills()));
		out.append(row("HP / MP / CP", (int) player.getCurrentHp() + " / " + (int) player.getCurrentMp() + " / " + (int) player.getCurrentCp()));
		out.append(row("Adena", String.valueOf(player.getAdena())));
		if (player.isJailed() || player.isChatBanned()) {
			out.append(row("Punished", color((player.isJailed() ? "jail " : "") + (player.isChatBanned() ? "chat" : ""), "FF6666")));
		}
		out.append("</table>").append(line());
		out.append("<table width=270><tr>");
		out.append("<td>").append(button("Go to", "admin_teleportto " + n, 66)).append("</td>");
		out.append("<td>").append(button("Recall", "admin_recall " + n, 66)).append("</td>");
		out.append("<td>").append(button("Status", "admin_players look " + n + " status", 66)).append("</td>");
		out.append("<td>").append(button("Info", "admin_character_info " + n, 66)).append("</td>");
		out.append("</tr><tr>");
		out.append("<td>").append(button("Bag", "admin_players look " + n + " bag", 66)).append("</td>");
		out.append("<td>").append(button("Warehouse", "admin_players look " + n + " warehouse", 66)).append("</td>");
		out.append("<td>").append(button("Skills", "admin_players look " + n + " skills", 66)).append("</td>");
		out.append("<td>").append(button("Quests", "admin_charquestmenu " + n, 66)).append("</td>");
		out.append("</tr><tr>");
		out.append("<td>").append(button(player.isJailed() ? "Unjail" : "Jail", (player.isJailed() ? "admin_unjail " : "admin_jail ") + n, 66)).append("</td>");
		out.append("<td>").append(button("Silence 1h", "admin_punishment_add " + n + " CHARACTER CHAT_BAN 60 Silenced by a GM", 66)).append("</td>");
		out.append("<td>").append(button("Unsilence", "admin_unban_chat " + n, 66)).append("</td>");
		out.append("<td>").append(button("Kick", "admin_kick " + n, 66)).append("</td>");
		out.append("</tr></table>").append(line());
		out.append("Give an item: its id and a count, or find it by a part of its name<br1>");
		out.append("<table width=270><tr><td><edit var=\"item\" width=100 height=15></td><td><edit var=\"count\" width=50 height=15></td><td>");
		out.append(button("Give", "admin_players give " + n + " $item $count", 60)).append("</td></tr><tr><td><edit var=\"what\" width=100 height=15></td><td></td><td>");
		out.append(button("Search", "admin_players items " + n + " $what", 60)).append("</td></tr></table>");
		out.append("<br>").append(button("Back", "admin_players", 80));
		AdminPanelHtml.send(gm, out);
	}

	/** Opens the GM windows of the client (Alt+G) straight for this player. */
	private static void look(L2PcInstance gm, String name, String what) {
		final L2PcInstance player = L2World.getInstance().getPlayer(name);
		if (player == null) {
			gm.sendMessage(name + " is not online.");
			return;
		}
		switch (what.toLowerCase(Locale.ROOT)) {
			case "bag" -> {
				gm.sendPacket(new GMViewItemList(player));
				gm.sendPacket(new GMHennaInfo(player));
			}
			case "warehouse" -> gm.sendPacket(new GMViewWarehouseWithdrawList(player));
			case "skills" -> gm.sendPacket(new GMViewSkillInfo(player));
			default -> {
				gm.sendPacket(new GMViewCharacterInfo(player));
				gm.sendPacket(new GMHennaInfo(player));
			}
		}
	}

	private static void items(L2PcInstance gm, String name, String text) {
		final String wanted = text.trim().toLowerCase(Locale.ROOT);
		final List<L2Item> found = new ArrayList<>();
		if (wanted.length() >= 2) {
			final ItemTable table = ItemTable.getInstance();
			for (int id = 1; (id < table.getArraySize()) && (found.size() < 60); id++) {
				final L2Item item = table.getTemplate(id);
				if ((item != null) && item.getName().toLowerCase(Locale.ROOT).contains(wanted)) {
					found.add(item);
				}
			}
		}
		final StringBuilder out = AdminPanelHtml.page("Players");
		out.append("Items with \"").append(safe(text.trim())).append("\" for ").append(color(name, "LEVEL")).append(": ");
		out.append(found.size() >= 60 ? "60 and more, the first" : String.valueOf(found.size())).append("<br>");
		out.append("<table width=270>");
		for (L2Item item : found.subList(0, Math.min(found.size(), 20))) {
			final String grade = item.getCrystalType().name();
			out.append("<tr><td width=40>").append(item.getId()).append("</td><td width=180>").append(safe(item.getName()));
			out.append("NONE".equals(grade) ? "" : color(" " + grade, "999999")).append("</td><td width=40>");
			out.append(link("Give", "admin_players give " + name + " " + item.getId() + " 1")).append("</td></tr>");
		}
		out.append("</table><br>").append(button("Back", "admin_players card " + name, 80));
		AdminPanelHtml.send(gm, out);
	}

	private static void give(L2PcInstance gm, String name, int itemId, int count) {
		final L2PcInstance player = L2World.getInstance().getPlayer(name);
		final L2Item item = ItemTable.getInstance().getTemplate(itemId);
		if ((player == null) || (item == null) || (count < 1)) {
			gm.sendMessage(player == null ? name + " is not online." : "No such item.");
			return;
		}
		player.addItem("GM panel", itemId, count, gm, true);
		gm.sendMessage(name + " got " + count + " " + item.getName() + ".");
		card(gm, name);
	}

	private static String row(String what, String value) {
		return "<tr><td width=100>" + what + "</td><td>" + value + "</td></tr>";
	}

	private static String cls(L2PcInstance player) {
		return ClassListData.getInstance().getClass(player.getClassId()).getClientCode();
	}

	private static String town(L2PcInstance player) {
		return MapRegionManager.getInstance().getClosestTownName(player);
	}

	@Override
	public String[] getAdminCommandList() {
		return ADMIN_COMMANDS;
	}
}
