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
package com.l2jserver.datapack.ai.npc.Fisherman;

import static com.l2jserver.gameserver.config.Configuration.character;
import static com.l2jserver.gameserver.model.base.AcquireSkillType.FISHING;
import static com.l2jserver.gameserver.network.SystemMessageId.DO_NOT_HAVE_FURTHER_SKILLS_TO_LEARN_S1;
import static com.l2jserver.gameserver.network.SystemMessageId.NO_MORE_SKILLS_TO_LEARN;

import com.l2jserver.datapack.ai.npc.AbstractNpcAI;
import com.l2jserver.gameserver.data.xml.impl.SkillTreesData;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2MerchantInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerLearnSkillRequested;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerSkillLearned;
import com.l2jserver.gameserver.network.serverpackets.AcquireSkillList;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;

/**
 * Fisherman AI.
 * @author Adry_85
 * @since 2.6.0.0
 */
public class Fisherman extends AbstractNpcAI {
	// NPC
	private static final int[] FISHERMAN = {
		31562,
		31563,
		31564,
		31565,
		31566,
		31567,
		31568,
		31569,
		31570,
		31571,
		31572,
		31573,
		31574,
		31575,
		31576,
		31577,
		31578,
		31579,
		31696,
		31697,
		31989,
		32007,
		32348
	};
	
	public Fisherman() {
		super(Fisherman.class.getSimpleName(), "ai/npc");
		bindStartNpc(FISHERMAN);
		bindFirstTalk(FISHERMAN);
		bindTalk(FISHERMAN);
		bindLearnSkillRequested(FISHERMAN);
		bindSkillLearned(FISHERMAN);
	}
	
	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		String htmltext = null;
		switch (event) {
			case "fishing_championship.htm": {
				htmltext = event;
				break;
			}
			case "BuySellRefund": {
				((L2MerchantInstance) npc).showBuyWindow(player, npc.getId() * 100, true);
				break;
			}
		}
		return htmltext;
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player) {
		if ((player.getKarma() > 0) && !character().karmaPlayerCanShop()) {
			return npc.getId() + "-pk.htm";
		}
		return npc.getId() + ".htm";
	}
	
	@Override
	public void onLearnSkillRequested(PlayerLearnSkillRequested event) {
		showFishSkillList(event.player());
	}
	
	@Override
	public void onSkillLearned(PlayerSkillLearned event) {
		showFishSkillList(event.player());
	}
	
	/**
	 * Display the Fishing Skill list to the player.
	 * @param player the player
	 */
	public static void showFishSkillList(L2PcInstance player) {
		final var skills = SkillTreesData.getInstance().getAvailableFishingSkills(player);
		if (skills.size() > 0) {
			player.sendPacket(new AcquireSkillList(FISHING, skills));
		} else {
			final int minlLevel = SkillTreesData.getInstance().getMinLevelForNewSkill(player, SkillTreesData.getInstance().getFishingSkillTree());
			if (minlLevel > 0) {
				final var sm = SystemMessage.getSystemMessage(DO_NOT_HAVE_FURTHER_SKILLS_TO_LEARN_S1);
				sm.addInt(minlLevel);
				player.sendPacket(sm);
			} else {
				player.sendPacket(NO_MORE_SKILLS_TO_LEARN);
			}
		}
	}
}
