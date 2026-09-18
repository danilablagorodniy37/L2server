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

import java.util.ArrayList;
import java.util.List;

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.GeoData;
import com.l2jserver.gameserver.ai.CtrlIntention;
import com.l2jserver.gameserver.model.L2Object;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.instance.L2MonsterInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.items.instance.L2ItemInstance;
import com.l2jserver.gameserver.model.skills.Skill;

/**
 * How a bot fights: what it attacks, which skill it uses and when it has had enough.<br>
 * Everything here works on one bot; who hunts where is decided by {@link Phantoms}.
 */
public class PhantomCombat {
	/** A bot only fights what stands in its hunting ground. */
	public static final int HUNT_RADIUS = 1400;
	/** Monsters further away than this are not worth walking to. */
	private static final int SEARCH_RADIUS = 1200;
	/** Below this share of HP the bot backs off and sits down. */
	private static final double RETREAT_HP = 0.35;
	/** Below this share of HP the bot goes home. */
	private static final double FLEE_HP = 0.15;
	/** A bot sits until it has this share of HP again. */
	private static final double RESTED_HP = 0.85;
	/** Levels above the bot it still dares to attack. */
	private static final int MAX_LEVEL_ABOVE = 3;
	/** How far a bot walks for something lying on the ground. */
	private static final int LOOT_RADIUS = 700;
	/** How close it has to stand to pick it up. */
	private static final int PICKUP_RANGE = 60;

	private PhantomCombat() {
	}

	/**
	 * The monster a bot should attack: the closest one of a sane level that nobody else is fighting.
	 * @param bot the bot
	 * @param ground where it hunts, so it does not wander off
	 * @return the monster, or null when there is nothing to kill nearby
	 */
	public static L2MonsterInstance findTarget(L2PcInstance bot, PhantomHunting.Ground ground) {
		L2MonsterInstance best = null;
		double bestDistance = Double.MAX_VALUE;
		for (L2Object object : L2World.getInstance().getVisibleObjects(bot, SEARCH_RADIUS)) {
			if (!(object instanceof L2MonsterInstance monster)) {
				continue;
			}
			if (monster.isAlikeDead() || monster.isRaid() || monster.isRaidMinion() || !monster.isAutoAttackable(bot)) {
				continue;
			}
			if (monster.getLevel() > (bot.getLevel() + MAX_LEVEL_ABOVE)) {
				continue;
			}
			// someone is already killing it
			if ((monster.getTarget() != null) && (monster.getTarget() != bot)) {
				continue;
			}
			if ((ground != null) && (monster.calculateDistance(ground.x(), ground.y(), ground.z(), false, false) > HUNT_RADIUS)) {
				continue;
			}
			if (!GeoData.getInstance().canSeeTarget(bot, monster)) {
				continue;
			}
			final double distance = bot.calculateDistance(monster, false, false);
			if (distance < bestDistance) {
				best = monster;
				bestDistance = distance;
			}
		}
		return best;
	}

	/**
	 * What the bot's kills left on the ground: its own loot, nothing another player has a claim on.
	 * @param bot the bot
	 * @return the closest item it may take, or null when there is nothing lying around
	 */
	public static L2ItemInstance findLoot(L2PcInstance bot) {
		L2ItemInstance best = null;
		double bestDistance = Double.MAX_VALUE;
		for (L2Object object : L2World.getInstance().getVisibleObjects(bot, LOOT_RADIUS)) {
			if (!(object instanceof L2ItemInstance item) || !item.isVisible()) {
				continue;
			}
			if ((item.getOwnerId() != 0) && (item.getOwnerId() != bot.getObjectId())) {
				continue;
			}
			final double distance = bot.calculateDistance(item, false, false);
			if (distance < bestDistance) {
				best = item;
				bestDistance = distance;
			}
		}
		return best;
	}

	/**
	 * Walks to the item and picks it up once the bot stands over it, the way a player does.
	 * @param bot the bot
	 * @param item what is lying there
	 */
	public static void take(L2PcInstance bot, L2ItemInstance item) {
		if (bot.isSitting()) {
			bot.standUp();
		}
		if (bot.calculateDistance(item, false, false) > PICKUP_RANGE) {
			bot.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, item.getLocation());
			return;
		}
		bot.doPickupItem(item);
	}

	/** Sets the bot on its target; it keeps hitting until the monster or the bot is down. */
	public static void attack(L2PcInstance bot, L2Character target) {
		if (bot.isSitting()) {
			bot.standUp();
		}
		bot.setTarget(target);
		bot.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, target);
	}

	/**
	 * An attack skill the bot can afford right now.
	 * @param bot the bot
	 * @param target what it is fighting
	 * @return the skill, or null when it should just swing its weapon
	 */
	public static Skill pickAttackSkill(L2PcInstance bot, L2Character target) {
		final List<Skill> usable = new ArrayList<>();
		for (Skill skill : bot.getAllSkills()) {
			if (!skill.isActive() || !skill.isBad() || skill.isContinuous() || skill.isPassive()) {
				continue;
			}
			if (bot.isSkillDisabled(skill) || (skill.getMpConsume2() > bot.getCurrentMp())) {
				continue;
			}
			if ((skill.getCastRange() > 0) && (bot.calculateDistance(target, false, false) > skill.getCastRange())) {
				continue;
			}
			usable.add(skill);
		}
		return usable.isEmpty() ? null : usable.get(Rnd.get(usable.size()));
	}

	/** The buffs a bot casts on itself before it starts hunting. */
	public static List<Skill> selfBuffs(L2PcInstance bot) {
		final List<Skill> out = new ArrayList<>();
		for (Skill skill : bot.getAllSkills()) {
			if (skill.isActive() && skill.isSelfContinuous() && !bot.isSkillDisabled(skill) && (skill.getMpConsume2() <= bot.getCurrentMp())) {
				out.add(skill);
			}
		}
		return out;
	}

	public static double hpRatio(L2PcInstance bot) {
		return bot.getMaxHp() <= 0 ? 1 : (bot.getCurrentHp() / bot.getMaxHp());
	}

	/** True when the bot is hurt enough to stop fighting and sit down. */
	public static boolean shouldRest(L2PcInstance bot) {
		return hpRatio(bot) < RETREAT_HP;
	}

	/** True when the bot has rested enough to fight again. */
	public static boolean rested(L2PcInstance bot) {
		return hpRatio(bot) >= RESTED_HP;
	}

	/** True when things went badly and the bot should go back to town. */
	public static boolean shouldFlee(L2PcInstance bot) {
		return hpRatio(bot) < FLEE_HP;
	}
}
