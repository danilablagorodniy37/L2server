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
package com.l2jserver.datapack.quests.Q00631_DeliciousTopChoiceMeat;

import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.holders.ItemHolder;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestDroplist;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Delicious Top Choice Meat (631), Interlude version (ported from aCis).<br>
 * Grown Beast Farm animals drop Top-Quality Meat; 120 pieces buy a crafting material.
 */
public class Q00631_DeliciousTopChoiceMeat extends Quest {
	// NPC
	private static final int TUNATUN = 31537;
	// Item
	private static final int TOP_QUALITY_MEAT = 7546;
	private static final int REQUIRED_COUNT = 120;
	// Droplist: grown Kookaburras, Buffalos and Cougars
	private static final QuestDroplist DROPLIST = QuestDroplist.builder()
		.addSingleDrop(21460, TOP_QUALITY_MEAT, 60.1)
		.addSingleDrop(21461, TOP_QUALITY_MEAT, 48.0)
		.addSingleDrop(21462, TOP_QUALITY_MEAT, 44.7)
		.addSingleDrop(21463, TOP_QUALITY_MEAT, 80.8)
		.addSingleDrop(21464, TOP_QUALITY_MEAT, 44.7)
		.addSingleDrop(21465, TOP_QUALITY_MEAT, 80.8)
		.addSingleDrop(21466, TOP_QUALITY_MEAT, 44.7)
		.addSingleDrop(21467, TOP_QUALITY_MEAT, 80.8)
		.addSingleDrop(21479, TOP_QUALITY_MEAT, 47.7)
		.addSingleDrop(21480, TOP_QUALITY_MEAT, 86.3)
		.addSingleDrop(21481, TOP_QUALITY_MEAT, 47.7)
		.addSingleDrop(21482, TOP_QUALITY_MEAT, 86.3)
		.addSingleDrop(21483, TOP_QUALITY_MEAT, 47.7)
		.addSingleDrop(21484, TOP_QUALITY_MEAT, 86.3)
		.addSingleDrop(21485, TOP_QUALITY_MEAT, 47.7)
		.addSingleDrop(21486, TOP_QUALITY_MEAT, 86.3)
		.addSingleDrop(21498, TOP_QUALITY_MEAT, 50.9)
		.addSingleDrop(21499, TOP_QUALITY_MEAT, 92.0)
		.addSingleDrop(21500, TOP_QUALITY_MEAT, 50.9)
		.addSingleDrop(21501, TOP_QUALITY_MEAT, 92.0)
		.addSingleDrop(21502, TOP_QUALITY_MEAT, 50.9)
		.addSingleDrop(21503, TOP_QUALITY_MEAT, 92.0)
		.addSingleDrop(21504, TOP_QUALITY_MEAT, 50.9)
		.addSingleDrop(21505, TOP_QUALITY_MEAT, 92.0)
		.build();
	// Rewards, chosen by index in 31537-05.htm
	private static final ItemHolder[] REWARDS = {
		new ItemHolder(4039, 15), // Mold Glue
		new ItemHolder(4043, 15), // Asofe
		new ItemHolder(4044, 15), // Thons
		new ItemHolder(4040, 10), // Mold Lubricant
		new ItemHolder(4042, 10), // Enria
		new ItemHolder(4041, 5), // Mold Hardener
	};
	// Misc
	private static final int MIN_LEVEL = 65;

	public Q00631_DeliciousTopChoiceMeat() {
		super(631, Q00631_DeliciousTopChoiceMeat.class.getSimpleName(), "Delicious Top Choice Meat");
		bindStartNpc(TUNATUN);
		bindTalk(TUNATUN);
		bindKill(DROPLIST.getNpcIds());
		registerQuestItems(TOP_QUALITY_MEAT);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		String htmltext = null;
		switch (event) {
			case "31537-03.htm": {
				if (!qs.isCreated()) {
					break;
				}
				if (player.getLevel() >= MIN_LEVEL) {
					qs.startQuest();
					htmltext = event;
				} else {
					htmltext = "31537-02.htm";
				}
				break;
			}
			case "31537-05.htm": {
				if (qs.isCond(2)) {
					htmltext = event;
				}
				break;
			}
			case "0", "1", "2", "3", "4", "5": {
				if (!qs.isStarted()) {
					break;
				}
				if (getQuestItemsCount(player, TOP_QUALITY_MEAT) >= REQUIRED_COUNT) {
					takeItems(player, TOP_QUALITY_MEAT, -1);
					rewardItems(player, REWARDS[Integer.parseInt(event)]);
					qs.exitQuest(true, true);
					htmltext = "31537-06.htm";
				} else {
					qs.setCond(1);
					htmltext = "31537-07.htm";
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
			htmltext = "31537-01.htm";
		} else if (qs.isStarted()) {
			if (qs.isCond(2) && (getQuestItemsCount(player, TOP_QUALITY_MEAT) >= REQUIRED_COUNT)) {
				htmltext = "31537-04.htm";
			} else {
				qs.setCond(1);
				htmltext = "31537-03a.htm";
			}
		}
		return htmltext;
	}
}
