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
package com.l2jserver.datapack.quests.Q00646_SignsOfRevolt;

import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.holders.ItemHolder;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestDroplist;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Signs of Revolt (646), Interlude version (ported from aCis).<br>
 * Collect 180 Cursed Dolls and choose a reward.
 */
public class Q00646_SignsOfRevolt extends Quest {
	// NPC
	private static final int TORRANT = 32016;
	// Item
	private static final int CURSED_DOLL = 8087;
	private static final int REQUIRED_COUNT = 180;
	// Droplist
	private static final QuestDroplist DROPLIST = QuestDroplist.builder()
		.bulkAddSingleDrop(CURSED_DOLL, 75.0)
		.withNpcs(22029, 22030, 22031, 22032, 22033, 22034, 22035, 22036, 22037, 22038, 22039, 22040, 22041, 22042, 22043, 22044, 22045, 22047, 22049)
		.build()
		.build();
	// Rewards, chosen by index in 32016-06.htm
	private static final ItemHolder[] REWARDS = {
		new ItemHolder(1880, 9), // Steel
		new ItemHolder(1881, 12), // Coarse Bone Powder
		new ItemHolder(1882, 20), // Leather
		new ItemHolder(57, 21600), // Adena
	};
	// Misc
	private static final int MIN_LEVEL = 40;

	public Q00646_SignsOfRevolt() {
		super(646, Q00646_SignsOfRevolt.class.getSimpleName(), "Signs of Revolt");
		bindStartNpc(TORRANT);
		bindTalk(TORRANT);
		bindKill(DROPLIST.getNpcIds());
		registerQuestItems(CURSED_DOLL);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		String htmltext = null;
		switch (event) {
			case "32016-03.htm": {
				if (qs.isCreated() && (player.getLevel() >= MIN_LEVEL)) {
					qs.startQuest();
					htmltext = event;
				}
				break;
			}
			case "32016-06.htm": {
				if (qs.isCond(2)) {
					htmltext = event;
				}
				break;
			}
			case "0", "1", "2", "3": {
				if (qs.isCond(2) && (getQuestItemsCount(player, CURSED_DOLL) >= REQUIRED_COUNT)) {
					takeItems(player, CURSED_DOLL, -1);
					giveItems(player, REWARDS[Integer.parseInt(event)]);
					qs.exitQuest(true, true);
					htmltext = "32016-07.htm";
				}
				break;
			}
		}
		return htmltext;
	}

	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isSummon) {
		final QuestState qs = getRandomPartyMemberState(killer, 1, 3, npc);
		if ((qs != null) && giveItemRandomly(qs.getPlayer(), npc, qs.getPlayer(), DROPLIST.get(npc).drop(), REQUIRED_COUNT, true)) {
			qs.setCond(2);
		}
		return super.onKill(npc, killer, isSummon);
	}

	@Override
	public String onTalk(L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, true);
		String htmltext = getNoQuestMsg(player);
		if (qs.isCreated()) {
			htmltext = (player.getLevel() >= MIN_LEVEL) ? "32016-01.htm" : "32016-02.htm";
		} else if (qs.isStarted()) {
			htmltext = qs.isCond(2) ? "32016-05.htm" : "32016-04.htm";
		}
		return htmltext;
	}
}
