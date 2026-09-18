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

import static com.l2jserver.gameserver.config.Configuration.server;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.handler.ChatHandler;
import com.l2jserver.gameserver.instancemanager.MapRegionManager;
import com.l2jserver.gameserver.ai.CtrlIntention;
import com.l2jserver.gameserver.model.L2Object;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.Location;
import com.l2jserver.gameserver.model.actor.instance.L2MonsterInstance;
import com.l2jserver.gameserver.model.items.instance.L2ItemInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.skills.Skill;
import com.l2jserver.gameserver.model.events.Containers;
import com.l2jserver.gameserver.model.events.EventType;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerChat;
import com.l2jserver.gameserver.model.events.listeners.ConsumerEventListener;
import com.l2jserver.gameserver.network.L2GameClient;
import com.l2jserver.gameserver.network.clientpackets.Say2;

/**
 * Phantom players: characters without a client that live in the big towns, walk
 * around and chat about this server. Switched on in config/phantoms.properties.
 */
public class Phantoms {
	private static final Logger LOG = LoggerFactory.getLogger(Phantoms.class);
	/** The running manager, so the chat handlers can tell a bot from a player. */
	private static Phantoms _instance;
	/** Bot lines go to the usual chat log, so they can be read in game/logs/chat. */
	private static final Logger LOG_CHAT = LoggerFactory.getLogger("chat");

	private static final int DIALOG_RANGE = 700;
	private static final int REPLY_RANGE = 900;
	/** How far a bot notices what happens: deaths, level ups, someone arriving. */
	private static final int SIGHT_RANGE = 1600;
	private static final long SEEN_MEMORY = 5 * 60 * 1000L;
	private static final long NEWS_MEMORY = 20 * 60 * 1000L;

	private final List<Phantom> _phantoms = new ArrayList<>();
	private final Set<Integer> _objectIds = new HashSet<>();
	private final Map<Integer, Long> _regionChat = new HashMap<>();
	private final Map<Integer, Long> _regionChain = new HashMap<>();

	private final Properties _config = new Properties();
	private PhraseBook _phrases;
	private PhantomNews _news;
	private PhantomFacts _facts;
	private PhantomFactory _factory;
	private PhantomHunting _hunting;
	private PhantomTrade _trade;
	private String[] _towns;
	private int _chatInterval;
	private int _regionChatInterval;
	private int _radius;
	private boolean _replyToPlayers;
	private boolean _debugChat;
	private int _huntShare;
	private int _huntMinutes;
	private int _traders;
	private String _tradeTown;

	public Phantoms() {
		final File root = server().getDatapackRoot();
		try (var in = new FileInputStream(new File(root, "config/phantoms.properties"))) {
			_config.load(in);
		} catch (Exception ex) {
			LOG.info("config/phantoms.properties not found, bots are off.");
			return;
		}

		if (!Boolean.parseBoolean(_config.getProperty("Enabled", "False").trim())) {
			return;
		}

		try {
			final Path data = new File(root, "data/phantoms").toPath();
			_phrases = new PhraseBook(data.resolve("phrases.txt"));
			_factory = new PhantomFactory(data.resolve("gear.txt"), property("Account", "phantoms"), number("MinLevel", 20), number("MaxLevel", 80));
			_hunting = new PhantomHunting(data.resolve("hunting.txt"));
			_facts = new PhantomFacts(data.resolve("hunting.txt"), _factory.gear());
			_trade = new PhantomTrade(data.resolve("trade.txt"));
		} catch (Exception ex) {
			LOG.warn("could not read data/phantoms!", ex);
			return;
		}

		_towns = property("Towns", "giran_castle_town").split(",");
		_chatInterval = number("ChatInterval", 180);
		_regionChatInterval = number("RegionChatInterval", 40);
		_radius = number("WanderRadius", 1500);
		_replyToPlayers = Boolean.parseBoolean(_config.getProperty("ReplyToPlayers", "True").trim());
		// Share of the bots that leave town to hunt, and how long one trip lasts.
		_huntShare = number("HuntShare", 0);
		_huntMinutes = number("HuntMinutes", 20);
		// Bots that keep a private store instead of wandering, and the town they trade in.
		_traders = number("Traders", 0);
		_tradeTown = property("TradeTown", "giran_castle_town");
		_debugChat = Boolean.parseBoolean(_config.getProperty("DebugChat", "False").trim());

		final int count = number("Count", 100);
		ThreadPoolManager.getInstance().scheduleGeneral(() -> spawnAll(count), number("StartDelay", 30), TimeUnit.SECONDS);
	}

