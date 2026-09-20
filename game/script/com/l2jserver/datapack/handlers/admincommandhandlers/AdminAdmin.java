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

import static com.l2jserver.gameserver.config.Configuration.character;
import static com.l2jserver.gameserver.config.Configuration.chronicle;
import static com.l2jserver.gameserver.config.Configuration.rates;

import java.lang.reflect.Modifier;
import java.util.StringTokenizer;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Mutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.gameserver.config.Configuration;
import com.l2jserver.gameserver.data.xml.impl.AdminData;
import com.l2jserver.gameserver.handler.IAdminCommandHandler;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.entity.Hero;
import com.l2jserver.gameserver.model.olympiad.Olympiad;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * This class handles following admin commands: - admin|admin1/admin2/admin3/admin4/admin5 = slots for the 5 starting admin menus - gmliston/gmlistoff = includes/excludes active character from /gmlist results - silence = toggles private messages acceptance mode - diet = toggles weight penalty mode -
 * tradeoff = toggles trade acceptance mode - reload = reloads specified component from multisell|skill|npc|htm|item - set/set_menu/set_mod = alters specified server setting - saveolymp = saves olympiad state manually - manualhero = cycles olympiad and calculate new heroes.
 * @version $Revision: 1.3.2.1.2.4 $ $Date: 2007/07/28 10:06:06 $
 */
public class AdminAdmin implements IAdminCommandHandler {
	private static final Logger LOG = LoggerFactory.getLogger(AdminAdmin.class);
	
	private static final String[] ADMIN_COMMANDS = {
		"admin_admin",
		"admin_admin1",
		"admin_admin2",
		"admin_admin3",
		"admin_admin4",
		"admin_admin5",
		"admin_admin6",
		"admin_admin7",
		"admin_gmliston",
		"admin_gmlistoff",
		"admin_silence",
		"admin_diet",
		"admin_tradeoff",
		"admin_set",
		"admin_set_mod",
		"admin_saveolymp",
		"admin_sethero",
		"admin_givehero",
		"admin_endolympiad",
		"admin_setconfig",
		"admin_config_server",
		"admin_gmon"
	};
	
