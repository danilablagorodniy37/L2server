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
package com.l2jserver.datapack.quests.Q00127_KamaelAWindowToTheFuture;

import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestState;
import com.l2jserver.gameserver.network.serverpackets.ExPlayScene;

/**
 * Kamael: A Window to the Future (127), an Interlude quest missing from H5 (ported from aCis).<br>
 * Dominic sends the player around the five races to collect what each of them thinks of the
 * newcomers; Rodemai's tale ends with the Kamael cut scene.<br>
 * As in aCis, Dominic's closing dialog promises a payment the quest does not hand out --
 * the Interlude reward is unknown, so nothing is granted.
 */
public class Q00127_KamaelAWindowToTheFuture extends Quest {
	// NPCs
	private static final int DOMINIC = 31350;
	private static final int KLAUS = 30187;
	private static final int ALDER = 32092;
	private static final int AKLAN = 31288;
	private static final int OLTLIN = 30862;
	private static final int JURIS = 30113;
	private static final int RODEMAI = 30756;
	// Items
	private static final int MARK_DOMINIC = 8939;
	private static final int MARK_HUMAN = 8940;
	private static final int MARK_DWARF = 8941;
	private static final int MARK_ELF = 8942;
	private static final int MARK_DELF = 8943;
	private static final int MARK_ORC = 8944;

	public Q00127_KamaelAWindowToTheFuture() {
		super(127, Q00127_KamaelAWindowToTheFuture.class.getSimpleName(), "Kamael: A Window to the Future");
		bindStartNpc(DOMINIC);
		bindTalk(DOMINIC, KLAUS, ALDER, AKLAN, OLTLIN, JURIS, RODEMAI);
		registerQuestItems(MARK_DOMINIC, MARK_HUMAN, MARK_DWARF, MARK_ELF, MARK_DELF, MARK_ORC);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		switch (event) {
			case "31350-04.htm": {
				if (!qs.isCreated()) {
					return null;
				}
				qs.startQuest();
				giveItems(player, MARK_DOMINIC, 1);
				return event;
			}
			case "31350-06.htm": {
				if (!qs.isCond(9)) {
					return null;
				}
				takeItems(player, MARK_HUMAN, -1);
				takeItems(player, MARK_DWARF, -1);
				takeItems(player, MARK_ELF, -1);
				takeItems(player, MARK_DELF, -1);
				takeItems(player, MARK_ORC, -1);
				takeItems(player, MARK_DOMINIC, -1);
				qs.exitQuest(false, true);
				return event;
			}
			case "30187-06.htm": {
				return step(qs, 1, 2, event, 0);
			}
			case "30187-08.htm": {
				return step(qs, 2, 3, event, MARK_HUMAN);
			}
			case "32092-05.htm": {
				return step(qs, 3, 4, event, MARK_DWARF);
			}
			case "31288-04.htm": {
				return step(qs, 4, 5, event, MARK_ORC);
			}
			case "30862-04.htm": {
				return step(qs, 5, 6, event, MARK_DELF);
			}
			case "30113-04.htm": {
				return step(qs, 6, 7, event, MARK_ELF);
			}
			case "kamaelstory": {
				if (qs.isCond(7)) {
					qs.setCond(8, true);
					player.sendPacket(ExPlayScene.STATIC_PACKET);
				}
				return null;
			}
			case "30756-05.htm": {
				return step(qs, 8, 9, event, 0);
			}
		}
		// The dialogs in between simply lead to the next page, Dominic's intro before the quest starts.
		return event.endsWith(".htm") ? event : null;
	}

	/**
	 * Advances the quest one step and hands out the mark the collaborator gives.
	 * @param qs the quest state
	 * @param from the condition the step starts at
	 * @param to the condition the step ends at
	 * @param event the dialog to show
	 * @param mark the mark to give, {@code 0} for a step without one
	 * @return the dialog, or {@code null} if the player is not at {@code from}
	 */
	private String step(QuestState qs, int from, int to, String event, int mark) {
		if (!qs.isCond(from)) {
			return null;
		}
		qs.setCond(to, true);
		if (mark != 0) {
			giveItems(qs.getPlayer(), mark, 1);
		}
		return event;
	}

	@Override
	public String onTalk(L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, true);
		String htmltext = getNoQuestMsg(player);
		if (qs.isCreated()) {
			if (npc.getId() == DOMINIC) {
				htmltext = "31350-01.htm";
			}
		} else if (qs.isCompleted()) {
			htmltext = getAlreadyCompletedMsg(player);
		} else {
			final int cond = qs.getCond();
			switch (npc.getId()) {
				case KLAUS: {
					if (cond == 1) {
						htmltext = "30187-01.htm";
					} else if (cond == 2) {
						htmltext = "30187-06.htm";
					}
					break;
				}
				case ALDER: {
					if (cond == 3) {
						htmltext = "32092-01.htm";
					}
					break;
				}
				case AKLAN: {
					if (cond == 4) {
						htmltext = "31288-01.htm";
					}
					break;
				}
				case OLTLIN: {
					if (cond == 5) {
						htmltext = "30862-01.htm";
					}
					break;
				}
				case JURIS: {
					if (cond == 6) {
						htmltext = "30113-01.htm";
					}
					break;
				}
				case RODEMAI: {
					if (cond == 7) {
						htmltext = "30756-01.htm";
					} else if (cond == 8) {
						htmltext = "30756-04.htm";
					}
					break;
				}
				case DOMINIC: {
					if (cond == 9) {
						htmltext = "31350-05.htm";
					}
					break;
				}
			}
		}
		return htmltext;
	}
}
