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
package com.l2jserver.gameserver.model.actor.instance;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.l2jserver.gameserver.config.Configuration;
import com.l2jserver.gameserver.config.CustomsConfiguration;
import com.l2jserver.gameserver.enums.PrivateStoreType;

/**
 * Closing a private store takes an offline trader out of the world, but not a phantom: the bots of the
 * Giran market have no client of their own and their shops are closed every time the goods are changed.
 */
class L2PcInstanceStoreTest {
	
	@Test
	void phantomStaysInTheWorldWhenItsStoreCloses() {
		final CustomsConfiguration customs = offlineDisconnectFinished();
		try (MockedStatic<Configuration> configuration = mockStatic(Configuration.class)) {
			configuration.when(Configuration::customs).thenReturn(customs);
			
			final L2PcInstance bot = mock(L2PcInstance.class, CALLS_REAL_METHODS);
			doReturn(true).when(bot).deleteMe();
			bot.setPhantom(true);
			
			bot.setPrivateStoreType(PrivateStoreType.NONE);
			
			verify(bot, never()).deleteMe();
		}
	}
	
	@Test
	void offlineTraderLeavesTheWorldWhenItsStoreCloses() {
		final CustomsConfiguration customs = offlineDisconnectFinished();
		try (MockedStatic<Configuration> configuration = mockStatic(Configuration.class)) {
			configuration.when(Configuration::customs).thenReturn(customs);
			
			final L2PcInstance offliner = mock(L2PcInstance.class, CALLS_REAL_METHODS);
			doReturn(true).when(offliner).deleteMe();
			
			offliner.setPrivateStoreType(PrivateStoreType.NONE);
			
			verify(offliner, times(1)).deleteMe();
		}
	}
	
	private static CustomsConfiguration offlineDisconnectFinished() {
		final CustomsConfiguration customs = mock(CustomsConfiguration.class);
		when(customs.offlineDisconnectFinished()).thenReturn(true);
		return customs;
	}
}
