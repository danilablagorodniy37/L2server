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
package com.l2jserver.datapack.quests.Q00375_WhisperOfDreamsPart2;

import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.holders.QuestItemChanceHolder;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestDroplist;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Whisper of Dreams, Part 2 (375), an Interlude quest missing from H5 (ported from aCis).<br>
 * The Mysterious Stone from {@code Q00374_WhisperOfDreamsPart1} lets Manakia send the player
 * into the Lair of Antharas; a full set of horns and skulls buys an A-grade robe recipe.
 */
public class Q00375_WhisperOfDreamsPart2 extends Quest {
	// NPC
	private static final int MANAKIA = 30515;
	// Monsters
	private static final int KARIK = 20629;
	private static final int CAVE_HOWLER = 20624;
	// Items
	private static final int MYSTERIOUS_STONE = 5887;
	private static final int KARIK_HORN = 5888;
	private static final int CAVE_HOWLER_SKULL = 5889;
	private static final int REQUIRED_COUNT = 100;
	// Droplist
	private static final QuestDroplist DROPLIST = QuestDroplist.builder()
		.addSingleDrop(KARIK, new QuestItemChanceHolder(KARIK_HORN, 100.0, 1, REQUIRED_COUNT))
		.addSingleDrop(CAVE_HOWLER, new QuestItemChanceHolder(CAVE_HOWLER_SKULL, 90.0, 1, REQUIRED_COUNT))
		.build();
	// Rewards: A-grade robe recipes, one at random
	private static final int[] REWARDS = {
		5348, // Recipe: Sealed Dark Crystal Robe (60%)
		5350, // Recipe: Sealed Robe of Nightmare (60%)
		5352, // Recipe: Sealed Majestic Robe (60%)
	};
	// Misc
	private static final int MIN_LEVEL = 60;

	public Q00375_WhisperOfDreamsPart2() {
		super(375, Q00375_WhisperOfDreamsPart2.class.getSimpleName(), "Whisper of Dreams, Part 2");
		bindStartNpc(MANAKIA);
		bindTalk(MANAKIA);
		bindKill(KARIK, CAVE_HOWLER);
		registerQuestItems(KARIK_HORN, CAVE_HOWLER_SKULL);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		switch (event) {
			case "30515-03.htm": {
				if (qs.isCreated() && (player.getLevel() >= MIN_LEVEL) && hasQuestItems(player, MYSTERIOUS_STONE)) {
					qs.startQuest();
					takeItems(player, MYSTERIOUS_STONE, 1);
					return event;
				}
				return null;
			}
			case "30515-06.htm": {
				return qs.isStarted() ? event : null;
			}
			case "30515-07.htm": {
				if (qs.isStarted()) {
					qs.exitQuest(true, true);
					return event;
				}
				return null;
			}
		}
		return null;
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
			htmltext = ((player.getLevel() >= MIN_LEVEL) && hasQuestItems(player, MYSTERIOUS_STONE)) ? "30515-02.htm" : "30515-01.htm";
		} else if (qs.isStarted()) {
			if ((getQuestItemsCount(player, KARIK_HORN) >= REQUIRED_COUNT) && (getQuestItemsCount(player, CAVE_HOWLER_SKULL) >= REQUIRED_COUNT)) {
				takeItems(player, KARIK_HORN, REQUIRED_COUNT);
				takeItems(player, CAVE_HOWLER_SKULL, REQUIRED_COUNT);
				giveItems(player, REWARDS[getRandom(REWARDS.length)], 1);
				htmltext = "30515-05.htm";
			} else {
				htmltext = "30515-04.htm";
			}
		}
		return htmltext;
	}
}