	private String property(String key, String fallback) {
		return _config.getProperty(key, fallback).trim();
	}

	private int number(String key, int fallback) {
		try {
			return Integer.parseInt(property(key, String.valueOf(fallback)));
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private void spawnAll(int count) {
		_factory.blockAccount();
		final List<Integer> stored = _factory.storedCharacters();
		int created = 0;
		for (int i = 0; i < count; i++) {
			L2PcInstance player = null;
			if (i < stored.size()) {
				player = L2PcInstance.load(stored.get(i));
			} else {
				player = _factory.create();
				created++;
			}
			if (player == null) {
				continue;
			}
			final boolean trader = i < _traders;
			final Location home = townSquare(trader ? _tradeTown : _towns[i % _towns.length]);
			if (home == null) {
				continue;
			}
			enterWorld(player, home);
			final Phantom phantom = new Phantom(player, home, _radius);
			if (trader && _trade.openShop(player)) {
				phantom.enter(Phantom.State.TRADE, null, Long.MAX_VALUE / 2);
			}
			// Spread the first lines over one chat interval instead of all at once.
			phantom.chatDone(System.currentTimeMillis(), _chatInterval);
			_phantoms.add(phantom);
			_objectIds.add(player.getObjectId());
		}

		if (_phantoms.isEmpty()) {
			LOG.warn("no bots could be spawned.");
			return;
		}

		_news = new PhantomNews(this, this::onNews);
		_instance = this;
		ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(this::tick, 5, 2, TimeUnit.SECONDS);
		if (_huntShare > 0) {
			ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(this::huntTick, 20, 1, TimeUnit.SECONDS);
		}
		if (_replyToPlayers) {
			Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.PLAYER_CHAT, (PlayerChat event) -> onPlayerChat(event), this));
		}
		final long traders = _phantoms.stream().filter(p -> p.state() == Phantom.State.TRADE).count();
		LOG.info("{} bots in the world ({} created), {} of them trading in {}, {} phrases.", _phantoms.size(), created, traders, _tradeTown, _phrases.size());
		if (Boolean.parseBoolean(_config.getProperty("DebugNews", "False").trim())) {
			ThreadPoolManager.getInstance().scheduleGeneral(this::injectDebugNews, 25, TimeUnit.SECONDS);
		}
		ThreadPoolManager.getInstance().scheduleGeneral(() -> {
			final long moved = _phantoms.stream().filter(Phantom::hasMoved).count();
			LOG.info("{} of {} bots are walking around.", moved, _phantoms.size());
		}, 90, TimeUnit.SECONDS);
		if (_huntShare > 0) {
			ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(this::report, 5, 5, TimeUnit.MINUTES);
		}
	}

	private Location townSquare(String region) {
		final var mapRegion = MapRegionManager.getInstance().getMapRegionByName(region.trim());
		if ((mapRegion == null) || mapRegion.getSpawns().isEmpty()) {
			LOG.warn("unknown town {}!", region);
			return null;
		}
		return mapRegion.getSpawns().get(Rnd.get(mapRegion.getSpawns().size()));
	}

