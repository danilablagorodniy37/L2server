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
package com.l2jserver.datapack.ai.npc.coach.WizardCoach;

import com.l2jserver.datapack.ai.npc.coach.GuildCoach;
import com.l2jserver.gameserver.enums.CategoryType;
import com.l2jserver.gameserver.enums.Race;

/**
 * Wizard Coach.
 * @author Zoey76
 * @version 2.6.3.0
 */
public class WizardCoach extends GuildCoach {
	
	// @formatter:off
	private static final int[] COACHES = {
		30033, 30034, 30035, 30069, 30110, 30111, 30112, 30113, 30114, 30157, 
		30171, 30189, 30190, 30344, 30345, 30376, 30461, 30695, 30696, 30697, 
		30698, 30715, 30717, 30718, 30720, 30721, 30833, 30835, 30855, 30856, 
		30907, 30909, 31282, 31283, 31332, 31333, 31971, 31972, 32149
	};
	// @formatter:on
	
	private static final Condition[] CONDITIONS = new Condition[] {
		new Condition(Race.HUMAN, CategoryType.HUMAN_WALL_CLASS),
		new Condition(Race.ELF, CategoryType.ELF_WALL_CLASS),
		new Condition(Race.DARK_ELF, CategoryType.DELF_WALL_CLASS)
	};
	
	public WizardCoach() {
		super(WizardCoach.class.getSimpleName(), COACHES, CONDITIONS);
	}
}
