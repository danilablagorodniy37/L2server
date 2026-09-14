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
package com.l2jserver.datapack.ai.npc.coach.FighterCoach;

import com.l2jserver.datapack.ai.npc.coach.GuildCoach;
import com.l2jserver.gameserver.enums.CategoryType;
import com.l2jserver.gameserver.enums.Race;

/**
 * Fighter Coach.
 * @author Zoey76
 * @version 2.6.3.0
 */
public class FighterCoach extends GuildCoach {
	
	// @formatter:off
	private static final int[] COACHES = {
		30010, 30014, 30027, 30028, 30029, 30064, 30065, 30105, 30106, 30107, 30108,
		30143, 30155, 30156, 30184, 30185, 30186, 30192, 30325, 30326, 30327, 30328, 
		30329, 30360, 30369, 30374, 30378, 30459, 30460, 30463, 30472, 30475, 30501, 
		30506, 30509, 30514, 30569, 30570, 30679, 30683, 30690, 30691, 30692, 30693, 
		30700, 30705, 30850, 30851, 30852, 30853, 30863, 30866, 30901, 30902, 30903, 
		30904, 30911, 30914, 31277, 31278, 31286, 31289, 31322, 31323, 31325, 31327, 
		31580, 31582, 31966, 31967, 31975, 31978, 32148, 32151, 32156, 32161
	};
	// @formatter:on
	
	private static final Condition[] CONDITIONS = new Condition[] {
		new Condition(Race.HUMAN, CategoryType.HUMAN_FALL_CLASS),
		new Condition(Race.ELF, CategoryType.ELF_FALL_CLASS),
		new Condition(Race.DARK_ELF, CategoryType.DELF_FALL_CLASS),
		new Condition(Race.ORC, CategoryType.ORC_FALL_CLASS),
		new Condition(Race.DWARF, CategoryType.DWARF_SMITH_CLASS)
	};
	
	public FighterCoach() {
		super(FighterCoach.class.getSimpleName(), COACHES, CONDITIONS);
	}
	
	public FighterCoach(String name, int[] coaches, Condition[] conditions) {
		super(name, coaches, conditions);
	}
	
	public FighterCoach(String name, int[] coaches) {
		super(name, coaches, CONDITIONS);
	}
}
