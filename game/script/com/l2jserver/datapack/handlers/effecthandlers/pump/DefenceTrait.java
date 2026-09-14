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

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.gameserver.model.StatsSet;
import com.l2jserver.gameserver.model.actor.stat.CharStat;
import com.l2jserver.gameserver.model.conditions.Condition;
import com.l2jserver.gameserver.model.effects.AbstractEffect;
import com.l2jserver.gameserver.model.skills.BuffInfo;
import com.l2jserver.gameserver.model.stats.TraitType;

/**
 * Defence Trait effect implementation.
 * @author NosBit
 */
public final class DefenceTrait extends AbstractEffect {
	
	private static final Logger LOG = LoggerFactory.getLogger(DefenceTrait.class);
	
	private final Map<TraitType, Float> _defenceTraits = new HashMap<>();
	
	public DefenceTrait(Condition attachCond, Condition applyCond, StatsSet set, StatsSet params) {
		super(attachCond, applyCond, set, params);
		
		if (params.isEmpty()) {
			LOG.warn("This effect must have parameters!");
			return;
		}
		
		for (var param : params.getSet().entrySet()) {
			try {
				final var traitType = TraitType.valueOf(param.getKey());
				final float value = Float.parseFloat((String) param.getValue());
				if (value == 0) {
					continue;
				}
				_defenceTraits.put(traitType, (value + 100) / 100);
			} catch (NumberFormatException e) {
				LOG.warn("The value of {} must be float value, {} found!", param.getKey(), param.getValue());
			} catch (Exception e) {
				LOG.warn("The value of TraitType enum required but found {}!", param.getValue());
			}
		}
	}
	
	@Override
	public void onExit(BuffInfo info) {
		final var charStat = info.getEffected().getStat();
		synchronized (charStat.getDefenceTraits()) {
			for (var trait : _defenceTraits.entrySet()) {
				if (trait.getValue() < 2.0f) {
					charStat.getDefenceTraits()[trait.getKey().getId()] /= trait.getValue();
					charStat.getDefenceTraitsCount()[trait.getKey().getId()]--;
				} else {
					charStat.getTraitsInvul()[trait.getKey().getId()]--;
				}
			}
		}
	}
	
	@Override
	public void onStart(BuffInfo info) {
		final CharStat charStat = info.getEffected().getStat();
		synchronized (charStat.getDefenceTraits()) {
			for (var trait : _defenceTraits.entrySet()) {
				if (trait.getValue() < 2.0f) {
					charStat.getDefenceTraits()[trait.getKey().getId()] *= trait.getValue();
					charStat.getDefenceTraitsCount()[trait.getKey().getId()]++;
				} else {
					charStat.getTraitsInvul()[trait.getKey().getId()]++;
				}
			}
		}
	}
}
