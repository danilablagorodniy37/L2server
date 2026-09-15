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
package com.l2jserver.datapack.quests.Q00639_GuardiansOfTheHolyGrail;

import java.util.Set;

import com.l2jserver.gameserver.enums.audio.Sound;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestDroplist;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Guardians of the Holy Grail (639), Interlude version (ported from aCis; H5 had only a stub).<br>
 * Monk Scriptures from the Monastery of Silence sell for adena or buy S-grade enchant scrolls.
 */
public class Q00639_GuardiansOfTheHolyGrail extends Quest {
	// NPCs
	private static final int DOMINIC = 31350;
	private static final int GREMORY = 32008;
	private static final int HOLY_GRAIL = 32028;
	// Items
	private static final int SCRIPTURE = 8069;
	private static final int WATER_BOTTLE = 8070;
	private static final int HOLY_WATER_BOTTLE = 8071;
	private static final int SCROLL_ENCHANT_WEAPON_S = 959;
	private static final int SCROLL_ENCHANT_ARMOR_S = 960;
	// Droplist: Monastery of Silence
	private static final QuestDroplist DROPLIST = QuestDroplist.builder()
		.addSingleDrop(22122, SCRIPTURE, 76.0)
		.addSingleDrop(22123, SCRIPTURE, 75.0)
		.addSingleDrop(22124, SCRIPTURE, 59.0)
		.addSingleDrop(22125, SCRIPTURE, 58.0)
		.addSingleDrop(22126, SCRIPTURE, 59.0)
		.addSingleDrop(22127, SCRIPTURE, 58.0)
		.addSingleDrop(22128, SCRIPTURE, 17.0)
		.addSingleDrop(22129, SCRIPTURE, 59.0)
		.addSingleDrop(22130, SCRIPTURE, 85.0)
		.addSingleDrop(22131, SCRIPTURE, 92.0)
		.addSingleDrop(22132, SCRIPTURE, 58.0)
		.addSingleDrop(22133, SCRIPTURE, 93.0)
		.addSingleDrop(22134, SCRIPTURE, 23.0)
		.addSingleDrop(22135, SCRIPTURE, 58.0)
		.build();
	// Dialogs that only show text
	private static final Set<String> TEXT_DIALOGS = Set.of("31350-07.htm", "32008-02.htm", "32008-03.htm", "32008-04.htm");
	// Misc
	private static final int MIN_LEVEL = 73;

	public Q00639_GuardiansOfTheHolyGrail() {
		super(639, Q00639_GuardiansOfTheHolyGrail.class.getSimpleName(), "Guardians of the Holy Grail");
		bindStartNpc(DOMINIC);
		bindTalk(DOMINIC, GREMORY, HOLY_GRAIL);
		bindKill(DROPLIST.getNpcIds());
		registerQuestItems(SCRIPTURE, WATER_BOTTLE, HOLY_WATER_BOTTLE);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		if (qs.isCreated()) {
			if (player.getLevel() < MIN_LEVEL) {
				return null;
			}
			if (event.equals("31350-03.htm")) {
				return event;
			}
			if (event.equals("31350-04.htm")) {
				qs.startQuest();
				return event;
			}
			return null;
		}
		if (!qs.isStarted()) {
			return null;
		}
		if (TEXT_DIALOGS.contains(event)) {
			return event;
		}

		switch (event) {
			case "31350-08.htm": { // Dominic buys the scriptures
				final long count = getQuestItemsCount(player, SCRIPTURE);
				takeItems(player, SCRIPTURE, -1);
				giveAdena(player, (1625 * count) + ((count >= 10) ? 33940 : 0), true);
				return event;
			}
			case "31350-09.htm": {
				qs.exitQuest(true);
				playSound(player, Sound.ITEMSOUND_QUEST_GIVEUP);
				return event;
			}
			case "32008-05.htm": {
				if (!qs.isCond(1)) {
					return null;
				}
				giveItems(player, WATER_BOTTLE, 1);
				qs.setCond(2, true);
				return event;
			}
			case "32028-02.htm": { // Holy Grail blesses the water
				if (!qs.isCond(2) || !hasQuestItems(player, WATER_BOTTLE)) {
					return null;
				}
				takeItems(player, WATER_BOTTLE, 1);
				giveItems(player, HOLY_WATER_BOTTLE, 1);
				qs.setCond(3, true);
				return event;
			}
			case "32008-09.htm": {
				if (!qs.isCond(3) || !hasQuestItems(player, HOLY_WATER_BOTTLE)) {
					return null;
				}
				takeItems(player, HOLY_WATER_BOTTLE, 1);
				qs.setCond(4, true);
				return event;
			}
			case "32008-12.htm": {
				return exchange(player, 4000, SCROLL_ENCHANT_WEAPON_S) ? "32008-11.htm" : event;
			}
			case "32008-14.htm": {
				return exchange(player, 400, SCROLL_ENCHANT_ARMOR_S) ? "32008-13.htm" : event;
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
			if (npc.getId() == DOMINIC) {
				htmltext = (player.getLevel() >= MIN_LEVEL) ? "31350-01.htm" : "31350-02.htm";
			}
		} else if (qs.isStarted()) {
			final int cond = qs.getCond();
			switch (npc.getId()) {
				case DOMINIC -> htmltext = hasQuestItems(player, SCRIPTURE) ? "31350-05.htm" : "31350-06.htm";
				case GREMORY -> htmltext = switch (cond) {
					case 1 -> "32008-01.htm";
					case 2 -> "32008-06.htm";
					case 3 -> "32008-08.htm";
					default -> "32008-10.htm";
				};
				case HOLY_GRAIL -> htmltext = (cond == 2) ? "32028-01.htm" : (cond > 2) ? "32028-03.htm" : htmltext;
			}
		}
		return htmltext;
	}

	private static boolean exchange(L2PcInstance player, long scriptures, int scroll) {
		if (getQuestItemsCount(player, SCRIPTURE) < scriptures) {
			return false;
		}
		takeItems(player, SCRIPTURE, scriptures);
		rewardItems(player, scroll, 1);
		return true;
	}
}
