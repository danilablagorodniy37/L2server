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
package com.l2jserver.datapack.quests.Q00241_PossessorOfAPreciousSoul1;

import java.util.Map;

import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestState;
import com.l2jserver.gameserver.network.serverpackets.SocialAction;

/**
 * Possessor of a Precious Soul - 1 (241), Interlude version (ported from aCis).<br>
 * First part of the Noblesse quest chain, done on a subclass.
 */
public class Q00241_PossessorOfAPreciousSoul1 extends Quest {
	// NPCs
	private static final int TALIEN = 31739;
	private static final int GABRIELLE = 30753;
	private static final int GILMORE = 30754;
	private static final int KANTABILON = 31042;
	private static final int STEDMIEL = 30692;
	private static final int VIRGIL = 31742;
	private static final int OGMAR = 31744;
	private static final int RAHORAKTI = 31336;
	private static final int KASSANDRA = 31743;
	private static final int CARADINE = 31740;
	private static final int NOEL = 31272;
	// Monsters
	private static final int BARAHAM = 27113;
	private static final int MALRUK_SUCCUBUS_1 = 20244;
	private static final int MALRUK_SUCCUBUS_TUREN_1 = 20245;
	private static final int MALRUK_SUCCUBUS_2 = 20283;
	private static final int MALRUK_SUCCUBUS_TUREN_2 = 20284;
	private static final int[] SPLINTER_STAKATOS = { 21508, 21509, 21510, 21511, 21512 };
	// Items
	private static final int LEGEND_OF_SEVENTEEN = 7587;
	private static final int POETRY_BOOK = 7588;
	private static final int ECHO_CRYSTAL = 7589;
	private static final int MALRUK_SUCCUBUS_CLAW = 7597;
	private static final int CRIMSON_MOSS = 7598;
	private static final int RAHORAKTI_MEDICINE = 7599;
	private static final int VIRGIL_LETTER = 7677;
	private static final int LUNARGENT = 6029;
	private static final int HELLFIRE_OIL = 6033;
	// Misc
	private static final int MIN_LEVEL = 50;

	/**
	 * A dialog step: allowed at {@code cond}, moves the quest to {@code next}, takes one {@code take} (all if {@code takeAll}) and gives one {@code give}.
	 */
	private record Step(int cond, int next, int take, boolean takeAll, int give) {
	}

	private static final Map<String, Step> STEPS = Map.ofEntries(
		Map.entry("31739-07.htm", new Step(4, 5, LEGEND_OF_SEVENTEEN, false, 0)), // Talien
		Map.entry("31739-10.htm", new Step(8, 9, ECHO_CRYSTAL, false, 0)),
		Map.entry("31739-13.htm", new Step(10, 11, POETRY_BOOK, false, 0)),
		Map.entry("30753-02.htm", new Step(1, 2, 0, false, 0)), // Gabrielle
		Map.entry("30754-02.htm", new Step(2, 3, 0, false, 0)), // Gilmore
		Map.entry("31042-02.htm", new Step(5, 6, 0, false, 0)), // Kantabilon
		Map.entry("31042-05.htm", new Step(7, 8, MALRUK_SUCCUBUS_CLAW, true, ECHO_CRYSTAL)),
		Map.entry("30692-02.htm", new Step(9, 10, 0, false, POETRY_BOOK)), // Stedmiel
		Map.entry("31742-02.htm", new Step(11, 12, 0, false, 0)), // Virgil
		Map.entry("31742-05.htm", new Step(17, 18, 0, false, 0)),
		Map.entry("31744-02.htm", new Step(12, 13, 0, false, 0)), // Ogmar
		Map.entry("31336-02.htm", new Step(13, 14, 0, false, 0)), // Rahorakti
		Map.entry("31336-05.htm", new Step(15, 16, CRIMSON_MOSS, true, RAHORAKTI_MEDICINE)),
		Map.entry("31743-02.htm", new Step(16, 17, RAHORAKTI_MEDICINE, false, 0)), // Kassandra
		Map.entry("31740-02.htm", new Step(18, 19, 0, false, 0)), // Caradine
		Map.entry("31272-02.htm", new Step(19, 20, 0, false, 0))); // Noel

