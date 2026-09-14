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
package com.l2jserver.datapack.ai.npc.coach.WarehouseKeeper;

import com.l2jserver.datapack.ai.npc.coach.FighterCoach.FighterCoach;
import com.l2jserver.gameserver.enums.CategoryType;
import com.l2jserver.gameserver.enums.Race;

/**
 * Warehouse Keeper.
 * @author Zoey76
 * @version 2.6.3.0
 */
public class WarehouseKeeper extends FighterCoach {
	
	// @formatter:off
	private static final int[] COACHES = {
		30005, 30054, 30055, 30057, 30058, 30083, 30086, 30092, 30095, 30103, 
		30104, 30139, 30140, 30151, 30152, 30153, 30169, 30170, 30182, 30183, 
		30210, 30232, 30255, 30316, 30322, 30350, 30521, 30522, 30562, 30563, 
		30686, 30843, 30844, 30895, 30896, 31267, 31268, 31270, 31311, 31312, 
		31313, 31315, 31956, 31957, 31959, 32170, 32172, 32890, 4315
	};
	// @formatter:on
	
	private static final Condition[] CONDITIONS = new Condition[] {
		new Condition(Race.DWARF, CategoryType.DWARF_BOUNTY_CLASS)
	};
	
	public WarehouseKeeper() {
		super(WarehouseKeeper.class.getSimpleName(), COACHES, CONDITIONS);
	}
	
	public WarehouseKeeper(String name, int[] coaches) {
		super(name, coaches, CONDITIONS);
	}
}
