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
package com.l2jserver.datapack.quests.Q00642_APowerfulPrimevalCreature;

import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestDroplist;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * A Powerful Primeval Creature (642), Interlude version (ported from aCis).<br>
 * Dinosaur tissues sell for adena; 150 tissues and an Ancient Egg buy an A-grade weapon recipe.
 */
public class Q00642_APowerfulPrimevalCreature extends Quest {
	// NPC
	private static final int DINN = 32105;
	// Monsters
	private static final int ANCIENT_EGG = 18344;
	// Items
	private static final int DINOSAUR_TISSUE = 8774;
	private static final int DINOSAUR_EGG = 8775;
	private static final int REQUIRED_TISSUES = 150;
	// Droplist
	private static final QuestDroplist DROPLIST = QuestDroplist.builder()
		.bulkAddSingleDrop(DINOSAUR_TISSUE, 33.0)
		.withNpcs(22196, 22197, 22198, 22199, 22200, 22201, 22202, 22203, 22204, 22205, 22218, 22219, 22220, 22223, 22224, 22225)
		.build()
		.addSingleDrop(ANCIENT_EGG, DINOSAUR_EGG, 1.0)
		.build();
	// Rewards, chosen by index in 32105-06.htm: A-grade weapon recipes (60%)
	private static final int[] RECIPES = {
		8690, // Sirra's Blade
		8692, // Sword of Ipos
		8694, // Barakiel's Axe
		8696, // Behemoth's Tuning Fork
		8698, // Naga Storm
		8700, // Tiphon's Spear
		8702, // Shyeed's Bow
		8704, // Sobekk's Hurricane
		8706, // Themis' Tongue
		8708, // Hand of Cabrio
		8710, // Daimon Crystal
	};
	// Misc
	private static final int MIN_LEVEL = 75;

	public Q00642_APowerfulPrimevalCreature() {
		super(642, Q00642_APowerfulPrimevalCreature.class.getSimpleName(), "A Powerful Primeval Creature");
		bindStartNpc(DINN);
		bindTalk(DINN);
		bindKill(DROPLIST.getNpcIds());
		registerQuestItems(DINOSAUR_TISSUE, DINOSAUR_EGG);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		if (event.equals("32105-02.htm") || event.equals("32105-03.htm")) {
			return qs.isCreated() ? event : null;
		}
		if (event.equals("32105-04.htm")) {
			if (qs.isCreated() && (player.getLevel() >= MIN_LEVEL)) {
				qs.startQuest();
				return event;
			}
			return null;
		}
		if (!qs.isStarted()) {
			return null;
		}

		switch (event) {
			case "32105-05.htm": {
				return event;
			}
			case "32105-07.htm": {
				final long tissues = getQuestItemsCount(player, DINOSAUR_TISSUE);
				if (tissues == 0) {
					return "32105-08.htm";
				}
				takeItems(player, DINOSAUR_TISSUE, -1);
				giveAdena(player, tissues * 5000, true);
				return event;
			}
			case "32105-08.htm": {
				return hasMaterials(player) ? "32105-06.htm" : event;
			}
		}

		if (event.startsWith("event_")) {
			final int index = Integer.parseInt(event.substring(6));
			if ((index < 0) || (index >= RECIPES.length) || !hasMaterials(player)) {
				return "32105-08.htm";
			}
			takeItems(player, DINOSAUR_TISSUE, REQUIRED_TISSUES);
			takeItems(player, DINOSAUR_EGG, 1);
			giveAdena(player, 44000, true);
			giveItems(player, RECIPES[index], 1);
			return "32105-07.htm";
		}
		return null;
	}

	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isSummon) {
		final QuestState qs = getQuestState(killer, false);
		if ((qs != null) && qs.isStarted() && giveItemRandomly(killer, npc, DROPLIST.get(npc), true) && hasMaterials(killer)) {
			qs.setCond(1, true);
		}
		return super.onKill(npc, killer, isSummon);
	}

	@Override
	public String onTalk(L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, true);
		String htmltext = getNoQuestMsg(player);
		if (qs.isCreated()) {
			htmltext = (player.getLevel() >= MIN_LEVEL) ? "32105-01.htm" : "32105-00.htm";
		} else if (qs.isStarted()) {
			htmltext = hasQuestItems(player, DINOSAUR_TISSUE) ? "32105-05.htm" : "32105-08.htm";
		}
		return htmltext;
	}

	private static boolean hasMaterials(L2PcInstance player) {
		return (getQuestItemsCount(player, DINOSAUR_TISSUE) >= REQUIRED_TISSUES) && hasQuestItems(player, DINOSAUR_EGG);
	}
}
