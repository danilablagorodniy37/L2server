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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.commons.database.ConnectionFactory;
import com.l2jserver.commons.util.Rnd;
import com.l2jserver.gameserver.data.json.ExperienceData;
import com.l2jserver.gameserver.data.sql.impl.CharNameTable;
import com.l2jserver.gameserver.enums.Race;
import com.l2jserver.gameserver.model.actor.appearance.PcAppearance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.base.ClassId;
import com.l2jserver.gameserver.data.xml.impl.SkillTreesData;
import com.l2jserver.gameserver.datatables.SkillData;
import com.l2jserver.gameserver.model.L2SkillLearn;
import com.l2jserver.gameserver.model.items.L2Item;
import com.l2jserver.gameserver.model.skills.Skill;
import com.l2jserver.gameserver.model.items.instance.L2ItemInstance;

/**
 * Creates the phantom characters and dresses them. Their equipment comes from
 * data/phantoms/gear.txt, built by tools/interlude/build_phantom_gear.py, so bots
 * only ever wear Interlude or Kamael items.
 */
public class PhantomFactory {
	private static final Logger LOG = LoggerFactory.getLogger(PhantomFactory.class);

	private static final String[] NAME_START = {
		"Ar", "Bel", "Cor", "Dra", "El", "Fen", "Gor", "Hal", "Ira", "Jor", "Kal", "Lyn", "Mor", "Nar", "Oru", "Pyr", "Quen", "Ran", "Sil", "Tor", "Ul", "Ven", "Wil", "Xan", "Yor", "Zel"
	};
	private static final String[] NAME_MIDDLE = {
		"a", "e", "i", "o", "u", "ae", "ia", "ou", "ari", "ene", "ora", "uli"
	};
	private static final String[] NAME_END = {
		"n", "r", "s", "th", "x", "l", "dor", "mir", "nis", "rak", "vel", "wyn", "zar", "ka", "ra", "to"
	};
	// Kamael classes are male or female by class, not by choice.
	private static final Set<Integer> FEMALE_KAMAEL = Set.of(124, 126, 129, 130, 133, 134);
	// Subclass-only Kamael classes.
	private static final Set<Integer> SKIPPED_CLASSES = Set.of(135, 136);

	private final PhantomGear _gear;
	private final String _account;
	private final int _minLevel;
	private final int _maxLevel;

	public PhantomFactory(Path gearFile, String account, int minLevel, int maxLevel) throws IOException {
		_gear = new PhantomGear(gearFile);
		_account = account;
		_minLevel = minLevel;
		_maxLevel = maxLevel;
	}

	/** Character ids of the bot account, oldest first. */
	public List<Integer> storedCharacters() {
		final List<Integer> ids = new ArrayList<>();
		try (var con = ConnectionFactory.getInstance().getConnection();
			var ps = con.prepareStatement("SELECT charId FROM characters WHERE account_name = ? ORDER BY charId")) {
			ps.setString(1, _account);
			try (var rs = ps.executeQuery()) {
				while (rs.next()) {
					ids.add(rs.getInt("charId"));
				}
			}
		} catch (Exception ex) {
			LOG.warn("could not read the bot characters!", ex);
		}
		return ids;
	}

	/** Blocks logging into the bot account: the login server would create it on first use otherwise. */
	public void blockAccount() {
		try (var con = ConnectionFactory.getInstance().getConnection();
			var ps = con.prepareStatement("INSERT IGNORE INTO accounts (login, password, accessLevel) VALUES (?, ?, -100)")) {
			ps.setString(1, _account);
			ps.setString(2, "phantoms-no-login-" + Rnd.get(100000, 999999));
			ps.executeUpdate();
		} catch (Exception ex) {
			LOG.info("the account {} was not blocked ({}), check that logging into it is not possible.", _account, ex.getMessage());
		}
	}

