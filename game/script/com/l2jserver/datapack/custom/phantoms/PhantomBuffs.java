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

import com.l2jserver.gameserver.datatables.SkillData;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.skills.Skill;

/**
 * The buffs a bot goes hunting with.<br>
 * A player walks to a buffer before every trip; a bot has nobody to walk to, so it is given the
 * plain set of Interlude buffs any player would carry: the prophet's dances of the body and soul,
 * and one or two more for its trade. Nothing a player could not have bought in town.
 * <p>
 * A bot is buffed again whenever it fills its pouch with shots, once a minute, and only what has
 * run out is cast; a buff from a real prophet nearby is stronger and stays.
 */
public class PhantomBuffs {
	/** A skill and the level it is given at. */
	private record Buff(int id, int level) {
	}

	/** What everybody gets. */
	private static final Buff[] COMMON = {
		new Buff(1204, 2), // Wind Walk
		new Buff(1040, 3), // Shield
		new Buff(1035, 4), // Mental Shield
		new Buff(1036, 2), // Magic Barrier
		new Buff(1045, 6), // Bless the Body
		new Buff(1048, 6), // Bless the Soul
		new Buff(1062, 2), // Berserker Spirit
	};

	/** What a fighter gets on top. */
	private static final Buff[] FIGHTER = {
		new Buff(1068, 3), // Might
		new Buff(1086, 2), // Haste
		new Buff(1077, 3), // Focus
		new Buff(1242, 3), // Death Whisper
		new Buff(1240, 3), // Guidance
		new Buff(1268, 4), // Vampiric Rage
	};

	/** What a mage gets on top. */
	private static final Buff[] MAGE = {
		new Buff(1085, 3), // Acumen
		new Buff(1059, 3), // Empower
		new Buff(1078, 6), // Concentration
	};

	private PhantomBuffs() {
	}

	/**
	 * Puts the whole set on a bot, skipping what it already has.
	 * @param bot the bot heading out
	 * @return how many buffs were added
	 */
	public static int cast(L2PcInstance bot) {
		int given = give(bot, COMMON);
		given += give(bot, bot.isMageClass() ? MAGE : FIGHTER);
		if (given > 0) {
			bot.broadcastUserInfo();
		}
		return given;
	}

	private static int give(L2PcInstance bot, Buff[] buffs) {
		int given = 0;
		for (Buff buff : buffs) {
			if (bot.getEffectList().getBuffInfoBySkillId(buff.id()) != null) {
				continue;
			}
			final Skill skill = SkillData.getInstance().getSkill(buff.id(), buff.level());
			if (skill != null) {
				skill.applyEffects(bot, bot);
				given++;
			}
		}
		return given;
	}
}
