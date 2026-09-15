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
package com.l2jserver.gameserver.model.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import com.l2jserver.gameserver.config.ChronicleConfiguration;
import com.l2jserver.gameserver.config.Configuration;
import com.l2jserver.gameserver.model.StatsSet;

/**
 * Attributes (elements) exist only when the EnableAttributes chronicle switch is on.
 */
class L2ItemElementableTest {

	private MockedStatic<Configuration> configuration;
	private ChronicleConfiguration chronicle;

	@BeforeEach
	void setUp() {
		configuration = mockStatic(Configuration.class, RETURNS_MOCKS);
		chronicle = mock(ChronicleConfiguration.class);
		configuration.when(Configuration::chronicle).thenReturn(chronicle);
	}

	@AfterEach
	void tearDown() {
		configuration.close();
	}

	@ParameterizedTest
	@CsvSource({
		"true, true, true",
		"true, false, false",
		"false, true, false",
		"false, false, false"
	})
	void elementableOnlyWhenTemplateAndSwitchAllowIt(boolean template, boolean attributesEnabled, boolean expected) {
		final var set = new StatsSet();
		set.set("item_id", 1);
		set.set("name", "Test Item");
		set.set("element_enabled", template);
		when(chronicle.enableAttributes()).thenReturn(attributesEnabled);

		assertThat(new L2EtcItem(set).isElementable()).isEqualTo(expected);
	}
}
