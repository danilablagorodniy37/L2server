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

import static com.l2jserver.gameserver.enums.TriggerTargetType.SELF;
import static com.l2jserver.gameserver.model.events.EventType.CREATURE_ATTACK_AVOID;

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.enums.TriggerTargetType;
import com.l2jserver.gameserver.model.StatsSet;
import com.l2jserver.gameserver.model.conditions.Condition;
import com.l2jserver.gameserver.model.effects.AbstractEffect;
import com.l2jserver.gameserver.model.events.impl.character.CreatureAttackAvoid;
import com.l2jserver.gameserver.model.events.listeners.ConsumerEventListener;
import com.l2jserver.gameserver.model.holders.SkillHolder;
import com.l2jserver.gameserver.model.skills.BuffInfo;
import com.l2jserver.gameserver.util.Util;

/**
 * Trigger Skill By Avoid effect implementation.
 * @author Zealar
 * @author Zoey76
 */
public final class TriggerSkillByAvoid extends AbstractEffect {
	private final int _chance;
	private final SkillHolder _skill;
	private final TriggerTargetType _targetType;
	
	public TriggerSkillByAvoid(Condition attachCond, Condition applyCond, StatsSet set, StatsSet params) {
		super(attachCond, applyCond, set, params);
		
		_chance = params.getInt("chance", 100);
		_skill = new SkillHolder(params.getInt("skillId", 0), params.getInt("skillLevel", 0));
		_targetType = params.getEnum("targetType", TriggerTargetType.class, SELF);
	}
	
	public void onAvoidEvent(CreatureAttackAvoid event) {
		if (event.damageOverTime() || (_chance == 0) || ((_skill.getSkillId() == 0) || (_skill.getSkillLvl() == 0))) {
			return;
		}
		
		if (((_targetType == SELF) && (_skill.getSkill().getCastRange() > 0)) && (Util.calculateDistance(event.attacker(), event.target(), true, false) > _skill.getSkill().getCastRange())) {
			return;
		}
		
		if (Rnd.get(100) > _chance) {
			return;
		}
		
		final var triggerSkill = _skill.getSkill();
		final var targets = _targetType.getTargets(event.target(), event.attacker());
		for (var target : targets) {
			if (!target.isInvul()) {
				event.target().makeTriggerCast(triggerSkill, target);
			}
		}
	}
	
	@Override
	public void onExit(BuffInfo info) {
		info.getEffected().removeListenerIf(CREATURE_ATTACK_AVOID, listener -> listener.getOwner() == this);
	}
	
	@Override
	public void onStart(BuffInfo info) {
		info.getEffected().addListener(new ConsumerEventListener(info.getEffected(), CREATURE_ATTACK_AVOID, (CreatureAttackAvoid event) -> onAvoidEvent(event), this));
	}
}
