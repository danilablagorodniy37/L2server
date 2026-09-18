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
import com.l2jserver.gameserver.ai.CtrlIntention;
import com.l2jserver.gameserver.enums.PartyDistributionType;
import com.l2jserver.gameserver.model.L2Party;
import com.l2jserver.gameserver.model.Location;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.actor.instance.L2MonsterInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.effects.L2EffectType;
import com.l2jserver.gameserver.model.skills.Skill;

/**
 * A party of bots hunting one place together: the leader picks what dies next, the healer keeps
 * everyone standing, the buffers hand out their songs and dances, and the rest hit what the leader hits.
 */
public class PhantomSquad {
	/** How far from the camp a fight may wander. */
	private static final int CAMP_RADIUS = 1600;
	/** Below this share of HP the healer takes care of a member. */
	private static final double HEAL_AT = 0.7;
	/** How often the buffers go round the party. */
	private static final long BUFF_EVERY = 3 * 60 * 1000L;
	/** How long a dead member lies there before the party brings it back. */
	private static final long RAISE_AFTER = 15 * 1000L;
	/** One action of a kind per member and second, so a party does not cast in a loop. */
	private static final long CAST_EVERY = 3000L;

	private final String _name;
	private final List<Phantom> _members = new ArrayList<>();
	private final Location _home;
	private final String _homeName;
	private Location _camp;
	private String _where;
	private L2Character _quarry;
	private long _raidUntil;
	private long _nextRaid;
	private long _nextBuff;
	private long _nextCast;
	private long _nextRaise;

	public PhantomSquad(String name, Location camp, String where) {
		_name = name;
		_camp = camp;
		_where = where;
		_home = camp;
		_homeName = where;
	}

	/** The raid boss the party is on, or null when it is at its usual place. */
	public L2Character quarry() {
		return _quarry;
	}

	public boolean raiding() {
		return (_quarry != null) && !_quarry.isDead();
	}

	public long raidUntil() {
		return _raidUntil;
	}

	public long nextRaid() {
		return _nextRaid;
	}

	public void nextRaid(long when) {
		_nextRaid = when;
	}

	/**
	 * The party walks off to a raid boss and fights it until it or the party has had enough.
	 * @param boss the boss
	 * @param until when the party gives up and goes home
	 */
	public void goRaid(L2Character boss, long until) {
		_quarry = boss;
		_raidUntil = until;
		moveTo(new Location(boss.getX(), boss.getY(), boss.getZ()), boss.getName());
	}

	/** Back to the hunting ground the party holds. */
	public void goHome() {
		_quarry = null;
		_raidUntil = 0;
		moveTo(_home, _homeName);
	}

	public void add(Phantom phantom) {
		_members.add(phantom);
	}

	public List<Phantom> members() {
		return _members;
	}

	public String name() {
		return _name;
	}

	public String where() {
		return _where;
	}

	public Location camp() {
		return _camp;
	}

	/** Moves the whole party to another hunting ground. */
	public void moveTo(Location camp, String where) {
		_camp = camp;
		_where = where;
		for (Phantom member : _members) {
			member.player().teleToLocation(camp.getX() + Rnd.get(-150, 150), camp.getY() + Rnd.get(-150, 150), camp.getZ(), false);
		}
	}

	/** The leader: the first member still standing. */
	public Phantom leader() {
		for (Phantom member : _members) {
			if (!member.player().isDead() && member.player().isVisible()) {
				return member;
			}
		}
		return _members.isEmpty() ? null : _members.get(0);
	}

	/** Puts the members in one party, with the first of them leading. */
	public void formParty() {
		final Phantom head = leader();
		if (head == null) {
			return;
		}
		final L2PcInstance leader = head.player();
		if (!leader.isInParty()) {
			leader.setParty(new L2Party(leader, PartyDistributionType.FINDERS_KEEPERS));
		}
		for (Phantom member : _members) {
			final L2PcInstance player = member.player();
			if ((player != leader) && !player.isInParty() && (leader.getParty().getMemberCount() < 9)) {
				player.joinParty(leader.getParty());
			}
		}
	}

	/** True when everybody is where they should be; a member that wandered off is brought back. */
	public void keepTogether() {
		final Phantom head = leader();
		if (head == null) {
			return;
		}
		for (Phantom member : _members) {
			final L2PcInstance player = member.player();
			if (player.isDead() || !player.isVisible()) {
				continue;
			}
			if (player.calculateDistance(_camp.getX(), _camp.getY(), _camp.getZ(), false, false) > (CAMP_RADIUS * 2)) {
				player.teleToLocation(_camp.getX() + Rnd.get(-150, 150), _camp.getY() + Rnd.get(-150, 150), _camp.getZ(), false);
			}
		}
	}

	/**
	 * One second of the party's life: raising the fallen, healing, buffing and fighting.
	 * @param now the current time
	 */
	public void act(long now) {
		_members.removeIf(member -> (member.player() == null) || !member.player().isVisible());
		if (_members.isEmpty()) {
			return;
		}
		raise(now);
		heal(now);
		buff(now);
		fight();
	}

