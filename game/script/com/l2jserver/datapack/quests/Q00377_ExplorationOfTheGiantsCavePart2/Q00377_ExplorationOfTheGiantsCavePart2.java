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
package com.l2jserver.datapack.quests.Q00377_ExplorationOfTheGiantsCavePart2;

import com.l2jserver.gameserver.enums.audio.Sound;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestDroplist;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Exploration of the Giants' Cave, Part 2 (377), Interlude version (ported from aCis).<br>
 * Ancient Titan Books dropped in the deep Giants' Cave turn into book chapters (skill 2173);
 * Sobling exchanges a complete set of five chapters for an S-grade armor recipe.
 */
public class Q00377_ExplorationOfTheGiantsCavePart2 extends Quest {
	// NPC
	private static final int SOBLING = 31147;
	// Items
	private static final int ANCIENT_TITAN_BOOK = 5955;
	private static final int ANCIENT_DICTIONARY_INTERMEDIATE_LEVEL = 5892;
	// Book chapters and the recipes a complete book is exchanged for
	private static final int[][] BOOKS = {
		{ 5945, 5946, 5947, 5948, 5949 }, // Science and Technology
		{ 5950, 5951, 5952, 5953, 5954 }, // Titan Culture
	};
	private static final int[][] RECIPES = {
		{ 5338, 5336 }, // Sealed Majestic Leather Armor, Sealed Leather Armor of Nightmare
		{ 5420, 5422 }, // Sealed Armor of Nightmare, Sealed Majestic Plate Armor
	};
	// Droplist
	private static final QuestDroplist DROPLIST = QuestDroplist.builder()
		.addSingleDrop(20654, ANCIENT_TITAN_BOOK, 2.5) // Lesser Giant Soldier
		.addSingleDrop(20656, ANCIENT_TITAN_BOOK, 2.2) // Lesser Giant Scout
		.addSingleDrop(20657, ANCIENT_TITAN_BOOK, 1.6) // Lesser Giant Mage
		.addSingleDrop(20658, ANCIENT_TITAN_BOOK, 1.5) // Lesser Giant Elder
		.build();
	// Misc
	private static final int MIN_LEVEL = 57;

	public Q00377_ExplorationOfTheGiantsCavePart2() {
		super(377, Q00377_ExplorationOfTheGiantsCavePart2.class.getSimpleName(), "Exploration of the Giants' Cave, Part 2");
		bindStartNpc(SOBLING);
		bindTalk(SOBLING);
		bindKill(DROPLIST.getNpcIds());
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
				if ((player.getLevel() >= MIN_LEVEL) && hasQuestItems(player, ANCIENT_DICTIONARY_INTERMEDIATE_LEVEL)) {
					qs.startQuest();
					htmltext = event;
				}
				break;
			}
			case "31147-04.htm": {
				if (qs.isStarted()) {
					htmltext = checkItems(player);
				}
				break;
			}
			case "31147-06.htm": {
				htmltext = event;
				break;
			}
			case "31147-07.htm": {
				qs.exitQuest(true, true);
				htmltext = event;
				break;
			}
		}
		return htmltext;
	}

	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isSummon) {
		final QuestState qs = getRandomPartyMemberState(killer, -1, 3, npc);
		if (qs != null) {
			giveItemRandomly(qs.getPlayer(), npc, DROPLIST.get(npc), true);
		}
		return super.onKill(npc, killer, isSummon);
	}

	@Override
	public String onTalk(L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, true);
		String htmltext = getNoQuestMsg(player);
		if (qs.isCreated()) {
			htmltext = ((player.getLevel() >= MIN_LEVEL) && hasQuestItems(player, ANCIENT_DICTIONARY_INTERMEDIATE_LEVEL)) ? "31147-02.htm" : "31147-01.htm";
		} else if (qs.isStarted()) {
			htmltext = checkItems(player);
		}
		return htmltext;
	}

	private static String checkItems(L2PcInstance player) {
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
