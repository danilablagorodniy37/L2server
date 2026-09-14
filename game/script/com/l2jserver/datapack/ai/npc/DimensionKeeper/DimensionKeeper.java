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
package com.l2jserver.datapack.ai.npc.DimensionKeeper;

import static com.l2jserver.gameserver.network.SystemMessageId.YOU_CAN_PROCEED_ONLY_WHEN_THE_INVENTORY_WEIGHT_IS_BELOW_80_PERCENT_AND_THE_QUANTITY_IS_BELOW_90_PERCENT;

import com.l2jserver.datapack.ai.npc.AbstractNpcAI;
import com.l2jserver.gameserver.instancemanager.QuestManager;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerMenuSelected;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerQuestAccepted;
import com.l2jserver.gameserver.network.serverpackets.NpcQuestHtmlMessage;

/**
 * Dimension Keeper AI
 * @author Charus
 * @version 2.6.3.0
 */
public final class DimensionKeeper extends AbstractNpcAI {
	
	// NPCs
	private static final int[] NPCS = {
		31494,
		31495,
		31496,
		31497,
		31498,
		31499,
		31500,
		31501,
		31502,
		31503,
		31504,
		31505,
		31506,
		31507
	};
	
	private static final int DIMENSIONAL_FRAGMENT = 7079;
	
	private static final int Q00255_TUTORIAL = 255;
	private static final int Q00635_INTOTHEDIMENSIONALRIFT = 635;
	
	private static final double WEIGHT_LIMIT = 0.80;
	private static final double QUEST_LIMIT = 0.90;
	private static final int MAX_QUEST_COUNT = 40;
	
	public DimensionKeeper() {
		super(DimensionKeeper.class.getSimpleName(), "ai/npc");
		bindStartNpc(NPCS);
		bindFirstTalk(NPCS);
		bindTalk(NPCS);
		bindMenuSelected(NPCS);
		bindQuestAccepted(NPCS);
	}
	
	@Override
	public void onQuestAccepted(PlayerQuestAccepted event) {
		final var player = event.player();
		final var npc = event.npc();
		
		final var questId = event.questId();
		
		if (questId == Q00635_INTOTHEDIMENSIONALRIFT) {
			if (player.getInventory().getSize(true) >= (player.getQuestInventoryLimit() * QUEST_LIMIT) || player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT) || player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT)) {
				player.sendPacket(YOU_CAN_PROCEED_ONLY_WHEN_THE_INVENTORY_WEIGHT_IS_BELOW_80_PERCENT_AND_THE_QUANTITY_IS_BELOW_90_PERCENT);
				return;
			}
			
			final var q255 = QuestManager.getInstance().getQuest(Q00255_TUTORIAL);
			final var qs255 = q255.getQuestState(player, true);
			
			int i0 = qs255.getMemoStateEx(1);
			int i1 = i0 % 100;
			if (i1 >= 95 || i1 < 0) {
				i1 = 0;
			}
			
			final var q635 = QuestManager.getInstance().getQuest(Q00635_INTOTHEDIMENSIONALRIFT);
			final var qs635 = q635.getQuestState(player, true);
			
