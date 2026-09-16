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
package com.l2jserver.datapack.quests.Q00374_WhisperOfDreamsPart1;

import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.holders.ItemHolder;
import com.l2jserver.gameserver.model.holders.QuestItemChanceHolder;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestDroplist;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Whisper of Dreams, Part 1 (374), an Interlude quest missing from H5 (ported from aCis).<br>
 * Manakia of Hardin's Academy trades the spoils of the Forest of Mirrors for sealed armor parts;
 * the Sealed Mysterious Stone that drops along the way opens Part 2 after Torai unseals it.
 */
public class Q00374_WhisperOfDreamsPart1 extends Quest {
	// NPCs
	private static final int MANAKIA = 30515;
	private static final int TORAI = 30557;
	// Monsters
	private static final int CAVE_BEAST = 20620;
	private static final int DEATH_WAVE = 20621;
	// Items
	private static final int CAVE_BEAST_TOOTH = 5884;
	private static final int DEATH_WAVE_LIGHT = 5885;
	private static final int SEALED_MYSTERIOUS_STONE = 5886;
	private static final int MYSTERIOUS_STONE = 5887;
	private static final int REQUIRED_COUNT = 65;
	// Droplist
	private static final QuestDroplist DROPLIST = QuestDroplist.builder()
		.addSingleDrop(CAVE_BEAST, new QuestItemChanceHolder(CAVE_BEAST_TOOTH, 50.0, 1, REQUIRED_COUNT))
		.addSingleDrop(DEATH_WAVE, new QuestItemChanceHolder(DEATH_WAVE_LIGHT, 50.0, 1, REQUIRED_COUNT))
		.build();
	private static final double SEALED_STONE_CHANCE = 0.1;
	// Rewards, chosen by index in 30515-05.htm: sealed armor part and adena
	private static final ItemHolder[] REWARDS = {
		new ItemHolder(5486, 3), // Sealed Dark Crystal Robe Fabric
		new ItemHolder(5487, 2), // Sealed Robe of Nightmare Fabric
		new ItemHolder(5488, 2), // Sealed Majestic Robe Fabric
		new ItemHolder(5485, 4), // Sealed Tallum Tunic Texture
		new ItemHolder(5489, 6), // Sealed Tallum Stockings Fabric
	};
	private static final int[] ADENA = {
		2950, 18050, 18050, 10450, 15550
	};
	// Misc
	private static final int MIN_LEVEL = 56;
	// The party member who still misses the Sealed Mysterious Stone keeps this flag.
	private static final String STONE_VAR = "condStone";

	public Q00374_WhisperOfDreamsPart1() {
		super(374, Q00374_WhisperOfDreamsPart1.class.getSimpleName(), "Whisper of Dreams, Part 1");
		bindStartNpc(MANAKIA);
		bindTalk(MANAKIA, TORAI);
		bindKill(CAVE_BEAST, DEATH_WAVE);
		registerQuestItems(CAVE_BEAST_TOOTH, DEATH_WAVE_LIGHT, SEALED_MYSTERIOUS_STONE, MYSTERIOUS_STONE);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		switch (event) {
			case "30515-03.htm": {
				if (qs.isCreated() && (player.getLevel() >= MIN_LEVEL)) {
					qs.startQuest();
					qs.set(STONE_VAR, 1);
					return event;
				}
				return null;
			}
			case "30515-07.htm": {
				return qs.isStarted() ? event : null;
			}
			case "30515-08.htm": {
				if (qs.isStarted()) {
					qs.exitQuest(true, true);
					return event;
				}
				return null;
			}
			case "30557-02.htm": {
				if (qs.isCond(2) && hasQuestItems(player, SEALED_MYSTERIOUS_STONE)) {
					qs.setCond(3, true);
					takeItems(player, SEALED_MYSTERIOUS_STONE, -1);
					giveItems(player, MYSTERIOUS_STONE, 1);
					return event;
				}
				return "30557-03.htm";
			}
			// The five reward links of 30515-05.htm; the dialog they open is 30515-06.htm.
			case "30515-06-0.htm": {
				return reward(player, qs, 0);
			}
			case "30515-06-1.htm": {
				return reward(player, qs, 1);
			}
			case "30515-06-2.htm": {
				return reward(player, qs, 2);
			}
			case "30515-06-3.htm": {
				return reward(player, qs, 3);
			}
			case "30515-06-4.htm": {
				return reward(player, qs, 4);
			}
		}
		return null;
	}

	/**
	 * Hands out one of the rewards for a full set of teeth and lights.
	 * @param player the player
	 * @param qs the quest state
	 * @param index the reward chosen in 30515-05.htm
	 * @return the dialog to show
	 */
	private String reward(L2PcInstance player, QuestState qs, int index) {
		if (!qs.isStarted()) {
			return null;
		}
		if ((getQuestItemsCount(player, CAVE_BEAST_TOOTH) < REQUIRED_COUNT) || (getQuestItemsCount(player, DEATH_WAVE_LIGHT) < REQUIRED_COUNT)) {
			return "30515-07.htm";
		}
		takeItems(player, CAVE_BEAST_TOOTH, -1);
		takeItems(player, DEATH_WAVE_LIGHT, -1);
		giveAdena(player, ADENA[index], true);
		giveItems(player, REWARDS[index]);
		return "30515-06.htm";
	}

	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isSummon) {
		final QuestState qs = getRandomPartyMemberState(killer, -1, 3, npc);
		if (qs != null) {
			giveItemRandomly(qs.getPlayer(), npc, DROPLIST.get(npc), true);
		}
		// The Sealed Mysterious Stone drops once, to a party member who still needs it.
		final L2PcInstance member = getRandomPartyMember(killer, STONE_VAR, "1");
		if (member != null) {
			if (giveItemRandomly(member, npc, member, QuestDroplist.singleDropItem(SEALED_MYSTERIOUS_STONE, SEALED_STONE_CHANCE), 1, true)) {
				getQuestState(member, false).unset(STONE_VAR);
			}
		}
		return super.onKill(npc, killer, isSummon);
	}

	@Override
	public String onTalk(L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, true);
		String htmltext = getNoQuestMsg(player);
		if (qs.isCreated()) {
			if (npc.getId() == MANAKIA) {
				htmltext = (player.getLevel() >= MIN_LEVEL) ? "30515-02.htm" : "30515-01.htm";
			}
		} else if (qs.isStarted()) {
			switch (npc.getId()) {
				case MANAKIA: {
					if (!hasQuestItems(player, SEALED_MYSTERIOUS_STONE)) {
						final boolean enough = (getQuestItemsCount(player, CAVE_BEAST_TOOTH) >= REQUIRED_COUNT) && (getQuestItemsCount(player, DEATH_WAVE_LIGHT) >= REQUIRED_COUNT);
						htmltext = enough ? "30515-05.htm" : "30515-04.htm";
					} else if (qs.isCond(1)) {
						qs.setCond(2, true);
						htmltext = "30515-09.htm";
					} else {
						htmltext = "30515-10.htm";
					}
					break;
				}
				case TORAI: {
					if (qs.isCond(2) && hasQuestItems(player, SEALED_MYSTERIOUS_STONE)) {
						htmltext = "30557-01.htm";
					}
					break;
				}
			}
		}
		return htmltext;
	}
}
