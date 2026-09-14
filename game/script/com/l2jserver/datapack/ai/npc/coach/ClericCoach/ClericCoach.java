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
package com.l2jserver.datapack.ai.npc.coach.ClericCoach;

import com.l2jserver.datapack.ai.npc.coach.SkillTransferCoach;
import com.l2jserver.gameserver.enums.CategoryType;
import com.l2jserver.gameserver.enums.Race;

/**
 * Cleric Coach.
 * @author Zoey76
 */
public final class ClericCoach extends SkillTransferCoach {
	
	// @formatter:off
	private static final int[] COACHES = {
		30022, 30030, 30032, 30036, 30067, 30068, 30116, 30117, 30118, 30119, 
		30188, 30293, 30375, 30473, 30680, 30858, 30859, 30860, 30861, 30906, 
		30908, 31280, 31281, 31329, 31330, 31969, 31970, 32155
	};
	// @formatter:on
	
	private static final Condition[] CONDITIONS = new Condition[] {
		new Condition(Race.HUMAN, CategoryType.HUMAN_CALL_CLASS),
		new Condition(Race.ELF, CategoryType.ELF_CALL_CLASS),
		new Condition(Race.DARK_ELF, CategoryType.DELF_CALL_CLASS)
	};
	
	public ClericCoach() {
		super(ClericCoach.class.getSimpleName(), COACHES, CONDITIONS);
	}
}
