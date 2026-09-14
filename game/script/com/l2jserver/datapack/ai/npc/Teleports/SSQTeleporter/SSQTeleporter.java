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
package com.l2jserver.datapack.ai.npc.Teleports.SSQTeleporter;

import com.l2jserver.datapack.ai.npc.AbstractNpcAI;
import com.l2jserver.gameserver.SevenSigns;
import com.l2jserver.gameserver.instancemanager.QuestManager;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.itemcontainer.Inventory;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestState;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * SSQ Teleporters
 * @author Charus
 */
public final class SSQTeleporter extends AbstractNpcAI {
	
	// NPCs
	private final static int[] NPCS = {
		// NECRO ENTER
		31095,
		31096,
		31097,
		31098,
		31099,
		31100,
		31101,
		31102,
		// NECRO EXIT
		31103,
		31104,
		31105,
		31106,
		31107,
		31108,
		31109,
		31110,
		// CATA ENTER
		31114,
		31115,
		31116,
		31117,
		31118,
		31119,
		// CATA EXIT
		31120,
		31121,
		31122,
		31123,
		31124,
		31125
	};
	
	private static final int Q00255_Tutorial = 255;
	private static final int Q00635_IntoTheDimensionalRift = 635;
	
	private static final int DIMENSIONAL_FRAGMENT = 7079;
	
	private static final double WEIGHT_LIMIT = 0.80;
	private static final int MAX_QUEST_COUNT = 40;
	
	public SSQTeleporter() {
		super(SSQTeleporter.class.getSimpleName(), "ai/npc/Teleports");
		bindStartNpc(NPCS);
		bindTalk(NPCS);
		bindFirstTalk(NPCS);
	}
	
	private int getMemoStateId(int npcId) {
		return switch (npcId) {
			case 31095, 31103 -> 210000;
			case 31096, 31104 -> 230000;
			case 31097, 31105 -> 250000;
			case 31098, 31106 -> 270000;
			case 31099, 31107 -> 290000;
			case 31100, 31108 -> 300000;
			case 31101, 31109 -> 310000;
			case 31102, 31110 -> 320000;
		
			case 31114, 31120 -> 220000;
			case 31115, 31121 -> 240000;
			case 31116, 31122 -> 260000;
			case 31117, 31123 -> 280000;
			case 31118, 31124 -> 330000;
			case 31119, 31125 -> 340000;
			default -> 0;
		};
	}
	
	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final SevenSigns ss = SevenSigns.getInstance();
		final int ssqPart = ss.getPlayerCabal(player.getObjectId());
		
		final int role = npc.getTemplate().getParameters().getInt("Role", 0);
		final int signNumber = npc.getTemplate().getParameters().getInt("SignNumber", 0);
		
		final int x = npc.getTemplate().getParameters().getInt("PosX");
		final int y = npc.getTemplate().getParameters().getInt("PosY");
		final int z = npc.getTemplate().getParameters().getInt("PosZ");
		
