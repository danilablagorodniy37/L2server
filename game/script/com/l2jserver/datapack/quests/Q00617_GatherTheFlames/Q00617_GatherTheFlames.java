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
package com.l2jserver.datapack.quests.Q00617_GatherTheFlames;

import java.util.HashMap;
import java.util.Map;

import com.l2jserver.gameserver.enums.audio.Sound;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestState;

/**
 * Gather the Flames (617)
 * @author malyelfik
 */
public class Q00617_GatherTheFlames extends Quest {
	// NPCs
	private static final int HILDA = 31271;
	private static final int VULCAN = 31539;
	private static final int ROONEY = 32049;
	// Item
	private static final int TORCH = 7264;
	// Reward
	private static final int[] REWARD = {
		6881,
		6883,
		6885,
		6887,
		6891,
		6893,
		6895,
		6897,
		6899,
		7580
	};
	
	// Monsters
	private static final Map<Integer, Integer> MOBS = new HashMap<>();
	
	// Interlude Forge of the Gods monsters, chance per mille (aCis)
	static {
		MOBS.put(21376, 480); // Scarlet Stakato Worker
		MOBS.put(21377, 480); // Scarlet Stakato Soldier
		MOBS.put(21378, 490); // Scarlet Stakato Noble
		MOBS.put(21379, 590); // Tepra Scorpion
		MOBS.put(21380, 490); // Tepra Scarab
		MOBS.put(21381, 510); // Assassin Beetle
		MOBS.put(21382, 600); // Mercenary of Destruction
		MOBS.put(21383, 510); // Knight of Destruction
		MOBS.put(21384, 640); // Necromancer of Destruction
		MOBS.put(21385, 520); // Lavastone Golem
		MOBS.put(21386, 520); // Magma Golem
		MOBS.put(21387, 530); // Arimanes of Destruction
		MOBS.put(21388, 530); // Iblis of Destruction
		MOBS.put(21389, 550); // Balrog of Destruction
		MOBS.put(21390, 560); // Ashuras of Destruction
		MOBS.put(21391, 550); // Lavasillisk
		MOBS.put(21392, 560); // Blazing Ifrit
		MOBS.put(21393, 580); // Magma Drake
		MOBS.put(21394, 510); // Lavasaurus
		MOBS.put(21395, 560); // Elder Lavasaurus
		MOBS.put(21652, 490); // Scarlet Stakato Noble
		MOBS.put(21653, 510); // Assassin Beetle
		MOBS.put(21654, 520); // Necromancer of Destruction
		MOBS.put(21655, 530); // Arimanes of Destruction
		MOBS.put(21656, 690); // Ashuras of Destruction
		MOBS.put(21657, 570); // Magma Drake
	}
	
	public Q00617_GatherTheFlames() {
		super(617, Q00617_GatherTheFlames.class.getSimpleName(), "Gather the Flames");
		bindStartNpc(HILDA, VULCAN);
		bindTalk(ROONEY, HILDA, VULCAN);
		bindKill(MOBS.keySet());
		registerQuestItems(TORCH);
	}
	
	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState st = getQuestState(player, false);
		if (st == null) {
			return getNoQuestMsg(player);
		}
		
		String htmltext = event;
		switch (event) {
			case "31539-03.htm":
			case "31271-03.htm":
				st.startQuest();
				break;
			case "32049-02.html":
			case "31539-04.html":
			case "31539-06.html":
				break;
			case "31539-07.html":
				if ((st.getQuestItemsCount(TORCH) < 1000) || !st.isStarted()) {
					return getNoQuestMsg(player);
				}
				st.giveItems(REWARD[getRandom(REWARD.length)], 1);
				st.takeItems(TORCH, 1000);
				break;
			case "31539-08.html":
				st.exitQuest(true, true);
				break;
			case "6883":
			case "6885":
			case "7580":
			case "6891":
			case "6893":
			case "6895":
			case "6897":
			case "6899":
				if ((st.getQuestItemsCount(TORCH) < 1200) || !st.isStarted()) {
					return getNoQuestMsg(player);
				}
				st.giveItems(Integer.parseInt(event), 1);
				st.takeItems(TORCH, 1200);
				htmltext = "32049-04.html";
				break;
			case "6887":
			case "6881":
				if ((st.getQuestItemsCount(TORCH) < 1200) || !st.isStarted()) {
					return getNoQuestMsg(player);
				}
				st.giveItems(Integer.parseInt(event), 1);
				st.takeItems(TORCH, 1200);
				htmltext = "32049-03.html";
				break;
			default:
				htmltext = null;
				break;
		}
		return htmltext;
	}
	
	@Override
	public String onKill(L2Npc npc, L2PcInstance player, boolean isSummon) {
		final L2PcInstance partyMember = getRandomPartyMember(player, 1);
		if (partyMember == null) {
			return super.onKill(npc, player, isSummon);
		}
		
		final QuestState st = getQuestState(partyMember, false);
		
		if (getRandom(1000) < MOBS.get(npc.getId())) {
			st.giveItems(TORCH, 2);
		} else {
			st.giveItems(TORCH, 1);
		}
		st.playSound(Sound.ITEMSOUND_QUEST_ITEMGET);
		return super.onKill(npc, player, isSummon);
	}
	
	@Override
	public String onTalk(L2Npc npc, L2PcInstance player) {
		String htmltext = getNoQuestMsg(player);
		final QuestState st = getQuestState(player, true);
		switch (npc.getId()) {
			case ROONEY:
				if (st.isStarted()) {
					htmltext = (st.getQuestItemsCount(TORCH) >= 1200) ? "32049-02.html" : "32049-01.html";
				}
				break;
			case VULCAN:
				if (st.isCreated()) {
					htmltext = (player.getLevel() >= 74) ? "31539-01.htm" : "31539-02.htm";
				} else {
					htmltext = (st.getQuestItemsCount(TORCH) >= 1000) ? "31539-04.html" : "31539-05.html";
				}
				break;
			case HILDA:
				if (st.isCreated()) {
					htmltext = (player.getLevel() >= 74) ? "31271-01.htm" : "31271-02.htm";
				} else {
					htmltext = "31271-04.html";
				}
				break;
		}
		return htmltext;
	}
}