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
package com.l2jserver.datapack.quests.Q00645_GhostsOfBatur;

import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.holders.ItemHolder;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestDroplist;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Ghosts of Batur (645), Interlude version (ported from aCis).<br>
 * Collect 180 Cursed Grave Goods from the ghosts and choose a crafting material as reward.
 */
public class Q00645_GhostsOfBatur extends Quest {
	// NPC
	private static final int KARUDA = 32017;
	// Item
	private static final int CURSED_GRAVE_GOODS = 8089;
	private static final int REQUIRED_COUNT = 180;
	// Droplist
	private static final QuestDroplist DROPLIST = QuestDroplist.builder()
		.bulkAddSingleDrop(CURSED_GRAVE_GOODS, 75.0)
		.withNpcs(22007, 22009, 22010, 22011, 22012, 22013, 22014, 22015, 22016) // Ghosts
		.build()
		.build();
	// Rewards, chosen by index in 32017-06.htm
	private static final ItemHolder[] REWARDS = {
		new ItemHolder(1878, 18), // Braided Hemp
		new ItemHolder(1879, 7), // Cokes
		new ItemHolder(1880, 4), // Steel
		new ItemHolder(1881, 6), // Coarse Bone Powder
		new ItemHolder(1882, 10), // Leather
		new ItemHolder(1883, 2), // Steel Mold
	};
	// Misc
	private static final int MIN_LEVEL = 23;

	public Q00645_GhostsOfBatur() {
		super(645, Q00645_GhostsOfBatur.class.getSimpleName(), "Ghosts of Batur");
		bindStartNpc(KARUDA);
		bindTalk(KARUDA);
		bindKill(DROPLIST.getNpcIds());
		registerQuestItems(CURSED_GRAVE_GOODS);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		String htmltext = null;
		switch (event) {
			case "32017-03.htm": {
				if (player.getLevel() >= MIN_LEVEL) {
					qs.startQuest();
					htmltext = event;
				}
				break;
			}
			case "32017-06.htm": {
				if (qs.isCond(2)) {
					htmltext = event;
				}
				break;
			}
			case "0", "1", "2", "3", "4", "5": {
				if (qs.isCond(2) && (getQuestItemsCount(player, CURSED_GRAVE_GOODS) >= REQUIRED_COUNT)) {
					takeItems(player, CURSED_GRAVE_GOODS, -1);
					giveItems(player, REWARDS[Integer.parseInt(event)]);
					qs.exitQuest(true, true);
					htmltext = "32017-07.htm";
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
			htmltext = (player.getLevel() >= MIN_LEVEL) ? "32017-01.htm" : "32017-02.htm";
		} else if (qs.isStarted()) {
			htmltext = qs.isCond(2) ? "32017-05.htm" : "32017-04.htm";
		}
		return htmltext;
	}
}
