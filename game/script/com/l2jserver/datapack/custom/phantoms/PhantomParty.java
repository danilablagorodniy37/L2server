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
package com.l2jserver.datapack.custom.phantoms;

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.enums.PartyDistributionType;
import com.l2jserver.gameserver.model.L2Clan;
import com.l2jserver.gameserver.model.L2Party;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.network.serverpackets.JoinParty;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;

/**
 * What a bot does when a player asks it into a party.<br>
 * The window a player would answer in never reaches a bot, so the answer is decided here.
 */
public class PhantomParty {
	/** How many levels apart a bot still says yes. */
	private static final int LEVEL_GAP = 15;
	/** How full a party may be. */
	private static final int PARTY_SIZE = 9;
	/** How long a bot takes to answer, as a player reading the window would. */
	public static final int ANSWER_MIN = 2000;
	public static final int ANSWER_MAX = 7000;

	private PhantomParty() {
	}

	/**
	 * Whether the bot goes along with the invitation.
	 * @param bot the bot invited
	 * @param requestor the player inviting
	 * @param busy true when the bot is doing something it will not leave, such as keeping a shop
	 * @return true when it joins
	 */
	public static boolean accepts(L2PcInstance bot, L2PcInstance requestor, boolean busy) {
		if (busy || (bot == null) || (requestor == null)) {
			return false;
		}
		if (bot.isDead() || bot.isInParty() || !bot.isVisible()) {
			return false;
		}
		if (requestor.isInParty() && (requestor.getParty().getMemberCount() >= PARTY_SIZE)) {
			return false;
		}
		// nobody wants to be carried by, or to carry, someone far out of their level
		if (Math.abs(bot.getLevel() - requestor.getLevel()) > LEVEL_GAP) {
			return false;
		}
		return Rnd.get(100) < 75;
	}

	/**
	 * The bot joins the party of the player who invited it, starting one when there is none.
	 * @param bot the bot
	 * @param requestor the player who invited it
	 * @return true when the bot is in the party afterwards
	 */
	public static boolean join(L2PcInstance bot, L2PcInstance requestor) {
		if (!requestor.isInParty()) {
			final PartyDistributionType type = (requestor.getPartyDistributionType() != null) //
				? requestor.getPartyDistributionType()
				: PartyDistributionType.FINDERS_KEEPERS;
			requestor.setParty(new L2Party(requestor, type));
		}
		if (requestor.getParty().getMemberCount() >= PARTY_SIZE) {
			return false;
		}
		bot.joinParty(requestor.getParty());
		requestor.getParty().setPendingInvitation(false);
		requestor.sendPacket(new JoinParty(1));
		return true;
	}

	/**
	 * Whether the bot joins the clan it was asked into.
	 * @param bot the bot
	 * @param clan the clan asking
	 * @return true when it says yes
	 */
	public static boolean joinsClan(L2PcInstance bot, L2Clan clan) {
		if ((clan == null) || (bot.getClan() != null) || bot.isDead()) {
			return false;
		}
		if (clan.getMembersCount() >= clan.getMaxNrOfMembers(0)) {
			return false;
		}
		// a bot with a place of its own in the world says yes about half the time
		return Rnd.get(100) < 55;
	}

	/**
	 * Puts the bot in the clan, the way the answer packet would.
	 * @param bot the bot
	 * @param clan the clan
	 * @param pledgeType main clan, academy or a royal guard
	 */
	public static void joinClan(L2PcInstance bot, L2Clan clan, int pledgeType) {
		bot.setPledgeType(pledgeType);
		if (pledgeType == L2Clan.SUBUNIT_ACADEMY) {
			bot.setPowerGrade(9);
			bot.setLvlJoinedAcademy(bot.getLevel());
		} else {
			bot.setPowerGrade(5);
		}
		clan.addClanMember(bot);
		bot.setClanPrivileges(bot.getClan().getRankPrivs(bot.getPowerGrade()));
		final SystemMessage joined = SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_JOINED_CLAN);
		joined.addString(bot.getName());
		clan.broadcastToOnlineMembers(joined);
		bot.broadcastUserInfo();
	}

	/** The bot turns the invitation down; the player's window closes as if it had been declined. */
	public static void decline(L2PcInstance requestor) {
		if (requestor.isInParty()) {
			requestor.getParty().setPendingInvitation(false);
		}
		requestor.sendPacket(new JoinParty(0));
	}
}
