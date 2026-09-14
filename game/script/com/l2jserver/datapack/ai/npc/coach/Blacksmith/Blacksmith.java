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
package com.l2jserver.datapack.ai.npc.coach.Blacksmith;

import com.l2jserver.datapack.ai.npc.coach.FighterCoach.FighterCoach;

/**
 * Blacksmith.
 * @author Zoey76
 * @version 2.6.3.0
 */
public class Blacksmith extends FighterCoach {
	
	// @formatter:off
	private static final int[] COACHES = {
		30283, 30298, 30300, 30307, 30317, 30363, 30458, 30471, 30526, 30527, 30564, 
		30678, 30688, 30846, 30898, 31271, 31316, 31583, 31960, 31990, 32159
	};
	// @formatter:on
	
	public Blacksmith() {
		super(Blacksmith.class.getSimpleName(), COACHES);
	}
}
