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
package com.l2jserver.datapack.quests.Q00019_GoToThePastureland;

import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Go to the Pastureland! (19), Interlude version (ported from aCis).
 */
public class Q00019_GoToThePastureland extends Quest {
	// NPCs
	private static final int VLADIMIR = 31302;
	private static final int TUNATUN = 31537;
	// Item
	private static final int YOUNG_WILD_BEAST_MEAT = 7547;
	// Misc
	private static final int MIN_LEVEL = 63;

	public Q00019_GoToThePastureland() {
		super(19, Q00019_GoToThePastureland.class.getSimpleName(), "Go to the Pastureland!");
		bindStartNpc(VLADIMIR);
		bindTalk(VLADIMIR, TUNATUN);
		registerQuestItems(YOUNG_WILD_BEAST_MEAT);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		String htmltext = null;
		switch (event) {
			case "31302-01.htm": {
				if (qs.isCreated() && (player.getLevel() >= MIN_LEVEL)) {
					qs.startQuest();
					giveItems(player, YOUNG_WILD_BEAST_MEAT, 1);
					htmltext = event;
				}
				break;
			}
			case "019_finish": {
				if (!qs.isStarted()) {
					break;
				}
				if (hasQuestItems(player, YOUNG_WILD_BEAST_MEAT)) {
					takeItems(player, YOUNG_WILD_BEAST_MEAT, 1);
					giveAdena(player, 30000, true);
					qs.exitQuest(false, true);
					htmltext = "31537-01.htm";
				} else {
					htmltext = "31537-02.htm";
				}
				break;
			}
		}
		return htmltext;
	}

	@Override
	public String onTalk(L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, true);
		String htmltext = getNoQuestMsg(player);
		if (qs.isCompleted()) {
			htmltext = getAlreadyCompletedMsg(player);
		} else if (qs.isCreated()) {
			if (npc.getId() == VLADIMIR) {
				htmltext = (player.getLevel() >= MIN_LEVEL) ? "31302-00.htm" : "31302-03.htm";
			}
		} else if (qs.isStarted()) {
			htmltext = (npc.getId() == VLADIMIR) ? "31302-02.htm" : "31537-00.htm";
		}
		return htmltext;
	}
}
