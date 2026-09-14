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
package com.l2jserver.datapack.ai.npc.SSQPriest;

import static com.l2jserver.gameserver.config.Configuration.sevenSigns;
import static com.l2jserver.gameserver.network.SystemMessageId.CONTRIB_SCORE_EXCEEDED;
import static com.l2jserver.gameserver.network.SystemMessageId.CONTRIB_SCORE_INCREASED_S1;
import static com.l2jserver.gameserver.network.SystemMessageId.FIGHT_FOR_AVARICE;
import static com.l2jserver.gameserver.network.SystemMessageId.FIGHT_FOR_GNOSIS;
import static com.l2jserver.gameserver.network.SystemMessageId.FIGHT_FOR_STRIFE;
import static com.l2jserver.gameserver.network.SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT;
import static com.l2jserver.gameserver.network.SystemMessageId.SEAL_STONES_ONLY_WHILE_QUEST;
import static com.l2jserver.gameserver.network.SystemMessageId.SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION;
import static com.l2jserver.gameserver.network.SystemMessageId.SEVENSIGNS_PARTECIPATION_DAWN;
import static com.l2jserver.gameserver.network.SystemMessageId.SEVENSIGNS_PARTECIPATION_DUSK;

import java.util.StringTokenizer;

import com.l2jserver.datapack.ai.npc.AbstractNpcAI;
import com.l2jserver.gameserver.SevenSigns;
import com.l2jserver.gameserver.data.xml.impl.MultisellData;
import com.l2jserver.gameserver.enums.CategoryType;
import com.l2jserver.gameserver.enums.audio.Sound;
import com.l2jserver.gameserver.instancemanager.QuestManager;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.itemcontainer.Inventory;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;
import com.l2jserver.gameserver.util.Util;

/**
 * SSQ Priest
 * @author Charus
 */
public final class SSQPriest extends AbstractNpcAI {
	
	// NPCs
	private static final int[] DAWN_NPCS = {
		31078,
		31079,
		31080,
		31081,
		31082,
		31083,
		31084,
		31168,
		31692,
		31694,
		31997
	};
	
	private static final int[] DUSK_NPCS = {
		31085,
		31086,
		31087,
		31088,
		31089,
		31090,
		31091,
		31169,
		31693,
		31695,
		31998
	};
	
	private static final String QUEST_ID = "q0506";
	
	private static final int Q00255_TUTORIAL = 255;
	private static final int Q00505_BLOODOFFERING = 505;
	
	private static final double WEIGHT_LIMIT = 0.80;
	
	private static final int THE_LORD_OF_MANORS_AGREEMENT = 5708;
	private static final int THE_LORD_OF_MANORS_AGREEMENT_NEW = 6388;
	private static final int BLOOD_OF_OFFERING = 5901;
	
	private static final String[][] GLUDIN_POSITION = {
		{
			"Necropolis of Sacrifice",
			"-41184 206752 -3357",
			"3300; 5500"
		}
	};
	
	private static final String[][] GLUDIO_POSITION = {
		{
			"The Patriot's Necropolis",
			"-25472 77728 -3446",
			"5500; 9200"
		},
		{
			"Necropolis of Devotion",
			"-56064 78720 -3011",
			"8700; 14000"
		},
		{
			"Necropolis of Sacrifice",
			"-41184 206752 -3357",
			"4200; 7000"
		},
		{
			"Catacomb of Dark Omens",
			"-22480 13872 -3174",
			"18000; 30000"
		}
	};
	
	private static final String[][] DION_POSITION = {
		{
			"The Pilgrim's Necropolis",
			"45600 126944 -3686",
			"2300; 3900"
		},
		{
			"The Catacomb of the Heretic",
			"39232 143568 -3651",
			"1500; 2600"
		},
		{
			"Catacomb of the Branded",
			"43200 170688 -3251",
			"3500; 5900"
		},
		{
			"Necropolis of Worship",
			"107514 174329 -3704",
			"6800; 11000"
		}
	};
	
	private static final String[][] GIRAN_POSITION = {
		{
			"Necropolis of Martyrdom",
			"114496 132416 -3101",
			"5100; 8600"
		}
	};
	
	private static final String[][] HEINE_POSITION = {
		{
			"Necropolis of Worship",
			"107514 174329 -3704",
			"3300; 5500"
		},
		{
			"The Pilgrim's Necropolis",
			"45600 126944 -3686",
			"8200; 13000"
		},
		{
			"The Catacomb of the Heretic",
			"39232 143568 -3651",
			"7600; 12000"
		},
		{
			"Catacomb of the Branded",
			"43200 170688 -3251",
			"8100; 13000"
		},
		{
			"The Saint's Necropolis",
			"79296 209584 -3709",
			"5700; 9600"
		}
	};
	
	private static final String[][] OREN_POSITION = {
		{
			"Catacomb of the Apostate",
			"74672 78032 -3398",
			"3000; 5000"
		}
	};
	
	private static final String[][] ADEN_POSITION = {
		{
			"The Disciple's Necropolis",
			"168560 -17968 -3174",
			"8400; 14000"
		},
		{
			"Catacomb of the Witch",
			"136672 79328 -3702",
			"7500; 12000"
		},
		{
			"Catacomb of the Forbidden Path",
			"110912 84912 -4816",
			"11000; 18000"
		}
	};
	
	private static final String[][] HUNTERS_POSITION = {
		{
			"Catacomb of the Witch",
			"136672 79328 -3702",
			"3000; 5000"
		},
		{
			"Catacomb of the Forbidden Path",
			"110912 84912 -4816",
			"1700; 2800"
		}
	};
	
	private static final String[][] GODDARD_POSITION = {
		{
			"The Disciple's Necropolis",
			"168560 -17968 -3174",
			"7100; 11000"
		},
		{
			"Catacomb of the Forbidden Path",
			"110912 84912 -4816",
			"24000; 40000"
		},
		{
			"Catacomb of Dark Omens",
			"-22480 13872 -3174",
			"31000; 51000"
		},
		{
			"The Saint's Necropolis",
			"79296 209584 -3709",
			"46000; 76000"
		}
	};
	
	private static final String[][] RUNE_POSITION = {
		{
			"Necropolis of Martyrdom",
			"114496 132416 -3101",
			"28000; 46000"
		},
		{
			"Catacomb of the Witch",
			"136672 79328 -3702",
			"22000; 37000"
		},
		{
			"The Patriot's Necropolis",
			"-25472 77728 -3446",
			"17000; 29000"
		},
		{
			"Necropolis of Devotion",
			"-56064 78720 -3011",
			"23000; 39000"
		},
		{
			"The Disciple's Necropolis",
			"168560 -17968 -3174",
			"21000; 35000"
		},
		{
			"Catacomb of the Forbidden Path",
			"110912 84912 -4816",
			"25000; 41000"
		},
		{
			"Catacomb of Dark Omens",
			"-22480 13872 -3174",
			"15000; 25000"
		},
		{
			"The Saint's Necropolis",
			"79296 209584 -3709",
			"43000; 73000"
		}
	};
	
	private static final String[][] SCHUTT_POSITION = {
		{
			"Necropolis of Sacrifice",
			"-41184 206752 -3357",
			"17000; 29000"
		},
		{
			"The Pilgrim's Necropolis",
			"45600 126944 -3686",
			"19000; 32000"
		},
		{
			"The Catacomb of the Heretic",
			"39232 143568 -3651",
			"20000; 34000"
		},
		{
			"Catacomb of the Branded",
			"43200 170688 -3251",
			"30000; 50000"
		},
		{
			"Necropolis of Worship",
			"107514 174329 -3704",
			"22000; 38000"
		},
		{
			"Catacomb of the Apostate",
			"74672 78032 -3398",
			"26000; 44000"
		},
		{
			"The Patriot's Necropolis",
			"-25472 77728 -3446",
			"29000; 49000"
		}
	};
	
	public SSQPriest() {
		super(SSQPriest.class.getSimpleName(), "ai/npc");
		bindStartNpc(DAWN_NPCS);
		bindStartNpc(DUSK_NPCS);
		bindFirstTalk(DAWN_NPCS);
		bindFirstTalk(DUSK_NPCS);
	}
	
	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		if (event.endsWith(".htm")) {
			return "ssq_npc_priest" + event;
		}
		
		final var ss = SevenSigns.getInstance();
		final var ssqPart = ss.getPlayerCabal(player.getObjectId());
		final var ssqWinner = ss.getCabalHighestScore();
		
		if (event.equalsIgnoreCase("Teleport")) {
			if (!ss.isSealValidationPeriod()) {
				if (ssqPart == SevenSigns.CABAL_NULL) {
					if (Util.contains(DAWN_NPCS, npc.getId())) {
						return "ssq_npc_priest087.htm";
					}
					
					return "ssq_npc_priest088.htm";
				}
				
				if ((player.getTransformationId() == 111) || (player.getTransformationId() == 112) || (player.getTransformationId() == 124)) {
					return "q194_noteleport.htm";
				}
				
				return teleportList(player, npc, true);
			}
			
			if (ss.isSealValidationPeriod() && (ssqPart == ssqWinner)) {
				if ((player.getTransformationId() == 111) || (player.getTransformationId() == 112) || (player.getTransformationId() == 124)) {
					return "q194_noteleport.htm";
				}
				
				return teleportList(player, npc, false);
			}
			
			return super.onEvent(event, npc, player);
		}
		
		if (event.startsWith("Goto")) {
			String portInfo = event.substring(5).trim();
			StringTokenizer st = new StringTokenizer(portInfo);
			
			int x = Integer.parseInt(st.nextToken());
			int y = Integer.parseInt(st.nextToken());
			int z = Integer.parseInt(st.nextToken());
			
			long aa = Long.parseLong(st.nextToken());
			
			if (player.reduceAncientAdena("SevenSigns", aa, npc, true)) {
				player.teleToLocation(x, y, z);
			}
			
			return super.onEvent(event, npc, player);
		}
		
