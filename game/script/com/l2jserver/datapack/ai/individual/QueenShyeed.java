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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.l2jserver.datapack.ai.npc.AbstractNpcAI;
import com.l2jserver.gameserver.instancemanager.RaidBossSpawnManager;
import com.l2jserver.gameserver.instancemanager.RaidBossSpawnManager.StatusEnum;
import com.l2jserver.gameserver.instancemanager.ZoneManager;
import com.l2jserver.gameserver.model.L2Spawn;
import com.l2jserver.gameserver.model.actor.L2Attackable;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.zone.type.L2EffectZone;
import com.l2jserver.gameserver.network.NpcStringId;
import com.l2jserver.gameserver.network.clientpackets.Say2;

/**
 * Queen Shyeed AI
 * @author malyelfik
 * @author maneco2
 */
public final class QueenShyeed extends AbstractNpcAI {
	// NPCs
	private static final int SHYEED = 25671;
	private static final int SPIKED_STAKATO = 22617;
	private static final int SPIKED_STAKATO_WORKER = 22618;
	private static final int SPIKED_STAKATO_SORCERER = 22623;
	private static final int SPIKED_STAKATO_SOLDIER = 22627;
	private static final int SPIKED_STAKATO_DRONE = 22628;
	// ZONES
	private static final L2EffectZone MOB_BUFF_ZONE = ZoneManager.getInstance().getZoneById(200103, L2EffectZone.class);
	private static final L2EffectZone MOB_BUFF_DISPLAY_ZONE = ZoneManager.getInstance().getZoneById(200104, L2EffectZone.class);
	private static final L2EffectZone PC_BUFF_ZONE = ZoneManager.getInstance().getZoneById(200105, L2EffectZone.class);
	
	private final List<L2Attackable> _minions = new CopyOnWriteArrayList<>();
	
	private L2Attackable _mob = null;
	
	public QueenShyeed() {
		super(QueenShyeed.class.getSimpleName(), "ai/individual");
		registerMobs(SHYEED, SPIKED_STAKATO, SPIKED_STAKATO_WORKER, SPIKED_STAKATO_SORCERER, SPIKED_STAKATO_SOLDIER, SPIKED_STAKATO_DRONE);
		checkShyeed();
	}
	
	private void checkShyeed() {
		startQuestTimer("SHYEED_DEAD", 30000, null, null);
	}
	
	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		if (event.equalsIgnoreCase("SHYEED_DEAD")) {
			if (RaidBossSpawnManager.getInstance().getRaidBossStatusId(SHYEED) == StatusEnum.DEAD) {
				// Buffs
				PC_BUFF_ZONE.setEnabled(true);
				MOB_BUFF_ZONE.setEnabled(false);
				MOB_BUFF_DISPLAY_ZONE.setEnabled(false);
				// Spawns
				_mob = (L2Attackable) addSpawn(SPIKED_STAKATO, 79783, -54677, -6120, 0, false, 0);
				_minions.add(_mob);
				_mob = (L2Attackable) addSpawn(SPIKED_STAKATO, 80370, -56113, -6136, 0, false, 0);
				_minions.add(_mob);
				_mob = (L2Attackable) addSpawn(SPIKED_STAKATO_WORKER, 80061, -55042, -6120, 0, false, 0);
				_minions.add(_mob);
				_mob = (L2Attackable) addSpawn(SPIKED_STAKATO_WORKER, 79106, -56292, -6120, 0, false, 0);
				_minions.add(_mob);
				_mob = (L2Attackable) addSpawn(SPIKED_STAKATO_SORCERER, 79081, -54904, -6120, 0, false, 0);
				_minions.add(_mob);
				_mob = (L2Attackable) addSpawn(SPIKED_STAKATO_SORCERER, 79908, -56039, -6120, 0, false, 0);
				_minions.add(_mob);
				_mob = (L2Attackable) addSpawn(SPIKED_STAKATO_SOLDIER, 79473, -55709, -6128, 0, false, 0);
				_minions.add(_mob);
				_mob = (L2Attackable) addSpawn(SPIKED_STAKATO_SOLDIER, 79575, -55153, -6128, 0, false, 0);
				_minions.add(_mob);
				_mob = (L2Attackable) addSpawn(SPIKED_STAKATO_DRONE, 80318, -55624, -6120, 0, false, 0);
				_minions.add(_mob);
				_mob = (L2Attackable) addSpawn(SPIKED_STAKATO_DRONE, 78904, -55535, -6120, 0, false, 0);
				_minions.add(_mob);
			}
		}
		return super.onEvent(event, npc, player);
	}
	
	@Override
	public String onSpawn(L2Npc npc) {
		if (npc.getId() == SHYEED) {
			if (RaidBossSpawnManager.getInstance().getRaidBossStatusId(SHYEED) == StatusEnum.ALIVE) {
				PC_BUFF_ZONE.setEnabled(false);
				MOB_BUFF_ZONE.setEnabled(true);
				MOB_BUFF_DISPLAY_ZONE.setEnabled(true);
				if (_minions != null) {
					_minions.forEach(L2Attackable::decayMe);
					_minions.clear();
				}
			}
		} else {
			if (_mob != null) {
				final L2Spawn spawn = _mob.getSpawn();
				spawn.setAmount(1);
				spawn.setRespawnDelay(64);
				spawn.startRespawn();
			}
		}
		return super.onSpawn(npc);
	}
	
	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isSummon) {
		if (npc.getId() == SHYEED) {
			broadcastNpcSay(npc, Say2.NPC_ALL, NpcStringId.SHYEEDS_CRY_IS_STEADILY_DYING_DOWN);
			startQuestTimer("SHYEED_DEAD", 7000, null, null);
			PC_BUFF_ZONE.setEnabled(true);
			MOB_BUFF_ZONE.setEnabled(false);
			MOB_BUFF_DISPLAY_ZONE.setEnabled(false);
		}
		return super.onKill(npc, killer, isSummon);
	}
}