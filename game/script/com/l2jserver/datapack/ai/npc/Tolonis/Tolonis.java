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
package com.l2jserver.datapack.ai.npc.Tolonis;

import static com.l2jserver.gameserver.config.Configuration.character;
import static com.l2jserver.gameserver.model.base.AcquireSkillType.COLLECT;
import static com.l2jserver.gameserver.network.SystemMessageId.DO_NOT_HAVE_FURTHER_SKILLS_TO_LEARN_S1;
import static com.l2jserver.gameserver.network.SystemMessageId.NO_MORE_SKILLS_TO_LEARN;

import com.l2jserver.datapack.ai.npc.AbstractNpcAI;
import com.l2jserver.gameserver.data.xml.impl.SkillTreesData;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerLearnSkillRequested;
import com.l2jserver.gameserver.network.serverpackets.AcquireSkillList;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;

/**
 * Officer Tolonis.
 * @author Zoey76
 * @version 2.6.3.0
 */
public class Tolonis extends AbstractNpcAI {
	
	private static final int TOLONIS_ID = 32611;
	
	private static final int MINIMUM_LEVEL = 75;
	
	public Tolonis() {
		super(Tolonis.class.getSimpleName(), "ai/npc");
		bindFirstTalk(TOLONIS_ID);
		bindLearnSkillRequested(TOLONIS_ID);
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player) {
		if ((player.getKarma() > 0) && !character().karmaPlayerCanShop()) {
			return "officer_tolonis006.html";
		}
		return "officer_tolonis001.html";
	}
	
	@Override
	public void onLearnSkillRequested(PlayerLearnSkillRequested event) {
		if (event.player().getLevel() >= MINIMUM_LEVEL) {
			showEtcSkillList(event.player());
		} else {
			showPage(event.player(), "officer_tolonis001a.html");
		}
	}
	
	// TODO(Zoey76): Generalize this function and move it to L2Npc class.
	private static void showEtcSkillList(L2PcInstance player) {
		final var skills = SkillTreesData.getInstance().getAvailableCollectSkills(player);
		if (skills.size() == 0) {
			final int minLevel = SkillTreesData.getInstance().getMinLevelForNewSkill(player, SkillTreesData.getInstance().getCollectSkillTree());
			if (minLevel > 0) {
				final var sm = SystemMessage.getSystemMessage(DO_NOT_HAVE_FURTHER_SKILLS_TO_LEARN_S1);
				sm.addInt(minLevel);
				player.sendPacket(sm);
			} else {
				player.sendPacket(NO_MORE_SKILLS_TO_LEARN);
			}
		} else {
			player.sendPacket(new AcquireSkillList(COLLECT, skills));
		}
	}
}
