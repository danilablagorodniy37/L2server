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
package com.l2jserver.datapack.ai.npc.RiftWatcher;

import com.l2jserver.datapack.ai.npc.AbstractNpcAI;
import com.l2jserver.gameserver.SevenSigns;
import com.l2jserver.gameserver.instancemanager.DimensionalRiftManager;
import com.l2jserver.gameserver.instancemanager.QuestManager;
import com.l2jserver.gameserver.model.L2Party;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.QuestState;
import com.l2jserver.gameserver.network.SystemMessageId;
import com.l2jserver.gameserver.util.Util;

/**
 * Rift Watcher AI
 * @author Charus
 */
public final class RiftWatcher extends AbstractNpcAI {
	
	private final static int[] NPCS = {
		31488,
		31489,
		31490,
		31491,
		31492,
		31493
	};
	
	private static final int Q00255_Tutorial = 255;
	private static final int Q00635_IntoTheDimensionalRift = 635;
	
	private static final int DIMENSIONAL_FRAGMENT = 7079;
	
	private static final double WEIGHT_LIMIT = 0.80;
	private static final int MAX_QUEST_COUNT = 40;
	
	private static final int MAX_DISTANCE = 1500;
	
	public RiftWatcher() {
		super(RiftWatcher.class.getSimpleName(), "ai/npc");
		
		bindStartNpc(NPCS);
		bindFirstTalk(NPCS);
	}
	
	private int getItemsNeeded(int npcId) {
		return switch (npcId) {
			case 31488 -> 21;
			case 31489 -> 24;
			case 31490 -> 27;
			case 31491 -> 30;
			case 31492 -> 33;
			case 31493 -> 36;
			default -> 36;
		};
	}
	
	private byte getType(int npcId) {
		return switch (npcId) {
			case 31488 -> 1;
			case 31489 -> 2;
			case 31490 -> 3;
			case 31491 -> 4;
			case 31492 -> 5;
			case 31493 -> 6;
			default -> 1;
		};
	}
	
	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		if (event.endsWith(".htm")) {
			return "rift_watcher_" + event;
		}
		
		final SevenSigns ss = SevenSigns.getInstance();
		final int ssqPart = ss.getPlayerCabal(player.getObjectId());
		
