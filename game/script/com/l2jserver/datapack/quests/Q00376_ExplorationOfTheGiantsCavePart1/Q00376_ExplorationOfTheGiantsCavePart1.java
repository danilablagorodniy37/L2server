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
package com.l2jserver.datapack.quests.Q00376_ExplorationOfTheGiantsCavePart1;

import com.l2jserver.gameserver.enums.audio.Sound;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestDroplist;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Exploration of the Giants' Cave, Part 1 (376), Interlude version (ported from aCis).<br>
 * Ancient Parchments dropped in the Giants' Cave turn into book chapters (skill 2172);
 * Sobling exchanges a complete set of five chapters for an A-grade armor recipe.
 */
public class Q00376_ExplorationOfTheGiantsCavePart1 extends Quest {
	// NPCs
	private static final int SOBLING = 31147;
	private static final int CLIFF = 30182;
	// Items
	private static final int ANCIENT_PARCHMENT = 5944;
	private static final int MYSTERIOUS_BOOK = 5890;
	private static final int ANCIENT_DICTIONARY_BASIC_LEVEL = 5891;
	private static final int ANCIENT_DICTIONARY_INTERMEDIATE_LEVEL = 5892;
	// Book chapters and the recipes a complete book is exchanged for
	private static final int[][] BOOKS = {
		{ 5937, 5938, 5939, 5940, 5941 }, // Medical Science
		{ 5932, 5933, 5934, 5935, 5936 }, // Architecture
		{ 5922, 5923, 5924, 5925, 5926 }, // Golem Plans
		{ 5927, 5928, 5929, 5930, 5931 }, // Basics of Magic
	};
	private static final int[][] RECIPES = {
		{ 5346, 5354 }, // Sealed Tallum Tunic, Sealed Tallum Stockings
		{ 5332, 5334 }, // Sealed Dark Crystal Leather Armor, Sealed Tallum Leather Armor
		{ 5416, 5418 }, // Sealed Dark Crystal Breastplate, Sealed Tallum Plate Armor
		{ 5424, 5340 }, // Sealed Dark Crystal Gaiters, Sealed Dark Crystal Leggings
	};
	// Droplist
	private static final QuestDroplist PARCHMENT_DROPLIST = QuestDroplist.builder()
		.addSingleDrop(20647, ANCIENT_PARCHMENT, 2.6) // Yintzu
		.addSingleDrop(20648, ANCIENT_PARCHMENT, 2.8) // Paliote
		.addSingleDrop(20649, ANCIENT_PARCHMENT, 3.0) // Hamrut
		.addSingleDrop(20650, ANCIENT_PARCHMENT, 3.2) // Kranrot
		.build();
	private static final double MYSTERIOUS_BOOK_CHANCE = 0.2;
	// Misc
	private static final int MIN_LEVEL = 51;

	public Q00376_ExplorationOfTheGiantsCavePart1() {
		super(376, Q00376_ExplorationOfTheGiantsCavePart1.class.getSimpleName(), "Exploration of the Giants' Cave, Part 1");
		bindStartNpc(SOBLING);
		bindTalk(SOBLING, CLIFF);
		bindKill(PARCHMENT_DROPLIST.getNpcIds());
		registerQuestItems(ANCIENT_DICTIONARY_BASIC_LEVEL, MYSTERIOUS_BOOK);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		String htmltext = null;
		switch (event) {
			case "31147-03.htm": {
				if (player.getLevel() >= MIN_LEVEL) {
					qs.startQuest();
					giveItems(player, ANCIENT_DICTIONARY_BASIC_LEVEL, 1);
					htmltext = event;
				}
				break;
			}
			case "31147-04.htm": {
				if (qs.isStarted()) {
					htmltext = checkItems(player, qs);
				}
				break;
			}
			case "31147-06.htm": {
				htmltext = event;
				break;
			}
			case "31147-09.htm": {
				qs.exitQuest(true, true);
				htmltext = event;
				break;
			}
			case "30182-02.htm": {
				if (qs.isCond(2) && hasQuestItems(player, MYSTERIOUS_BOOK)) {
					qs.setCond(3, true);
					takeItems(player, MYSTERIOUS_BOOK, -1);
					giveItems(player, ANCIENT_DICTIONARY_INTERMEDIATE_LEVEL, 1);
					htmltext = event;
				}
				break;
			}
		}
		return htmltext;
	}

	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isSummon) {
		final QuestState qs = getRandomPartyMemberState(killer, -1, 3, npc);
		if (qs != null) {
			final L2PcInstance player = qs.getPlayer();
			// The Mysterious Book drops only to those who still need it.
			if (!hasAtLeastOneQuestItem(player, MYSTERIOUS_BOOK, ANCIENT_DICTIONARY_INTERMEDIATE_LEVEL)) {
				giveItemRandomly(player, npc, player, QuestDroplist.singleDropItem(MYSTERIOUS_BOOK, MYSTERIOUS_BOOK_CHANCE), 1, true);
			}
			giveItemRandomly(player, npc, PARCHMENT_DROPLIST.get(npc), true);
		}
		return super.onKill(npc, killer, isSummon);
	}

	@Override
	public String onTalk(L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, true);
		String htmltext = getNoQuestMsg(player);
		if (qs.isCreated()) {
			if (npc.getId() == SOBLING) {
				htmltext = (player.getLevel() >= MIN_LEVEL) ? "31147-02.htm" : "31147-01.htm";
			}
		} else if (qs.isStarted()) {
			if (npc.getId() == SOBLING) {
				htmltext = checkItems(player, qs);
			} else if (qs.isCond(2) && hasQuestItems(player, MYSTERIOUS_BOOK)) {
				htmltext = "30182-01.htm";
			} else if (qs.isCond(3)) {
				htmltext = "30182-03.htm";
			}
		}
		return htmltext;
	}

	private static String checkItems(L2PcInstance player, QuestState qs) {
		if (hasQuestItems(player, MYSTERIOUS_BOOK)) {
			if (qs.isCond(1)) {
				qs.setCond(2, true);
				return "31147-07.htm";
			}
			return "31147-08.htm";
		}

		for (int type = 0; type < BOOKS.length; type++) {
			if (hasQuestItems(player, BOOKS[type])) {
				for (int book : BOOKS[type]) {
					takeItems(player, book, 1);
				}
				giveItems(player, RECIPES[type][getRandom(RECIPES[type].length)], 1);
				playSound(player, Sound.ITEMSOUND_QUEST_MIDDLE);
				return "31147-04.htm";
			}
		}
		return "31147-05.htm";
	}
}
