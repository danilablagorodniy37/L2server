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
package com.l2jserver.datapack.ai.npc.coach.MageCoach;

import com.l2jserver.datapack.ai.npc.coach.SkillTransferCoach;
import com.l2jserver.gameserver.enums.CategoryType;
import com.l2jserver.gameserver.enums.Race;

/**
 * Mage Coach.
 * @author Zoey76
 * @version 2.6.3.0
 */
public class MageCoach extends SkillTransferCoach {
	
	// @formatter:off
	private static final int[] COACHES = {
		30144, 30145, 30158, 30194, 30330, 30377, 30464, 30476, 30502, 30507, 
		30510, 30515, 30571, 30572, 30682, 30701, 30706, 30864, 30867, 30912, 
		30915, 31287, 31290, 31335, 31337, 31581, 31976, 31979, 32152, 32162
	};
	// @formatter:on
	
	private static final Condition[] CONDITIONS = new Condition[] {
		new Condition(Race.HUMAN, CategoryType.HUMAN_MALL_CLASS),
		new Condition(Race.ELF, CategoryType.ELF_MALL_CLASS),
		new Condition(Race.DARK_ELF, CategoryType.DELF_MALL_CLASS),
		new Condition(Race.ORC, CategoryType.ORC_MALL_CLASS)
	};
	
	public MageCoach() {
		super(MageCoach.class.getSimpleName(), COACHES, CONDITIONS);
	}
}
