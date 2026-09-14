/*
 * Copyright © 2004-2025 L2J Server
 * 
 * This file is part of L2J Server.
 * 
 * L2J Server is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jserver.gameserver.model.drops.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.drops.GeneralDropItem;

/**
 * IDropCalculationStrategy test.
 * @author Zoey76
 * @version 2.6.3.0
 */
@ExtendWith(MockitoExtension.class)
class IDropCalculationStrategyTest {
	
	private static final int ITEM_ID = 51424;
	
	@Mock
	private GeneralDropItem item;
	
	@Mock
	private L2Character victim;
	
	@Mock
	private L2Character killer;
	
	@Test
	void testDefaultStrategy() {
		when(item.getItemId()).thenReturn(ITEM_ID);
		when(item.getChance(victim, killer)).thenReturn(150d);
		when(item.isPreciseCalculated()).thenReturn(true);
		try (var rnd = mockStatic(Rnd.class)) {
			rnd.when(Rnd::nextDouble).thenReturn(0.1d);
			rnd.when(() -> Rnd.get(anyLong(), anyLong())).thenReturn(1L);
			final var drops = IDropCalculationStrategy.DEFAULT_STRATEGY.calculateDrops(item, victim, killer);
			assertEquals(ITEM_ID, drops.get(0).getId());
			assertEquals(2, drops.get(0).getCount());
		}
	}
}