	@Override
	public boolean useAdminCommand(String command, L2PcInstance activeChar) {
		if (command.startsWith("admin_admin")) {
			showMainPage(activeChar, command);
		} else if (command.equals("admin_config_server")) {
			showConfigPage(activeChar);
		} else if (command.startsWith("admin_gmliston")) {
			AdminData.getInstance().showGm(activeChar);
			activeChar.sendMessage("Registered into gm list");
			AdminHtml.showAdminHtml(activeChar, "gm_menu.htm");
		} else if (command.startsWith("admin_gmlistoff")) {
			AdminData.getInstance().hideGm(activeChar);
			activeChar.sendMessage("Removed from gm list");
			AdminHtml.showAdminHtml(activeChar, "gm_menu.htm");
		} else if (command.startsWith("admin_silence")) {
			if (activeChar.isSilenceMode()) // already in message refusal mode
			{
				activeChar.setSilenceMode(false);
				activeChar.sendPacket(SystemMessageId.MESSAGE_ACCEPTANCE_MODE);
			} else {
				activeChar.setSilenceMode(true);
				activeChar.sendPacket(SystemMessageId.MESSAGE_REFUSAL_MODE);
			}
			AdminHtml.showAdminHtml(activeChar, "gm_menu.htm");
		} else if (command.startsWith("admin_saveolymp")) {
			Olympiad.getInstance().saveOlympiadStatus();
			activeChar.sendMessage("olympiad system saved.");
		} else if (command.startsWith("admin_endolympiad")) {
			try {
				Olympiad.getInstance().manualSelectHeroes();
			} catch (Exception e) {
				LOG.warn("An error occurred while ending olympiad: {}", e.getMessage(), e);
			}
			activeChar.sendMessage("Heroes formed.");
		} else if (command.startsWith("admin_sethero")) {
			if (activeChar.getTarget() == null) {
				activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
				return false;
			}
			
			final L2PcInstance target = activeChar.getTarget().isPlayer() ? activeChar.getTarget().getActingPlayer() : activeChar;
			target.setHero(!target.isHero());
			target.broadcastUserInfo();
		} else if (command.startsWith("admin_givehero")) {
			if (activeChar.getTarget() == null) {
				activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
				return false;
			}
			
			final L2PcInstance target = activeChar.getTarget().isPlayer() ? activeChar.getTarget().getActingPlayer() : activeChar;
			if (Hero.getInstance().isHero(target.getObjectId())) {
				activeChar.sendMessage("This player has already claimed the hero status.");
				return false;
			}
			
			if (!Hero.getInstance().isUnclaimedHero(target.getObjectId())) {
				activeChar.sendMessage("This player cannot claim the hero status.");
				return false;
			}
			Hero.getInstance().claimHero(target);
		} else if (command.startsWith("admin_diet")) {
			try {
				StringTokenizer st = new StringTokenizer(command);
				st.nextToken();
				if (st.nextToken().equalsIgnoreCase("on")) {
					activeChar.setDietMode(true);
					activeChar.sendMessage("Diet mode on");
				} else if (st.nextToken().equalsIgnoreCase("off")) {
					activeChar.setDietMode(false);
					activeChar.sendMessage("Diet mode off");
				}
			} catch (Exception ex) {
				if (activeChar.getDietMode()) {
					activeChar.setDietMode(false);
					activeChar.sendMessage("Diet mode off");
				} else {
					activeChar.setDietMode(true);
					activeChar.sendMessage("Diet mode on");
				}
			} finally {
				activeChar.refreshOverloaded();
			}
			AdminHtml.showAdminHtml(activeChar, "gm_menu.htm");
		} else if (command.startsWith("admin_tradeoff")) {
			try {
				String mode = command.substring(15);
				if (mode.equalsIgnoreCase("on")) {
					activeChar.setTradeRefusal(true);
					activeChar.sendMessage("Trade refusal enabled");
				} else if (mode.equalsIgnoreCase("off")) {
					activeChar.setTradeRefusal(false);
					activeChar.sendMessage("Trade refusal disabled");
				}
			} catch (Exception ex) {
				if (activeChar.getTradeRefusal()) {
					activeChar.setTradeRefusal(false);
					activeChar.sendMessage("Trade refusal disabled");
				} else {
					activeChar.setTradeRefusal(true);
					activeChar.sendMessage("Trade refusal enabled");
				}
			}
			AdminHtml.showAdminHtml(activeChar, "gm_menu.htm");
		} else if (command.startsWith("admin_setconfig")) {
			final StringTokenizer st = new StringTokenizer(command);
			st.nextToken();
			if (st.countTokens() < 2) {
				activeChar.sendMessage("Usage: //setconfig <parameter> <value>");
			} else {
				setConfig(activeChar, st.nextToken(), st.nextToken());
			}
			showConfigPage(activeChar);
		} else if (command.startsWith("admin_set")) {
			// //set <parameter> <value>, and the old form with the file in front of it
			final StringTokenizer st = new StringTokenizer(command);
			st.nextToken();
			final String[] words = new String[st.countTokens()];
			for (int i = 0; i < words.length; i++) {
				words[i] = st.nextToken();
			}
			if (words.length >= 3) {
				setConfig(activeChar, words[1], words[2]);
			} else if (words.length == 2) {
				setConfig(activeChar, words[0], words[1]);
			} else {
				activeChar.sendMessage("Usage: //set <parameter> <value>");
			}
			showConfigPage(activeChar);
		} else if (command.startsWith("admin_gmon")) {
			// nothing
		}
		return true;
	}
	
	@Override
	public String[] getAdminCommandList() {
		return ADMIN_COMMANDS;
	}
	
	private void showMainPage(L2PcInstance activeChar, String command) {
		final var filename = switch (command.substring(11)) {
			case "1" -> "main";
			case "2" -> "game";
			case "3" -> "effects";
			case "4" -> "server";
			case "5" -> "mods";
			case "6" -> "char";
			case "7" -> "gm";
			default -> "main";
		};
		AdminHtml.showAdminHtml(activeChar, filename + "_menu.htm");
	}
	
