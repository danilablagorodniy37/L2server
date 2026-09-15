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
package com.l2jserver.datapack.quests.Q00113_StatusOfTheBeaconTower;

import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Status of the Beacon Tower (113), Interlude version (ported from aCis).
 */
public class Q00113_StatusOfTheBeaconTower extends Quest {
	// NPCs
	private static final int MOIRA = 31979;
	private static final int TORRANT = 32016;
	// Item
	private static final int BOX = 8086;
	// Misc
	private static final int MIN_LEVEL = 40;

	public Q00113_StatusOfTheBeaconTower() {
		super(113, Q00113_StatusOfTheBeaconTower.class.getSimpleName(), "Status of the Beacon Tower");
		bindStartNpc(MOIRA);
		bindTalk(MOIRA, TORRANT);
		registerQuestItems(BOX);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		String htmltext = null;
		switch (event) {
			case "31979-02.htm": {
				if (qs.isCreated() && (player.getLevel() >= MIN_LEVEL)) {
					qs.startQuest();
					giveItems(player, BOX, 1);
					htmltext = event;
				}
				break;
			}
			case "32016-02.htm": {
				if (qs.isStarted() && hasQuestItems(player, BOX)) {
					takeItems(player, BOX, 1);
					giveAdena(player, 21578, true);
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
			if (npc.getId() == MOIRA) {
				htmltext = (player.getLevel() >= MIN_LEVEL) ? "31979-01.htm" : "31979-00.htm";
			}
		} else if (qs.isStarted()) {
			htmltext = (npc.getId() == MOIRA) ? "31979-03.htm" : "32016-01.htm";
		}
		return htmltext;
	}
}
