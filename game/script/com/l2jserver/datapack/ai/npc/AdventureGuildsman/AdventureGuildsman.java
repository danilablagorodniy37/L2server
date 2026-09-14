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
package com.l2jserver.datapack.ai.npc.AdventureGuildsman;

import com.l2jserver.datapack.ai.npc.AbstractNpcAI;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerMenuSelected;
import com.l2jserver.gameserver.network.serverpackets.ExShowQuestInfo;

/**
 * Adventure Guildsman.
 * @author Zoey76
 * @version 2.6.3.0
 */
public class AdventureGuildsman extends AbstractNpcAI {
	
	// @formatter:off
	private static final int[] ADVENTURE_GUILDSMAN = {
		31775, 31776, 31777, 31778, 31779, 31780, 31781, 31782, 31783, 31784,
		31785, 31786, 31787, 31788, 31789, 31790, 31791, 31792, 31793, 31794,
		31795, 31796, 31797, 31798, 31799, 31800, 31801, 31802, 31803, 31804,
		31805, 31806, 31807, 31808, 31809, 31810, 31811, 31812, 31813, 31814,
		31815, 31816, 31817, 31818, 31819, 31820, 31821, 31822, 31823, 31824,
		31825, 31826, 31827, 31828, 31829, 31830, 31831, 31832, 31833, 31834,
		31835, 31836, 31837, 31838, 31839, 31840, 31841, 31991, 31992, 31993,
		31994, 31995, 32337, 32338, 32339, 32340
	};
	// @formatter:on
	
	public AdventureGuildsman() {
		super(AdventureGuildsman.class.getSimpleName(), "ai/npc");
		bindMenuSelected(ADVENTURE_GUILDSMAN);
	}
	
	@Override
	public void onMenuSelected(PlayerMenuSelected event) {
		if (event.ask() == -18) {
			if (event.reply() == 1) {
				event.player().sendPacket(ExShowQuestInfo.STATIC_PACKET);
			}
		}
		super.onMenuSelected(event);
	}
}
