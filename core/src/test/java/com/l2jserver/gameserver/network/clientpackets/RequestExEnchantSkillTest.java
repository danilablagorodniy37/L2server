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
package com.l2jserver.gameserver.network.clientpackets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The experience cost of skill enchanting (Interlude rule).
 */
class RequestExEnchantSkillTest {

	@ParameterizedTest
	@CsvSource({
		// current, cost, floor, can pay
		"2000, 0, 5000, true", // no experience cost, as in H5
		"9000, 4000, 5000, true", // lands exactly on the floor
		"9000, 4001, 5000, false", // would drop below the start of level 76
		"20000, 5000, 5000, true",
	})
	void hasEnoughExp(long current, long cost, long floor, boolean expected) {
		assertEquals(expected, RequestExEnchantSkill.hasEnoughExp(current, cost, floor));
	}
}
