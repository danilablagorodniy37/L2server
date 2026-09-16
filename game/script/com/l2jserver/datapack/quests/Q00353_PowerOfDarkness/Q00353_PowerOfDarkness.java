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
package com.l2jserver.datapack.quests.Q00353_PowerOfDarkness;

import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestDroplist;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Power of Darkness (353), an Interlude quest missing from H5 (ported from aCis).<br>
 * Galman of the Giran valley buys Stones of Darkness from the succubi.
 */
public class Q00353_PowerOfDarkness extends Quest {
	// NPC
	private static final int GALMAN = 31044;
	// Item
	private static final int STONE_OF_DARKNESS = 5862;
	// Droplist
	private static final QuestDroplist DROPLIST = QuestDroplist.builder()
		.addSingleDrop(20244, STONE_OF_DARKNESS, 48.0) // Malruk Succubus
		.addSingleDrop(20283, STONE_OF_DARKNESS, 48.0) // Malruk Succubus
		.addSingleDrop(20245, STONE_OF_DARKNESS, 50.0) // Malruk Succubus Turen
		.addSingleDrop(20284, STONE_OF_DARKNESS, 50.0) // Malruk Succubus Turen
		.build();
	// Misc
	private static final int MIN_LEVEL = 55;

	public Q00353_PowerOfDarkness() {
		super(353, Q00353_PowerOfDarkness.class.getSimpleName(), "Power of Darkness");
		bindStartNpc(GALMAN);
		bindTalk(GALMAN);
		bindKill(DROPLIST.getNpcIds());
		registerQuestItems(STONE_OF_DARKNESS);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		switch (event) {
			case "31044-03.htm": {
				return qs.isCreated() ? event : null;
			}
			case "31044-04.htm": {
				if (qs.isCreated() && (player.getLevel() >= MIN_LEVEL)) {
					qs.startQuest();
					return event;
				}
				return null;
			}
			case "31044-07.htm": {
				return qs.isStarted() ? event : null;
			}
			case "31044-08.htm": {
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
		final QuestState qs = getQuestState(killer, false);
		if ((qs != null) && qs.isStarted()) {
			giveItemRandomly(killer, npc, DROPLIST.get(npc), true);
		}
		return super.onKill(npc, killer, isSummon);
	}

	@Override
	public String onTalk(L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, true);
		String htmltext = getNoQuestMsg(player);
		if (qs.isCreated()) {
			htmltext = (player.getLevel() >= MIN_LEVEL) ? "31044-02.htm" : "31044-01.htm";
		} else if (qs.isStarted()) {
			final long stones = getQuestItemsCount(player, STONE_OF_DARKNESS);
			if (stones == 0) {
				htmltext = "31044-05.htm";
			} else {
				takeItems(player, STONE_OF_DARKNESS, -1);
				giveAdena(player, 2500 + (230 * stones), true);
				htmltext = "31044-06.htm";
			}
		}
		return htmltext;
	}
}
