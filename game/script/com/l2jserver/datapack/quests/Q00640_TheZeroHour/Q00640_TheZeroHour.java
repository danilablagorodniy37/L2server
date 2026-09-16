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
package com.l2jserver.datapack.quests.Q00640_TheZeroHour;

import com.l2jserver.datapack.quests.Q00109_InSearchOfTheNest.Q00109_InSearchOfTheNest;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.holders.ItemHolder;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestDroplist;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * The Zero Hour (640), an Interlude quest missing from H5 (ported from aCis).<br>
 * Kahman exchanges the Fangs of the spiked stakatos for crafting materials.
 */
public class Q00640_TheZeroHour extends Quest {
	// NPC
	private static final int KAHMAN = 31554;
	// Item
	private static final int FANG_OF_STAKATO = 8085;
	// Droplist: the spiked stakatos, without the babies and the cannibalistic followers
	private static final QuestDroplist DROPLIST = QuestDroplist.builder()
		.bulkAddSingleDrop(FANG_OF_STAKATO, 100.0)
		.withNpcs(22105, 22106, 22107, 22108, 22109, 22110, 22111, 22113, 22114, 22115, 22116, 22117, 22118, 22119, 22121)
		.build()
		.build();
	// Rewards: fangs needed, item, count; the order matches the links of 31554-05.htm
	private static final int[] FANGS = {
		12, 6, 6, 81, 33, 30, 150, 131, 123
	};
	private static final ItemHolder[] REWARDS = {
		new ItemHolder(4042, 1), // Enria
		new ItemHolder(4043, 1), // Asofe
		new ItemHolder(4044, 1), // Thons
		new ItemHolder(1887, 10), // Varnish of Purity
		new ItemHolder(1888, 5), // Synthetic Cokes
		new ItemHolder(1889, 10), // Compound Braid
		new ItemHolder(5550, 10), // Durable Metal Plate
		new ItemHolder(1890, 10), // Mithril Alloy
		new ItemHolder(1893, 5), // Oriharukon
	};
	// Misc
	private static final int MIN_LEVEL = 66;

	public Q00640_TheZeroHour() {
		super(640, Q00640_TheZeroHour.class.getSimpleName(), "The Zero Hour");
		bindStartNpc(KAHMAN);
		bindTalk(KAHMAN);
		bindKill(DROPLIST.getNpcIds());
		registerQuestItems(FANG_OF_STAKATO);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		switch (event) {
			case "31554-02.htm": {
				if (qs.isCreated() && (player.getLevel() >= MIN_LEVEL) && player.hasQuestCompleted(Q00109_InSearchOfTheNest.class.getSimpleName())) {
					qs.startQuest();
					return event;
				}
				return null;
			}
			case "31554-05.htm": {
				if (!qs.isStarted()) {
					return null;
				}
				return hasQuestItems(player, FANG_OF_STAKATO) ? event : "31554-06.htm";
			}
			case "31554-07.htm": {
				return qs.isStarted() ? event : null;
			}
			case "31554-08.htm": {
				if (qs.isStarted()) {
					qs.exitQuest(true, true);
					return event;
				}
				return null;
			}
		}

		if (!qs.isStarted()) {
			return null;
		}
		final int index = index(event);
		if (index < 0) {
			return null;
		}
		if (getQuestItemsCount(player, FANG_OF_STAKATO) < FANGS[index]) {
			return "31554-06.htm";
		}
		takeItems(player, FANG_OF_STAKATO, FANGS[index]);
		rewardItems(player, REWARDS[index]);
		return "31554-09.htm";
	}

	private static int index(String event) {
		try {
			final int index = Integer.parseInt(event);
			return ((index >= 0) && (index < REWARDS.length)) ? index : -1;
		} catch (NumberFormatException ex) {
			return -1;
		}
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
			if (player.getLevel() < MIN_LEVEL) {
				htmltext = "31554-00.htm";
			} else {
				htmltext = player.hasQuestCompleted(Q00109_InSearchOfTheNest.class.getSimpleName()) ? "31554-01.htm" : "31554-10.htm";
			}
		} else if (qs.isStarted()) {
			htmltext = hasQuestItems(player, FANG_OF_STAKATO) ? "31554-04.htm" : "31554-03.htm";
		}
		return htmltext;
	}
}
