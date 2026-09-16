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
package com.l2jserver.datapack.quests.Q00234_FatesWhisper;

import static com.l2jserver.gameserver.network.NpcStringId.WHO_DARES_TO_TRY_AND_STEAL_MY_NOBLE_BLOOD;

import java.util.Map;

import com.l2jserver.gameserver.datatables.ItemTable;
import com.l2jserver.gameserver.enums.audio.Sound;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestState;
import com.l2jserver.gameserver.network.clientpackets.Say2;
import com.l2jserver.gameserver.network.serverpackets.NpcSay;

/**
 * Fate's Whisper (234), Interlude version (ported from aCis).<br>
 * H5 rebuilt the quest around the Blood Stained Cloth of the Platinum Tribe; in Interlude the
 * blood of Baium is taken with the Pipette Knife, and Maestro Reorin melts a B-grade weapon of
 * the player's choosing into an A-grade one plus the Star of Destiny.
 */
public final class Q00234_FatesWhisper extends Quest {
	// NPCs
	private static final int ZENKIN = 30178;
	private static final int CLIFF = 30182;
	private static final int MASTER_KASPAR = 30833;
	private static final int HEAD_BLACKSMITH_FERRIS = 30847;
	private static final int MAESTRO_REORIN = 31002;
	private static final int COFFER_OF_THE_DEAD = 31027;
	private static final int CHEST_OF_KERNON = 31028;
	private static final int CHEST_OF_GOLKONDA = 31029;
	private static final int CHEST_OF_HALLATE = 31030;
	private static final int BAIUM = 29020;
	// Raid bosses and the chest each of them leaves behind
	private static final Map<Integer, Integer> CHESTS = Map.of(
		25035, COFFER_OF_THE_DEAD, // Demon Kadomb Death Cabrio
		25054, CHEST_OF_KERNON, // Kernon
		25126, CHEST_OF_GOLKONDA, // Golkonda the Longhorn
		25220, CHEST_OF_HALLATE); // Hallate the Death Lord
	// Items
	private static final int PIPETTE_KNIFE = 4665;
	private static final int REIRIAS_SOUL_ORB = 4666;
	private static final int KERNON_INFERNIUM_SCEPTER = 4667;
	private static final int GOLKONDA_INFERNIUM_SCEPTER = 4668;
	private static final int HALLATE_INFERNIUM_SCEPTER = 4669;
	private static final int REORIN_HAMMER = 4670;
	private static final int REORIN_MOLD = 4671;
	private static final int INFERNIUM_VARNISH = 4672;
	private static final int RED_PIPETTE_KNIFE = 4673;
	private static final int STAR_OF_DESTINY = 5011;
	private static final int CRYSTAL_B = 1460;
	private static final int CRYSTAL_B_COUNT = 984;
	// The scepter each chest holds
	private static final Map<Integer, Integer> SCEPTERS = Map.of(
		CHEST_OF_KERNON, KERNON_INFERNIUM_SCEPTER,
		CHEST_OF_GOLKONDA, GOLKONDA_INFERNIUM_SCEPTER,
		CHEST_OF_HALLATE, HALLATE_INFERNIUM_SCEPTER);
	// The B-grade weapons Reorin melts down, with the name his dialogs show
	private static final Map<Integer, String> B_WEAPONS = Map.ofEntries(
		Map.entry(79, "Sword of Damascus"),
		Map.entry(97, "Lance"),
		Map.entry(171, "Deadman's Glory"),
		Map.entry(175, "Art of Battle Axe"),
		Map.entry(210, "Staff of Evil Spirits"),
		Map.entry(234, "Demon Dagger"),
		Map.entry(268, "Bellion Cestus"),
		Map.entry(287, "Bow of Peril"),
		Map.entry(2626, "Samurai Dual-sword"),
		Map.entry(7883, "Guardian Sword"),
		Map.entry(7889, "Wizard's Tear"),
		Map.entry(7893, "Kaim Vanul's Bones"),
		Map.entry(7901, "Star Buster"));
	// The A-grade weapons he forges in return (the links of 31002-AGradeList.htm)
	private static final int[] A_WEAPONS = {
		80, 98, 150, 212, 235, 269, 288, 2504, 5233, 7884, 7894, 7899
	};
	// Quest variables: the B-grade weapon chosen and whether the choice is confirmed
	private static final String WEAPON_VAR = "weaponId";
	private static final String CONFIRMED_VAR = "bypass";
	// Misc
	private static final int MIN_LEVEL = 75;
	private static final int CHEST_DESPAWN = 120000;

