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
import com.l2jserver.gameserver.model.itemcontainer.Inventory;
import com.l2jserver.gameserver.model.items.instance.L2ItemInstance;
import com.l2jserver.gameserver.model.TradeList;
import com.l2jserver.gameserver.network.serverpackets.PrivateStoreMsgBuy;
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
	/** With this many slots taken the bot has hunted enough and walks back to sell. */
	public static final int BAG_LIMIT = 60;
	/** How many kinds of goods a buying bot asks for. */
	private static final int BUY_SIZE = 3;
	/** What a buyer offers: a share of what the shops charge. */
	private static final double BUY_RATE = 0.4;
	/** Spending money a buying bot keeps on hand. */
	private static final long PURSE = 20000000;
	/** Titles of the buying stores. */
	private static final String[] BUYING = {
		"buying %s", "wtb %s", "buying gear", "wtb any grade"
	};
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

	/**
	 * Opens a buying store: the bot offers adena for gear, so a player has somewhere to sell what drops.
	 * @param bot the shop keeper
	 * @return true when the store is open
	 */
	public boolean openBuyShop(L2PcInstance bot) {
		if (_goods.isEmpty()) {
			return false;
		}
		final TradeList list = bot.getBuyList();
		list.clear();
		String first = null;
		long total = 0;
		for (int i = 0; i < BUY_SIZE; i++) {
			final Good good = _goods.get(Rnd.get(_goods.size()));
			final long price = Math.max(1, (long) (good.price() * BUY_RATE));
			list.addItemByItemId(good.itemId(), 1, price);
			total += price;
			if (first == null) {
				first = good.name();
			}
		}
		if (list.getItemCount() == 0) {
			return false;
		}
		// a store nobody can pay from closes itself, so the purse is filled first
		if (bot.getAdena() < (total + PURSE)) {
			bot.addAdena("PhantomBuy", (total + PURSE) - bot.getAdena(), null, false);
		}
		list.setTitle(String.format(BUYING[Rnd.get(BUYING.length)], first).trim());
		bot.setPrivateStoreType(PrivateStoreType.BUY);
		bot.sitDown();
		bot.broadcastUserInfo();
		Broadcast.toSelfAndKnownPlayers(bot, new PrivateStoreMsgBuy(bot));
		return true;
	}

	/**
	 * Puts fresh goods in a store that has been standing for a while or has sold out.
	 * @param bot the shop keeper
	 * @param buying true when this one buys instead of selling
	 * @return true when the store is open again
	 */
	public boolean restock(L2PcInstance bot, boolean buying) {
		bot.setPrivateStoreType(PrivateStoreType.NONE);
		if (buying) {
			return openBuyShop(bot);
		}
		bot.getSellList().clear();
		return openShop(bot);
	}

	/**
	 * The bot sells its loot in town: everything it is not wearing goes for half the shop price.
	 * @param bot the bot back from hunting
	 * @return the adena it made
	 */
	public static long sellLoot(L2PcInstance bot) {
		long paid = 0;
		for (L2ItemInstance item : bot.getInventory().getItems()) {
			if (item.isEquipped() || (item.getId() == Inventory.ADENA_ID) || !item.isSellable() || item.isQuestItem()) {
				continue;
			}
			final long price = (item.getReferencePrice() / 2) * item.getCount();
			if (bot.getInventory().destroyItem("PhantomSell", item, bot, null) == null) {
				continue;
			}
			paid += price;
		}
		if (paid > 0) {
			bot.addAdena("PhantomSell", paid, null, false);
		}
		return paid;
	}

	/** True when the bot carries as much as it can and should head back to town. */
	public static boolean bagFull(L2PcInstance bot) {
		return bot.getInventory().getSize() >= BAG_LIMIT;
	}

	@Override
	public String toString() {
		return _goods.size() + " goods";
	}
}
