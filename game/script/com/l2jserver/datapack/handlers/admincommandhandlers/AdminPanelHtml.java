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

import java.util.List;
import java.util.function.Function;

import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * The look of the four GM panels (bots, bosses, players, server): the row of tabs on top, buttons, links and pages of a
 * long list. Every panel page is built from these, so they all look and behave alike.
 */
final class AdminPanelHtml {
	/** How many rows a list shows on one page; the client window holds about that many without scrolling. */
	static final int ROWS = 12;
	/** A page longer than this is cut by the server (and the client may crash on it), so lists are paged well below. */
	private static final int LIMIT = 16000;

	private static final String[][] TABS = {
		{
			"Bots",
			"admin_bots"
		},
		{
			"Bosses",
			"admin_bosses"
		},
		{
			"Players",
			"admin_players"
		},
		{
			"Server",
			"admin_srv"
		},
	};

	private AdminPanelHtml() {
	}

	/** The row of panel tabs, also put on top of the old L2J admin menus. */
	static String tabs() {
		final StringBuilder out = new StringBuilder("<table width=270 border=0 bgcolor=\"333333\"><tr>");
		for (String[] tab : TABS) {
			out.append("<td>").append(button(tab[0], tab[1], 64)).append("</td>");
		}
		return out.append("</tr></table>").toString();
	}

	/** Starts a panel page: the title in the window bar, the L2J menu row and the panel tabs. */
	static StringBuilder page(String title) {
		final StringBuilder out = new StringBuilder(4096);
		out.append("<html><title>").append(title).append("</title><body><center>");
		out.append("<table width=270 border=0><tr>");
		out.append("<td>").append(button("L2J Menu", "admin_admin", 90)).append("</td>");
		out.append("<td align=right><font color=\"LEVEL\">").append(title).append("</font></td>");
		out.append("</tr></table>");
		out.append(tabs()).append("</center><br>");
		return out;
	}

	/** Ends a page and shows it. */
	static void send(L2PcInstance gm, StringBuilder page) {
		page.append("</body></html>");
		String html = page.toString();
		if (html.length() > LIMIT) {
			html = html.substring(0, LIMIT) + "</body></html>";
		}
		final NpcHtmlMessage message = new NpcHtmlMessage();
		message.setHtml(html);
		gm.sendPacket(message);
	}

	static String button(String text, String bypass, int width) {
		return "<button value=\"" + text + "\" action=\"bypass -h " + bypass + "\" width=" + width
			+ " height=21 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">";
	}

	static String link(String text, String bypass) {
		return "<a action=\"bypass -h " + bypass + "\">" + text + "</a>";
	}

	static String color(String text, String color) {
		return "<font color=\"" + color + "\">" + text + "</font>";
	}

	/** A thin line between two parts of a page. */
	static String line() {
		return "<img src=\"L2UI.SquareGray\" width=270 height=1><br>";
	}

	/** Text a player wrote (a name, a clan) can hold characters the page must not take for markup. */
	static String safe(String text) {
		return text == null ? "" : text.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	/** A duration such as 2d 5h, 3h 20m or 45s. */
	static String duration(long millis) {
		final long seconds = Math.max(0, millis / 1000);
		if (seconds >= 86400) {
			return (seconds / 86400) + "d " + ((seconds % 86400) / 3600) + "h";
		}
		if (seconds >= 3600) {
			return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
		}
		if (seconds >= 60) {
			return (seconds / 60) + "m " + (seconds % 60) + "s";
		}
		return seconds + "s";
	}

	/**
	 * One page of a list, and the links to the other pages under it.
	 * @param out the page being written
	 * @param items the whole list
	 * @param page the page to show, counted from 0
	 * @param row how one item becomes one row of the table
	 * @param bypass the command that shows another page; the page number is put after it
	 */
	static <T> void paged(StringBuilder out, List<T> items, int page, Function<T, String> row, String bypass) {
		final int pages = Math.max(1, (items.size() + ROWS - 1) / ROWS);
		final int current = Math.max(0, Math.min(page, pages - 1));
		out.append("<table width=270>");
		for (int i = current * ROWS; i < Math.min(items.size(), (current + 1) * ROWS); i++) {
			out.append(row.apply(items.get(i)));
		}
		out.append("</table>");
		if (pages > 1) {
			out.append("<center><table><tr>");
			if (current > 0) {
				out.append("<td>").append(link("&lt;&lt;", bypass + " " + (current - 1))).append("</td>");
			}
			out.append("<td width=60 align=center>").append(current + 1).append(" / ").append(pages).append("</td>");
			if (current < (pages - 1)) {
				out.append("<td>").append(link("&gt;&gt;", bypass + " " + (current + 1))).append("</td>");
			}
			out.append("</tr></table></center>");
		}
	}

	/** A whole number from a command, or the fallback when there is none. */
	static int number(String text, int fallback) {
		try {
			return Integer.parseInt(text.trim());
		} catch (Exception ex) {
			return fallback;
		}
	}
}