	public L2PcInstance create() {
		return create(null, Rnd.get(_minLevel, _maxLevel));
	}

	/**
	 * A new bot of a given trade, for a party that needs a healer rather than whoever comes along.
	 * @param wanted the class it should be, null for any class of its level
	 * @param level the level it starts at
	 * @return the character, or null when no name was free
	 */
	public L2PcInstance create(ClassId wanted, int level) {
		final ClassId classId = (wanted != null) ? wanted : randomClass(level);
		final boolean female = (classId.getRace() == Race.KAMAEL) ? FEMALE_KAMAEL.contains(classId.getId()) : Rnd.nextBoolean();
		final var appearance = new PcAppearance((byte) Rnd.get(3), (byte) Rnd.get(3), (byte) Rnd.get(3), female);
		final String name = randomName();
		if (name == null) {
			return null;
		}

		final L2PcInstance player = L2PcInstance.create(classId.getId(), _account, name, appearance);
		if (player == null) {
			return null;
		}

		player.addExpAndSp(ExperienceData.getInstance().getExpForLevel(level), 0);
		teach(player);
		player.setCurrentHpMp(player.getMaxHp(), player.getMaxMp());
		player.setCurrentCp(player.getMaxCp());
		equip(player, classId, level);
		player.storeMe();
		player.getInventory().updateDatabase();
		return player;
	}

	/**
	 * Teaches a bot everything its class knows at its level: without it a healer cannot heal,
	 * a prophet cannot buff and a fighter swings its weapon and nothing else.
	 * @param player the bot
	 * @return how many skills it learned
	 */
	public static int teach(L2PcInstance player) {
		int learned = 0;
		for (L2SkillLearn learn : SkillTreesData.getInstance().getCompleteClassSkillTree(player.getClassId()).values()) {
			if (learn.getGetLevel() > player.getLevel()) {
				continue;
			}
			final Skill skill = SkillData.getInstance().getSkill(learn.getSkillId(), learn.getSkillLevel());
			if ((skill != null) && (player.getKnownSkill(skill.getId()) == null)) {
				player.addSkill(skill, false);
				learned++;
			}
		}
		return learned;
	}

	private ClassId randomClass(int level) {
		final int tier = (level < 40) ? 1 : (level < 76) ? 2 : 3;
		final List<ClassId> classes = new ArrayList<>();
		for (ClassId classId : ClassId.values()) {
			if ((classId.level() == tier) && !SKIPPED_CLASSES.contains(classId.getId()) && (classId.getRace() != null)) {
				classes.add(classId);
			}
		}
		return classes.get(Rnd.get(classes.size()));
	}

	private String randomName() {
		for (int i = 0; i < 50; i++) {
			final String name = NAME_START[Rnd.get(NAME_START.length)] + NAME_MIDDLE[Rnd.get(NAME_MIDDLE.length)] + NAME_END[Rnd.get(NAME_END.length)];
			if (!CharNameTable.getInstance().doesCharNameExist(name)) {
				return name;
			}
		}
		LOG.warn("could not find a free character name!");
		return null;
	}

	private void equip(L2PcInstance player, ClassId classId, int level) {
		final String grade = PhantomGear.gradeForLevel(level);
		final Integer weapon = _gear.randomWeapon(grade, classId);
		if (weapon != null) {
			give(player, weapon);
		}
		final int[] armor = _gear.randomSet(grade, classId);
		if (armor != null) {
			// chest, legs, head, gloves, feet, shield
			for (int i = 0; i < armor.length; i++) {
				final boolean shield = (i == armor.length - 1);
				if ((armor[i] != 0) && (!shield || usesShield(player, classId))) {
					give(player, armor[i]);
				}
			}
		}
	}

	private boolean usesShield(L2PcInstance player, ClassId classId) {
		final L2ItemInstance weapon = player.getActiveWeaponInstance();
		return (weapon != null) && (weapon.getItem().getBodyPart() == L2Item.SLOT_R_HAND) && !classId.isMage();
	}