	private void enterWorld(L2PcInstance player, Location home) {
		final L2GameClient client = new L2GameClient(null);
		client.setDetached(true);
		client.setActiveChar(player);
		player.setClient(client);
		player.setOnlineStatus(true, false);
		player.setXYZInvisible(home.getX() + Rnd.get(-200, 200), home.getY() + Rnd.get(-200, 200), home.getZ());
		L2World.getInstance().addPlayerToWorld(player);
		player.spawnMe(player.getX(), player.getY(), player.getZ());
		player.setRunning();
		player.broadcastUserInfo();
	}

	private void tick() {
		final long now = System.currentTimeMillis();
		for (Phantom phantom : _phantoms) {
			try {
				phantom.act(now);
				if (phantom.chatDue(now)) {
					phantom.chatDone(now, _chatInterval);
					speak(phantom, now);
				}
			} catch (Exception ex) {
				LOG.warn("{} failed to act!", phantom.player().getName(), ex);
			}
		}
	}

	/** What the bots are up to, so the log shows whether hunting works. */
	private void report() {
		final Map<Phantom.State, Integer> states = new HashMap<>();
		int kills = 0;
		int deaths = 0;
		long earned = 0;
		for (Phantom phantom : _phantoms) {
			states.merge(phantom.state(), 1, Integer::sum);
			kills += phantom.kills();
			deaths += phantom.deaths();
			earned += phantom.earned();
		}
		LOG.info("bots: {} in town, {} travelling, {} hunting, {} coming back, {} trading; {} kills, {} deaths and {} adena of loot sold.",
			states.getOrDefault(Phantom.State.TOWN, 0), states.getOrDefault(Phantom.State.TRAVEL, 0),
			states.getOrDefault(Phantom.State.HUNT, 0), states.getOrDefault(Phantom.State.RETURN, 0),
			states.getOrDefault(Phantom.State.TRADE, 0), kills, deaths, earned);
	}

	/** Drives the bots that are out hunting: leaving town, travelling, coming back. */
	private void huntTick() {
		final long now = System.currentTimeMillis();
		for (Phantom phantom : _phantoms) {
			try {
				if (phantom.state() == Phantom.State.TRADE) {
					continue;
				}
				if (phantom.player().isDead()) {
					revive(phantom, now);
					continue;
				}
				if (phantom.state() == Phantom.State.HUNT) {
					fight(phantom, now);
				}
				if (!phantom.stateOver(now)) {
					continue;
				}
				switch (phantom.state()) {
					case TOWN -> leaveForHunt(phantom);
					case TRAVEL -> arrive(phantom);
					case HUNT -> goHome(phantom);
					case RETURN -> arriveHome(phantom);
				}
			} catch (Exception ex) {
				LOG.warn("{} failed to hunt!", phantom.player().getName(), ex);
			}
		}
	}

	/** One step of a fight: rest, pick a target, hit it, use a skill. */
	private void fight(Phantom phantom, long now) {
		final L2PcInstance bot = phantom.player();
		if (PhantomCombat.shouldFlee(bot)) {
			// too badly hurt to stay out here
			goHome(phantom);
			return;
		}
		if (PhantomTrade.bagFull(bot)) {
			// nothing more fits in the bag, so back to town to sell
			goHome(phantom);
			return;
		}
		if (phantom.resting(now)) {
			if (PhantomCombat.rested(bot)) {
				phantom.stopResting();
			} else {
				if (!bot.isSitting() && !bot.isInCombat()) {
					bot.sitDown();
				}
				return;
			}
		} else if (PhantomCombat.shouldRest(bot)) {
			bot.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
			bot.sitDown();
			phantom.rest(Rnd.get(20000, 60000));
			return;
		}

		final L2Object target = bot.getTarget();
		if ((target instanceof L2MonsterInstance monster) && !monster.isAlikeDead() && bot.isInCombat()) {
			if (phantom.skillDue(now)) {
				final Skill skill = PhantomCombat.pickAttackSkill(bot, monster);
				if (skill != null) {
					bot.useMagic(skill, false, false);
				}
				phantom.skillUsed(now);
			}
			return;
		}
		if ((target instanceof L2MonsterInstance dead) && dead.isAlikeDead()) {
			phantom.killed();
			bot.setTarget(null);
		}

		final L2ItemInstance loot = PhantomCombat.findLoot(bot);
		if (loot != null) {
			PhantomCombat.take(bot, loot);
			return;
		}

		final L2MonsterInstance next = PhantomCombat.findTarget(bot, phantom.ground());
		if (next != null) {
			PhantomCombat.attack(bot, next);
			for (Skill buff : PhantomCombat.selfBuffs(bot)) {
				if (bot.getEffectList().getBuffInfoBySkillId(buff.getId()) == null) {
					bot.useMagic(buff, false, false);
					break;
				}
			}
		}
	}

