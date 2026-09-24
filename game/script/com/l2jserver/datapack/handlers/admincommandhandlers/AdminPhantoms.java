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
import static com.l2jserver.datapack.handlers.admincommandhandlers.AdminPanelHtml.line;
import static com.l2jserver.datapack.handlers.admincommandhandlers.AdminPanelHtml.link;
import static com.l2jserver.datapack.handlers.admincommandhandlers.AdminPanelHtml.number;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.l2jserver.datapack.custom.phantoms.Phantom;
import com.l2jserver.datapack.custom.phantoms.PhantomHunting;
import com.l2jserver.datapack.custom.phantoms.PhantomSquad;
import com.l2jserver.datapack.custom.phantoms.Phantoms;
import com.l2jserver.gameserver.data.xml.impl.ClassListData;
import com.l2jserver.gameserver.handler.IAdminCommandHandler;
import com.l2jserver.gameserver.model.Location;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

/**
 * The bots panel: how many bots there are and what they do, the standing parties with their hunting grounds, every bot
 * by name, and the controls - stop and start them all, send a party to another ground, go to a party or a bot.
 * <ul>
 * <li>//bots - the overview and the parties</li>
 * <li>//bots pause | resume - every bot stands still, or goes on</li>
 * <li>//bots where &lt;party&gt; - the GM goes to the camp of a party (3.2)</li>
 * <li>//bots move &lt;party&gt; [page] - the hunting grounds, to pick one for the party</li>
 * <li>//bots moveto &lt;party&gt; &lt;ground&gt; - the party goes there</li>
 * <li>//bots list &lt;state&gt; [page] - the bots of one state (town, travel, hunt, return, trade, all)</li>
 * <li>//bots find &lt;part of a name&gt; - bots whose name has it</li>
 * <li>//bots go &lt;object id&gt; - the GM goes to a bot</li>
 * </ul>
 */
public class AdminPhantoms implements IAdminCommandHandler {
	private static final String[] ADMIN_COMMANDS = {
		"admin_bots"
	};

	@Override
	public boolean useAdminCommand(String command, L2PcInstance gm) {
		final String[] args = command.trim().split("\\s+");
		final String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
		switch (action) {
			case "pause", "resume" -> {
				if (Phantoms.pause("pause".equals(action))) {
					gm.sendMessage("pause".equals(action) ? "Every bot stands still now." : "The bots go on.");
				}
				overview(gm);
			}
			case "where" -> {
				final Location camp = args.length > 2 ? Phantoms.partyCamp(args[2]) : null;
				if (camp == null) {
					gm.sendMessage("No such party.");
				} else {
					gm.teleToLocation(camp.getX(), camp.getY(), camp.getZ(), true);
				}
			}
			case "move" -> {
				if (args.length > 2) {
					grounds(gm, args[2], args.length > 3 ? number(args[3], 0) : 0);
				}
			}
			case "moveto" -> {
				if (args.length > 3) {
					gm.sendMessage(Phantoms.moveParty(args[2], number(args[3], -1)));
				}
				overview(gm);
			}
			case "list" -> list(gm, args.length > 2 ? args[2] : "all", args.length > 3 ? number(args[3], 0) : 0);
			case "find" -> {
				// the paging links put the page after the name
				final boolean paged = (args.length > 3) && args[args.length - 1].matches("\\d+");
				find(gm, args.length > 2 ? args[2] : "", paged ? number(args[args.length - 1], 0) : 0);
			}
			case "go" -> {
				// straight from the list of bots: the shopkeepers are not in the players of L2World
				final int objectId = args.length > 2 ? number(args[2], 0) : 0;
				final L2PcInstance bot = Phantoms.all().stream().map(Phantom::player).filter(p -> p.getObjectId() == objectId).findFirst().orElse(null);
				if (bot == null) {
					gm.sendMessage("That bot is not in the world.");
				} else {
					gm.teleToLocation(bot.getX(), bot.getY(), bot.getZ(), true);
				}
			}
			default -> overview(gm);
		}
		return true;
	}

	private static void overview(L2PcInstance gm) {
		final StringBuilder out = AdminPanelHtml.page("Bots");
		final List<Phantom> bots = Phantoms.all();
		if (bots.isEmpty()) {
			AdminPanelHtml.send(gm, out.append("The bots are not running (config/phantoms.properties)."));
			return;
		}
		final Map<Phantom.State, Integer> states = new EnumMap<>(Phantom.State.class);
		int kills = 0;
		int deaths = 0;
		long earned = 0;
		for (Phantom bot : bots) {
			states.merge(bot.state(), 1, Integer::sum);
			kills += bot.kills();
			deaths += bot.deaths();
			earned += bot.earned();
		}
		final boolean paused = Phantoms.paused();
		out.append("<table width=270><tr><td>").append(bots.size()).append(" bots, ");
		out.append(paused ? color("stopped", "FF6666") : color("running", "66FF66")).append("</td><td align=right>");
		out.append(paused ? button("Resume", "admin_bots resume", 80) : button("Pause", "admin_bots pause", 80));
		out.append("</td></tr></table>");
		out.append("<table width=270>");
		for (Phantom.State state : Phantom.State.values()) {
			out.append("<tr><td width=140>").append(link(stateName(state), "admin_bots list " + state.name().toLowerCase(Locale.ROOT)));
			out.append("</td><td>").append(color(String.valueOf(states.getOrDefault(state, 0)), "LEVEL")).append("</td></tr>");
		}
		out.append("</table>");
		out.append("Kills ").append(color(String.valueOf(kills), "LEVEL")).append(", deaths ").append(color(String.valueOf(deaths), "LEVEL"));
		out.append(", loot sold for ").append(color(String.valueOf(earned), "LEVEL")).append(" adena<br>");
		out.append("<table width=270><tr><td><edit var=\"name\" width=150 height=15></td><td>");
		out.append(button("Find bot", "admin_bots find $name", 80)).append("</td></tr></table>").append(line());

		out.append("<center>Standing parties</center><table width=270>");
		for (PhantomSquad squad : Phantoms.squads()) {
			if (squad.members().size() < 5) {
				continue;
			}
			final String name = squad.name().replace("party ", "");
			final long up = squad.members().stream().filter(member -> !member.player().isDead()).count();
			out.append("<tr><td width=40>").append(color(name, "LEVEL")).append("</td>");
			out.append("<td width=150>").append(squad.where()).append("<br1>").append(up).append('/').append(squad.members().size());
			out.append(" up, ").append(squad.kills()).append(" k / ").append(squad.deaths()).append(" d");
			out.append(squad.raiding() ? color(" raid", "FF9900") : "").append("</td>");
			out.append("<td>").append(link("Go", "admin_bots where " + name)).append("<br1>");
			out.append(link("Move", "admin_bots move " + name)).append("</td></tr>");
		}
		AdminPanelHtml.send(gm, out.append("</table>"));
	}

