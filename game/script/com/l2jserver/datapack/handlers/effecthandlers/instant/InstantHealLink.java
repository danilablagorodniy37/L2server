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
package com.l2jserver.datapack.handlers.effecthandlers.instant;

import static com.l2jserver.gameserver.enums.EffectCalculationType.PER;
import static com.l2jserver.gameserver.network.SystemMessageId.S1_HP_HAS_BEEN_RESTORED;
import static com.l2jserver.gameserver.network.SystemMessageId.S2_HP_HAS_BEEN_RESTORED_BY_C1;

import com.l2jserver.gameserver.enums.EffectCalculationType;
import com.l2jserver.gameserver.model.StatsSet;
import com.l2jserver.gameserver.model.conditions.Condition;
import com.l2jserver.gameserver.model.effects.AbstractEffect;
import com.l2jserver.gameserver.model.skills.BuffInfo;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;

/**
 * Instant Heal Link.
 * @author Zoey76
 * @version 2.6.3.0
 */
public final class InstantHealLink extends AbstractEffect {
	
	private final double amount;
	private final EffectCalculationType mode;
	private final double step;
	
	public InstantHealLink(Condition attachCond, Condition applyCond, StatsSet set, StatsSet params) {
		super(attachCond, applyCond, set, params);
		amount = params.getDouble("amount", 0);
		mode = params.getEnum("mode", EffectCalculationType.class, PER);
		step = params.getDouble("step", 0);
	}
	
	@Override
	public boolean isInstant() {
		return true;
	}
	
	@Override
	public void onStart(BuffInfo info) {
		final var target = info.getEffected();
		if ((target == null) || target.isDead() || target.isDoor() || target.isInvul() || target.isHpBlocked()) {
			return;
		}
		
		final double hp = switch (mode) {
			case DIFF -> Math.max(Math.min(amount - step * info.getIndex(), target.getMaxRecoverableHp() - target.getCurrentHp()), 0);
			case PER -> Math.max(Math.min(target.getMaxHp() * (amount - step * info.getIndex()) / 100.0, target.getMaxRecoverableHp() - target.getCurrentHp()), 0);
		};
		
		target.setCurrentHp(hp + target.getCurrentHp());
		sendMessage(info, hp);
	}
	
	private void sendMessage(BuffInfo info, double amount) {
		final SystemMessage sm;
		if (info.getEffector().getObjectId() != info.getEffected().getObjectId()) {
			sm = SystemMessage.getSystemMessage(S2_HP_HAS_BEEN_RESTORED_BY_C1);
			sm.addCharName(info.getEffector());
		} else {
			sm = SystemMessage.getSystemMessage(S1_HP_HAS_BEEN_RESTORED);
		}
		sm.addInt((int) amount);
		info.getEffected().sendPacket(sm);
	}
}
