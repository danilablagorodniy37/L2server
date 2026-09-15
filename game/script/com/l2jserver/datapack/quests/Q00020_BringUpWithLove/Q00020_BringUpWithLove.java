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
package com.l2jserver.datapack.quests.Q00020_BringUpWithLove;

import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Bring Up With Love (20), Interlude version (ported from aCis).<br>
 * A beast raised at the Beast Farm (FeedableBeasts) may produce a Jewel of Innocence.
 */
public class Q00020_BringUpWithLove extends Quest {
	// NPC
	private static final int TUNATUN = 31537;
	// Item
	private static final int JEWEL_OF_INNOCENCE = 7185;
	// Misc
	private static final int MIN_LEVEL = 65;

	public Q00020_BringUpWithLove() {
		super(20, Q00020_BringUpWithLove.class.getSimpleName(), "Bring Up With Love");
		bindStartNpc(TUNATUN);
		bindTalk(TUNATUN);
		registerQuestItems(JEWEL_OF_INNOCENCE);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		String htmltext = null;
		switch (event) {
			case "31537-03.htm", "31537-04.htm", "31537-05.htm", "31537-06.htm", "31537-07.htm", "31537-08.htm": {
				if (qs.isCreated() && (player.getLevel() >= MIN_LEVEL)) {
					htmltext = event;
				}
				break;
			}
			case "31537-09.htm": {
				if (qs.isCreated() && (player.getLevel() >= MIN_LEVEL)) {
					qs.startQuest();
					htmltext = event;
				}
				break;
			}
			case "31537-12.htm": {
				if (qs.isCond(2) && hasQuestItems(player, JEWEL_OF_INNOCENCE)) {
					takeItems(player, JEWEL_OF_INNOCENCE, -1);
					giveAdena(player, 68500, true);
					qs.exitQuest(false, true);
					htmltext = event;
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
			htmltext = (player.getLevel() >= MIN_LEVEL) ? "31537-01.htm" : "31537-02.htm";
		} else if (qs.isStarted()) {
			htmltext = qs.isCond(2) ? "31537-11.htm" : "31537-10.htm";
		}
		return htmltext;
	}

	/**
	 * Called by the Beast Farm AI when a player raises a beast: 5% chance of a Jewel of Innocence.
	 * @param player the player who fed the beast
	 */
	public static void checkJewelOfInnocence(L2PcInstance player) {
		final QuestState qs = player.getQuestState(Q00020_BringUpWithLove.class.getSimpleName());
		if ((qs != null) && qs.isCond(1) && !hasQuestItems(player, JEWEL_OF_INNOCENCE) && (getRandom(100) < 5)) {
			giveItems(player, JEWEL_OF_INNOCENCE, 1);
			qs.setCond(2, true);
		}
	}
}