	/** The hunting grounds, with the ones that suit the level of the party first. */
	private static void grounds(L2PcInstance gm, String party, int page) {
		final PhantomSquad squad = Phantoms.squads().stream() //
			.filter(s -> s.name().equalsIgnoreCase(party) || s.name().equalsIgnoreCase("party " + party)) //
			.findFirst().orElse(null);
		if (squad == null) {
			gm.sendMessage("No such party.");
			return;
		}
		final List<PhantomHunting.Ground> grounds = Phantoms.grounds();
		final List<Integer> order = new ArrayList<>();
		for (int i = 0; i < grounds.size(); i++) {
			order.add(i);
		}
		final int level = squad.level();
		order.sort(Comparator.comparing((Integer i) -> !grounds.get(i).fits(level)).thenComparing(i -> grounds.get(i).minLevel()));
		final StringBuilder out = AdminPanelHtml.page("Bots");
		out.append("Where does party ").append(color(party, "LEVEL")).append(" (level ").append(level).append(") go?<br>");
		out.append("Now: ").append(squad.where()).append("<br>");
		AdminPanelHtml.paged(out, order, page, i -> {
			final PhantomHunting.Ground ground = grounds.get(i);
			final String levels = ground.minLevel() + "-" + ground.maxLevel();
			return "<tr><td width=200>" + link(ground.name(), "admin_bots moveto " + party + " " + i) + "</td><td>"
				+ color(levels, ground.fits(level) ? "66FF66" : "999999") + "</td></tr>";
		}, "admin_bots move " + party);
		out.append("<br>").append(button("Back", "admin_bots", 80));
		AdminPanelHtml.send(gm, out);
	}

	private static void list(L2PcInstance gm, String state, int page) {
		final List<Phantom> bots = new ArrayList<>();
		for (Phantom bot : Phantoms.all()) {
			if ("all".equalsIgnoreCase(state) || bot.state().name().equalsIgnoreCase(state)) {
				bots.add(bot);
			}
		}
		bots.sort(Comparator.comparing((Phantom bot) -> bot.player().getLevel()).reversed());
		final StringBuilder out = AdminPanelHtml.page("Bots");
		out.append(bots.size()).append(" bots ").append(state.toLowerCase(Locale.ROOT)).append("<br>");
		AdminPanelHtml.paged(out, bots, page, AdminPhantoms::row, "admin_bots list " + state);
		out.append("<br>").append(button("Back", "admin_bots", 80));
		AdminPanelHtml.send(gm, out);
	}

	private static void find(L2PcInstance gm, String text, int page) {
		final String wanted = text.trim().toLowerCase(Locale.ROOT);
		final List<Phantom> bots = new ArrayList<>();
		for (Phantom bot : Phantoms.all()) {
			if (!wanted.isEmpty() && bot.player().getName().toLowerCase(Locale.ROOT).contains(wanted)) {
				bots.add(bot);
			}
		}
		final StringBuilder out = AdminPanelHtml.page("Bots");
		out.append(bots.size()).append(" bots with \"").append(AdminPanelHtml.safe(text.trim())).append("\" in the name<br>");
		AdminPanelHtml.paged(out, bots, page, AdminPhantoms::row, "admin_bots find " + wanted);
		out.append("<br>").append(button("Back", "admin_bots", 80));
		AdminPanelHtml.send(gm, out);
	}

	private static String row(Phantom bot) {
		final L2PcInstance player = bot.player();
		final String cls = ClassListData.getInstance().getClass(player.getClassId()).getClientCode();
		return "<tr><td width=110>" + link(player.getName(), "admin_bots go " + player.getObjectId()) + "</td><td width=30>"
			+ player.getLevel() + "</td><td width=100>" + cls + "</td><td>" + stateName(bot.state()) + "</td></tr>";
	}

	private static String stateName(Phantom.State state) {
		return switch (state) {
			case TOWN -> "In town";
			case TRAVEL -> "Travelling";
			case HUNT -> "Hunting";
			case RETURN -> "Coming back";
			case TRADE -> "Keeping a shop";
		};
	}

	@Override
	public String[] getAdminCommandList() {
		return ADMIN_COMMANDS;
	}
}