	public Q00234_FatesWhisper() {
		super(234, Q00234_FatesWhisper.class.getSimpleName(), "Fate's Whisper");
		bindStartNpc(MAESTRO_REORIN);
		bindTalk(MAESTRO_REORIN, CLIFF, HEAD_BLACKSMITH_FERRIS, ZENKIN, MASTER_KASPAR);
		bindTalk(COFFER_OF_THE_DEAD, CHEST_OF_KERNON, CHEST_OF_GOLKONDA, CHEST_OF_HALLATE);
		bindKill(CHESTS.keySet());
		bindAttack(BAIUM);
		registerQuestItems(PIPETTE_KNIFE, RED_PIPETTE_KNIFE);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, false);
		if (qs == null) {
			return null;
		}

		switch (event) {
			case "31002-03.htm": {
				if (qs.isCreated() && (player.getLevel() >= MIN_LEVEL)) {
					qs.startQuest();
					return event;
				}
				return null;
			}
			case "30182-01c.htm": {
				// Cliff hands over the varnish only once, at the step that asks for it.
				if (qs.isCond(3) && !hasQuestItems(player, INFERNIUM_VARNISH)) {
					giveItems(player, INFERNIUM_VARNISH, 1);
					playSound(player, Sound.ITEMSOUND_QUEST_ITEMGET);
					return event;
				}
				return null;
			}
			case "30178-01a.htm": {
				if (qs.isCond(5)) {
					qs.setCond(6, true);
					return event;
				}
				return null;
			}
			case "30833-01b.htm": {
				if (qs.isCond(6)) {
					qs.setCond(7, true);
					giveItems(player, PIPETTE_KNIFE, 1);
					return event;
				}
				return null;
			}
			case "confirmWeapon": {
				final int weapon = qs.getInt(WEAPON_VAR);
				if (!qs.isCond(10) || !B_WEAPONS.containsKey(weapon)) {
					return null;
				}
				qs.set(CONFIRMED_VAR, 1);
				return weaponHtml(player, "31002-14.htm", B_WEAPONS.get(weapon));
			}
		}