	/**
	 * What this server is set to, and a box to change one of the settings that can be changed while
	 * it runs. Most of them cannot: they are read once at the start, and the page says so instead of
	 * offering a button that does nothing.
	 * @param activeChar the GM
	 */
	public void showConfigPage(L2PcInstance activeChar) {
		final StringBuilder out = new StringBuilder(2048);
		out.append("<html><title>Settings</title><body>");
		out.append("<center><table width=280 border=0 bgcolor=\"444444\"><tr>");
		out.append(tab("Panel", "admin_panel")).append(tab("Main", "admin_admin"));
		out.append(tab("Server", "admin_admin4")).append(tab("Settings", "admin_config_server"));
		out.append("</tr></table></center>");
		out.append("<table width=280>");
		out.append(line("Level cap", String.valueOf(character().getMaxPlayerLevel())));
		out.append(line("Experience", "x" + (int) rates().getRateXp() + ", SP x" + (int) rates().getRateSp()));
		out.append(line("Drop chance", "x" + (int) rates().getDeathDropChanceMultiplier()
			+ ", spoil x" + (int) rates().getCorpseDropChanceMultiplier()));
		out.append(line("Quest rewards", "x" + (int) rates().getRateQuestReward()));
		out.append(line("Instances", onOff(chronicle().enableInstances())));
		out.append(line("Gracia", onOff(chronicle().enableGracia())));
		out.append(line("Hellbound", onOff(chronicle().enableHellbound())));
		out.append(line("Territory War", onOff(chronicle().enableTerritoryWar())));
		out.append(line("Attributes", onOff(chronicle().enableAttributes())));
		out.append("</table><br>");
		out.append("<center>Change one right now</center>");
		out.append("<table width=280><tr><td width=100><edit var=\"name\" width=95 height=15></td>");
		out.append("<td width=100><edit var=\"value\" width=95 height=15></td>");
		out.append("<td width=60><button value=\"Set\" action=\"bypass -h admin_setconfig $name $value\" ");
		out.append("width=55 height=21 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></td></tr></table>");
		out.append("<font color=\"888888\">Only what server.properties, general.properties and geodata.properties ");
		out.append("hold changes without a restart. Everything else here is read once at the start: change the file ");
		out.append("and start the server again.</font>");
		out.append("</body></html>");
		activeChar.sendPacket(new NpcHtmlMessage(out.toString()));
	}
	
	/**
	 * Changes one setting of a configuration file that can be written to while the server runs.
	 * @param activeChar the GM to answer
	 * @param name the setting, as it is spelled in the file
	 * @param value what it should be
	 */
	private void setConfig(L2PcInstance activeChar, String name, String value) {
		for (var field : Configuration.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			final Object config;
			try {
				field.setAccessible(true);
				config = field.get(null);
			} catch (Exception ex) {
				continue;
			}
			// only a file that can say whether it holds the setting is asked about it
			if (!(config instanceof Accessible accessible) || (accessible.getProperty(name) == null)) {
				continue;
			}
			if (!(config instanceof Mutable mutable)) {
				activeChar.sendMessage(name + " is read once at the start; change the file and restart.");
				return;
			}
			try {
				mutable.setProperty(name, value);
				activeChar.sendMessage(name + " is now " + value + " (" + field.getName() + ").");
			} catch (Exception ex) {
				LOG.warn("Could not set {} to {}.", name, value, ex);
				activeChar.sendMessage("Could not set " + name + " to " + value + ".");
			}
			return;
		}
		activeChar.sendMessage("No setting called " + name + " can be changed while the server runs.");
	}
	
	private static String onOff(boolean on) {
		return on ? "<font color=\"00AA00\">on</font>" : "off";
	}
	
	private static String line(String name, String value) {
		return "<tr><td width=100>" + name + "</td><td width=180><font color=\"LEVEL\">" + value + "</font></td></tr>";
	}
	
	private static String tab(String name, String bypass) {
		return "<td><button value=\"" + name + "\" action=\"bypass -h " + bypass + "\" width=68 height=21 "
			+ "back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\"></td>";
	}
}
