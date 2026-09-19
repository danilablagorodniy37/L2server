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

import java.util.StringTokenizer;

import com.l2jserver.datapack.custom.phantoms.Phantoms;
import com.l2jserver.gameserver.handler.IAdminCommandHandler;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * What the bots are doing, in a window: how many are in town, hunting or keeping shops, what each
 * standing party is up to, and a link that takes the GM to any of their camps.
 * <ul>
 * <li>//bots - the page</li>
 * <li>//bots where &lt;party&gt; - fly to that party</li>
 * </ul>
 */
public class AdminPhantoms implements IAdminCommandHandler {
	private static final String[] ADMIN_COMMANDS = {
		"admin_bots"
	};

	@Override
	public boolean useAdminCommand(String command, L2PcInstance activeChar) {
		final StringTokenizer parts = new StringTokenizer(command);
		parts.nextToken();
		if (parts.hasMoreTokens() && "where".equals(parts.nextToken()) && parts.hasMoreTokens()) {
			final var camp = Phantoms.partyCamp(parts.nextToken());
			if (camp == null) {
				activeChar.sendMessage("No such party.");
				return true;
			}
			activeChar.teleToLocation(camp.getX(), camp.getY(), camp.getZ(), true);
			return true;
		}

		final NpcHtmlMessage page = new NpcHtmlMessage();
		page.setHtml(Phantoms.page());
		activeChar.sendPacket(page);
		return true;
	}

	@Override
	public String[] getAdminCommandList() {
		return ADMIN_COMMANDS;
	}
}