	/** A dead bot gets up in its town and goes back out later. */
	private void revive(Phantom phantom, long now) {
		if (phantom.state() == Phantom.State.TOWN) {
			return;
		}
		final L2PcInstance bot = phantom.player();
		phantom.died();
		bot.doRevive();
		bot.setCurrentHp(bot.getMaxHp());
		bot.setCurrentMp(bot.getMaxMp());
		final Location home = phantom.home();
		bot.teleToLocation(home.getX() + Rnd.get(-150, 150), home.getY() + Rnd.get(-150, 150), home.getZ(), false);
		if (_phrases.has("died")) {
			say(phantom, Say2.ALL, _phrases.random("died"), null, null);
		}
		phantom.enter(Phantom.State.TOWN, null, Rnd.get(2 * 60000, 6 * 60000));
	}

	/** In town a bot now and then decides to go hunting. */
	private void leaveForHunt(Phantom phantom) {
		final int hunting = (int) _phantoms.stream().filter(p -> p.state() != Phantom.State.TOWN).count();
		if ((hunting * 100) >= (_phantoms.size() * _huntShare)) {
			phantom.enter(Phantom.State.TOWN, null, Rnd.get(30000, 90000));
			return;
		}
		final PhantomHunting.Ground ground = _hunting.pick(phantom.player().getLevel());
		if (ground == null) {
			phantom.enter(Phantom.State.TOWN, null, 5 * 60000L);
			return;
		}
		if (_phrases.has("leaving")) {
			say(phantom, Say2.ALL, _phrases.random("leaving").replace("{zone}", ground.name()), null, null);
		}
		phantom.enter(Phantom.State.TRAVEL, ground, Rnd.get(8000, 20000));
	}

	/** The trip itself is a teleport, the way a player uses a gatekeeper. */
	private void arrive(Phantom phantom) {
		final PhantomHunting.Ground ground = phantom.ground();
		if (ground == null) {
			phantom.enter(Phantom.State.TOWN, null, 30000);
			return;
		}
		phantom.player().teleToLocation(ground.x() + Rnd.get(-200, 200), ground.y() + Rnd.get(-200, 200), ground.z(), false);
		phantom.enter(Phantom.State.HUNT, ground, (_huntMinutes * 60000L) + Rnd.get(0, 5 * 60000));
	}

	private void goHome(Phantom phantom) {
		if (_phrases.has("returning")) {
			say(phantom, Say2.ALL, _phrases.random("returning"), null, null);
		}
		phantom.enter(Phantom.State.RETURN, phantom.ground(), Rnd.get(5000, 15000));
	}

	private void arriveHome(Phantom phantom) {
		final Location home = phantom.home();
		phantom.player().teleToLocation(home.getX() + Rnd.get(-150, 150), home.getY() + Rnd.get(-150, 150), home.getZ(), false);
		phantom.earned(PhantomTrade.sellLoot(phantom.player()));
		phantom.enter(Phantom.State.TOWN, null, Rnd.get(3 * 60000, 12 * 60000));
	}

