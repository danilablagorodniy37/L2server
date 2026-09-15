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

import java.util.List;
import java.util.Set;

import com.l2jserver.gameserver.model.base.ClassId;

/**
 * What a class wears: weapon types and armor kind, so a bot looks like its class.
 */
public class PhantomClasses {
	private static final Set<String> DAGGER = Set.of("treasureHunter", "adventurer", "plainsWalker", "windRider", "abyssWalker", "ghostHunter");
	private static final Set<String> BOW = Set.of("hawkeye", "sagittarius", "silverRanger", "moonlightSentinel", "phantomRanger", "ghostSentinel");
	private static final Set<String> POLE = Set.of("warlord", "dreadnought");
	private static final Set<String> DUAL = Set.of("gladiator", "duelist", "bladedancer", "spectralDancer");
	private static final Set<String> FIST = Set.of("tyrant", "grandKhavatari");
	private static final Set<String> TWO_HANDED = Set.of("destroyer", "titan");
	private static final Set<String> BLUNT = Set.of("scavenger", "bountyHunter", "fortuneSeeker", "artisan", "warsmith", "maestro");
	private static final Set<String> KAMAEL_ANCIENT = Set.of("maleSoldier", "trooper", "berserker", "doombringer");
	private static final Set<String> KAMAEL_CROSSBOW = Set.of("arbalester", "trickster");
	private static final Set<String> KAMAEL_RAPIER = Set.of("femaleSoldier", "warder", "maleSoulbreaker", "femaleSoulbreaker", "maleSoulhound", "femaleSoulhound");
	private static final Set<String> LIGHT_ARMOR = Set.of("swordSinger", "swordMuse", "bladedancer", "spectralDancer");

	public static List<String> weaponTypes(ClassId classId) {
		final String name = classId.name();
		if (classId.isMage()) {
			return List.of("BLUNT", "SWORD");
		} else if (DAGGER.contains(name)) {
			return List.of("DAGGER");
		} else if (BOW.contains(name)) {
			return List.of("BOW");
		} else if (POLE.contains(name)) {
			return List.of("POLE");
		} else if (DUAL.contains(name)) {
			return List.of("DUAL", "SWORD");
		} else if (FIST.contains(name)) {
			return List.of("DUALFIST", "FIST");
		} else if (TWO_HANDED.contains(name)) {
			return List.of("SWORD", "BLUNT");
		} else if (BLUNT.contains(name)) {
			return List.of("BLUNT");
		} else if (KAMAEL_ANCIENT.contains(name)) {
			return List.of("ANCIENTSWORD");
		} else if (KAMAEL_CROSSBOW.contains(name)) {
			return List.of("CROSSBOW");
		} else if (KAMAEL_RAPIER.contains(name)) {
			return List.of("RAPIER");
		}
		// Knights and everything else: sword and shield.
		return List.of("SWORD");
	}

	public static String armorType(ClassId classId) {
		final String name = classId.name();
		if (classId.isMage()) {
			return "MAGIC";
		} else if (DAGGER.contains(name) || BOW.contains(name) || LIGHT_ARMOR.contains(name) || KAMAEL_ANCIENT.contains(name) || KAMAEL_CROSSBOW.contains(name) || KAMAEL_RAPIER.contains(name)) {
			return "LIGHT";
		}
		return "HEAVY";
	}

	/** Readable class name for the chat: "Dark Avenger" from darkAvenger. */
	public static String readable(ClassId classId) {
		final StringBuilder sb = new StringBuilder();
		for (char c : classId.name().toCharArray()) {
			if (Character.isUpperCase(c) && !sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(sb.isEmpty() ? Character.toUpperCase(c) : c);
		}
		return sb.toString();
	}
}
