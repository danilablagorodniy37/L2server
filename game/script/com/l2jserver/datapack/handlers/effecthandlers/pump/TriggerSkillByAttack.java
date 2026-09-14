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
package com.l2jserver.datapack.handlers.effecthandlers.pump;

import static com.l2jserver.gameserver.enums.TriggerAttackType.NONE;
import static com.l2jserver.gameserver.enums.TriggerTargetType.SELF;
import static com.l2jserver.gameserver.model.events.EventType.CREATURE_DAMAGE_DEALT;

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.enums.TriggerAttackType;
import com.l2jserver.gameserver.enums.TriggerTargetType;
import com.l2jserver.gameserver.model.StatsSet;
import com.l2jserver.gameserver.model.conditions.Condition;
import com.l2jserver.gameserver.model.effects.AbstractEffect;
import com.l2jserver.gameserver.model.events.impl.character.CreatureDamageDealt;
import com.l2jserver.gameserver.model.events.listeners.ConsumerEventListener;
import com.l2jserver.gameserver.model.holders.SkillHolder;
import com.l2jserver.gameserver.model.items.type.WeaponType;
import com.l2jserver.gameserver.model.skills.BuffInfo;
import com.l2jserver.gameserver.util.Util;

/**
 * Trigger Skill By Attack effect implementation.
 * @author Zealar
 * @author Zoey76
 */
public final class TriggerSkillByAttack extends AbstractEffect {
	private final int _minAttackerLevel;
	private final int _maxAttackerLevel;
	private final int _minDamage;
	private final int _chance;
	private final SkillHolder _skill;
	private final TriggerTargetType _targetType;
	private final TriggerAttackType _attackerType;
	private int _allowWeapons;
	private final boolean _isCritical;
	
	public TriggerSkillByAttack(Condition attachCond, Condition applyCond, StatsSet set, StatsSet params) {
		super(attachCond, applyCond, set, params);
		
		_minAttackerLevel = params.getInt("minAttackerLevel", 1);
		_maxAttackerLevel = params.getInt("maxAttackerLevel", 100);
		_minDamage = params.getInt("minDamage", 1);
		_chance = params.getInt("chance", 100);
		_skill = new SkillHolder(params.getInt("skillId"), params.getInt("skillLevel", 1));
		_targetType = params.getEnum("targetType", TriggerTargetType.class, SELF);
		_attackerType = params.getEnum("attackerType", TriggerAttackType.class, NONE);
		_isCritical = params.getBoolean("isCritical", false);
		
		if (params.getString("allowWeapons").equalsIgnoreCase("ALL")) {
			_allowWeapons = 0;
		} else {
			for (String s : params.getString("allowWeapons").split(",")) {
				_allowWeapons |= WeaponType.valueOf(s).mask();
			}
		}
	}
	
	public void onAttackEvent(CreatureDamageDealt event) {
		if ((event.skill() != null) || event.damageOverTime() || event.isReflect() || (_chance == 0) || ((_skill.getSkillId() == 0) || (_skill.getSkillLvl() == 0))) {
			return;
		}
		
		if (((_targetType == SELF) && (_skill.getSkill().getCastRange() > 0)) && (Util.calculateDistance(event.attacker(), event.target(), true, false) > _skill.getSkill().getCastRange())) {
			return;
		}
		
		if (_isCritical != event.critical()) {
			return;
		}
		
		if (event.attacker() == event.target()) {
			return;
		}
		
		if ((event.attacker().getLevel() < _minAttackerLevel) || (event.attacker().getLevel() > _maxAttackerLevel)) {
			return;
		}
		
		if ((event.damage() < _minDamage) || (Rnd.get(100) > _chance) || !_attackerType.check(event.attacker(), event.target())) {
			return;
		}
		
		if (_allowWeapons > 0) {
			if ((event.attacker().getActiveWeaponItem() == null) || ((event.attacker().getActiveWeaponItem().getItemType().mask() & _allowWeapons) == 0)) {
				return;
			}
		}
		
		final var triggerSkill = _skill.getSkill();
		final var targets = _targetType.getTargets(event.attacker(), event.target());
		for (var target : targets) {
			if (!target.isInvul()) {
				event.attacker().makeTriggerCast(triggerSkill, target);
			}
		}
	}
	
	@Override
	public void onExit(BuffInfo info) {
		info.getEffected().removeListenerIf(CREATURE_DAMAGE_DEALT, listener -> listener.getOwner() == this);
	}
	
	@Override
	public void onStart(BuffInfo info) {
		info.getEffected().addListener(new ConsumerEventListener(info.getEffected(), CREATURE_DAMAGE_DEALT, (CreatureDamageDealt event) -> onAttackEvent(event), this));
	}
}
