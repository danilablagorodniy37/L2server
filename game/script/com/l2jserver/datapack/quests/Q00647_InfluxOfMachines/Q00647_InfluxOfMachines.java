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
package com.l2jserver.datapack.quests.Q00647_InfluxOfMachines;

import java.util.stream.IntStream;

import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestDroplist;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Influx of Machines (647), Interlude version (ported from aCis).<br>
 * 500 Destroyed Golem Shards from the golems of the Valley of Saints buy a random B-grade weapon recipe.
 */
public class Q00647_InfluxOfMachines extends Quest {
	// NPC
	private static final int GUTENHAGEN = 32069;
	// Item
	private static final int DESTROYED_GOLEM_SHARD = 8100;
	private static final int REQUIRED_COUNT = 500;
	// Droplist: golems 22052-22078
	private static final QuestDroplist DROPLIST = QuestDroplist.builder()
		.bulkAddSingleDrop(DESTROYED_GOLEM_SHARD, 30.0)
		.withNpcs(IntStream.rangeClosed(22052, 22078).toArray())
		.build()
		.build();
	// Rewards: Recipe: Great Sword (60%) ... Recipe: Great Axe (60%)
	private static final int FIRST_RECIPE = 4963;
	private static final int LAST_RECIPE = 4972;
	// Misc
	private static final int MIN_LEVEL = 46;

	public Q00647_InfluxOfMachines() {
		super(647, Q00647_InfluxOfMachines.class.getSimpleName(), "Influx of Machines");
		bindStartNpc(GUTENHAGEN);
		bindTalk(GUTENHAGEN);
		bindKill(DROPLIST.getNpcIds());
		registerQuestItems(DESTROYED_GOLEM_SHARD);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		String htmltext = null;
		switch (event) {
			case "32069-02.htm": {
				if (qs.isCreated() && (player.getLevel() >= MIN_LEVEL)) {
					qs.startQuest();
					htmltext = event;
				}
				break;
			}
			case "32069-06.htm": {
				if (qs.isCond(2) && (getQuestItemsCount(player, DESTROYED_GOLEM_SHARD) >= REQUIRED_COUNT)) {
					takeItems(player, DESTROYED_GOLEM_SHARD, -1);
					giveItems(player, getRandom(FIRST_RECIPE, LAST_RECIPE), 1);
					qs.exitQuest(true, true);
					htmltext = event;
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
			htmltext = (player.getLevel() >= MIN_LEVEL) ? "32069-01.htm" : "32069-03.htm";
		} else if (qs.isStarted()) {
			htmltext = qs.isCond(2) ? "32069-05.htm" : "32069-04.htm";
		}
		return htmltext;
	}
}
