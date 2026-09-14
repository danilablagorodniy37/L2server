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
package com.l2jserver.datapack.ai.npc.Teleports.GuardianBorder;

import java.util.HashMap;
import java.util.Map;

import com.l2jserver.datapack.ai.npc.AbstractNpcAI;
import com.l2jserver.gameserver.instancemanager.DimensionalRiftManager;
import com.l2jserver.gameserver.instancemanager.QuestManager;
import com.l2jserver.gameserver.instancemanager.ZoneManager;
import com.l2jserver.gameserver.model.L2Party;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestState;
import com.l2jserver.gameserver.model.zone.L2ZoneType;

/**
 * Guardian of Border AI
 * @author Charus
 */
public final class GuardianBorder extends AbstractNpcAI {
	
	// NPCs
	private final static int[] NPCS = {
		// Dungeon 0
		31865,
		31866,
		31867,
		31868,
		31869,
		31870,
		31871,
		31872,
		31873,
		// Dungeon 1
		31874,
		31875,
		31876,
		31877,
		31878,
		31879,
		31880,
		31881,
		31882,
		// Dungeon 2
		31883,
		31884,
		31885,
		31886,
		31887,
		31888,
		31889,
		31890,
		31891,
		// Dungeon 3
		31892,
		31893,
		31894,
		31895,
		31896,
		31897,
		31898,
		31899,
		31900,
		// Dungeon 4
		31901,
		31902,
		31903,
		31904,
		31905,
		31906,
		31907,
		31908,
		31909,
		// Dungeon 5
		31910,
		31911,
		31912,
		31913,
		31914,
		31915,
		31916,
		31917,
		31918
	};
	
	private final static int[][] ZONES = {
		// Dungeon 0
		{
			31865,
			1612301
		},
		{
			31866,
			1612302
		},
		{
			31867,
			1612303
		},
		{
			31868,
			1612304
		},
		{
			31869,
			1612305
		},
		{
			31870,
			1612306
		},
		{
			31871,
			1612307
		},
		{
			31872,
			1612308
		},
		{
			31873,
			1612309
		},
		// Dungeon 1
		{
			31874,
			1612401
		},
		{
			31875,
			1612402
		},
		{
			31876,
			1612403
		},
		{
			31877,
			1612404
		},
		{
			31878,
			1612405
		},
		{
			31879,
			1612406
		},
		{
			31880,
			1612407
		},
		{
			31881,
			1612408
		},
		{
			31882,
			1612409
		},
		// Dungeon 2
		{
			31883,
			1612501
		},
		{
			31884,
			1612502
		},
		{
			31885,
			1612503
		},
		{
			31886,
			1612504
		},
		{
			31887,
			1612505
		},
		{
			31888,
			1612506
		},
		{
			31889,
			1612507
		},
		{
			31890,
			1612508
		},
		{
			31891,
			1612509
		},
		// Dungeon 3
		{
			31892,
			1612601
		},
		{
			31893,
			1612602
		},
		{
			31894,
			1612603
		},
		{
			31895,
			1612604
		},
		{
			31896,
			1612605
		},
		{
			31897,
			1612606
		},
		{
			31898,
			1612607
		},
		{
			31899,
			1612608
		},
		{
			31900,
			1612609
		},
		// Dungeon 4
		{
			31901,
			1612701
		},
		{
			31902,
			1612702
		},
		{
			31903,
			1612703
		},
		{
			31904,
			1612704
		},
		{
			31905,
			1612705
		},
		{
			31906,
			1612706
		},
		{
			31907,
			1612707
		},
		{
			31908,
			1612708
		},
		{
			31909,
			1612709
		},
		// Dungeon 5
		{
			31910,
			1612801
		},
		{
			31911,
			1612802
		},
		{
			31912,
			1612803
		},
		{
			31913,
			1612804
		},
		{
			31914,
			1612805
		},
		{
			31915,
			1612806
		},
		{
			31916,
			1612807
		},
		{
			31917,
			1612808
		},
		{
			31918,
			1612809
		}
	};
	
