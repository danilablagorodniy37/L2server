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
import com.l2jserver.gameserver.handler.IItemHandler;
import com.l2jserver.gameserver.handler.ItemHandler;
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
	private static final double RETREAT_HP = 0.5;
	/** Below this share of HP the bot goes home. */
	private static final double FLEE_HP = 0.25;
	/** A bot sits until it has this share of HP again. */
	private static final double RESTED_HP = 0.85;
	/** The share of mana a bot keeps for the next fight instead of spending it all on skills. */
	private static final double MP_RESERVE = 0.25;
	/** Levels above the bot it still dares to attack: it picks on weaker monsters, the way a player farms. */
	private static final int MAX_LEVEL_ABOVE = -5;
	/** Soulshots by grade, from no grade to S: a bot fights with shots like anybody else. */
	private static final int[] SOULSHOTS = {
		1835, 1463, 1464, 1465, 1466, 1467
	};
	/** The same for a mage. */
	private static final int[] SPIRITSHOTS = {
		2509, 2510, 2511, 2512, 2513, 2514
	};
	/** How many shots a bot carries: it is topped up long before it can run out. */
	private static final int SHOTS = 5000;
	private static final int SHOTS_LEFT = 2000;
	/** Greater Healing Potion, and how many a bot carries. */
	private static final int POTION = 1539;
	private static final int POTIONS = 200;
	private static final int POTIONS_LEFT = 50;
	/** Below this share of HP a bot drinks, and no oftener than the potion allows. */
	private static final double DRINK_AT = 0.55;
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
		return findTarget(bot, ground, MAX_LEVEL_ABOVE);
	}

	/**
	 * The same, for a party that has to be more careful because nobody in it can heal.
	 * @param bot the bot
	 * @param ground where it hunts, or null to look around itself
	 * @param maxLevelAbove how far above its own level a monster may be
	 * @return the monster, or null
	 */
	public static L2MonsterInstance findTarget(L2PcInstance bot, PhantomHunting.Ground ground, int maxLevelAbove) {
		L2MonsterInstance best = null;
		double bestDistance = Double.MAX_VALUE;
		for (L2Object object : L2World.getInstance().getVisibleObjects(bot, SEARCH_RADIUS)) {
			if (!(object instanceof L2MonsterInstance monster)) {
				continue;
			}
			if (monster.isAlikeDead() || monster.isRaid() || monster.isRaidMinion() || !monster.isAutoAttackable(bot)) {
				continue;
			}
			if (monster.getLevel() > (bot.getLevel() + maxLevelAbove)) {
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

	/**
	 * Fills the bot's pouch with shots for its weapon and switches them on.<br>
	 * Without them a bot hits half as hard as the player next to it and dies to monsters of its own level.
	 * @param bot the bot heading out to hunt
	 */
	public static void supply(L2PcInstance bot) {
		PhantomBuffs.cast(bot);
		final L2ItemInstance weapon = bot.getActiveWeaponInstance();
		if (weapon == null) {
			return;
		}
		final boolean mage = bot.isMageClass();
		final int grade = Math.max(0, Math.min(weapon.getItem().getCrystalType().getId(), SOULSHOTS.length - 1));
		final int shotId = mage ? SPIRITSHOTS[grade] : SOULSHOTS[grade];
		final long left = bot.getInventory().getInventoryItemCount(shotId, -1);
		if (left < SHOTS_LEFT) {
			bot.getInventory().addItem("PhantomShots", shotId, SHOTS - left, bot, null);
		}
		final long potions = bot.getInventory().getInventoryItemCount(POTION, -1);
		if (potions < POTIONS_LEFT) {
			bot.getInventory().addItem("PhantomShots", POTION, POTIONS - potions, bot, null);
		}
		if (!bot.getAutoSoulShot().contains(shotId)) {
			bot.addAutoSoulShot(shotId);
		}
		bot.rechargeShots(!mage, mage);
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
		// what is left after the weakest quarter of the mana: a bot keeps something back for the next fight
		final double spare = bot.getCurrentMp() - (bot.getMaxMp() * MP_RESERVE);
		Skill best = null;
		double bestWorth = 0;
		for (Skill skill : bot.getAllSkills()) {
			if (!skill.isActive() || !skill.isBad() || skill.isContinuous() || skill.isPassive()) {
				continue;
			}
			if (bot.isSkillDisabled(skill) || (skill.getMpConsume2() > spare)) {
				continue;
			}
			if ((skill.getCastRange() > 0) && (bot.calculateDistance(target, false, false) > skill.getCastRange())) {
				continue;
			}
			// the costliest skill of the highest grade hits hardest; a little chance for the others,
			// so a bot does not cast the same thing every single time
			final double worth = skill.getMpConsume2() + skill.getMagicLevel() + Rnd.get(20);
			if (worth > bestWorth) {
				best = skill;
				bestWorth = worth;
			}
		}
		return best;
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

	/**
	 * A hurt bot drinks a healing potion, the way a player does before it comes to running away.
	 * @param bot the bot
	 * @return true when it drank
	 */
	public static boolean drink(L2PcInstance bot) {
		if ((hpRatio(bot) > DRINK_AT) || bot.isDead()) {
			return false;
		}
		final L2ItemInstance potion = bot.getInventory().getItemByItemId(POTION);
		if (potion == null) {
			return false;
		}
		final IItemHandler handler = ItemHandler.getInstance().getHandler(potion.getEtcItem());
		return (handler != null) && handler.useItem(bot, potion, false);
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