	/** The party brings its dead back on the spot, the way a healer with a resurrection does. */
	private void raise(long now) {
		if (now < _nextRaise) {
			return;
		}
		for (Phantom member : _members) {
			final L2PcInstance player = member.player();
			if (!player.isDead()) {
				continue;
			}
			if (member.diedAt() == 0) {
				member.diedAt(now);
				continue;
			}
			if ((now - member.diedAt()) < RAISE_AFTER) {
				continue;
			}
			player.doRevive();
			player.setCurrentHp(player.getMaxHp() * 0.7);
			player.setCurrentMp(player.getMaxMp() * 0.7);
			player.teleToLocation(_camp.getX() + Rnd.get(-150, 150), _camp.getY() + Rnd.get(-150, 150), _camp.getZ(), false);
			member.diedAt(0);
			member.died();
			_nextRaise = now + CAST_EVERY;
			return;
		}
	}

	/** The healer looks after whoever is worst off. */
	private void heal(long now) {
		if (now < _nextCast) {
			return;
		}
		Phantom hurt = null;
		double worst = HEAL_AT;
		for (Phantom member : _members) {
			final L2PcInstance player = member.player();
			if (player.isDead()) {
				continue;
			}
			final double ratio = PhantomCombat.hpRatio(player);
			if (ratio < worst) {
				worst = ratio;
				hurt = member;
			}
		}
		if (hurt == null) {
			return;
		}
		for (Phantom member : _members) {
			final L2PcInstance healer = member.player();
			if (healer.isDead() || healer.isCastingNow()) {
				continue;
			}
			final Skill heal = pick(healer, L2EffectType.HP);
			if (heal == null) {
				continue;
			}
			healer.setTarget(hurt.player());
			healer.useMagic(heal, false, false);
			_nextCast = now + CAST_EVERY;
			return;
		}
	}

	/** Every few minutes the ones who know buffs cast them on the party. */
	private void buff(long now) {
		if (now < _nextBuff) {
			return;
		}
		_nextBuff = now + BUFF_EVERY;
		for (Phantom member : _members) {
			final L2PcInstance buffer = member.player();
			if (buffer.isDead()) {
				continue;
			}
			final List<Skill> buffs = partyBuffs(buffer);
			if (buffs.isEmpty()) {
				continue;
			}
			// one buff each round, so a party is dressed over a minute instead of in one tick
			final Skill skill = buffs.get(Rnd.get(buffs.size()));
			buffer.setTarget(buffer);
			buffer.useMagic(skill, false, false);
		}
	}

	/** The leader chooses what the party kills, everybody else joins in. */
	private void fight() {
		final Phantom head = leader();
		if ((head == null) || head.player().isDead()) {
			return;
		}
		final L2PcInstance leader = head.player();
		L2Character target;
		if (raiding()) {
			// on a raid the whole party is on the boss and nothing else
			target = _quarry;
			if (leader.getTarget() != target) {
				PhantomCombat.attack(leader, target);
			}
		} else {
			target = (leader.getTarget() instanceof L2MonsterInstance monster) && !monster.isAlikeDead() ? monster : null;
			if (target == null) {
				target = PhantomCombat.findTarget(leader, null);
				if (target == null) {
					return;
				}
				PhantomCombat.attack(leader, target);
			}
		}
		for (Phantom member : _members) {
			final L2PcInstance player = member.player();
			if (player.isDead() || (player == leader) || player.isCastingNow()) {
				continue;
			}
			if (player.calculateDistance(target, false, false) > CAMP_RADIUS) {
				continue;
			}
			if ((player.getTarget() != target) || !player.isInCombat()) {
				PhantomCombat.attack(player, target);
			} else if (Rnd.get(100) < 25) {
				final Skill skill = PhantomCombat.pickAttackSkill(player, target);
				if (skill != null) {
					player.useMagic(skill, false, false);
				}
			}
		}
	}

	/** A skill of the bot with this kind of effect, cast on one target. */
	private static Skill pick(L2PcInstance bot, L2EffectType effect) {
		for (Skill skill : bot.getAllSkills()) {
			if (!skill.isActive() || skill.isBad() || bot.isSkillDisabled(skill)) {
				continue;
			}
			if (skill.getMpConsume2() > bot.getCurrentMp()) {
				continue;
			}
			if (skill.hasEffectType(effect)) {
				return skill;
			}
		}
		return null;
	}

	/** The buffs, songs and dances a bot may put on its party. */
	private static List<Skill> partyBuffs(L2PcInstance bot) {
		final List<Skill> out = new ArrayList<>();
		for (Skill skill : bot.getAllSkills()) {
			if (!skill.isActive() || skill.isBad() || !skill.isContinuous() || bot.isSkillDisabled(skill)) {
				continue;
			}
			if (skill.getMpConsume2() > bot.getCurrentMp()) {
				continue;
			}
			out.add(skill);
		}
		return out;
	}

	/** Sets every member on its way to the camp and keeps them there. */
	public void settle() {
		for (Phantom member : _members) {
			final L2PcInstance player = member.player();
			player.teleToLocation(_camp.getX() + Rnd.get(-200, 200), _camp.getY() + Rnd.get(-200, 200), _camp.getZ(), false);
			player.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
			PhantomCombat.supply(player);
			member.enter(Phantom.State.HUNT, null, Long.MAX_VALUE / 2);
		}
		formParty();
	}

	/** The level of the party: what its members average out at. */
	public int level() {
		return _members.isEmpty() ? 0 : (int) _members.stream().mapToInt(m -> m.player().getLevel()).average().orElse(0);
	}

	/** Kills and deaths of the whole party. */
	public int kills() {
		return _members.stream().mapToInt(Phantom::kills).sum();
	}

	public int deaths() {
		return _members.stream().mapToInt(Phantom::deaths).sum();
	}

	@Override
	public String toString() {
		return _name + " (" + _members.size() + " in " + _where + ")";
	}
}