	private void speak(Phantom phantom, long now) {
		// 1. Something the bot saw happen next to it.
		final PhantomNews.Item seen = _news.seenNear(phantom.player().getX(), phantom.player().getY(), SIGHT_RANGE, SEEN_MEMORY);
		if ((seen != null) && _phrases.has("seen " + seen.kind()) && (Rnd.get(100) < 45)) {
			_news.mention(seen);
			say(phantom, Say2.ALL, _phrases.random("seen " + seen.kind()), null, seen);
			return;
		}
		
		// 2. Server news everybody talks about.
		final PhantomNews.Item news = _news.recentGlobal(NEWS_MEMORY);
		if ((news != null) && _phrases.has("news " + news.kind()) && (Rnd.get(100) < 25)) {
			_news.mention(news);
			final int channel = (Rnd.get(100) < 25) && regionFree(phantom, now) ? Say2.SHOUT : Say2.ALL;
			say(phantom, channel, _phrases.random("news " + news.kind()), null, news);
			return;
		}
		
		// 3. A conversation with a bot nearby, on the topic of the latest news.
		final Phantom listener = neighbour(phantom, DIALOG_RANGE);
		if ((listener != null) && (Rnd.get(100) < 45) && chainFree(phantom, now)) {
			final List<String> chain = _phrases.randomChain(topic(news, seen));
			if (chain != null) {
				startChain(phantom, listener, chain, (seen != null) ? seen : news);
				return;
			}
			final PhraseBook.Dialog dialog = _phrases.randomDialog();
			if (dialog != null) {
				say(phantom, Say2.ALL, dialog.line(), null, null);
				listener.delayChat(15000);
				ThreadPoolManager.getInstance().scheduleGeneral(() -> say(listener, Say2.ALL, dialog.answer(), null, null), Rnd.get(3000, 8000));
				return;
			}
		}
		
		// 4. Small talk.
		final int roll = Rnd.get(100);
		if ((roll < 12) && regionFree(phantom, now)) {
			say(phantom, Say2.TRADE, _phrases.random("trade"), null, null);
		} else if ((roll < 20) && regionFree(phantom, now)) {
			say(phantom, Say2.SHOUT, _phrases.random("shout"), null, null);
		} else {
			say(phantom, Say2.ALL, _phrases.random("general"), null, null);
		}
	}
	
	private static String topic(PhantomNews.Item news, PhantomNews.Item seen) {
		final PhantomNews.Item item = (seen != null) ? seen : news;
		if (item == null) {
			return "general";
		}
		return switch (item.kind()) {
			case "raidkill" -> "raid";
			case "siegestart", "siegeend", "owner" -> "siege";
			case "pvp", "death" -> "pvp";
			case "levelup", "profession" -> "level";
			default -> "general";
		};
	}
	
	/** Two bots talk in turns, a few seconds apart. */
	private void startChain(Phantom first, Phantom second, List<String> turns, PhantomNews.Item about) {
		long delay = 0;
		for (int i = 0; i < turns.size(); i++) {
			final Phantom speaker = ((i % 2) == 0) ? first : second;
			final String text = turns.get(i);
			speaker.delayChat(((turns.size() - i) * 8000L) + 10000);
			if (i == 0) {
				say(speaker, Say2.ALL, text, null, about);
			} else {
				delay += Rnd.get(3000, 7000);
				ThreadPoolManager.getInstance().scheduleGeneral(() -> say(speaker, Say2.ALL, text, null, about), delay);
			}
		}
	}
	
	/** Only one conversation at a time per town, otherwise everybody talks over each other. */
	private boolean chainFree(Phantom phantom, long now) {
		final int region = MapRegionManager.getInstance().getMapRegionLocId(phantom.player());
		final Long last = _regionChain.get(region);
		if ((last != null) && ((now - last) < 60000)) {
			return false;
		}
		_regionChain.put(region, now);
		return true;
	}
	
	/** Trade and shout are heard by a whole region, so they are rate limited per region. */
	private boolean regionFree(Phantom phantom, long now) {
		final int region = MapRegionManager.getInstance().getMapRegionLocId(phantom.player());
		final Long last = _regionChat.get(region);
		if ((last != null) && ((now - last) < (_regionChatInterval * 1000L))) {
			return false;
		}
		_regionChat.put(region, now);
		return true;
	}