		int ask = Integer.parseInt(event.split(";")[0]);
		switch (ask) {
			case 635 -> {
				if ((player.getInventory().getSize(false) >= (player.getInventoryLimit() * WEIGHT_LIMIT)) || (player.getCurrentLoad() >= (player.getMaxLoad() * WEIGHT_LIMIT))) {
					player.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
					return null;
				}
				
				int reply = Integer.parseInt(event.split(";")[1]);
				switch (reply) {
					case 1 -> {
						final byte type = getType(npc.getId());
						final int itemsNeed = getItemsNeeded(npc.getId());
						
						int i1 = 0;
						
						L2Party party0 = player.getParty();
						if (party0 == null) {
							return "rift_watcher_1_q0635_01.htm";
						}
						
						L2PcInstance c0 = party0.getLeader();
						if (c0.getObjectId() != player.getObjectId()) {
							String html = getHtm(player.getHtmlPrefix(), "rift_watcher_1_q0635_02.htm");
							html = html.replace("<?name?>", c0.getName());
							return html;
						}
						
						int i6 = getGameTicks();
						
						for (L2PcInstance c1 : party0.getMembers()) {
							if ((c1.getAllActiveQuests().size() > (MAX_QUEST_COUNT - 1)) && !haveMemo(c1, Q00635_IntoTheDimensionalRift)) {
								i1 = 2;
								
								String html = getHtm(player.getHtmlPrefix(), "rift_watcher_1_q0635_03a.htm");
								html = html.replace("<?name?>", c1.getName());
								return html;
							}
							
							if (getQuestItemsCount(c1, DIMENSIONAL_FRAGMENT) < itemsNeed) {
								Quest q635 = QuestManager.getInstance().getQuest(Q00635_IntoTheDimensionalRift);
								QuestState qs635 = q635.getQuestState(c1, true);
								
								int i5 = qs635.getMemoStateEx(1);
								if ((qs635.getState() != 2) || ((i6 - i5) > 3600)) {
									i1 = 1;
									
									String html = getHtm(player.getHtmlPrefix(), "rift_watcher_1_q0635_03.htm");
									html = html.replace("<?name?>", c1.getName());
									return html;
								}
							}
						}
						
						if (i1 == 0) {
							if (!DimensionalRiftManager.getInstance().isAllowedEnter(type)) {
								return "rift_watcher_1_q0635_04.htm";
							}
							
							i6 = getGameTicks();
							for (L2PcInstance c1 : player.getParty().getMembers()) {
								final double distance = Util.calculateDistance(npc, c0, true, false);
								if (distance <= MAX_DISTANCE) {
									Quest q635 = QuestManager.getInstance().getQuest(Q00635_IntoTheDimensionalRift);
									QuestState qs635 = q635.getQuestState(c1, true);
									
									if (qs635.getMemoState() == 2) {
										qs635.setMemoState(0);
									} else {
										takeItems(c1, DIMENSIONAL_FRAGMENT, itemsNeed);
									}
									
									qs635.setMemoState(i6);
									
									if (c1 == c0) {
										qs635.setMemoStateEx(1, 1);
									} else {
										qs635.setMemoStateEx(1, 0);
									}
								}
							}
							
							showPage(player, "rift_watcher_1_q0635_05.htm");
							DimensionalRiftManager.getInstance().start(player, type, npc);
						}
					}
					case 2 -> {
						Quest q255 = QuestManager.getInstance().getQuest(Q00255_Tutorial);
						QuestState qs255 = q255.getQuestState(player, true);
						
						Quest q635 = QuestManager.getInstance().getQuest(Q00635_IntoTheDimensionalRift);
						QuestState qs635 = q635.getQuestState(player, true);
						
						int i0 = qs255.getMemoStateEx(1);
						int i1 = i0 % 10000;
						int i2 = ((i0 - i1) + 5) / 10000;
						int i6 = 0;
						
						qs635.exitQuest(true, false);
						
						if (ssqPart == SevenSigns.CABAL_DAWN) {
							i6 = 1;
						} else {
							i6 = 2;
						}
						
						if ((i1 >= 95) && (i1 < 195)) {
							switch (i6) {
								case 1 -> {
									player.teleToLocation(-80542, 150315, -3040);
								}
								case 2 -> {
									player.teleToLocation(-82340, 151575, -3120);
								}
							}
						} else {
							if ((i1 >= 195) && (i1 < 295)) {
								switch (i6) {
									case 1 -> {
										player.teleToLocation(-13996, 121413, -2984);
									}
									case 2 -> {
										player.teleToLocation(-14727, 124002, -3112);
									}
								}
							} else {
								if ((i1 >= 295) && (i1 < 395)) {
									switch (i6) {
										case 1 -> {
											player.teleToLocation(16320, 142915, -2696);
										}
										case 2 -> {
											player.teleToLocation(18501, 144673, -3056);
										}
									}
								} else {
									if ((i1 >= 395) && (i1 < 495)) {
										switch (i6) {
											case 1 -> {
												player.teleToLocation(83312, 149236, -3400);
											}
											case 2 -> {
												player.teleToLocation(81572, 148580, -3464);
											}
										}
									} else {
										if ((i1 >= 495) && (i1 < 595)) {
											switch (i6) {
												case 1 -> {
													player.teleToLocation(111359, 220959, -3544);
												}
												case 2 -> {
													player.teleToLocation(112441, 220149, -3544);
												}
											}
										} else {
											if ((i1 >= 595) && (i1 < 695)) {
												switch (i6) {
													case 1 -> {
														player.teleToLocation(83057, 53983, -1488);
													}
													case 2 -> {
														player.teleToLocation(82842, 54613, -1520);
													}
												}
											} else {
												if ((i1 >= 695) && (i1 < 795)) {
													switch (i6) {
														case 1 -> {
															player.teleToLocation(146955, 26690, -2200);
														}
														case 2 -> {
															player.teleToLocation(147528, 28899, -2264);
														}
													}
												} else {
													if ((i1 >= 795) && (i1 < 895)) {
														switch (i6) {
															case 1 -> {
																player.teleToLocation(115206, 74775, -2600);
															}
															case 2 -> {
																player.teleToLocation(116651, 77512, -2688);
															}
														}
													} else {
														if ((i1 >= 995) && (i1 < 1095)) {
															switch (i6) {
																case 1 -> {
																	player.teleToLocation(148326, -55533, -2776);
																}
																case 2 -> {
																	player.teleToLocation(149968, -56645, -2976);
																}
															}
														} else {
															if ((i1 >= 1095) && (i0 < 1195)) {
																switch (i6) {
																	case 1 -> {
																		player.teleToLocation(45605, -50360, -792);
																	}
																	case 2 -> {
																		player.teleToLocation(44505, -48331, -792);
																	}
																}
															} else {
																if ((i1 >= 1195) && (i0 < 1295)) {
																	switch (i6) {
																		case 1 -> {
																			player.teleToLocation(86730, -143148, -1336);
																		}
																		case 2 -> {
																			player.teleToLocation(85048, -142046, -1536);
																		}
																	}
																} else {
																	if (i2 == 1) {
																		player.teleToLocation(-41443, 210030, -5080);
																	} else {
																		if (i2 == 2) {
																			player.teleToLocation(-53034, -250421, -7935);
																		} else {
																			if (i2 == 3) {
																				player.teleToLocation(45160, 123605, -5408);
																			} else {
																				if (i2 == 4) {
																					player.teleToLocation(46488, 170184, -4976);
																				} else {
																					if (i2 == 5) {
																						player.teleToLocation(111521, 173905, -5432);
																					} else {
																						if (i2 == 6) {
																							player.teleToLocation(-20395, -250930, -8191);
																						} else {
																							if (i2 == 7) {
																								player.teleToLocation(-21482, 77253, -5168);
																							} else {
																								if (i2 == 8) {
																									player.teleToLocation(140688, 79565, -5424);
																								} else {
																									if (i2 == 9) {
																										player.teleToLocation(-52007, 78986, -4736);
																									} else {
																										if (i2 == 10) {
																											player.teleToLocation(118547, 132669, -4824);
																										} else {
																											if (i2 == 11) {
																												player.teleToLocation(172562, -17730, -4896);
																											} else {
																												if (i2 == 12) {
																													player.teleToLocation(83344, 209110, -5432);
																												} else {
																													if (i2 == 13) {
																														player.teleToLocation(-19154, 13415, -4896);
																													} else {
																														if (i2 == 14) {
																															player.teleToLocation(12747, -248614, -9607);
																														} else {
																															if (i2 == 21) {
																																player.teleToLocation(-41559, 209140, -5080);
																															} else {
																																if (i2 == 22) {
																																	player.teleToLocation(42448, 143943, -5376);
																																} else {
																																	if (i2 == 23) {
																																		player.teleToLocation(45239, 124522, -5408);
																																	} else {
																																		if (i2 == 24) {
																																			player.teleToLocation(45680, 170299, -4976);
																																		} else {
																																			if (i2 == 25) {
																																				player.teleToLocation(110659, 174008, -5432);
																																			} else {
																																				if (i2 == 26) {
																																					player.teleToLocation(77132, 78399, -5120);
																																				} else {
																																					if (i2 == 27) {
																																						player.teleToLocation(-22408, 77375, -5168);
																																					} else {
																																						if (i2 == 28) {
																																							player.teleToLocation(139807, 79675, -5424);
																																						} else {
																																							if (i2 == 29) {
																																								player.teleToLocation(-53177, 79100, -4736);
																																							} else {
																																								if (i2 == 30) {
																																									player.teleToLocation(117647, 132801, -4824);
																																								} else {
																																									if (i2 == 31) {
																																										player.teleToLocation(171684, -17602, -4896);
																																									} else {
																																										if (i2 == 32) {
																																											player.teleToLocation(82456, 209218, -5432);
																																										} else {
																																											if (i2 == 33) {
																																												player.teleToLocation(-20105, 13505, -4896);
																																											} else {
																																												if (i2 == 34) {
																																													player.teleToLocation(113299, 84547, -6536);
																																												}
																																											}
																																										}
																																									}
																																								}
																																							}
																																						}
																																					}
																																				}
																																			}
																																		}
																																	}
																																}
																															}
																														}
																													}
																												}
																											}
																										}
																									}
																								}
																							}
																						}
																					}
																				}
																			}
																		}
																	}
																}
															}
														}
													}
												}
											}
										}
									}
								}
							}
						}
						
						return "rift_watcher_1_q0635_06.htm";
					}
					case 3 -> {
						if (ss.isCompetitionPeriod()) {
							if (ssqPart != SevenSigns.CABAL_DAWN) {
								switch (getRandom(2)) {
									case 0 -> {
										player.teleToLocation(-81328, 86536, -5152);
									}
									case 1 -> {
										player.teleToLocation(-81262, 86468, -5152);
									}
									case 2 -> {
										player.teleToLocation(-81248, 86582, -5152);
									}
								}
								
								return "rift_watcher_1_q0635_10.htm";
							}
							
							if (ssqPart == SevenSigns.CABAL_DAWN) {
								switch (getRandom(2)) {
									case 0 -> {
										player.teleToLocation(-80316, 111356, -4896);
									}
									case 1 -> {
										player.teleToLocation(-80226, 111290, -4896);
									}
									case 2 -> {
										player.teleToLocation(-80217, 111435, -4896);
									}
								}
								
								return "rift_watcher_1_q0635_10.htm";
							}
							
							return "rift_watcher_1_q0635_11.htm";
						}
						
						return "rift_watcher_1_q0635_12.htm";
					}
				}
			}
		}
		
		return super.onEvent(event, npc, player);
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player) {
		return "rift_watcher_1001.htm";
	}
}