		int ask = Integer.parseInt(event.split(";")[0]);
		switch (ask) {
			case 506 -> {
				final var reply = Integer.parseInt(event.split(";")[1]);
				
				switch (reply) {
					case 0, 38 -> { // participation request
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest_" + QUEST_ID + "_02.htm";
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_36.htm";
					}
					case 1, 39 -> { // purchase the record
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest_" + QUEST_ID + "_16.htm";
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_49.htm";
					}
					case 2, 40 -> { // contribution request
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							if (ssqPart == SevenSigns.CABAL_DAWN) {
								return "ssq_npc_priest_" + QUEST_ID + "_19.htm";
							}
							
							return "ssq_npc_priest_" + QUEST_ID + "_20.htm";
						}
						
						if (ssqPart == SevenSigns.CABAL_DUSK) {
							return "ssq_npc_priest_" + QUEST_ID + "_52.htm";
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_53.htm";
					}
					case 3, 41 -> { // seal choose
						switch (ssqPart) {
							case SevenSigns.CABAL_DAWN -> {
								return "ssq_npc_priest_" + QUEST_ID + "_08.htm";
							}
							case SevenSigns.CABAL_DUSK -> {
								return "ssq_npc_priest_" + QUEST_ID + "_41.htm";
							}
						}
						if (player.isInCategory(CategoryType.FIRST_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_FIRST_CLASS_GROUP)) {
							if (Util.contains(DAWN_NPCS, npc.getId())) {
								return "ssq_npc_priest_" + QUEST_ID + "_03.htm";
							}
							
							return "ssq_npc_priest_" + QUEST_ID + "_37.htm";
						}
						if (player.isInCategory(CategoryType.SECOND_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_SECOND_CLASS_GROUP)) {
							if (Util.contains(DAWN_NPCS, npc.getId())) {
								return "ssq_npc_priest_" + QUEST_ID + "_04.htm";
							}
							
							if (ss.isJoinableToDawn(player)) {
								return "ssq_npc_priest_" + QUEST_ID + "_39a.htm";
							}
							
							return "ssq_npc_priest_" + QUEST_ID + "_38.htm";
						}
						if (player.isInCategory(CategoryType.THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.FOURTH_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_FOURTH_CLASS_GROUP)) {
							if (Util.contains(DAWN_NPCS, npc.getId())) {
								if (ss.isJoinableToDawn(player)) {
									return "ssq_npc_priest_" + QUEST_ID + "_05.htm";
								}
								
								if (getQuestItemsCount(player, THE_LORD_OF_MANORS_AGREEMENT_NEW) > 0) {
									return "ssq_npc_priest_" + QUEST_ID + "_07.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_07a.htm";
							}
							
							if (ss.isJoinableToDawn(player)) {
								return "ssq_npc_priest_" + QUEST_ID + "_39.htm";
							}
							
							return "ssq_npc_priest_" + QUEST_ID + "_40.htm";
						}
					}
					case 4, 5, 9, 14, 15, 20, 21, 22, 30, 31, 33, 35, 42, 43, 48, 53, 54, 55, 56, 64, 67, 69 -> { // back
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							if (ss.isCompetitionPeriod() && ((ssqPart == SevenSigns.CABAL_DAWN) || (ssqPart == SevenSigns.CABAL_NULL))) {
								return "ssq_npc_priest001.htm";
							}
							
							if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_DAWN) && (ss.getSealOwner(SevenSigns.SEAL_GNOSIS) == SevenSigns.CABAL_DAWN) && (ssqPart == SevenSigns.CABAL_DAWN)) {
								return "ssq_npc_priest026.htm";
							}
							
							if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_DAWN) && (ss.getSealOwner(SevenSigns.SEAL_GNOSIS) != SevenSigns.CABAL_DAWN) && (ssqPart == SevenSigns.CABAL_DAWN)) {
								return "ssq_npc_priest029.htm";
							}
							
							if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_DUSK) && ((ssqPart == SevenSigns.CABAL_DAWN) || (ssqPart == SevenSigns.CABAL_NULL))) {
								return "ssq_npc_priest032.htm";
							}
							
							if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_NULL) && ((ssqPart == SevenSigns.CABAL_DAWN) || (ssqPart == SevenSigns.CABAL_NULL))) {
								return "ssq_npc_priest033.htm";
							}
							
							if (ssqPart == SevenSigns.CABAL_DUSK) {
								return "ssq_npc_priest034.htm";
							}
							
							if (ss.isInitializationPeriod()) {
								return "ssq_npc_priest072.htm";
							}
							
							if (ss.isCompResultsPeriod()) {
								return "ssq_npc_priest085.htm";
							}
							
							if (ss.isSealValidationPeriod() && (ssqPart != SevenSigns.CABAL_DAWN)) {
								return "ssq_npc_priest083.htm";
							}
						} else {
							if (ss.isCompetitionPeriod() && ((ssqPart == SevenSigns.CABAL_DUSK) || (ssqPart == SevenSigns.CABAL_NULL))) {
								return "ssq_npc_priest035.htm";
							}
							
							if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_DUSK) && (ss.getSealOwner(SevenSigns.SEAL_GNOSIS) == SevenSigns.CABAL_DUSK) && (ssqPart == SevenSigns.CABAL_DUSK)) {
								return "ssq_npc_priest059.htm";
							}
							
							if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_DUSK) && (ss.getSealOwner(SevenSigns.SEAL_GNOSIS) != SevenSigns.CABAL_DUSK) && (ssqPart == SevenSigns.CABAL_DUSK)) {
								return "ssq_npc_priest062.htm";
							}
							
							if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_DAWN) && ((ssqPart == SevenSigns.CABAL_DUSK) || (ssqPart == SevenSigns.CABAL_NULL))) {
								return "ssq_npc_priest065.htm";
							}
							
							if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_NULL) && ((ssqPart == SevenSigns.CABAL_DUSK) || (ssqPart == SevenSigns.CABAL_NULL))) {
								return "ssq_npc_priest066.htm";
							}
							
							if (ssqPart == SevenSigns.CABAL_DAWN) {
								return "ssq_npc_priest067.htm";
							}
							
							if (ss.isInitializationPeriod()) {
								return "ssq_npc_priest076.htm";
							}
							
							if (ss.isCompResultsPeriod()) {
								return "ssq_npc_priest086.htm";
							}
							
							if (ss.isSealValidationPeriod() && (ssqPart != SevenSigns.CABAL_DUSK)) {
								return "ssq_npc_priest084.htm";
							}
						}
					}
					case 6, 10, 16, 44, 49 -> { // seal of avarice
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest_" + QUEST_ID + "_10.htm";
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_43.htm";
					}
					case 7, 11, 17, 45, 50 -> { // seal of gnosis
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest_" + QUEST_ID + "_11.htm";
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_44.htm";
					}
					case 8, 12, 18, 46, 51 -> { // seal of strife
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest_" + QUEST_ID + "_12.htm";
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_45.htm";
					}
					case 13, 19, 47, 52 -> { // status of seals
						String html = null;
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_09.htm");
						} else {
							html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_42.htm");
						}
						
						if (!ss.isSealValidationPeriod()) {
							html = html.replace("<?seal_avarice?>", "Nothingness");
						} else {
							switch (ss.getSealOwner(SevenSigns.SEAL_AVARICE)) {
								case SevenSigns.CABAL_NULL -> {
									html = html.replace("<?seal_avarice?>", "Nothingness");
								}
								case SevenSigns.CABAL_DAWN -> {
									html = html.replace("<?seal_avarice?>", "Dawn");
								}
								case SevenSigns.CABAL_DUSK -> {
									html = html.replace("<?seal_avarice?>", "Dusk");
								}
							}
						}
						
						if (!ss.isSealValidationPeriod()) {
							html = html.replace("<?seal_revelation?>", "Nothingness");
						} else {
							switch (ss.getSealOwner(SevenSigns.SEAL_GNOSIS)) {
								case SevenSigns.CABAL_NULL -> {
									html = html.replace("<?seal_revelation?>", "Nothingness");
								}
								case SevenSigns.CABAL_DAWN -> {
									html = html.replace("<?seal_revelation?>", "Dawn");
								}
								case SevenSigns.CABAL_DUSK -> {
									html = html.replace("<?seal_revelation?>", "Dusk");
								}
							}
						}
						
						if (!ss.isSealValidationPeriod()) {
							html = html.replace("<?seal_strife?>", "Nothingness");
						} else {
							switch (ss.getSealOwner(SevenSigns.SEAL_STRIFE)) {
								case SevenSigns.CABAL_NULL -> {
									html = html.replace("<?seal_strife?>", "Nothingness");
								}
								case SevenSigns.CABAL_DAWN -> {
									html = html.replace("<?seal_strife?>", "Dawn");
								}
								case SevenSigns.CABAL_DUSK -> {
									html = html.replace("<?seal_strife?>", "Dusk");
								}
							}
						}
						
						return html;
					}
					case 23, 57 -> { // fight for avarice
						final var seal = SevenSigns.SEAL_AVARICE;
						
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
								player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
								return null;
							}
							
							if (ss.getSSQPrevWinner() == SevenSigns.CABAL_DAWN) {
								if (!ss.isCompetitionPeriod()) {
									player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
									return null;
								}
								
								int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
								int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
								int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
								if ((i0 > 0) || (i1 > 0) || (i2 > 0)) {
									int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
									if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
										ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_BLUE_ID, i0);
										ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_GREEN_ID, i1);
										ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_RED_ID, i2);
									} else {
										if (Util.contains(DAWN_NPCS, npc.getId())) {
											return "ssq_npc_priest091.htm";
										}
										
										return "ssq_npc_priest092.htm";
									}
								}
							}
							
							int i4 = 0;
							if (player.isInCategory(CategoryType.THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.FOURTH_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_FOURTH_CLASS_GROUP)) {
								if (!ss.isCompetitionPeriod()) {
									return "ssq_npc_priest_" + QUEST_ID + "_73.htm";
								}
								
								switch (ssqPart) {
									case SevenSigns.CABAL_NULL -> {
										if (ssqPart == SevenSigns.CABAL_NULL) {
											if (Util.contains(DAWN_NPCS, npc.getId())) {
												if (!ss.isJoinableToDawn(player)) {
													if (getQuestItemsCount(player, THE_LORD_OF_MANORS_AGREEMENT_NEW) > 0) {
														takeItems(player, THE_LORD_OF_MANORS_AGREEMENT_NEW, 1);
														i4 = 2;
													} else {
														if (getQuestItemsCount(player, Inventory.ADENA_ID) >= sevenSigns().getSevenSignsJoinDawnFee()) {
															takeItems(player, Inventory.ADENA_ID, sevenSigns().getSevenSignsJoinDawnFee());
															i4 = 3;
														} else {
															return "ssq_npc_priest_" + QUEST_ID + "_06.htm";
														}
													}
												} else {
													i4 = 1;
												}
											} else {
												i4 = 0;
											}
										}
									}
									case SevenSigns.CABAL_DAWN -> {
										return "ssq_npc_priest_" + QUEST_ID + "_08.htm";
									}
									case SevenSigns.CABAL_DUSK -> {
										return "ssq_npc_priest_" + QUEST_ID + "_41.htm";
									}
								}
								if (ss.getSSQPrevWinner() != SevenSigns.CABAL_DAWN) {
									int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
									int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
									int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
									int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
									if (ss.addSSQMember(player, SevenSigns.CABAL_DAWN, seal, 2, i3, i4) == 0) {
										return null;
									}
								} else {
									int i3 = 0;
									if (ss.addSSQMember(player, SevenSigns.CABAL_DAWN, seal, 2, i3, i4) == 0) {
										return null;
									}
								}
							} else {
								i4 = 0;
								if (!ss.isCompetitionPeriod()) {
									return "ssq_npc_priest_" + QUEST_ID + "_73.htm";
								}
								
								if (ss.getSSQPrevWinner() != SevenSigns.CABAL_DAWN) {
									int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
									int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
									int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
									int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
									if (ss.addSSQMember(player, SevenSigns.CABAL_DAWN, seal, 1, i3, i4) == 0) {
										return null;
									}
								} else {
									int i3 = 0;
									if (ss.addSSQMember(player, SevenSigns.CABAL_DAWN, seal, 1, i3, i4) == 0) {
										return null;
									}
								}
							}
							
							if (Util.contains(DAWN_NPCS, npc.getId())) {
								player.sendPacket(SEVENSIGNS_PARTECIPATION_DAWN);
							} else {
								player.sendPacket(SEVENSIGNS_PARTECIPATION_DUSK);
							}
							
							switch (seal) {
								case SevenSigns.SEAL_AVARICE -> {
									player.sendPacket(FIGHT_FOR_AVARICE);
								}
								case SevenSigns.SEAL_GNOSIS -> {
									player.sendPacket(FIGHT_FOR_GNOSIS);
								}
								case SevenSigns.SEAL_STRIFE -> {
									player.sendPacket(FIGHT_FOR_STRIFE);
								}
							}
							
							return "ssq_npc_priest_" + QUEST_ID + "_13.htm";
						}
						if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
							player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
							return null;
						}
						
						if (ss.getSSQPrevWinner() == SevenSigns.CABAL_DUSK) {
							if (!ss.isCompetitionPeriod()) {
								player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
								return null;
							}
							
							int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
							int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
							int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
							if ((i0 > 0) || (i1 > 0) || (i2 > 0)) {
								int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
								if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_BLUE_ID, i0);
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_GREEN_ID, i1);
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_RED_ID, i2);
								} else {
									if (Util.contains(DAWN_NPCS, npc.getId())) {
										return "ssq_npc_priest091.htm";
									}
									
									return "ssq_npc_priest092.htm";
								}
							}
						}
						
						int i4 = 0;
						if (player.isInCategory(CategoryType.THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.FOURTH_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_FOURTH_CLASS_GROUP)) {
							if (!ss.isCompetitionPeriod()) {
								return "ssq_npc_priest_" + QUEST_ID + "_73.htm";
							}
							
							switch (ssqPart) {
								case SevenSigns.CABAL_NULL -> {
									if (ssqPart == SevenSigns.CABAL_NULL) {
										if (Util.contains(DAWN_NPCS, npc.getId())) {
											if (!ss.isJoinableToDawn(player)) {
												if (getQuestItemsCount(player, THE_LORD_OF_MANORS_AGREEMENT_NEW) > 0) {
													takeItems(player, THE_LORD_OF_MANORS_AGREEMENT_NEW, 1);
													i4 = 2;
												} else {
													if (getQuestItemsCount(player, Inventory.ADENA_ID) >= sevenSigns().getSevenSignsJoinDawnFee()) {
														takeItems(player, Inventory.ADENA_ID, sevenSigns().getSevenSignsJoinDawnFee());
														i4 = 3;
													} else {
														return "ssq_npc_priest_" + QUEST_ID + "_06.htm";
													}
												}
											} else {
												i4 = 1;
											}
										} else {
											i4 = 0;
										}
									}
								}
								case SevenSigns.CABAL_DAWN -> {
									return "ssq_npc_priest_" + QUEST_ID + "_08.htm";
								}
								case SevenSigns.CABAL_DUSK -> {
									return "ssq_npc_priest_" + QUEST_ID + "_41.htm";
								}
							}
							
							if (ss.getSSQPrevWinner() != SevenSigns.CABAL_DUSK) {
								int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
								int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
								int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
								int i3 = ((i0) + (i1)) + (i2 * 10);
								if (ss.addSSQMember(player, SevenSigns.CABAL_DUSK, seal, 2, i3, i4) == 0) {
									return null;
								}
							} else {
								int i3 = 0;
								if (ss.addSSQMember(player, SevenSigns.CABAL_DUSK, seal, 2, i3, i4) == 0) {
									return null;
								}
							}
						} else {
							i4 = 0;
							if (!ss.isCompetitionPeriod()) {
								return "ssq_npc_priest_" + QUEST_ID + "_73.htm";
							}
							
							if (ss.getSSQPrevWinner() != SevenSigns.CABAL_DUSK) {
								int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
								int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
								int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
								int i3 = ((i0) + (i1)) + (i2 * 10);
								if (ss.addSSQMember(player, SevenSigns.CABAL_DUSK, seal, 1, i3, i4) == 0) {
									return null;
								}
							} else {
								int i3 = 0;
								if (ss.addSSQMember(player, SevenSigns.CABAL_DUSK, seal, 1, i3, i4) == 0) {
									return null;
								}
							}
						}
						
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							player.sendPacket(SEVENSIGNS_PARTECIPATION_DAWN);
						} else {
							player.sendPacket(SEVENSIGNS_PARTECIPATION_DUSK);
						}
						
						switch (seal) {
							case SevenSigns.SEAL_AVARICE -> {
								player.sendPacket(FIGHT_FOR_AVARICE);
							}
							case SevenSigns.SEAL_GNOSIS -> {
								player.sendPacket(FIGHT_FOR_GNOSIS);
							}
							case SevenSigns.SEAL_STRIFE -> {
								player.sendPacket(FIGHT_FOR_STRIFE);
							}
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_46.htm";
					}
					case 24, 58 -> { // fight for gnosis
						final var seal = SevenSigns.SEAL_GNOSIS;
						
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
								player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
								return null;
							}
							
							if (ss.getSSQPrevWinner() == SevenSigns.CABAL_DAWN) {
								if (!ss.isCompetitionPeriod()) {
									player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
									return null;
								}
								
								int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
								int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
								int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
								if ((i0 > 0) || (i1 > 0) || (i2 > 0)) {
									int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
									if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
										ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_BLUE_ID, i0);
										ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_GREEN_ID, i1);
										ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_RED_ID, i2);
									} else {
										if (Util.contains(DAWN_NPCS, npc.getId())) {
											return "ssq_npc_priest091.htm";
										}
										
										return "ssq_npc_priest092.htm";
									}
								}
							}
							
							int i4 = 0;
							if (player.isInCategory(CategoryType.THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.FOURTH_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_FOURTH_CLASS_GROUP)) {
								if (!ss.isCompetitionPeriod()) {
									return "ssq_npc_priest_" + QUEST_ID + "_73.htm";
								}
								
								switch (ssqPart) {
									case SevenSigns.CABAL_NULL -> {
										if (ssqPart == SevenSigns.CABAL_NULL) {
											if (Util.contains(DAWN_NPCS, npc.getId())) {
												if (!ss.isJoinableToDawn(player)) {
													if (getQuestItemsCount(player, THE_LORD_OF_MANORS_AGREEMENT_NEW) > 0) {
														takeItems(player, THE_LORD_OF_MANORS_AGREEMENT_NEW, 1);
														i4 = 2;
													} else {
														if (getQuestItemsCount(player, Inventory.ADENA_ID) >= sevenSigns().getSevenSignsJoinDawnFee()) {
															takeItems(player, Inventory.ADENA_ID, sevenSigns().getSevenSignsJoinDawnFee());
															i4 = 3;
														} else {
															return "ssq_npc_priest_" + QUEST_ID + "_06.htm";
														}
													}
												} else {
													i4 = 1;
												}
											} else {
												i4 = 0;
											}
										}
									}
									case SevenSigns.CABAL_DAWN -> {
										return "ssq_npc_priest_" + QUEST_ID + "_08.htm";
									}
									case SevenSigns.CABAL_DUSK -> {
										return "ssq_npc_priest_" + QUEST_ID + "_41.htm";
									}
								}
								if (ss.getSSQPrevWinner() != SevenSigns.CABAL_DAWN) {
									int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
									int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
									int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
									int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
									if (ss.addSSQMember(player, SevenSigns.CABAL_DAWN, seal, 2, i3, i4) == 0) {
										return null;
									}
								} else {
									int i3 = 0;
									if (ss.addSSQMember(player, SevenSigns.CABAL_DAWN, seal, 2, i3, i4) == 0) {
										return null;
									}
								}
							} else {
								i4 = 0;
								if (!ss.isCompetitionPeriod()) {
									return "ssq_npc_priest_" + QUEST_ID + "_73.htm";
								}
								
								if (ss.getSSQPrevWinner() != SevenSigns.CABAL_DAWN) {
									int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
									int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
									int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
									int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
									if (ss.addSSQMember(player, SevenSigns.CABAL_DAWN, seal, 1, i3, i4) == 0) {
										return null;
									}
								} else {
									int i3 = 0;
									if (ss.addSSQMember(player, SevenSigns.CABAL_DAWN, seal, 1, i3, i4) == 0) {
										return null;
									}
								}
							}
							
							if (Util.contains(DAWN_NPCS, npc.getId())) {
								player.sendPacket(SEVENSIGNS_PARTECIPATION_DAWN);
							} else {
								player.sendPacket(SEVENSIGNS_PARTECIPATION_DUSK);
							}
							
							switch (seal) {
								case SevenSigns.SEAL_AVARICE -> {
									player.sendPacket(FIGHT_FOR_AVARICE);
								}
								case SevenSigns.SEAL_GNOSIS -> {
									player.sendPacket(FIGHT_FOR_GNOSIS);
								}
								case SevenSigns.SEAL_STRIFE -> {
									player.sendPacket(FIGHT_FOR_STRIFE);
								}
							}
							
							return "ssq_npc_priest_" + QUEST_ID + "_13.htm";
						}
						
						if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
							player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
							return null;
						}
						
						if (ss.getSSQPrevWinner() == SevenSigns.CABAL_DUSK) {
							if (!ss.isCompetitionPeriod()) {
								player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
								return null;
							}
							
							int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
							int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
							int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
							if ((i0 > 0) || (i1 > 0) || (i2 > 0)) {
								int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
								if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_BLUE_ID, i0);
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_GREEN_ID, i1);
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_RED_ID, i2);
								} else {
									if (Util.contains(DAWN_NPCS, npc.getId())) {
										return "ssq_npc_priest091.htm";
									}
									
									return "ssq_npc_priest092.htm";
								}
							}
						}
						
						int i4 = 0;
						if (player.isInCategory(CategoryType.THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.FOURTH_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_FOURTH_CLASS_GROUP)) {
							if (!ss.isCompetitionPeriod()) {
								return "ssq_npc_priest_" + QUEST_ID + "_73.htm";
							}
							
							switch (ssqPart) {
								case SevenSigns.CABAL_NULL -> {
									if (ssqPart == SevenSigns.CABAL_NULL) {
										if (Util.contains(DAWN_NPCS, npc.getId())) {
											if (!ss.isJoinableToDawn(player)) {
												if (getQuestItemsCount(player, THE_LORD_OF_MANORS_AGREEMENT_NEW) > 0) {
													takeItems(player, THE_LORD_OF_MANORS_AGREEMENT_NEW, 1);
													i4 = 2;
												} else {
													if (getQuestItemsCount(player, Inventory.ADENA_ID) >= sevenSigns().getSevenSignsJoinDawnFee()) {
														takeItems(player, Inventory.ADENA_ID, sevenSigns().getSevenSignsJoinDawnFee());
														i4 = 3;
													} else {
														return "ssq_npc_priest_" + QUEST_ID + "_06.htm";
													}
												}
											} else {
												i4 = 1;
											}
										} else {
											i4 = 0;
										}
									}
								}
								case SevenSigns.CABAL_DAWN -> {
									return "ssq_npc_priest_" + QUEST_ID + "_08.htm";
								}
								case SevenSigns.CABAL_DUSK -> {
									return "ssq_npc_priest_" + QUEST_ID + "_41.htm";
								}
							}
							
							if (ss.getSSQPrevWinner() != SevenSigns.CABAL_DUSK) {
								int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
								int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
								int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
								int i3 = ((i0) + (i1)) + (i2 * 10);
								if (ss.addSSQMember(player, SevenSigns.CABAL_DUSK, seal, 2, i3, i4) == 0) {
									return null;
								}
							} else {
								int i3 = 0;
								if (ss.addSSQMember(player, SevenSigns.CABAL_DUSK, seal, 2, i3, i4) == 0) {
									return null;
								}
							}
						} else {
							i4 = 0;
							if (!ss.isCompetitionPeriod()) {
								return "ssq_npc_priest_" + QUEST_ID + "_73.htm";
							}
							
							if (ss.getSSQPrevWinner() != SevenSigns.CABAL_DUSK) {
								int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
								int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
								int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
								int i3 = ((i0) + (i1)) + (i2 * 10);
								if (ss.addSSQMember(player, SevenSigns.CABAL_DUSK, seal, 1, i3, i4) == 0) {
									return null;
								}
							} else {
								int i3 = 0;
								if (ss.addSSQMember(player, SevenSigns.CABAL_DUSK, seal, 1, i3, i4) == 0) {
									return null;
								}
							}
						}
						
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							player.sendPacket(SEVENSIGNS_PARTECIPATION_DAWN);
						} else {
							player.sendPacket(SEVENSIGNS_PARTECIPATION_DUSK);
						}
						
						switch (seal) {
							case SevenSigns.SEAL_AVARICE -> {
								player.sendPacket(FIGHT_FOR_AVARICE);
							}
							case SevenSigns.SEAL_GNOSIS -> {
								player.sendPacket(FIGHT_FOR_GNOSIS);
							}
							case SevenSigns.SEAL_STRIFE -> {
								player.sendPacket(FIGHT_FOR_STRIFE);
							}
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_46.htm";
					}
					case 25, 59 -> { // fight for strife
						final var seal = SevenSigns.SEAL_STRIFE;
						
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
								player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
								return null;
							}
							
							if (ss.getSSQPrevWinner() == SevenSigns.CABAL_DAWN) {
								if (!ss.isCompetitionPeriod()) {
									player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
									return null;
								}
								
								int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
								int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
								int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
								if ((i0 > 0) || (i1 > 0) || (i2 > 0)) {
									int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
									if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
										ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_BLUE_ID, i0);
										ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_GREEN_ID, i1);
										ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_RED_ID, i2);
									} else {
										if (Util.contains(DAWN_NPCS, npc.getId())) {
											return "ssq_npc_priest091.htm";
										}
										
										return "ssq_npc_priest092.htm";
									}
								}
							}
							
							int i4 = 0;
							if (player.isInCategory(CategoryType.THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.FOURTH_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_FOURTH_CLASS_GROUP)) {
								if (!ss.isCompetitionPeriod()) {
									return "ssq_npc_priest_" + QUEST_ID + "_73.htm";
								}
								
								switch (ssqPart) {
									case SevenSigns.CABAL_NULL -> {
										if (ssqPart == SevenSigns.CABAL_NULL) {
											if (Util.contains(DAWN_NPCS, npc.getId())) {
												if (!ss.isJoinableToDawn(player)) {
													if (getQuestItemsCount(player, THE_LORD_OF_MANORS_AGREEMENT_NEW) > 0) {
														takeItems(player, THE_LORD_OF_MANORS_AGREEMENT_NEW, 1);
														i4 = 2;
													} else {
														if (getQuestItemsCount(player, Inventory.ADENA_ID) >= sevenSigns().getSevenSignsJoinDawnFee()) {
															takeItems(player, Inventory.ADENA_ID, sevenSigns().getSevenSignsJoinDawnFee());
															i4 = 3;
														} else {
															return "ssq_npc_priest_" + QUEST_ID + "_06.htm";
														}
													}
												} else {
													i4 = 1;
												}
											} else {
												i4 = 0;
											}
										}
									}
									case SevenSigns.CABAL_DAWN -> {
										return "ssq_npc_priest_" + QUEST_ID + "_08.htm";
									}
									case SevenSigns.CABAL_DUSK -> {
										return "ssq_npc_priest_" + QUEST_ID + "_41.htm";
									}
								}
								if (ss.getSSQPrevWinner() != SevenSigns.CABAL_DAWN) {
									int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
									int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
									int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
									int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
									if (ss.addSSQMember(player, SevenSigns.CABAL_DAWN, seal, 2, i3, i4) == 0) {
										return null;
									}
								} else {
									int i3 = 0;
									if (ss.addSSQMember(player, SevenSigns.CABAL_DAWN, seal, 2, i3, i4) == 0) {
										return null;
									}
								}
							} else {
								i4 = 0;
								if (!ss.isCompetitionPeriod()) {
									return "ssq_npc_priest_" + QUEST_ID + "_73.htm";
								}
								
								if (ss.getSSQPrevWinner() != SevenSigns.CABAL_DAWN) {
									int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
									int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
									int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
									int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
									if (ss.addSSQMember(player, SevenSigns.CABAL_DAWN, seal, 1, i3, i4) == 0) {
										return null;
									}
								} else {
									int i3 = 0;
									if (ss.addSSQMember(player, SevenSigns.CABAL_DAWN, seal, 1, i3, i4) == 0) {
										return null;
									}
								}
							}
							
							if (Util.contains(DAWN_NPCS, npc.getId())) {
								player.sendPacket(SEVENSIGNS_PARTECIPATION_DAWN);
							} else {
								player.sendPacket(SEVENSIGNS_PARTECIPATION_DUSK);
							}
							
							switch (seal) {
								case SevenSigns.SEAL_AVARICE -> {
									player.sendPacket(FIGHT_FOR_AVARICE);
								}
								case SevenSigns.SEAL_GNOSIS -> {
									player.sendPacket(FIGHT_FOR_GNOSIS);
								}
								case SevenSigns.SEAL_STRIFE -> {
									player.sendPacket(FIGHT_FOR_STRIFE);
								}
							}
							
							return "ssq_npc_priest_" + QUEST_ID + "_13.htm";
						}
						
						if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
							player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
							return null;
						}
						
						if (ss.getSSQPrevWinner() == SevenSigns.CABAL_DUSK) {
							if (!ss.isCompetitionPeriod()) {
								player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
								return null;
							}
							
							int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
							int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
							int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
							if ((i0 > 0) || (i1 > 0) || (i2 > 0)) {
								int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
								if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_BLUE_ID, i0);
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_GREEN_ID, i1);
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_RED_ID, i2);
								} else {
									if (Util.contains(DAWN_NPCS, npc.getId())) {
										return "ssq_npc_priest091.htm";
									}
									
									return "ssq_npc_priest092.htm";
								}
							}
						}
						
						int i4 = 0;
						if (player.isInCategory(CategoryType.THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.FOURTH_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_THIRD_CLASS_GROUP) || player.isInCategory(CategoryType.KAMAEL_FOURTH_CLASS_GROUP)) {
							if (!ss.isCompetitionPeriod()) {
								return "ssq_npc_priest_" + QUEST_ID + "_73.htm";
							}
							
							switch (ssqPart) {
								case SevenSigns.CABAL_NULL -> {
									if (ssqPart == SevenSigns.CABAL_NULL) {
										if (Util.contains(DAWN_NPCS, npc.getId())) {
											if (!ss.isJoinableToDawn(player)) {
												if (getQuestItemsCount(player, THE_LORD_OF_MANORS_AGREEMENT_NEW) > 0) {
													takeItems(player, THE_LORD_OF_MANORS_AGREEMENT_NEW, 1);
													i4 = 2;
												} else {
													if (getQuestItemsCount(player, Inventory.ADENA_ID) >= sevenSigns().getSevenSignsJoinDawnFee()) {
														takeItems(player, Inventory.ADENA_ID, sevenSigns().getSevenSignsJoinDawnFee());
														i4 = 3;
													} else {
														return "ssq_npc_priest_" + QUEST_ID + "_06.htm";
													}
												}
											} else {
												i4 = 1;
											}
										} else {
											i4 = 0;
										}
									}
								}
								case SevenSigns.CABAL_DAWN -> {
									return "ssq_npc_priest_" + QUEST_ID + "_08.htm";
								}
								case SevenSigns.CABAL_DUSK -> {
									return "ssq_npc_priest_" + QUEST_ID + "_41.htm";
								}
							}
							
							if (ss.getSSQPrevWinner() != SevenSigns.CABAL_DUSK) {
								int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
								int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
								int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
								int i3 = ((i0) + (i1)) + (i2 * 10);
								if (ss.addSSQMember(player, SevenSigns.CABAL_DUSK, seal, 2, i3, i4) == 0) {
									return null;
								}
							} else {
								int i3 = 0;
								if (ss.addSSQMember(player, SevenSigns.CABAL_DUSK, seal, 2, i3, i4) == 0) {
									return null;
								}
							}
						} else {
							i4 = 0;
							if (!ss.isCompetitionPeriod()) {
								return "ssq_npc_priest_" + QUEST_ID + "_73.htm";
							}
							
							if (ss.getSSQPrevWinner() != SevenSigns.CABAL_DUSK) {
								int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
								int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
								int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
								int i3 = ((i0) + (i1)) + (i2 * 10);
								if (ss.addSSQMember(player, SevenSigns.CABAL_DUSK, seal, 1, i3, i4) == 0) {
									return null;
								}
							} else {
								int i3 = 0;
								if (ss.addSSQMember(player, SevenSigns.CABAL_DUSK, seal, 1, i3, i4) == 0) {
									return null;
								}
							}
						}
						
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							player.sendPacket(SEVENSIGNS_PARTECIPATION_DAWN);
						} else {
							player.sendPacket(SEVENSIGNS_PARTECIPATION_DUSK);
						}
						
						switch (seal) {
							case SevenSigns.SEAL_AVARICE -> {
								player.sendPacket(FIGHT_FOR_AVARICE);
							}
							case SevenSigns.SEAL_GNOSIS -> {
								player.sendPacket(FIGHT_FOR_GNOSIS);
							}
							case SevenSigns.SEAL_STRIFE -> {
								player.sendPacket(FIGHT_FOR_STRIFE);
							}
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_46.htm";
					}
					case 26, 60 -> { // buy ssq record
						if (getQuestItemsCount(player, Inventory.ADENA_ID) > SevenSigns.RECORD_SEVEN_SIGNS_COST) {
							giveItems(player, SevenSigns.RECORD_SEVEN_SIGNS_ID, 1);
							takeItems(player, Inventory.ADENA_ID, SevenSigns.RECORD_SEVEN_SIGNS_COST);
							
							if (Util.contains(DAWN_NPCS, npc.getId())) {
								return "ssq_npc_priest_" + QUEST_ID + "_17.htm";
							}
							
							return "ssq_npc_priest_" + QUEST_ID + "_50.htm";
						}
						
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest_" + QUEST_ID + "_18.htm";
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_51.htm";
					}
					case 27 -> { // dawn blue seal stones contribute request
						if (!ss.isCompetitionPeriod()) {
							player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
							return null;
						}
						
						String html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_21.htm");
						html = html.replace("<?cnt?>", String.valueOf(getQuestItemsCount(player, SevenSigns.SEAL_STONE_BLUE_ID)));
						return html;
					}
					case 28 -> { // dawn green seal stones contribute request
						if (!ss.isCompetitionPeriod()) {
							player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
							return null;
						}
						
						String html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_22.htm");
						html = html.replace("<?cnt?>", String.valueOf(getQuestItemsCount(player, SevenSigns.SEAL_STONE_GREEN_ID)));
						return html;
					}
					case 29 -> { // dawn red seal stones contribute request
						if (!ss.isCompetitionPeriod()) {
							player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
							return null;
						}
						
						String html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_23.htm");
						html = html.replace("<?cnt?>", String.valueOf(getQuestItemsCount(player, SevenSigns.SEAL_STONE_RED_ID)));
						return html;
					}
					case 61 -> { // dusk blue seal stones contribute request
						if (!ss.isCompetitionPeriod()) {
							player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
							return null;
						}
						
						String html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_54.htm");
						html = html.replace("<?cnt?>", String.valueOf(getQuestItemsCount(player, SevenSigns.SEAL_STONE_BLUE_ID)));
						return html;
					}
					case 62 -> { // dusk green seal stones contribute request
						if (!ss.isCompetitionPeriod()) {
							player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
							return null;
						}
						
						String html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_55.htm");
						html = html.replace("<?cnt?>", String.valueOf(getQuestItemsCount(player, SevenSigns.SEAL_STONE_GREEN_ID)));
						return html;
					}
					case 63 -> { // dusk red seal stones contribute request
						if (!ss.isCompetitionPeriod()) {
							player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
							return null;
						}
						
						String html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_56.htm");
						html = html.replace("<?cnt?>", String.valueOf(getQuestItemsCount(player, SevenSigns.SEAL_STONE_RED_ID)));
						return html;
					}
					case 32, 36 -> { // dawn receive reward
						if ((ssqPart == SevenSigns.CABAL_DAWN) && (ssqWinner == SevenSigns.CABAL_DAWN)) {
							if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
								player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
								return null;
							}
							
							if (!ss.isSealValidationPeriod()) {
								player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
								return null;
							}
							
							int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
							int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
							int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
							if ((i0 > 0) || (i1 > 0) || (i2 > 0)) {
								int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
								if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_BLUE_ID, i0);
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_GREEN_ID, i1);
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_RED_ID, i2);
									
									if (Util.contains(DAWN_NPCS, npc.getId())) {
										return "ssq_npc_priest_" + QUEST_ID + "_30.htm";
									}
									
									return "ssq_npc_priest_" + QUEST_ID + "_60.htm";
								}
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest089.htm";
								}
								
								return "ssq_npc_priest090.htm";
							}
							if (Util.contains(DAWN_NPCS, npc.getId())) {
								return "ssq_npc_priest_" + QUEST_ID + "_31.htm";
							}
							
							return "ssq_npc_priest_" + QUEST_ID + "_61.htm";
						}
					}
					case 66, 70 -> { // dusk receive reward
						if ((ssqPart == SevenSigns.CABAL_DUSK) && (ssqWinner == SevenSigns.CABAL_DUSK)) {
							if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
								player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
								return null;
							}
							
							if (!ss.isSealValidationPeriod()) {
								player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
								return null;
							}
							
							int i0 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
							int i1 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
							int i2 = ss.getDepositedSSQItemCount(player, SevenSigns.SEAL_STONE_RED_ID);
							if ((i0 > 0) || (i1 > 0) || (i2 > 0)) {
								int i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
								if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_BLUE_ID, i0);
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_GREEN_ID, i1);
									ss.deleteDepositedSSQItemAndGiveRewards(player, SevenSigns.SEAL_STONE_RED_ID, i2);
									
									if (Util.contains(DAWN_NPCS, npc.getId())) {
										return "ssq_npc_priest_" + QUEST_ID + "_30.htm";
									}
									
									return "ssq_npc_priest_" + QUEST_ID + "_60.htm";
								}
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest089.htm";
								}
								
								return "ssq_npc_priest090.htm";
							}
							
							if (Util.contains(DAWN_NPCS, npc.getId())) {
								return "ssq_npc_priest_" + QUEST_ID + "_31.htm";
							}
							
							return "ssq_npc_priest_" + QUEST_ID + "_61.htm";
						}
					}
					case 71, 73 -> { // request contribute seal stones
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest_" + QUEST_ID + "_19.htm";
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_52.htm";
					}
					case 74 -> { // dawn contribute all seal stones
						if (!ss.isCompetitionPeriod()) {
							player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
							return null;
						}
						
						long i0 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
						long i1 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
						long i2 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_RED_ID);
						if ((i0 > 0) || (i1 > 0) || ((i2 > 0) && (ssqPart == SevenSigns.CABAL_DAWN))) {
							final long contribution = ss.depositSSQItem(player, i0, i1, i2);
							if (contribution != -1) {
								if (i0 > 0) {
									takeItems(player, SevenSigns.SEAL_STONE_BLUE_ID, i0);
								}
								if (i1 > 0) {
									takeItems(player, SevenSigns.SEAL_STONE_GREEN_ID, i1);
								}
								if (i2 > 0) {
									takeItems(player, SevenSigns.SEAL_STONE_RED_ID, i2);
								}
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_70.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_74.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_71.htm";
					}
					case 72 -> { // dusk contribute all seal stones
						if (!ss.isCompetitionPeriod()) {
							player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
							return null;
						}
						
						long i0 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
						long i1 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
						long i2 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_RED_ID);
						if ((i0 > 0) || (i1 > 0) || ((i2 > 0) && (ssqPart == SevenSigns.CABAL_DUSK))) {
							final long contribution = ss.depositSSQItem(player, i0, i1, i2);
							if (contribution != -1) {
								if (i0 > 0) {
									takeItems(player, SevenSigns.SEAL_STONE_BLUE_ID, i0);
								}
								if (i1 > 0) {
									takeItems(player, SevenSigns.SEAL_STONE_GREEN_ID, i1);
								}
								if (i2 > 0) {
									takeItems(player, SevenSigns.SEAL_STONE_RED_ID, i2);
								}
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_70.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_74.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_75.htm";
					}
					case 80 -> { // dawn seal stones exchange request
						if (ssqPart == SevenSigns.CABAL_DAWN) {
							return "ssq_npc_priest_" + QUEST_ID + "_80a.htm";
						}
					}
					case 82 -> { // dawn blue seal stones exchange request
						String html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_80b.htm");
						html = html.replace("<?cnt?>", String.valueOf(getQuestItemsCount(player, SevenSigns.SEAL_STONE_BLUE_ID)));
						return html;
					}
					case 83 -> { // dawn green seal stones exchange request
						String html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_80c.htm");
						html = html.replace("<?cnt?>", String.valueOf(getQuestItemsCount(player, SevenSigns.SEAL_STONE_GREEN_ID)));
						return html;
					}
					case 84 -> { // dawn red seal stones exchange request
						String html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_80d.htm");
						html = html.replace("<?cnt?>", String.valueOf(getQuestItemsCount(player, SevenSigns.SEAL_STONE_RED_ID)));
						return html;
					}
					case 85 -> { // dawn all seal stones exchange
						if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
							player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
							return null;
						}
						
						long i0 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
						long i1 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
						long i2 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_RED_ID);
						if (((i0 > 0) || (i1 > 0) || (i2 > 0)) && (ssqPart == SevenSigns.CABAL_DAWN)) {
							long i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
							if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
								if (i0 > 0) {
									takeItems(player, SevenSigns.SEAL_STONE_BLUE_ID, i0);
								}
								
								if (i1 > 0) {
									takeItems(player, SevenSigns.SEAL_STONE_GREEN_ID, i1);
								}
								
								if (i2 > 0) {
									takeItems(player, SevenSigns.SEAL_STONE_RED_ID, i2);
								}
								
								if (i3 > 0) {
									giveItems(player, Inventory.ANCIENT_ADENA_ID, i3);
								}
							} else {
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest089.htm";
								}
								
								return "ssq_npc_priest090.htm";
							}
							
							return "ssq_npc_priest_" + QUEST_ID + "_80e.htm";
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_80f.htm";
					}
					case 86 -> { // dusk seal stones exchange request
						if (ssqPart == SevenSigns.CABAL_DUSK) {
							return "ssq_npc_priest_" + QUEST_ID + "_81a.htm";
						}
					}
					case 87 -> { // dusk blue seal stones exchange request
						String html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_81b.htm");
						html = html.replace("<?cnt?>", String.valueOf(getQuestItemsCount(player, SevenSigns.SEAL_STONE_BLUE_ID)));
						return html;
					}
					case 88 -> { // dusk green seal stones exchange request
						String html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_81c.htm");
						html = html.replace("<?cnt?>", String.valueOf(getQuestItemsCount(player, SevenSigns.SEAL_STONE_GREEN_ID)));
						return html;
					}
					case 89 -> { // dusk red seal stones exchange request
						String html = getHtm(player.getHtmlPrefix(), "ssq_npc_priest_" + QUEST_ID + "_81d.htm");
						html = html.replace("<?cnt?>", String.valueOf(getQuestItemsCount(player, SevenSigns.SEAL_STONE_RED_ID)));
						return html;
					}
					case 90 -> { // dusk all seal stones exchange request
						if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
							player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
							return null;
						}
						
						long i0 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
						long i1 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
						long i2 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_RED_ID);
						if (((i0 > 0) || (i1 > 0) || (i2 > 0)) && (ssqPart == SevenSigns.CABAL_DUSK)) {
							long i3 = ((i0 * 3) + (i1 * 5)) + (i2 * 10);
							if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
								if (i0 > 0) {
									takeItems(player, SevenSigns.SEAL_STONE_BLUE_ID, i0);
								}
								
								if (i1 > 0) {
									takeItems(player, SevenSigns.SEAL_STONE_GREEN_ID, i1);
								}
								
								if (i2 > 0) {
									takeItems(player, SevenSigns.SEAL_STONE_RED_ID, i2);
								}
								
								if (i3 > 0) {
									giveItems(player, Inventory.ANCIENT_ADENA_ID, i3);
								}
							} else {
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest089.htm";
								}
								
								return "ssq_npc_priest090.htm";
							}
							
							return "ssq_npc_priest_" + QUEST_ID + "_81e.htm";
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_81f.htm";
					}
					case 91, 92, 93 -> { // dawn participation request
						if (player.isInCategory(CategoryType.SECOND_CLASS_GROUP)) {
							return "ssq_npc_priest_" + QUEST_ID + "_04.htm";
						}
						
						if (player.isInCategory(CategoryType.THIRD_CLASS_GROUP) && ss.isJoinableToDawn(player)) {
							return "ssq_npc_priest_" + QUEST_ID + "_05.htm";
						}
						
						if (player.isInCategory(CategoryType.THIRD_CLASS_GROUP) && (getQuestItemsCount(player, THE_LORD_OF_MANORS_AGREEMENT, ss.getCurrentCycle()) > 0)) {
							return "ssq_npc_priest_" + QUEST_ID + "_07.htm";
						}
						
						if (player.isInCategory(CategoryType.FOURTH_CLASS_GROUP) && ss.isJoinableToDawn(player)) {
							return "ssq_npc_priest_" + QUEST_ID + "_05.htm";
						}
						
						if (player.isInCategory(CategoryType.FOURTH_CLASS_GROUP) && (getQuestItemsCount(player, THE_LORD_OF_MANORS_AGREEMENT, ss.getCurrentCycle()) > 0)) {
							return "ssq_npc_priest_" + QUEST_ID + "_07.htm";
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_07a.htm";
					}
					case 94, 95, 96 -> { // dusk participation request
						if (player.isInCategory(CategoryType.SECOND_CLASS_GROUP)) {
							return "ssq_npc_priest_" + QUEST_ID + "_38.htm";
						}
						
						if (player.isInCategory(CategoryType.THIRD_CLASS_GROUP)) {
							return "ssq_npc_priest_" + QUEST_ID + "_40.htm";
						}
						
						if (player.isInCategory(CategoryType.FOURTH_CLASS_GROUP)) {
							return "ssq_npc_priest_" + QUEST_ID + "_40.htm";
						}
					}
					case 97 -> {
						if (getQuestItemsCount(player, Inventory.ADENA_ID) >= sevenSigns().getSevenSignsJoinDawnFee()) {
							return "ssq_npc_priest_" + QUEST_ID + "_07.htm";
						}
						
						return "ssq_npc_priest_" + QUEST_ID + "_07b.htm";
					}
				}
			}
			case -201 -> { // dawn blue seal stones contribute
				final var itemType = SevenSigns.SEAL_STONE_BLUE_ID;
				int reply = 0;
				
				try {
					reply = Integer.parseInt(event.split("; ")[1]);
				} catch (Exception NumberFormatException) {
				}
				
				if (!ss.isCompetitionPeriod()) {
					player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
					return null;
				}
				
				if (reply > 0) {
					if (reply > getQuestItemsCount(player, SevenSigns.SEAL_STONE_BLUE_ID)) {
						return "ssq_npc_priest_" + QUEST_ID + "_24.htm";
					}
					
					switch (itemType) {
						case SevenSigns.SEAL_STONE_BLUE_ID -> {
							final long contribution = ss.depositSSQItem(player, reply, 0, 0);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_BLUE_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						case SevenSigns.SEAL_STONE_GREEN_ID -> {
							final long contribution = ss.depositSSQItem(player, 0, reply, 0);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_BLUE_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						case SevenSigns.SEAL_STONE_RED_ID -> {
							final long contribution = ss.depositSSQItem(player, 0, 0, reply);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_BLUE_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
					}
				} else {
					return "ssq_npc_priest_" + QUEST_ID + "_79.htm";
				}
			}
			case -202 -> { // dawn green seal stones contribute
				final var itemType = SevenSigns.SEAL_STONE_GREEN_ID;
				int reply = 0;
				
				try {
					reply = Integer.parseInt(event.split("; ")[1]);
				} catch (Exception NumberFormatException) {
				}
				
				if (!ss.isCompetitionPeriod()) {
					player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
					return null;
				}
				
				if (reply > 0) {
					if (reply > getQuestItemsCount(player, SevenSigns.SEAL_STONE_GREEN_ID)) {
						return "ssq_npc_priest_" + QUEST_ID + "_24.htm";
					}
					
					switch (itemType) {
						case SevenSigns.SEAL_STONE_BLUE_ID -> {
							final long contribution = ss.depositSSQItem(player, reply, 0, 0);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_GREEN_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						case SevenSigns.SEAL_STONE_GREEN_ID -> {
							final long contribution = ss.depositSSQItem(player, 0, reply, 0);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_GREEN_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						case SevenSigns.SEAL_STONE_RED_ID -> {
							final long contribution = ss.depositSSQItem(player, 0, 0, reply);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_GREEN_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
					}
				} else {
					return "ssq_npc_priest_" + QUEST_ID + "_79.htm";
				}
			}
			case -203 -> { // dawn red seal stones contribute
				final var itemType = SevenSigns.SEAL_STONE_RED_ID;
				int reply = 0;
				
				try {
					reply = Integer.parseInt(event.split("; ")[1]);
				} catch (Exception NumberFormatException) {
				}
				
				if (!ss.isCompetitionPeriod()) {
					player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
					return null;
				}
				
				if (reply > 0) {
					if (reply > getQuestItemsCount(player, SevenSigns.SEAL_STONE_RED_ID)) {
						return "ssq_npc_priest_" + QUEST_ID + "_24.htm";
					}
					
					switch (itemType) {
						case SevenSigns.SEAL_STONE_BLUE_ID -> {
							final long contribution = ss.depositSSQItem(player, reply, 0, 0);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_RED_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						case SevenSigns.SEAL_STONE_GREEN_ID -> {
							final long contribution = ss.depositSSQItem(player, 0, reply, 0);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_RED_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						case SevenSigns.SEAL_STONE_RED_ID -> {
							final long contribution = ss.depositSSQItem(player, 0, 0, reply);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_RED_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
					}
				} else {
					return "ssq_npc_priest_" + QUEST_ID + "_79.htm";
				}
			}
			case -204 -> { // dusk blue seal stones contribute
				final var itemType = SevenSigns.SEAL_STONE_BLUE_ID;
				int reply = 0;
				
				try {
					reply = Integer.parseInt(event.split("; ")[1]);
				} catch (Exception NumberFormatException) {
				}
				
				if (!ss.isCompetitionPeriod()) {
					player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
					return null;
				}
				
				if (reply > 0) {
					if (reply > getQuestItemsCount(player, SevenSigns.SEAL_STONE_BLUE_ID)) {
						return "ssq_npc_priest_" + QUEST_ID + "_57.htm";
					}
					
					switch (itemType) {
						case SevenSigns.SEAL_STONE_BLUE_ID -> {
							final long contribution = ss.depositSSQItem(player, reply, 0, 0);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_BLUE_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						case SevenSigns.SEAL_STONE_GREEN_ID -> {
							final long contribution = ss.depositSSQItem(player, 0, reply, 0);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_BLUE_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						case SevenSigns.SEAL_STONE_RED_ID -> {
							final long contribution = ss.depositSSQItem(player, 0, 0, reply);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_BLUE_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
					}
				} else {
					return "ssq_npc_priest_" + QUEST_ID + "_80.htm";
				}
			}
			case -205 -> { // dusk green seal stones contribute
				final var itemType = SevenSigns.SEAL_STONE_GREEN_ID;
				int reply = 0;
				
				try {
					reply = Integer.parseInt(event.split("; ")[1]);
				} catch (Exception NumberFormatException) {
				}
				
				if (!ss.isCompetitionPeriod()) {
					player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
					return null;
				}
				
				if (reply > 0) {
					if (reply > getQuestItemsCount(player, SevenSigns.SEAL_STONE_GREEN_ID)) {
						return "ssq_npc_priest_" + QUEST_ID + "_57.htm";
					}
					
					switch (itemType) {
						case SevenSigns.SEAL_STONE_BLUE_ID -> {
							final long contribution = ss.depositSSQItem(player, reply, 0, 0);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_GREEN_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						case SevenSigns.SEAL_STONE_GREEN_ID -> {
							final long contribution = ss.depositSSQItem(player, 0, reply, 0);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_GREEN_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						case SevenSigns.SEAL_STONE_RED_ID -> {
							final long contribution = ss.depositSSQItem(player, 0, 0, reply);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_GREEN_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
					}
				} else {
					return "ssq_npc_priest_" + QUEST_ID + "_80.htm";
				}
			}
			case -206 -> { // dusk red seal stones contribute
				final var itemType = SevenSigns.SEAL_STONE_RED_ID;
				int reply = 0;
				
				try {
					reply = Integer.parseInt(event.split("; ")[1]);
				} catch (Exception NumberFormatException) {
				}
				
				if (!ss.isCompetitionPeriod()) {
					player.sendPacket(SEAL_STONES_ONLY_WHILE_QUEST);
					return null;
				}
				
				if (reply > 0) {
					if (reply > getQuestItemsCount(player, SevenSigns.SEAL_STONE_RED_ID)) {
						return "ssq_npc_priest_" + QUEST_ID + "_57.htm";
					}
					
					switch (itemType) {
						case SevenSigns.SEAL_STONE_BLUE_ID -> {
							final long contribution = ss.depositSSQItem(player, reply, 0, 0);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_RED_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						case SevenSigns.SEAL_STONE_GREEN_ID -> {
							final long contribution = ss.depositSSQItem(player, 0, reply, 0);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_RED_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
						case SevenSigns.SEAL_STONE_RED_ID -> {
							final long contribution = ss.depositSSQItem(player, 0, 0, reply);
							if (contribution != -1) {
								takeItems(player, SevenSigns.SEAL_STONE_RED_ID, reply);
								
								SystemMessage sm = SystemMessage.getSystemMessage(CONTRIB_SCORE_INCREASED_S1);
								sm.addLong(contribution);
								player.sendPacket(sm);
								
								if (Util.contains(DAWN_NPCS, npc.getId())) {
									return "ssq_npc_priest_" + QUEST_ID + "_25.htm";
								}
								
								return "ssq_npc_priest_" + QUEST_ID + "_58.htm";
							}
							
							player.sendPacket(CONTRIB_SCORE_EXCEEDED);
							return null;
						}
					}
				} else {
					return "ssq_npc_priest_" + QUEST_ID + "_80.htm";
				}
			}
			case -207 -> { // dawn blue seal stone exchange
				int reply = 0;
				
				try {
					reply = Integer.parseInt(event.split("; ")[1]);
				} catch (Exception NumberFormatException) {
				}
				
				if (!ss.isSealValidationPeriod()) {
					player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
					return null;
				}
				
				if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
					player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
					return null;
				}
				
				long i0 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
				if (reply > 0) {
					if (reply > i0) {
						return "ssq_npc_priest_" + QUEST_ID + "_80g.htm";
					}
					
					long i3 = reply * 3L;
					if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
						if (reply > 0) {
							takeItems(player, SevenSigns.SEAL_STONE_BLUE_ID, reply);
							
							if (i3 > 0) {
								giveItems(player, Inventory.ANCIENT_ADENA_ID, i3);
							}
						}
					} else {
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest089.htm";
						}
						
						return "ssq_npc_priest090.htm";
					}
					
					return "ssq_npc_priest_" + QUEST_ID + "_80h.htm";
				}
				
				return "ssq_npc_priest_" + QUEST_ID + "_80i.htm";
			}
			case -208 -> { // dawn green seal stone exchange
				int reply = 0;
				
				try {
					reply = Integer.parseInt(event.split("; ")[1]);
				} catch (Exception NumberFormatException) {
				}
				
				if (!ss.isSealValidationPeriod()) {
					player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
					return null;
				}
				
				if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
					player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
					return null;
				}
				
				long i0 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
				if (reply > 0) {
					if (reply > i0) {
						return "ssq_npc_priest_" + QUEST_ID + "_80g.htm";
					}
					
					long i3 = reply * 5L;
					if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
						if (reply > 0) {
							takeItems(player, SevenSigns.SEAL_STONE_GREEN_ID, reply);
							
							if (i3 > 0) {
								giveItems(player, Inventory.ANCIENT_ADENA_ID, i3);
							}
						}
					} else {
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest089.htm";
						}
						
						return "ssq_npc_priest090.htm";
					}
					
					return "ssq_npc_priest_" + QUEST_ID + "_80h.htm";
				}
				
				return "ssq_npc_priest_" + QUEST_ID + "_80i.htm";
			}
			case -209 -> { // dawn red seal stone exchange
				int reply = 0;
				
				try {
					reply = Integer.parseInt(event.split("; ")[1]);
				} catch (Exception NumberFormatException) {
				}
				
				if (!ss.isSealValidationPeriod()) {
					player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
					return null;
				}
				
				if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
					player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
					return null;
				}
				
				long i0 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_RED_ID);
				if (reply > 0) {
					if (reply > i0) {
						return "ssq_npc_priest_" + QUEST_ID + "_80g.htm";
					}
					
					long i3 = reply * 10L;
					if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
						if (reply > 0) {
							takeItems(player, SevenSigns.SEAL_STONE_RED_ID, reply);
							
							if (i3 > 0) {
								giveItems(player, Inventory.ANCIENT_ADENA_ID, i3);
							}
						}
					} else {
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest089.htm";
						}
						
						return "ssq_npc_priest090.htm";
					}
					
					return "ssq_npc_priest_" + QUEST_ID + "_80h.htm";
				}
				
				return "ssq_npc_priest_" + QUEST_ID + "_80i.htm";
			}
			case -210 -> { // dusk blue seal stone exchange
				int reply = 0;
				
				try {
					reply = Integer.parseInt(event.split("; ")[1]);
				} catch (Exception NumberFormatException) {
				}
				
				if (!ss.isSealValidationPeriod()) {
					player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
					return null;
				}
				
				if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
					player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
					return null;
				}
				
				long i0 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_BLUE_ID);
				if (reply > 0) {
					if (reply > i0) {
						return "ssq_npc_priest_" + QUEST_ID + "_81g.htm";
					}
					
					long i3 = reply * 3L;
					if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
						if (reply > 0) {
							takeItems(player, SevenSigns.SEAL_STONE_BLUE_ID, reply);
							
							if (i3 > 0) {
								giveItems(player, Inventory.ANCIENT_ADENA_ID, i3);
							}
						}
					} else {
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest089.htm";
						}
						
						return "ssq_npc_priest090.htm";
					}
					
					return "ssq_npc_priest_" + QUEST_ID + "_81h.htm";
				}
				
				return "ssq_npc_priest_" + QUEST_ID + "_81i.htm";
			}
			case -211 -> { // dusk green seal stone exchange
				int reply = 0;
				
				try {
					reply = Integer.parseInt(event.split("; ")[1]);
				} catch (Exception NumberFormatException) {
				}
				
				if (!ss.isSealValidationPeriod()) {
					player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
					return null;
				}
				
				if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
					player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
					return null;
				}
				
				long i0 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_GREEN_ID);
				if (reply > 0) {
					if (reply > i0) {
						return "ssq_npc_priest_" + QUEST_ID + "_81g.htm";
					}
					
					long i3 = reply * 5L;
					if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
						if (reply > 0) {
							takeItems(player, SevenSigns.SEAL_STONE_GREEN_ID, reply);
							
							if (i3 > 0) {
								giveItems(player, Inventory.ANCIENT_ADENA_ID, i3);
							}
						}
					} else {
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest089.htm";
						}
						
						return "ssq_npc_priest090.htm";
					}
					
					return "ssq_npc_priest_" + QUEST_ID + "_81h.htm";
				}
				
				return "ssq_npc_priest_" + QUEST_ID + "_81i.htm";
			}
			case -212 -> { // dusk red seal stone exchange
				int reply = 0;
				
				try {
					reply = Integer.parseInt(event.split("; ")[1]);
				} catch (Exception NumberFormatException) {
				}
				
				if (!ss.isSealValidationPeriod()) {
					player.sendPacket(SETTLE_ACCOUNT_ONLY_IN_SEAL_VALIDATION);
					return null;
				}
				
				if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
					player.sendPacket(INVENTORY_LESS_THAN_80_PERCENT);
					return null;
				}
				
				long i0 = getQuestItemsCount(player, SevenSigns.SEAL_STONE_RED_ID);
				if (reply > 0) {
					if (reply > i0) {
						return "ssq_npc_priest_" + QUEST_ID + "_81g.htm";
					}
					
					long i3 = reply * 10L;
					if ((getQuestItemsCount(player, Inventory.ANCIENT_ADENA_ID) + i3) < Integer.MAX_VALUE) {
						if (reply > 0) {
							takeItems(player, SevenSigns.SEAL_STONE_RED_ID, reply);
							
							if (i3 > 0) {
								giveItems(player, Inventory.ANCIENT_ADENA_ID, i3);
							}
						}
					} else {
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest089.htm";
						}
						
						return "ssq_npc_priest090.htm";
					}
					
					return "ssq_npc_priest_" + QUEST_ID + "_81h.htm";
				}
				
				return "ssq_npc_priest_" + QUEST_ID + "_81i.htm";
			}
			case -303 -> {
				final var reply = Integer.parseInt(event.split(";")[1]);
				MultisellData.getInstance().separateAndSend(reply, player, npc, false);
			}
			case 507 -> { // Festival of Darkness
				final var reply = Integer.parseInt(event.split(";")[1]);
				switch (reply) {
					case 1 -> {
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							return "ssq_npc_priest_q507_01.htm";
						}
						
						return "ssq_npc_priest_q507_02.htm";
					}
					case 2 -> {
						final var q255 = QuestManager.getInstance().getQuest(Q00255_TUTORIAL);
						final var qs255 = q255.getQuestState(player, true);
						
						int i0 = qs255.getMemoStateEx(1);
						i0 = i0 % 100;
						if (i0 >= 95 || i0 < 0) {
							i0 = 0;
						}
						
						qs255.setMemoStateEx(1, i0 + getMemoStateId(npc.getId()));
						
						if (haveMemo(player, Q00505_BLOODOFFERING)) {
							final var q505 = QuestManager.getInstance().getQuest(Q00505_BLOODOFFERING);
							final var qs505 = q505.getQuestState(player, true);
							qs505.exitQuest(true, false);
						}
						
						takeItems(player, BLOOD_OF_OFFERING, getQuestItemsCount(player, BLOOD_OF_OFFERING));
						
						if (Util.contains(DAWN_NPCS, npc.getId())) {
							switch (getRandom(2)) {
								case 0 -> {
									player.teleToLocation(-80316, 111356, -4896);
								}
								case 1 -> {
									player.teleToLocation(-80226, 111290, -4896);
								}
								case 2 -> {
									player.teleToLocation(-80217, 111435, -4896);
								}
							}
						} else {
							switch (getRandom(2)) {
								case 0 -> {
									player.teleToLocation(-81328, 86536, -5152);
								}
								case 1 -> {
									player.teleToLocation(-81262, 86468, -5152);
								}
								case 2 -> {
									player.teleToLocation(-81248, 86582, -5152);
								}
							}
						}
						
						playSound(player, Sound.ITEMSOUND_QUEST_ACCEPT);
					}
				}
			}
		}
		
		return super.onEvent(event, npc, player);
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player) {
		final var ss = SevenSigns.getInstance();
		final var ssqPart = ss.getPlayerCabal(player.getObjectId());
		final var ssqWinner = ss.getCabalHighestScore();
		
		if (Util.contains(DAWN_NPCS, npc.getId())) {
			if (ss.isCompetitionPeriod() && ((ssqPart == SevenSigns.CABAL_DAWN) || (ssqPart == SevenSigns.CABAL_NULL))) {
				return "ssq_npc_priest001.htm";
			}
			
			if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_DAWN) && (ss.getSealOwner(SevenSigns.SEAL_GNOSIS) == SevenSigns.CABAL_DAWN) && (ssqPart == SevenSigns.CABAL_DAWN)) {
				return "ssq_npc_priest026.htm";
			}
			
			if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_DAWN) && (ss.getSealOwner(SevenSigns.SEAL_GNOSIS) != SevenSigns.CABAL_DAWN) && (ssqPart == SevenSigns.CABAL_DAWN)) {
				return "ssq_npc_priest029.htm";
			}
			
			if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_DUSK) && ((ssqPart == SevenSigns.CABAL_DAWN) || (ssqPart == SevenSigns.CABAL_NULL))) {
				return "ssq_npc_priest032.htm";
			}
			
			if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_NULL) && ((ssqPart == SevenSigns.CABAL_DAWN) || (ssqPart == SevenSigns.CABAL_NULL))) {
				return "ssq_npc_priest033.htm";
			}
			
			if (ssqPart == SevenSigns.CABAL_DUSK) {
				return "ssq_npc_priest034.htm";
			}
			
			if (ss.isInitializationPeriod()) {
				return "ssq_npc_priest072.htm";
			}
			
			if (ss.isCompResultsPeriod()) {
				return "ssq_npc_priest085.htm";
			}
			
			if (ss.isSealValidationPeriod() && (ssqPart != SevenSigns.CABAL_DAWN)) {
				return "ssq_npc_priest083.htm";
			}
		} else {
			if (ss.isCompetitionPeriod() && ((ssqPart == SevenSigns.CABAL_DUSK) || (ssqPart == SevenSigns.CABAL_NULL))) {
				return "ssq_npc_priest035.htm";
			}
			
			if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_DUSK) && (ss.getSealOwner(SevenSigns.SEAL_GNOSIS) == SevenSigns.CABAL_DUSK) && (ssqPart == SevenSigns.CABAL_DUSK)) {
				return "ssq_npc_priest059.htm";
			}
			
			if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_DUSK) && (ss.getSealOwner(SevenSigns.SEAL_GNOSIS) != SevenSigns.CABAL_DUSK) && (ssqPart == SevenSigns.CABAL_DUSK)) {
				return "ssq_npc_priest062.htm";
			}
			
			if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_DAWN) && ((ssqPart == SevenSigns.CABAL_DUSK) || (ssqPart == SevenSigns.CABAL_NULL))) {
				return "ssq_npc_priest065.htm";
			}
			
			if (ss.isSealValidationPeriod() && (ssqWinner == SevenSigns.CABAL_NULL) && ((ssqPart == SevenSigns.CABAL_DUSK) || (ssqPart == SevenSigns.CABAL_NULL))) {
				return "ssq_npc_priest066.htm";
			}
			
			if (ssqPart == SevenSigns.CABAL_DAWN) {
				return "ssq_npc_priest067.htm";
			}
			
			if (ss.isInitializationPeriod()) {
				return "ssq_npc_priest076.htm";
			}
			
			if (ss.isCompResultsPeriod()) {
				return "ssq_npc_priest086.htm";
			}
			
			if (ss.isSealValidationPeriod() && (ssqPart != SevenSigns.CABAL_DUSK)) {
				return "ssq_npc_priest084.htm";
			}
		}
		
		return super.onFirstTalk(npc, player);
	}
	
	private static int getMemoStateId(int npcId) {
		return switch (npcId) {
			case 31078, 31085 -> 100;
			case 31079, 31086 -> 200;
			case 31080, 31087 -> 300;
			case 31081, 31088 -> 400;
			case 31082, 31089 -> 500;
			case 31083, 31090 -> 600;
			case 31084, 31091 -> 700;
			case 31168, 31169 -> 800;
			case 31692, 31693 -> 1000;
			case 31694, 31695 -> 1100;
			case 31997, 31998 -> 1200;
			default -> 0;
		};
	}
	
	private static String[][] getPositionArray(int npcId) {
		return switch (npcId) {
			case 31078, 31085 -> GLUDIN_POSITION;
			case 31079, 31086 -> GLUDIO_POSITION;
			case 31080, 31087 -> DION_POSITION;
			case 31081, 31088 -> GIRAN_POSITION;
			case 31082, 31089 -> HEINE_POSITION;
			case 31083, 31090 -> OREN_POSITION;
			case 31084, 31091 -> ADEN_POSITION;
			case 31168, 31169 -> HUNTERS_POSITION;
			case 31692, 31693 -> GODDARD_POSITION;
			case 31694, 31695 -> RUNE_POSITION;
			case 31997, 31998 -> SCHUTT_POSITION;
			default -> new String[0][0];
		};
	}
	
	private static String teleportList(L2PcInstance player, L2Npc npc, boolean comp) {
		final var html = new StringBuilder("<html><body>&$556;<br><br>");
		String[][] positions = getPositionArray(npc.getId());
		
		for (String[] s : positions) {
			String name = s[0];
			String location = s[1];
			String[] price = s[2].split(";");
			String pr = comp ? price[1].trim() : price[0].trim();
			
			html.append("<a action=\"bypass -h Quest SSQPriest Goto " + location + " " + pr + "\" msg=\"811;" + name + "\">" + name + " - " + pr + " Ancient Adena</a><br1>");
		}
		html.append("</body></html>");
		
		return html.toString();
	}
}