	public Q00241_PossessorOfAPreciousSoul1() {
		super(241, Q00241_PossessorOfAPreciousSoul1.class.getSimpleName(), "Possessor of a Precious Soul - 1");
		bindStartNpc(TALIEN);
		bindTalk(TALIEN, GABRIELLE, GILMORE, KANTABILON, STEDMIEL, VIRGIL, OGMAR, RAHORAKTI, KASSANDRA, CARADINE, NOEL);
		bindKill(BARAHAM, MALRUK_SUCCUBUS_1, MALRUK_SUCCUBUS_TUREN_1, MALRUK_SUCCUBUS_2, MALRUK_SUCCUBUS_TUREN_2);
		bindKill(SPLINTER_STAKATOS);
		registerQuestItems(LEGEND_OF_SEVENTEEN, MALRUK_SUCCUBUS_CLAW, ECHO_CRYSTAL, POETRY_BOOK, CRIMSON_MOSS, RAHORAKTI_MEDICINE);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if ((qs == null) || !player.isSubClassActive()) {
			return null;
		}

		if (event.equals("31739-03.htm")) {
			if (qs.isCreated() && (player.getLevel() >= MIN_LEVEL)) {
				qs.startQuest();
				return event;
			}
			return null;
		}
		if (!qs.isStarted()) {
			return null;
		}

		final Step step = STEPS.get(event);
		if (step != null) {
			if (!qs.isCond(step.cond()) || ((step.take() != 0) && !hasQuestItems(player, step.take()))) {
				return null;
			}
			if (step.take() != 0) {
				takeItems(player, step.take(), step.takeAll() ? -1 : 1);
			}
			if (step.give() != 0) {
				giveItems(player, step.give(), 1);
			}
			qs.setCond(step.next(), true);
			return event;
		}

		switch (event) {
			case "31272-05.htm": { // Noel: 5 Lunargent and a Hellfire Oil
				if (!qs.isCond(20)) {
					return null;
				}
				if (!hasMaterials(player)) {
					return "31272-07.htm";
				}
				takeItems(player, LUNARGENT, 5);
				takeItems(player, HELLFIRE_OIL, 1);
				qs.setCond(21, true);
				return event;
			}
			case "31740-05.htm": { // Caradine: reward
				if (!qs.isCond(21)) {
					return null;
				}
				giveItems(player, VIRGIL_LETTER, 1);
				addExpAndSp(player, 263043, 0);
				player.broadcastPacket(new SocialAction(player.getObjectId(), 3));
				qs.exitQuest(false, true);
				return event;
			}
		}
		return null;
	}

	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isSummon) {
		switch (npc.getId()) {
			case BARAHAM -> {
				final QuestState qs = memberAt(killer, 3, npc);
				if (qs != null) {
					giveItems(qs.getPlayer(), LEGEND_OF_SEVENTEEN, 1);
					qs.setCond(4, true);
				}
			}
			case MALRUK_SUCCUBUS_1, MALRUK_SUCCUBUS_2 -> dropAndAdvance(killer, npc, 6, MALRUK_SUCCUBUS_CLAW, 10, 10.0, 7);
			case MALRUK_SUCCUBUS_TUREN_1, MALRUK_SUCCUBUS_TUREN_2 -> dropAndAdvance(killer, npc, 6, MALRUK_SUCCUBUS_CLAW, 10, 12.0, 7);
			default -> dropAndAdvance(killer, npc, 14, CRIMSON_MOSS, 5, 10.0, 15); // Splinter Stakatos
		}
		return super.onKill(npc, killer, isSummon);
	}

	@Override
	public String onTalk(L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, true);
		String htmltext = getNoQuestMsg(player);
		if (qs.isCompleted()) {
			return getAlreadyCompletedMsg(player);
		}
		if (qs.isCreated()) {
			if (npc.getId() == TALIEN) {
				htmltext = (player.isSubClassActive() && (player.getLevel() >= MIN_LEVEL)) ? "31739-01.htm" : "31739-02.htm";
			}
			return htmltext;
		}
		if (!qs.isStarted() || !player.isSubClassActive()) {
			return htmltext;
		}

		final int cond = qs.getCond();
		final String dialog = switch (npc.getId()) {
			case TALIEN -> switch (cond) {
				case 1 -> "31739-04.htm";
				case 2, 3 -> "31739-05.htm";
				case 4 -> "31739-06.htm";
				case 5 -> "31739-08.htm";
				case 8 -> "31739-09.htm";
				case 9 -> "31739-11.htm";
				case 10 -> "31739-12.htm";
				case 11 -> "31739-14.htm";
				default -> null;
			};
			case GABRIELLE -> (cond == 1) ? "30753-01.htm" : (cond == 2) ? "30753-03.htm" : null;
			case GILMORE -> (cond == 2) ? "30754-01.htm" : (cond == 3) ? "30754-03.htm" : null;
			case KANTABILON -> switch (cond) {
				case 5 -> "31042-01.htm";
				case 6 -> "31042-03.htm";
				case 7 -> "31042-04.htm";
				case 8 -> "31042-06.htm";
				default -> null;
			};
			case STEDMIEL -> (cond == 9) ? "30692-01.htm" : (cond == 10) ? "30692-03.htm" : null;
			case VIRGIL -> switch (cond) {
				case 11 -> "31742-01.htm";
				case 12 -> "31742-03.htm";
				case 17 -> "31742-04.htm";
				case 18 -> "31742-06.htm";
				default -> null;
			};
			case OGMAR -> (cond == 12) ? "31744-01.htm" : (cond == 13) ? "31744-03.htm" : null;
			case RAHORAKTI -> switch (cond) {
				case 13 -> "31336-01.htm";
				case 14 -> "31336-03.htm";
				case 15 -> "31336-04.htm";
				case 16 -> "31336-06.htm";
				default -> null;
			};
			case KASSANDRA -> (cond == 16) ? "31743-01.htm" : (cond == 17) ? "31743-03.htm" : null;
			case CARADINE -> switch (cond) {
				case 18 -> "31740-01.htm";
				case 19 -> "31740-03.htm";
				case 21 -> "31740-04.htm";
				default -> null;
			};
			case NOEL -> switch (cond) {
				case 19 -> "31272-01.htm";
				case 20 -> hasMaterials(player) ? "31272-04.htm" : "31272-03.htm";
				case 21 -> "31272-06.htm";
				default -> null;
			};
			default -> null;
		};
		return (dialog != null) ? dialog : htmltext;
	}

	private QuestState memberAt(L2PcInstance killer, int cond, L2Npc npc) {
		final QuestState qs = getRandomPartyMemberState(killer, cond, 3, npc);
		return ((qs != null) && qs.getPlayer().isSubClassActive()) ? qs : null;
	}

	private void dropAndAdvance(L2PcInstance killer, L2Npc npc, int cond, int itemId, long limit, double chance, int next) {
		final QuestState qs = memberAt(killer, cond, npc);
		if ((qs != null) && giveItemRandomly(qs.getPlayer(), npc, itemId, 1, limit, chance / 100, true)) {
			qs.setCond(next);
		}
	}

	private static boolean hasMaterials(L2PcInstance player) {
		return hasQuestItems(player, HELLFIRE_OIL) && (getQuestItemsCount(player, LUNARGENT) >= 5);
	}
}
