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
package com.l2jserver.datapack.ai.npc.MerchantOfMammon;

import static com.l2jserver.gameserver.config.Configuration.sevenSigns;

import com.l2jserver.datapack.ai.npc.AbstractNpcAI;
import com.l2jserver.gameserver.SevenSigns;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

/**
 * Merchant of Mammon ai
 * @author Charus
 */
public final class MerchantOfMammon extends AbstractNpcAI {
	
	// NPC
	private static final int MERCHANT_MAMMON = 31113;
	
	public MerchantOfMammon() {
		super(MerchantOfMammon.class.getSimpleName(), "ai/npc");
		bindStartNpc(MERCHANT_MAMMON);
		bindFirstTalk(MERCHANT_MAMMON);
	}
	
	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		if (event.endsWith(".htm")) {
			return "merchant_of_mammon" + event;
		}
		return super.onEvent(event, npc, player);
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player) {
		int compWinner = SevenSigns.getInstance().getCabalHighestScore();
		int sealAvariceOwner = SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_AVARICE);
		int playerCabal = SevenSigns.getInstance().getPlayerCabal(player.getObjectId());
		
		if (sevenSigns().strictSevenSigns()) {
			switch (compWinner) {
				case SevenSigns.CABAL_DAWN -> {
					if ((playerCabal != compWinner) || (playerCabal != sealAvariceOwner)) {
						return "merchant_of_mammon002.htm";
					}
				}
				case SevenSigns.CABAL_DUSK -> {
					if ((playerCabal != compWinner) || (playerCabal != sealAvariceOwner)) {
						return "merchant_of_mammon002.htm";
					}
				}
			}
		}
		return "merchant_of_mammon001.htm";
	}
}