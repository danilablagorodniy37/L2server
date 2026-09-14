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
package com.l2jserver.datapack.ai.npc.BlackMarketeerOfMammon;

import java.time.LocalTime;

import com.l2jserver.datapack.ai.npc.AbstractNpcAI;
import com.l2jserver.gameserver.enums.QuestType;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.itemcontainer.Inventory;
import com.l2jserver.gameserver.model.quest.QuestState;
import com.l2jserver.gameserver.model.quest.State;
import com.l2jserver.gameserver.network.SystemMessageId;

/**
 * Black Marketeer of Mammon - Exchange Adena for AA.
 * @author Adry_85
 */
public final class BlackMarketeerOfMammon extends AbstractNpcAI {
	// NPC
	private static final int BLACK_MARKETEER = 31092;
	// Misc
	private static final int MIN_LEVEL = 60;
	
	private static final double WEIGHT_LIMIT = 0.80;
	
	public BlackMarketeerOfMammon() {
		super(BlackMarketeerOfMammon.class.getSimpleName(), "ai/npc");
		bindStartNpc(BLACK_MARKETEER);
		bindFirstTalk(BLACK_MARKETEER);
	}
	
	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		String htmltext = event;
		
		if (event.endsWith(".htm")) {
			return "marketeer_of_mammon" + event;
		}
		
		if (event.startsWith("exchange")) {
			long ancientAdena;
			try {
				ancientAdena = Long.parseLong(event.substring(9).trim());
			} catch (Exception e) {
				return "marketeer_of_mammon_q0506_14.htm";
			}
			
			if (player.getAncientAdena() < ancientAdena) {
				htmltext = "marketeer_of_mammon_q0506_12.htm";
			} else {
				if (ancientAdena <= 0) {
					htmltext = "marketeer_of_mammon_q0506_14.htm";
				} else {
					if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
						player.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
						return null;
					}
					takeItems(player, Inventory.ANCIENT_ADENA_ID, ancientAdena);
					giveItems(player, Inventory.ADENA_ID, ancientAdena);
					htmltext = "marketeer_of_mammon_q0506_13.htm";
				}
			}
			return htmltext;
		}
		
		int ask = Integer.parseInt(event.split(";")[0]);
		int reply = Integer.parseInt(event.split(";")[1]);
		switch (ask) {
			case 506 -> {
				switch (reply) {
					case 3, 4, 5 -> {
						htmltext = "marketeer_of_mammon_q0506_04.htm";
						break;
					}
				}
				break;
			}
			case 989 -> {
				switch (reply) {
					case 3 -> {
						if (!exchangeAvailable()) {
							htmltext = "marketeer_of_mammon002e.htm";
						} else {
							htmltext = "marketeer_of_mammon003.htm";
						}
						break;
					}
				}
				break;
			}
			case 990 -> {
				switch (reply) {
					case 3 -> {
						if (player.getAdena() < 2000000) {
							htmltext = "marketeer_of_mammon002c.htm";
						} else {
							final QuestState qs = getQuestState(player, true);
							if (!qs.isNowAvailable()) {
								htmltext = "marketeer_of_mammon002b.htm";
							} else {
								if (player.getLevel() < MIN_LEVEL) {
									htmltext = "marketeer_of_mammon002d.htm";
								} else {
									if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
										
										player.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
										return null;
									}
									
									qs.setState(State.STARTED);
									takeItems(player, Inventory.ADENA_ID, 2000000);
									giveItems(player, Inventory.ANCIENT_ADENA_ID, 500000);
									qs.exitQuest(QuestType.DAILY, false);
									htmltext = "marketeer_of_mammon004.htm";
								}
							}
						}
						break;
					}
				}
				break;
			}
		}
		
		return htmltext;
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player) {
		return "marketeer_of_mammon001.htm";
	}
	
	private boolean exchangeAvailable() {
		LocalTime localTime = LocalTime.now();
		return (localTime.isAfter(LocalTime.parse("20:00:00")) && localTime.isBefore(LocalTime.MAX));
	}
}