	private Phantom neighbour(Phantom phantom, int range) {
		final List<Phantom> near = new ArrayList<>();
		for (Phantom other : _phantoms) {
			if ((other != phantom) && other.player().isVisible() && (other.player().calculateDistance(phantom.player(), false, false) < range)) {
				near.add(other);
			}
		}
		return near.isEmpty() ? null : near.get(Rnd.get(near.size()));
	}

	private void say(Phantom phantom, int type, String text, String playerName, PhantomNews.Item news) {
		say(phantom, type, text, playerName, news, null);
	}

	/**
	 * @param tellTo name of the player the line goes to as a private message, null for the channel
	 */
	private void say(Phantom phantom, int type, String text, String playerName, PhantomNews.Item news, String tellTo) {
		if ((text == null) || !phantom.player().isVisible()) {
			return;
		}
		final var handler = ChatHandler.getInstance().getHandler(type);
		if (handler != null) {
			final String line = phantom.style().apply(_facts.fill(text, phantom.player(), playerName, news));
			handler.handleChat(type, phantom.player(), tellTo, line);
			LOG_CHAT.info("[phantom] {} says [{}]{}.", phantom.player().getName(), line, (tellTo != null) ? " to " + tellTo : "");
		}
	}

	private void onPlayerChat(PlayerChat event) {
		final L2PcInstance player = event.player();
		if ((player == null) || (_objectIds.contains(player.getObjectId()) && !_debugChat)) {
			return;
		}
		final String text = event.text();
		if ((text == null) || text.isBlank()) {
			return;
		}

		// A private message is answered by the bot it was sent to.
		if (event.chatType() == Say2.TELL) {
			final Phantom addressed = byObjectId(event.target());
			if (addressed != null) {
				answer(addressed, player, text, Say2.TELL, true);
			}
			return;
		}
		if ((event.chatType() != Say2.ALL) && (event.chatType() != Say2.SHOUT) && (event.chatType() != Say2.TRADE)) {
			return;
		}

		// The name of a bot standing nearby was said: that bot answers, not a random one.
		final Phantom named = byName(text, player);
		if (named != null) {
			answer(named, player, text, Say2.ALL, true);
			return;
		}

		// The player keeps talking to the bot that answered a moment ago.
		final Phantom talking = stillTalkingTo(player);
		if (talking != null) {
			answer(talking, player, text, Say2.ALL, true);
			return;
		}

		final List<Phantom> near = new ArrayList<>();
		for (Phantom phantom : _phantoms) {
			if (phantom.player().isVisible() && (phantom.player().calculateDistance(player, false, false) < REPLY_RANGE)) {
				near.add(phantom);
			}
		}
		if (!near.isEmpty()) {
			answer(near.get(Rnd.get(near.size())), player, text, Say2.ALL, false);
		}
	}

	/** The bot a private message was sent to. */
	private Phantom byObjectId(L2PcInstance target) {
		if (target == null) {
			return null;
		}
		for (Phantom phantom : _phantoms) {
			if (phantom.player().getObjectId() == target.getObjectId()) {
				return phantom;
			}
		}
		return null;
	}

	/** A bot within sight whose name is in the message. */
	private Phantom byName(String text, L2PcInstance player) {
		final String lower = text.toLowerCase(Locale.ROOT);
		for (Phantom phantom : _phantoms) {
			if (lower.contains(phantom.player().getName().toLowerCase(Locale.ROOT)) && phantom.player().isVisible() && (phantom.player().calculateDistance(player, false, false) < SIGHT_RANGE)) {
				return phantom;
			}
		}
		return null;
	}