	private static final int Q00635_IntoTheDimensionalRift = 635;
	
	private static final Map<Integer, Integer> _zoneList = new HashMap<>();
	
	public GuardianBorder() {
		super(GuardianBorder.class.getSimpleName(), "ai/npc/Teleports");
		
		bindSpawn(NPCS);
		bindStartNpc(NPCS);
		bindFirstTalk(NPCS);
		
		for (int[] zone : ZONES) {
			_zoneList.put(zone[0], zone[1]);
		}
	}
	
	@Override
	public String onSpawn(L2Npc npc) {
		startQuestTimer("2001", 1000, npc, null);
		
		return super.onSpawn(npc);
	}
	
	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		if (event.endsWith(".htm")) {
			return event;
		}
		
		if (event.equalsIgnoreCase("2001")) {
			L2ZoneType zone = ZoneManager.getInstance().getZoneById(_zoneList.get(npc.getId()));
			for (L2PcInstance pc : zone.getPlayersInside()) {
				if (pc != null) {
					L2Party party = pc.getParty();
					if (party == null) {
						for (L2PcInstance kick : zone.getPlayersInside()) {
							DimensionalRiftManager.getInstance().teleportToWaitingRoom(kick);
							
							if (haveMemo(kick, Q00635_IntoTheDimensionalRift)) {
								Quest q635 = QuestManager.getInstance().getQuest(Q00635_IntoTheDimensionalRift);
								QuestState qs635 = q635.getQuestState(kick, true);
								
								qs635.setMemoStateEx(1, -1);
							}
						}
						
						break;
					}
				}
			}
			
			startQuestTimer("2001", 10000, npc, null);
		}
		
		int ask = Integer.parseInt(event.split(";")[0]);
		switch (ask) {
			case -1000 -> {
				int reply = Integer.parseInt(event.split(";")[1]);
				switch (reply) {
					case 1 -> {
						final L2Party party0 = player.getParty();
						if (party0 != null) {
							final L2PcInstance leader = party0.getLeader();
							if (player.getObjectId() == leader.getObjectId()) {
								party0.getDimensionalRift().manualExitRift(player);
								
								for (L2PcInstance c0 : party0.getMembers()) {
									if (haveMemo(c0, Q00635_IntoTheDimensionalRift)) {
										Quest q635 = QuestManager.getInstance().getQuest(Q00635_IntoTheDimensionalRift);
										QuestState qs635 = q635.getQuestState(c0, true);
										
										qs635.setMemoStateEx(1, -1);
									}
								}
							} else {
								return "tel_dungeon_npc_notleader.htm";
							}
						} else {
							DimensionalRiftManager.getInstance().teleportToWaitingRoom(player);
							
							if (haveMemo(player, Q00635_IntoTheDimensionalRift)) {
								Quest q635 = QuestManager.getInstance().getQuest(Q00635_IntoTheDimensionalRift);
								QuestState qs635 = q635.getQuestState(player, true);
								
								qs635.setMemoStateEx(1, -1);
							}
						}
					}
					case 2 -> {
						final L2Party party0 = player.getParty();
						if (party0 != null) {
							final L2PcInstance leader = party0.getLeader();
							if (player.getObjectId() == leader.getObjectId()) {
								Quest q635 = QuestManager.getInstance().getQuest(Q00635_IntoTheDimensionalRift);
								QuestState qs635 = q635.getQuestState(player, true);
								
								int i0 = qs635.getMemoStateEx(1);
								if (i0 == 1) {
									qs635.setMemoStateEx(1, 0);
									
									party0.getDimensionalRift().manualTeleport(player);
								} else {
									return "tel_dungeon_npc_nomorechance.htm";
								}
							} else {
								return "tel_dungeon_npc_notleader.htm";
							}
						} else {
							return "tel_dungeon_npc_notleader.htm";
						}
					}
				}
			}
		}
		
		return super.onEvent(event, npc, player);
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player) {
		final String roomIndex = npc.getTemplate().getParameters().getString("RoomIndex");
		return "tel_dungeon_npc_hi" + roomIndex + ".htm";
	}
}