			qs635.setMemoState(0);
			qs635.startQuest();
			qs255.setMemoStateEx(1, i1 + getMemoStateId(npc.getId()));
			player.teleToLocation(-114796, -179334, -6752);
			showQuestPage(player, "dimension_keeper_1_q0635_07.htm", Q00635_INTOTHEDIMENSIONALRIFT);
		}
	}
	
	@Override
	public void onMenuSelected(PlayerMenuSelected event) {
		final var player = event.player();
		final var npc = event.npc();
		
		final var ask = event.ask();
		final var reply = event.reply();
		
		switch (ask) {
			case Q00635_INTOTHEDIMENSIONALRIFT -> {
				if (player.getInventory().getSize(true) >= (player.getQuestInventoryLimit() * QUEST_LIMIT) || player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT) || player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT)) {
					player.sendPacket(YOU_CAN_PROCEED_ONLY_WHEN_THE_INVENTORY_WEIGHT_IS_BELOW_80_PERCENT_AND_THE_QUANTITY_IS_BELOW_90_PERCENT);
					return;
				}
				
				if (reply == 1 && !haveMemo(player, Q00635_INTOTHEDIMENSIONALRIFT)) {
					if (player.getLevel() < 20) {
						showQuestPage(player, "dimension_keeper_1_q0635_01.htm", Q00635_INTOTHEDIMENSIONALRIFT);
					} else {
						if (player.getLevel() >= 20) {
							if (player.getAllActiveQuests().size() < MAX_QUEST_COUNT) {
								if (getQuestItemsCount(player, DIMENSIONAL_FRAGMENT) < 1) {
									showQuestPage(player, "dimension_keeper_1_q0635_03.htm", Q00635_INTOTHEDIMENSIONALRIFT);
								} else {
									String html = getHtm(player.getHtmlPrefix(), "dimension_keeper_1_q0635_04.htm");
									html = html.replace("<?quest_id?>", String.valueOf(Q00635_INTOTHEDIMENSIONALRIFT));
									player.sendPacket(new NpcQuestHtmlMessage(npc.getObjectId(), Q00635_INTOTHEDIMENSIONALRIFT, html));
								}
							} else {
								showQuestPage(player, "dimension_keeper_1_q0635_02.htm", Q00635_INTOTHEDIMENSIONALRIFT);
							}
						}
					}
				} else {
					if (reply == 1 && haveMemo(player, Q00635_INTOTHEDIMENSIONALRIFT)) {
						if (player.getLevel() < 20) {
							showPage(player, "dimension_keeper_1_q0635_01.htm");
						} else {
							if (player.getLevel() >= 20) {
								final var q255 = QuestManager.getInstance().getQuest(Q00255_TUTORIAL);
								final var qs255 = q255.getQuestState(player, true);
								
								int i0 = qs255.getMemoStateEx(1);
								int i1 = i0 % 100;
								if (i1 >= 95 || i1 < 0) {
									i1 = 0;
								}
								if (getQuestItemsCount(player, DIMENSIONAL_FRAGMENT) < 1) {
									showPage(player, "dimension_keeper_1_q0635_03.htm");
								} else {
									qs255.setMemoStateEx(1, i1 + getMemoStateId(npc.getId()));
									player.teleToLocation(-114796, -179334, -6752);
									showPage(player, "dimension_keeper_1_q0635_07a.htm");
								}
							}
						}
					}
				}
				if (reply == 2) {
					showPage(player, "dimension_keeper_1_q0635_05.htm");
				}
				if (reply == 3) {
					showPage(player, "dimension_keeper_1_q0635_06.htm");
				}
			}
		}
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player) {
		return switch (npc.getId()) {
			case 31494 -> "dimension_keeper_1001.htm";
			case 31495 -> "dimension_keeper_2001.htm";
			case 31496 -> "dimension_keeper_3001.htm";
			case 31497 -> "dimension_keeper_4001.htm";
			case 31498 -> "dimension_keeper_5001.htm";
			case 31499 -> "dimension_keeper_6001.htm";
			case 31500 -> "dimension_keeper_7001.htm";
			case 31501 -> "dimension_keeper_8001.htm";
			case 31502 -> "dimension_keeper_9001.htm";
			case 31503 -> "dimension_keeper_10001.htm";
			case 31504 -> "dimension_keeper_11001.htm";
			case 31505 -> "dimension_keeper_12001.htm";
			case 31506 -> "dimension_keeper_13001.htm";
			case 31507 -> "dimension_keeper_14001.htm";
			default -> null;
		};
	}
	
	private static int getMemoStateId(int npcId) {
		return switch (npcId) {
			case 31494 -> 10000;
			case 31495 -> 20000;
			case 31496 -> 30000;
			case 31497 -> 40000;
			case 31498 -> 50000;
			case 31499 -> 60000;
			case 31500 -> 70000;
			case 31501 -> 80000;
			case 31502 -> 90000;
			case 31503 -> 100000;
			case 31504 -> 110000;
			case 31505 -> 120000;
			case 31506 -> 130000;
			case 31507 -> 140000;
			default -> 0;
		};
	}
}