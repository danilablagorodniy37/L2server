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
package com.l2jserver.datapack.ai.npc.coach.KamaelCoach;

import com.l2jserver.datapack.ai.npc.coach.GuildCoach;
import com.l2jserver.gameserver.enums.CategoryType;
import com.l2jserver.gameserver.enums.Race;

/**
 * Kamael Coach.
 * @author Zoey76
 * @version 2.6.3.0
 */
public final class KamaelCoach extends GuildCoach {
	
	// @formatter:off
	private static final int[] COACHES = {
		32141, 32142, 32143, 32144, 32182, 32183, 32194, 32195, 32197, 32198,
		32200, 32201, 32203, 32204, 32207, 32208, 32211, 32212, 32215, 32216,
		32219, 32220, 32223, 32224, 32227, 32228, 32231, 32232
	};
	// @formatter:on
	
	private static final Condition[] CONDITIONS = new Condition[] {
		new Condition(Race.KAMAEL, CategoryType.KAMAEL_MALE_MAIN_OCCUPATION, MALE),
		new Condition(Race.KAMAEL, CategoryType.KAMAEL_FEMALE_MAIN_OCCUPATION, FEMALE)
	};
	
	public KamaelCoach() {
		super(KamaelCoach.class.getSimpleName(), COACHES, CONDITIONS);
	}
}
