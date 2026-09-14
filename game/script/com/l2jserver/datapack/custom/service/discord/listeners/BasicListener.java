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
package com.l2jserver.datapack.custom.service.discord.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.datapack.custom.service.discord.DiscordBot;

import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Basic Listener.
 * @author Stalitsa
 * @version 2.6.2.0
 */
public class BasicListener extends ListenerAdapter {
	private static final Logger LOG = LoggerFactory.getLogger(DiscordBot.class);
	
	@Override
	public void onReady(ReadyEvent event) {
		LOG.info("Joined Guilds: " + event.getGuildTotalCount());
	}
	
	@Override
	public void onSessionDisconnect(SessionDisconnectEvent event) {
		if (event.isClosedByServer()) {
			LOG.info(event.getJDA().getSelfUser().getName() + " disconnected (closed by the server) with code: " + event.getServiceCloseFrame().getCloseCode() + " " + event.getCloseCode());
		}
	}
	
	@Override
	public void onSessionResume(SessionResumeEvent event) {
		LOG.info(event.getJDA().getSelfUser().getName() + " has reconnected.");
	}
}
