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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.datatables.ItemTable;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.drops.GeneralDropItem;
import com.l2jserver.gameserver.model.items.L2Item;

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

	@Mock
	private ItemTable itemTable;

	@Mock
	private L2Item template;

	@Test
	void testDefaultStrategy() {
		when(item.getItemId()).thenReturn(ITEM_ID);
		when(item.getChance(victim, killer)).thenReturn(150d);
		when(item.isPreciseCalculated()).thenReturn(true);
		try (var rnd = mockStatic(Rnd.class); var items = itemsThatAre(true)) {
			rnd.when(Rnd::nextDouble).thenReturn(0.1d);
			rnd.when(() -> Rnd.get(anyLong(), anyLong())).thenReturn(1L);
			final var drops = IDropCalculationStrategy.DEFAULT_STRATEGY.calculateDrops(item, victim, killer);
			assertEquals(ITEM_ID, drops.get(0).getId());
			assertEquals(2, drops.get(0).getCount());
		}
	}

	@Test
	void testAWeaponDropsOnceWhateverTheRates() {
		when(item.getItemId()).thenReturn(ITEM_ID);
		when(item.getChance(victim, killer)).thenReturn(880d);
		when(item.isPreciseCalculated()).thenReturn(true);
		try (var rnd = mockStatic(Rnd.class); var items = itemsThatAre(false)) {
			rnd.when(Rnd::nextDouble).thenReturn(0.1d);
			rnd.when(() -> Rnd.get(anyLong(), anyLong())).thenReturn(1L);
			final var drops = IDropCalculationStrategy.DEFAULT_STRATEGY.calculateDrops(item, victim, killer);
			assertEquals(ITEM_ID, drops.get(0).getId());
			assertEquals(1, drops.get(0).getCount());
		}
	}

	@Test
	void testAmountMultiplier() {
		// below 100% nothing piles up
		assertEquals(1, IDropCalculationStrategy.amountMultiplier(20, true, 0));
		assertEquals(1, IDropCalculationStrategy.amountMultiplier(100, true, 0));
		// a drop of 20% at x44 gives eight piles, and a ninth four times out of five
		assertEquals(9, IDropCalculationStrategy.amountMultiplier(880, true, 50));
		assertEquals(8, IDropCalculationStrategy.amountMultiplier(880, true, 90));
		// what does not stack drops once
		assertEquals(1, IDropCalculationStrategy.amountMultiplier(880, false, 50));
	}

	/** The item table seen by the drop calculation, with every item stackable or none of them. */
	private MockedStatic<ItemTable> itemsThatAre(boolean stackable) {
		final var items = mockStatic(ItemTable.class);
		items.when(ItemTable::getInstance).thenReturn(itemTable);
		when(itemTable.getTemplate(anyInt())).thenReturn(template);
		when(template.isStackable()).thenReturn(stackable);
		return items;
	}
}
