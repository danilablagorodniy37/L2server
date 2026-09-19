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
package com.l2jserver.datapack.ai.individual;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jserver.datapack.ai.npc.AbstractNpcAI;
import com.l2jserver.gameserver.instancemanager.GrandBossManager;
import com.l2jserver.gameserver.model.StatsSet;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2GrandBossInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;

/**
 * The epics High Five locked behind instances stand in the world again, the way Interlude had them:
 * Zaken on the Devil's Isle and Frintezza in the Imperial Tomb. Nobody has to open a door: they are
 * spawned when the server starts, and again after their respawn time once they are killed.
 */
public final class InterludeEpics extends AbstractNpcAI {
	private static final Logger LOG = LoggerFactory.getLogger(InterludeEpics.class);

	/** A boss, where it stands and how long it stays down. */
	private record Epic(int id, int x, int y, int z, int heading, long respawn, long random) {
	}

	private static final byte ALIVE = 0;
	private static final byte DEAD = 1;

	private static final long HOUR = 3600000L;

	private static final Epic[] EPICS = {
		// Devil's Isle, sixty hours give or take twenty, as in Interlude
		new Epic(29022, 55312, 219168, -3223, 0, 60 * HOUR, 20 * HOUR),
		// the Imperial Tomb, forty eight hours give or take twenty four
		new Epic(29045, -87780, -155086, -9080, 16384, 48 * HOUR, 24 * HOUR),
	};

	public InterludeEpics() {
		super(InterludeEpics.class.getSimpleName(), "ai/individual");
		for (Epic epic : EPICS) {
			registerMobs(epic.id());
			restore(epic);
		}
	}

	/** Puts a boss back where it belongs, or starts the clock when it is still down. */
	private void restore(Epic epic) {
		final StatsSet info = GrandBossManager.getInstance().getStatsSet(epic.id());
		if (info == null) {
			LOG.warn("no row in grandboss_data for {}, it cannot be spawned.", epic.id());
			return;
		}
		if (GrandBossManager.getInstance().getBossStatus(epic.id()) == DEAD) {
			final long left = info.getLong("respawn_time") - System.currentTimeMillis();
			if (left > 0) {
				startQuestTimer("epic_" + epic.id(), left, null, null);
				return;
			}
		}
		spawn(epic, info);
	}

	private void spawn(Epic epic, StatsSet info) {
		final int x = (info != null) && (info.getInt("loc_x", 0) != 0) ? info.getInt("loc_x") : epic.x();
		final int y = (info != null) && (info.getInt("loc_y", 0) != 0) ? info.getInt("loc_y") : epic.y();
		final int z = (info != null) && (info.getInt("loc_z", 0) != 0) ? info.getInt("loc_z") : epic.z();
		final L2GrandBossInstance boss = (L2GrandBossInstance) addSpawn(epic.id(), x, y, z, epic.heading(), false, 0);
		if (boss == null) {
			return;
		}
		if (info != null) {
			final double hp = info.getDouble("currentHP", boss.getMaxHp());
			final double mp = info.getDouble("currentMP", boss.getMaxMp());
			boss.setCurrentHpMp(hp > 0 ? hp : boss.getMaxHp(), mp > 0 ? mp : boss.getMaxMp());
		}
		GrandBossManager.getInstance().addBoss(boss);
		GrandBossManager.getInstance().setBossStatus(epic.id(), ALIVE);
	}

	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		if (event.startsWith("epic_")) {
			final int id = Integer.parseInt(event.substring("epic_".length()));
			for (Epic epic : EPICS) {
				if (epic.id() == id) {
					spawn(epic, GrandBossManager.getInstance().getStatsSet(id));
					break;
				}
			}
		}
		return super.onEvent(event, npc, player);
	}

	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isSummon) {
		for (Epic epic : EPICS) {
			if (epic.id() != npc.getId()) {
				continue;
			}
			final long respawn = System.currentTimeMillis() + epic.respawn() + getRandom((int) epic.random());
			GrandBossManager.getInstance().setBossStatus(epic.id(), DEAD);
			final StatsSet info = GrandBossManager.getInstance().getStatsSet(epic.id());
			if (info != null) {
				info.set("respawn_time", respawn);
				GrandBossManager.getInstance().setStatsSet(epic.id(), info);
			}
			startQuestTimer("epic_" + epic.id(), respawn - System.currentTimeMillis(), null, null);
			LOG.info("{} was killed, it comes back in {} hours.", npc.getName(), (respawn - System.currentTimeMillis()) / HOUR);
			break;
		}
		return super.onKill(npc, killer, isSummon);
	}
}