		int ask = Integer.parseInt(event.split(";")[0]);
		switch (ask) {
			case 506 -> {
				int reply = Integer.parseInt(event.split(";")[1]);
				switch (reply) {
					case 1 -> {
						if (ss.isCompetitionPeriod()) {
							if (ssqPart != SevenSigns.CABAL_NULL) {
								player.teleToLocation(x, y, z);
								player.setIsIn7sDungeon(true);
							} else {
								if (role == 1) {
									player.teleToLocation(x, y, z);
									player.setIsIn7sDungeon(false);
								} else {
									return "ss_teleporter_q0506_01.htm";
								}
							}
						} else {
							if (ss.isSealValidationPeriod() || ss.isCompResultsPeriod()) {
								if (ssqPart == SevenSigns.CABAL_NULL) {
									return "ss_teleporter_q0506_01.htm";
								}
								if (ss.getSealOwner(signNumber) == SevenSigns.CABAL_NULL) {
									player.teleToLocation(x, y, z);
									player.setIsIn7sDungeon(true);
								} else {
									if (ssqPart == ss.getSealOwner(signNumber)) {
										player.teleToLocation(x, y, z);
										player.setIsIn7sDungeon(true);
									} else {
										if (role == 1) {
											player.teleToLocation(x, y, z);
											player.setIsIn7sDungeon(false);
										} else {
											return "ss_teleporter_q0506_02.htm";
										}
									}
								}
							} else {
								player.teleToLocation(x, y, z);
								player.setIsIn7sDungeon(false);
							}
						}
					}
				}
			}
			case 635 -> {
				if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
					player.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
					return null;
				}
				
				int reply = Integer.parseInt(event.split(";")[1]);
				switch (reply) {
					case 1 -> {
						if (!haveMemo(player, Q00635_IntoTheDimensionalRift)) {
							if (player.getAllActiveQuests().size() > (MAX_QUEST_COUNT - 1)) {
								final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
								html.setFile(player.getHtmlPrefix(), "data/html/fullquest.html");
								player.sendPacket(html);
								return null;
							}
							if (player.getLevel() >= 20) {
								if (getQuestItemsCount(player, DIMENSIONAL_FRAGMENT) >= 1) {
									int i0 = player.getLevel() / 10;
									if (i0 >= 8) {
										i0 = 7;
									}
									
									int i1 = (i0 * i0) * 500;
									if (getQuestItemsCount(player, Inventory.ADENA_ID) >= i1) {
										return "enter_necropolis1_q0635_04.htm";
									}
									
									return "enter_necropolis1_q0635_05.htm";
								}
								
								return "enter_necropolis1_q0635_03.htm";
							}
							
							return "enter_necropolis1_q0635_02.htm";
						}
						
						if (haveMemo(player, Q00635_IntoTheDimensionalRift)) {
							if (player.getLevel() >= 20) {
								if (getQuestItemsCount(player, DIMENSIONAL_FRAGMENT) >= 1) {
									int i0 = player.getLevel() / 10;
									if (i0 >= 8) {
										i0 = 7;
									}
									
									int i1 = (i0 * i0) * 500;
									if (getQuestItemsCount(player, Inventory.ADENA_ID) >= i1) {
										return "enter_necropolis1_q0635_08.htm";
									}
									
									return "enter_necropolis1_q0635_09.htm";
								}
								
								return "enter_necropolis1_q0635_07.htm";
							}
							
							return "enter_necropolis1_q0635_06.htm";
						}
					}
				}
			}
		}
		
		return super.onEvent(event, npc, player);
	}
	
	@Override
	public String onTalk(L2Npc npc, L2PcInstance player) {
		if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
			player.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
			return null;
		}
		
		Quest q255 = QuestManager.getInstance().getQuest(Q00255_Tutorial);
		QuestState qs255 = q255.getQuestState(player, true);
		
		int i0 = qs255.getMemoStateEx(1);
		int i1 = i0 % 100;
		if ((i1 >= 95) || (i1 < 0)) {
			i1 = 0;
		}
		int i2 = player.getLevel() / 10;
		int i3 = 0;
		if (i2 < 7) {
			i3 = (i2 * i2) * 500;
		} else {
			i3 = (7 * 7) * 500;
		}
		
		Quest q635 = QuestManager.getInstance().getQuest(Q00635_IntoTheDimensionalRift);
		QuestState qs635 = q635.getQuestState(player, true);
		
		if (!haveMemo(player, Q00635_IntoTheDimensionalRift)) {
			if (player.getAllActiveQuests().size() < MAX_QUEST_COUNT) {
				if (getQuestItemsCount(player, Inventory.ADENA_ID) >= i3) {
					qs635.setMemoState(0);
					qs635.startQuest();
					qs255.setMemoStateEx(1, i1 + getMemoStateId(npc.getId()));
					player.teleToLocation(-114796, -179334, -6752);
					takeItems(player, Inventory.ADENA_ID, i3);
					
					return "enter_necropolis1_q0635_10.htm";
				}
				
				return "enter_necropolis1_q0635_11.htm";
			}
		} else {
			if (haveMemo(player, Q00635_IntoTheDimensionalRift)) {
				if (getQuestItemsCount(player, Inventory.ADENA_ID) >= i3) {
					qs635.setMemoState(0);
					qs635.startQuest();
					qs255.setMemoStateEx(1, i1 + getMemoStateId(npc.getId()));
					player.teleToLocation(-114796, -179334, -6752);
					takeItems(player, Inventory.ADENA_ID, i3);
					
					return "enter_necropolis1_q0635_10.htm";
				}
				
				return "enter_necropolis1_q0635_11.htm";
			}
		}
		
		return super.onTalk(npc, player);
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player) {
		return "ss_teleporter001.htm";
	}
}