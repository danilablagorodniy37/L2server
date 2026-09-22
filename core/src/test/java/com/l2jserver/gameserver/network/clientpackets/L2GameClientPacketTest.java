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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.BufferUnderflowException;

import org.junit.jupiter.api.Test;

import com.l2jserver.gameserver.network.L2GameClient;

/**
 * A client packet that cannot be read is dropped without taking the server down;
 * a packet cut short counts against the client (tests/test_crash.py sends such packets to a live server).
 */
class L2GameClientPacketTest {

	private static final class Packet extends L2GameClientPacket {
		private final RuntimeException _failure;

		Packet(L2GameClient client, RuntimeException failure) {
			_client = client;
			_failure = failure;
		}

		@Override
		protected void readImpl() {
			if (_failure != null) {
				throw _failure;
			}
		}

		@Override
		protected void runImpl() {
		}

		@Override
		public String getType() {
			return "[C] test";
		}
	}

	@Test
	void readsAWholePacket() {
		final var client = mock(L2GameClient.class);
		assertTrue(new Packet(client, null).read());
		verify(client, never()).onBufferUnderflow();
	}

	@Test
	void shortPacketIsDroppedAndCountedAgainstTheClient() {
		final var client = mock(L2GameClient.class);
		assertFalse(new Packet(client, new BufferUnderflowException()).read());
		verify(client).onBufferUnderflow();
	}

	@Test
	void otherReadErrorIsDroppedWithoutCountingUnderflow() {
		final var client = mock(L2GameClient.class);
		assertFalse(new Packet(client, new IllegalStateException("broken")).read());
		verify(client, never()).onBufferUnderflow();
	}
}