	private void give(L2PcInstance player, int itemId) {
		final L2ItemInstance item = player.getInventory().addItem("Phantom", itemId, 1, player, null);
		if (item != null) {
			player.getInventory().equipItem(item);
		}
	}

	@Override
	public String toString() {
		return "PhantomFactory(" + _account + ", levels " + _minLevel + "-" + _maxLevel + ", " + _gear + ")";
	}

	/** Equipment loaded from data/phantoms/gear.txt. */
	static class PhantomGear {
		private record Weapon(String grade, String type, boolean magic, int itemId) {
		}

		private record ArmorSet(String grade, String type, int[] parts) {
		}

		private final List<Weapon> _weapons = new ArrayList<>();
		private final List<ArmorSet> _sets = new ArrayList<>();

		PhantomGear(Path file) throws IOException {
			for (String line : Files.readAllLines(file)) {
				final String[] p = line.split("#")[0].trim().split("\\s+");
				if (p[0].equals("weapon") && (p.length >= 5)) {
					_weapons.add(new Weapon(p[1], p[2], p[3].equals("1"), Integer.parseInt(p[4])));
				} else if (p[0].equals("set") && (p.length >= 9)) {
					final int[] parts = new int[6];
					for (int i = 0; i < 6; i++) {
						parts[i] = Integer.parseInt(p[3 + i]);
					}
					_sets.add(new ArmorSet(p[1], p[2], parts));
				}
			}
		}

		static String gradeForLevel(int level) {
			if (level < 20) {
				return "NONE";
			} else if (level < 40) {
				return "D";
			} else if (level < 52) {
				return "C";
			} else if (level < 61) {
				return "B";
			} else if (level < 76) {
				return "A";
			}
			return "S";
		}

		Integer randomWeapon(String grade, ClassId classId) {
			final List<String> types = PhantomClasses.weaponTypes(classId);
			final boolean magic = classId.isMage();
			final List<Weapon> fit = new ArrayList<>();
			for (Weapon w : _weapons) {
				if (w.grade().equals(grade) && types.contains(w.type()) && (w.magic() == magic)) {
					fit.add(w);
				}
			}
			if (fit.isEmpty()) {
				for (Weapon w : _weapons) {
					if (w.grade().equals(grade) && types.contains(w.type())) {
						fit.add(w);
					}
				}
			}
			return fit.isEmpty() ? null : fit.get(Rnd.get(fit.size())).itemId();
		}

		int[] randomSet(String grade, ClassId classId) {
			final String type = PhantomClasses.armorType(classId);
			final List<ArmorSet> fit = new ArrayList<>();
			for (ArmorSet set : _sets) {
				if (set.grade().equals(grade) && set.type().equals(type)) {
					fit.add(set);
				}
			}
			return fit.isEmpty() ? null : Arrays.copyOf(fit.get(Rnd.get(fit.size())).parts(), 6);
		}

		/** A weapon name of this grade, for the chat. */
		Integer randomWeaponOfGrade(String grade) {
			final List<Weapon> fit = new ArrayList<>();
			for (Weapon w : _weapons) {
				if (w.grade().equals(grade)) {
					fit.add(w);
				}
			}
			return fit.isEmpty() ? null : fit.get(Rnd.get(fit.size())).itemId();
		}

		Integer randomSetChestOfGrade(String grade) {
			final List<ArmorSet> fit = new ArrayList<>();
			for (ArmorSet set : _sets) {
				if (set.grade().equals(grade)) {
					fit.add(set);
				}
			}
			return fit.isEmpty() ? null : fit.get(Rnd.get(fit.size())).parts()[0];
		}

		@Override
		public String toString() {
			return _weapons.size() + " weapons, " + _sets.size() + " armor sets";
		}
	}

	PhantomGear gear() {
		return _gear;
	}
}
