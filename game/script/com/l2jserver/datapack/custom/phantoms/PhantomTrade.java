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
package com.l2jserver.datapack.custom.phantoms;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.enums.PrivateStoreType;
import com.l2jserver.gameserver.model.TradeItem;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.items.instance.L2ItemInstance;
import com.l2jserver.gameserver.network.serverpackets.PrivateStoreMsgSell;
import com.l2jserver.gameserver.util.Broadcast;

/**
 * The bot market: a bot sits in Giran with a private store of Interlude gear.<br>
 * The goods come from data/phantoms/trade.txt (tools/interlude/build_phantom_trade.py).
 */
public class PhantomTrade {
	/** One line of the shop: an item and what the bot wants for it. */
	public record Good(int itemId, long price, String name) {
	}

	/** How many lines one shop offers. */
	private static final int SHOP_SIZE = 4;
	/** Shop titles, so the row of stores does not read the same. */
	private static final String[] TITLES = {
		"cheap gear", "buy now", "good price", "%s", "selling %s", "%s cheap", "best price in town"
	};

	private final List<Good> _goods = new ArrayList<>();

	public PhantomTrade(Path file) throws IOException {
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			final String text = line.trim();
			if (text.isEmpty() || text.startsWith("#")) {
				continue;
			}
			final String[] p = text.split("\\s+", 3);
			if (p.length == 3) {
				_goods.add(new Good(Integer.parseInt(p[0]), Long.parseLong(p[1]), p[2]));
			}
		}
	}

	public int size() {
		return _goods.size();
	}

	/**
	 * Stocks a bot and opens its store.
	 * @param bot the shop keeper
	 * @return true when the store is open
	 */
	public boolean openShop(L2PcInstance bot) {
		if (_goods.isEmpty()) {
			return false;
		}
		String first = null;
		for (int i = 0; i < SHOP_SIZE; i++) {
			final Good good = _goods.get(Rnd.get(_goods.size()));
			final L2ItemInstance item = bot.getInventory().addItem("PhantomShop", good.itemId(), 1, bot, null);
			if (item == null) {
				continue;
			}
			final TradeItem line = bot.getSellList().addItem(item.getObjectId(), 1);
			if (line == null) {
				continue;
			}
			// a bot asks a little more or a little less than the next one
			line.setPrice((long) (good.price() * (0.9 + (Rnd.get(40) / 100.0))));
			if (first == null) {
				first = good.name();
			}
		}
		if (bot.getSellList().getItemCount() == 0) {
			return false;
		}
		bot.getSellList().setTitle(String.format(TITLES[Rnd.get(TITLES.length)], first).trim());
		bot.setPrivateStoreType(PrivateStoreType.SELL);
		bot.sitDown();
		bot.broadcastUserInfo();
		Broadcast.toSelfAndKnownPlayers(bot, new PrivateStoreMsgSell(bot));
		return true;
	}

	@Override
	public String toString() {
		return _goods.size() + " goods";
	}
}