		if (event.startsWith("selectBGrade_")) {
			return selectBGrade(player, qs, event.substring("selectBGrade_".length()));
		}
		if (event.startsWith("selectAGrade_")) {
			return selectAGrade(player, qs, event.substring("selectAGrade_".length()));
		}
		// The dialogs in between simply lead to the next page.
		return event.endsWith(".htm") ? event : null;
	}

	/**
	 * Remembers the B-grade weapon the player picked, as long as the choice is not confirmed yet.
	 * @param player the player
	 * @param qs the quest state
	 * @param id the item id of the link
	 * @return the confirmation dialog
	 */
	private String selectBGrade(L2PcInstance player, QuestState qs, String id) {
		final String name = B_WEAPONS.get(parseId(id));
		// Reorin sets the furnace to one weapon type, the choice cannot be taken back.
		if (!qs.isCond(10) || (name == null) || (qs.getInt(CONFIRMED_VAR) == 1)) {
			return null;
		}
		qs.set(WEAPON_VAR, id);
		return weaponHtml(player, "31002-13.htm", name);
	}

	/**
	 * Melts the chosen B-grade weapon into the A-grade one the player asks for.
	 * @param player the player
	 * @param qs the quest state
	 * @param id the item id of the link
	 * @return the dialog to show
	 */
	private String selectAGrade(L2PcInstance player, QuestState qs, String id) {
		if (!qs.isCond(10)) {
			return null;
		}
		final int bGrade = qs.getInt(WEAPON_VAR);
		if ((qs.getInt(CONFIRMED_VAR) != 1) || !B_WEAPONS.containsKey(bGrade)) {
			return "31002-16.htm";
		}
		if (!hasQuestItems(player, bGrade)) {
			return weaponHtml(player, "31002-15.htm", B_WEAPONS.get(bGrade));
		}
		final int aGrade = parseId(id);
		if (!isAGradeWeapon(aGrade)) {
			return null;
		}
		takeItems(player, bGrade, 1);
		giveItems(player, aGrade, 1);
		giveItems(player, STAR_OF_DESTINY, 1);
		player.broadcastSocialAction(3);
		qs.exitQuest(false, true);
		return weaponHtml(player, "31002-12.htm", ItemTable.getInstance().getTemplate(aGrade).getName());
	}

	private static boolean isAGradeWeapon(int itemId) {
		for (int allowed : A_WEAPONS) {
			if (allowed == itemId) {
				return true;
			}
		}
		return false;
	}

	private static int parseId(String id) {
		try {
			return Integer.parseInt(id);
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	/**
	 * @param player the player the dialog is shown to
	 * @param file the dialog file
	 * @param name the weapon name to put in place of %weaponname%
	 * @return the dialog text
	 */
	private String weaponHtml(L2PcInstance player, String file, String name) {
		return getHtm(player.getHtmlPrefix(), file).replace("%weaponname%", name);
	}

	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isSummon) {
		final Integer chest = CHESTS.get(npc.getId());
		if (chest != null) {
			addSpawn(chest, npc, true, CHEST_DESPAWN, false);
		}
		return super.onKill(npc, killer, isSummon);
	}

	@Override
	public String onAttack(L2Npc npc, L2PcInstance attacker, int damage, boolean isSummon) {
		final QuestState qs = getQuestState(attacker, false);
		if ((qs != null) && qs.isCond(7) && (attacker.getActiveWeaponItem() != null) && (attacker.getActiveWeaponItem().getId() == PIPETTE_KNIFE)
			&& !hasQuestItems(attacker, RED_PIPETTE_KNIFE)) {
			takeItems(attacker, PIPETTE_KNIFE, 1);
			giveItems(attacker, RED_PIPETTE_KNIFE, 1);
			playSound(attacker, Sound.ITEMSOUND_QUEST_ITEMGET);
			npc.broadcastPacket(new NpcSay(npc.getObjectId(), Say2.ALL, npc.getId(), WHO_DARES_TO_TRY_AND_STEAL_MY_NOBLE_BLOOD));
		}
		return super.onAttack(npc, attacker, damage, isSummon);
	}

	@Override
	public String onTalk(L2Npc npc, L2PcInstance player) {
		final QuestState qs = getQuestState(player, true);
		if (qs.isCompleted()) {
			return getAlreadyCompletedMsg(player);
		}
		if (qs.isCreated()) {
			if (npc.getId() != MAESTRO_REORIN) {
				return getNoQuestMsg(player);
			}
			return (player.getLevel() >= MIN_LEVEL) ? "31002-02.htm" : "31002-01.htm";
		}

		final int cond = qs.getCond();
		switch (npc.getId()) {
			case MAESTRO_REORIN: {
				return talkReorin(player, qs, cond);
			}
			case CLIFF: {
				if (cond == 3) {
					return hasQuestItems(player, INFERNIUM_VARNISH) ? "30182-02.htm" : "30182-01.htm";
				}
				break;
			}
			case HEAD_BLACKSMITH_FERRIS: {
				if ((cond == 4) && !hasQuestItems(player, REORIN_HAMMER)) {
					giveItems(player, REORIN_HAMMER, 1);
					playSound(player, Sound.ITEMSOUND_QUEST_ITEMGET);
					return "30847-01.htm";
				}
				if ((cond >= 4) && hasQuestItems(player, REORIN_HAMMER)) {
					return "30847-02.htm";
				}
				break;
			}
			case ZENKIN: {
				if (cond == 5) {
					return "30178-01.htm";
				}
				if (cond > 5) {
					return "30178-02.htm";
				}
				break;
			}
			case MASTER_KASPAR: {
				return talkKaspar(player, qs, cond);
			}
			case COFFER_OF_THE_DEAD: {
				if ((cond == 1) && !hasQuestItems(player, REIRIAS_SOUL_ORB)) {
					giveItems(player, REIRIAS_SOUL_ORB, 1);
					playSound(player, Sound.ITEMSOUND_QUEST_ITEMGET);
					return "31027-01.htm";
				}
				return "31027-02.htm";
			}
			case CHEST_OF_KERNON, CHEST_OF_GOLKONDA, CHEST_OF_HALLATE: {
				final int scepter = SCEPTERS.get(npc.getId());
				if ((cond == 2) && !hasQuestItems(player, scepter)) {
					giveItems(player, scepter, 1);
					playSound(player, Sound.ITEMSOUND_QUEST_ITEMGET);
					return npc.getId() + "-01.htm";
				}
				return npc.getId() + "-02.htm";
			}
		}
		return getNoQuestMsg(player);
	}

	/**
	 * Maestro Reorin walks the player through the whole chain, one ingredient per step.
	 * @param player the player
	 * @param qs the quest state
	 * @param cond the current condition
	 * @return the dialog to show
	 */
	private String talkReorin(L2PcInstance player, QuestState qs, int cond) {
		switch (cond) {
			case 1: {
				if (!hasQuestItems(player, REIRIAS_SOUL_ORB)) {
					return "31002-04b.htm";
				}
				takeItems(player, REIRIAS_SOUL_ORB, 1);
				qs.setCond(2, true);
				return "31002-05.htm";
			}
			case 2: {
				if (!hasQuestItems(player, KERNON_INFERNIUM_SCEPTER) || !hasQuestItems(player, GOLKONDA_INFERNIUM_SCEPTER) || !hasQuestItems(player, HALLATE_INFERNIUM_SCEPTER)) {
					return "31002-05c.htm";
				}
				takeItems(player, KERNON_INFERNIUM_SCEPTER, 1);
				takeItems(player, GOLKONDA_INFERNIUM_SCEPTER, 1);
				takeItems(player, HALLATE_INFERNIUM_SCEPTER, 1);
				qs.setCond(3, true);
				return "31002-06.htm";
			}
			case 3: {
				if (!hasQuestItems(player, INFERNIUM_VARNISH)) {
					return "31002-06b.htm";
				}
				takeItems(player, INFERNIUM_VARNISH, 1);
				qs.setCond(4, true);
				return "31002-07.htm";
			}
			case 4: {
				if (!hasQuestItems(player, REORIN_HAMMER)) {
					return "31002-07b.htm";
				}
				takeItems(player, REORIN_HAMMER, 1);
				qs.setCond(5, true);
				return "31002-08.htm";
			}
			case 5, 6, 7: {
				return "31002-08b.htm";
			}
			case 8: {
				takeItems(player, REORIN_MOLD, 1);
				qs.setCond(9, true);
				return "31002-09.htm";
			}
			case 9: {
				if (getQuestItemsCount(player, CRYSTAL_B) < CRYSTAL_B_COUNT) {
					return "31002-09b.htm";
				}
				takeItems(player, CRYSTAL_B, CRYSTAL_B_COUNT);
				qs.setCond(10, true);
				return "31002-BGradeList.htm";
			}
			case 10: {
				final int weapon = qs.getInt(WEAPON_VAR);
				if ((qs.getInt(CONFIRMED_VAR) != 1) || !B_WEAPONS.containsKey(weapon)) {
					return "31002-BGradeList.htm";
				}
				final String file = hasQuestItems(player, weapon) ? "31002-AGradeList.htm" : "31002-15.htm";
				return weaponHtml(player, file, B_WEAPONS.get(weapon));
			}
		}
		return getNoQuestMsg(player);
	}

	/**
	 * Master Kaspar turns the blood of Baium on the Pipette Knife into Reorin's Mold.
	 * @param player the player
	 * @param qs the quest state
	 * @param cond the current condition
	 * @return the dialog to show
	 */
	private String talkKaspar(L2PcInstance player, QuestState qs, int cond) {
		switch (cond) {
			case 6: {
				return "30833-01.htm";
			}
			case 7: {
				if (!hasQuestItems(player, RED_PIPETTE_KNIFE)) {
					return "30833-02.htm";
				}
				takeItems(player, RED_PIPETTE_KNIFE, 1);
				giveItems(player, REORIN_MOLD, 1);
				qs.setCond(8, true);
				return "30833-03.htm";
			}
			default: {
				return (cond > 7) ? "30833-04.htm" : getNoQuestMsg(player);
			}
		}
	}
}
