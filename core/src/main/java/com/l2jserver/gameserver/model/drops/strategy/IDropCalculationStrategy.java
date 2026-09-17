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

import static com.l2jserver.gameserver.config.Configuration.general;

import java.util.List;

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.datatables.ItemTable;
import com.l2jserver.gameserver.model.actor.L2Character;
import com.l2jserver.gameserver.model.drops.GeneralDropItem;
import com.l2jserver.gameserver.model.holders.ItemHolder;

/**
 * @author Battlecruiser
 */
public interface IDropCalculationStrategy {
	IDropCalculationStrategy DEFAULT_STRATEGY = (item, victim, killer) -> {
		double chance = item.getChance(victim, killer);
		if (chance > (Rnd.nextDouble() * 100)) {
			final int amountMultiply = item.isPreciseCalculated() ? amountMultiplier(chance, piles(item.getItemId()), Rnd.nextDouble() * 100) : 1;
			
			return List.of(new ItemHolder(item.getItemId(), Rnd.get(item.getMin(victim), item.getMax(victim)) * amountMultiply));
		}
		
		return null;
	};
	
	/**
	 * How many times over the drop is given once its chance passed 100%, as it does on a server with high rates.
	 * @param chance the chance in percent, rates included
	 * @param piles whether this item may drop in a bigger pile
	 * @param roll a number from 0 to 100 deciding the last, incomplete multiple
	 * @return what the dropped amount is multiplied by, never less than one
	 */
	static int amountMultiplier(double chance, boolean piles, double roll) {
		if (!piles || (chance <= 100)) {
			return 1;
		}
		
		int amountMultiply = (int) chance / 100;
		if ((chance % 100) > roll) {
			amountMultiply++;
		}
		return amountMultiply;
	}
	
	/**
	 * A chance over 100% means a bigger pile of adena or materials, but never a second sword: a monster that
	 * drops a weapon drops one of it, whatever the rates are.
	 * @param itemId the item that dropped
	 * @return <code>true</code> when the amount may be multiplied
	 */
	static boolean piles(int itemId) {
		return !general().preciseDropMultipliesStackableOnly() || ItemTable.getInstance().getTemplate(itemId).isStackable();
	}
	
	List<ItemHolder> calculateDrops(GeneralDropItem item, L2Character victim, L2Character killer);
}
