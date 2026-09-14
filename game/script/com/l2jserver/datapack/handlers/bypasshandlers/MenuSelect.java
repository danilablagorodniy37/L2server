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
package com.l2jserver.datapack.handlers.bypasshandlers;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.gameserver.handler.IBypassHandler;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.instance.L2NpcInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.events.EventDispatcher;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerMenuSelected;

/**
 * Menu Select bypass.
 * @author Zoey76
 * @version 2.6.3.0
 */
public class MenuSelect implements IBypassHandler {
	
	private static final Logger LOG = LoggerFactory.getLogger(MenuSelect.class);
	
	private static final Pattern MENU_SELECT_PATTERN = Pattern.compile("menu_select\\?ask=(-?\\d+).*?&reply=(-?\\d+)");
	
	private static final String[] COMMANDS = {
		"menu_select"
	};
	
	@Override
	public boolean useBypass(String command, L2PcInstance player, L2Character target) {
		if (!(target instanceof L2NpcInstance npc)) {
			return false;
		}
		
		try {
			final var matcher = MENU_SELECT_PATTERN.matcher(command);
			if (matcher.find()) {
				final var ask = Integer.parseInt(matcher.group(1));
				final var reply = Integer.parseInt(matcher.group(2));
				EventDispatcher.getInstance().notifyEventAsync(new PlayerMenuSelected(player, npc, ask, reply), target);
				return true;
			}
			LOG.warn("Invalid bypass {}!", command);
		} catch (Exception ex) {
			LOG.warn("Invalid bypass {}!", command, ex);
		}
		return false;
	}
	
	@Override
	public String[] getBypassList() {
		return COMMANDS;
	}
}