	private Phantom stillTalkingTo(L2PcInstance player) {
		final long now = System.currentTimeMillis();
		for (Phantom phantom : _phantoms) {
			if (phantom.isTalkingTo(player.getName(), now) && phantom.player().isVisible() && (phantom.player().calculateDistance(player, false, false) < REPLY_RANGE)) {
				return phantom;
			}
		}
		return null;
	}

	/**
	 * Answers a player.
	 * @param direct true when the player addressed this bot by name or by a private message: then the
	 *            bot always says something, stops walking and turns to the player
	 */
	private void answer(Phantom phantom, L2PcInstance player, String text, int channel, boolean direct) {
		final long now = System.currentTimeMillis();
		final boolean followup = phantom.isTalkingTo(player.getName(), now);
		String line = _phrases.answerTo(text, phantom.player().getName());
		if ((line == null) || line.equals(phantom.lastAnswer())) {
			if (!direct && (line == null)) {
				return;
			}
			line = _phrases.random(followup ? "reply followup" : "reply direct");
		}
		if (line == null) {
			return;
		}

		final String answer = line;
		phantom.startTalking(player.getName(), answer, now);
		phantom.delayChat(20000);
		if (direct) {
			phantom.lookAt(player);
		}
		// A short pause, as if the bot were typing.
		final String tellTo = (channel == Say2.TELL) ? player.getName() : null;
		ThreadPoolManager.getInstance().scheduleGeneral(() -> say(phantom, channel, answer, player.getName(), null, tellTo), Rnd.get(1200, 3500));
	}

	/** Right after something happens, a bot or two who saw it say something. */
	private void onNews(PhantomNews.Item item) {
		final String section = (item.global() ? "news " : "seen ") + item.kind();
		if (!_phrases.has(section)) {
			return;
		}
		final long now = System.currentTimeMillis();
		final List<Phantom> witnesses = new ArrayList<>();
		for (Phantom phantom : _phantoms) {
			if (!phantom.player().isVisible() || !phantom.canReact(now)) {
				continue;
			}
			final boolean near = (Math.abs(phantom.player().getX() - item.x()) < SIGHT_RANGE) && (Math.abs(phantom.player().getY() - item.y()) < SIGHT_RANGE);
			if (near || item.global()) {
				witnesses.add(phantom);
			}
		}
		if (witnesses.isEmpty()) {
			return;
		}
		
		final int speakers = item.global() ? 2 : 1;
		for (int i = 0; i < speakers; i++) {
			final Phantom phantom = witnesses.get(Rnd.get(witnesses.size()));
			if (!phantom.canReact(now)) {
				continue;
			}
			phantom.reacted(now);
			phantom.delayChat(30000);
			_news.mention(item);
			ThreadPoolManager.getInstance().scheduleGeneral(() -> say(phantom, Say2.ALL, _phrases.random(section), null, item), Rnd.get(3000, 12000));
		}
	}
	
	/** Made up events for DebugNews: the bots should react to them like to real ones. */
	private void injectDebugNews() {
		final long now = System.currentTimeMillis();
		final Phantom sample = _phantoms.get(Rnd.get(_phantoms.size()));
		final int x = sample.player().getX();
		final int y = sample.player().getY();
		_news.add(new PhantomNews.Item("raidkill", now, x, y, "Testhunter", "Ancient Weird Drake", "60", true));
		_news.add(new PhantomNews.Item("death", now, x, y, "Testvictim", "Cave Ant", "", false));
		_news.add(new PhantomNews.Item("levelup", now, x, y, "Testnewbie", "40", "", false));
		_news.add(new PhantomNews.Item("siegestart", now, 0, 0, "", "", "Giran", true));
		LOG.info("DebugNews: four made up events sent around {}.", sample.player().getName());
	}
	
	/**
	 * @return true when this player is a bot; the chat handlers use it, a bot has no client but reads messages
	 */
	public static boolean isPhantom(L2PcInstance player) {
		return (player != null) && (_instance != null) && _instance._objectIds.contains(player.getObjectId());
	}

	public static void main(String[] args) {
		new Phantoms();
	}
}
