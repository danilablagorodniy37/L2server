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
package com.l2jserver.datapack.ai.npc.coach;

import static com.l2jserver.gameserver.config.Configuration.character;
import static com.l2jserver.gameserver.model.base.AcquireSkillType.TRANSFER;
import static com.l2jserver.gameserver.network.SystemMessageId.CANNOT_RESET_SKILL_LINK_BECAUSE_NOT_ENOUGH_ADENA;
import static com.l2jserver.gameserver.network.SystemMessageId.NO_MORE_SKILLS_TO_LEARN;

import java.util.Set;

import com.l2jserver.gameserver.data.xml.impl.CategoryData;
import com.l2jserver.gameserver.data.xml.impl.SkillTreesData;
import com.l2jserver.gameserver.enums.CategoryType;
import com.l2jserver.gameserver.enums.Race;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.base.ClassId;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerMenuSelected;
import com.l2jserver.gameserver.network.serverpackets.AcquireSkillList;

/**
 * Skill Transfer Coach.
 * @author Zoey76
 * @version 2.6.3.0
 */
public abstract class SkillTransferCoach extends GuildCoach {
	
	private static final int SKILL_TRANSFER_MINIMUM_LEVEL = 75;
	
	private static final Condition[] SKILL_TRANSFER_CONDITIONS = new Condition[] {
		new Condition(Race.HUMAN, CategoryType.HUMAN_CALL_CLASS),
		new Condition(Race.ELF, CategoryType.ELF_CALL_CLASS),
		new Condition(Race.DARK_ELF, CategoryType.DELF_CALL_CLASS)
	};
	
	private static final Set<Integer> TRANSFORM_IDS = Set.of(312, 313, 314, 315, 316, 317, 318);
	
	public SkillTransferCoach(String name, int[] coaches, Condition[] conditions) {
		super(name, coaches, conditions);
		bindMenuSelected(coaches);
	}
	
	@Override
	public void onMenuSelected(PlayerMenuSelected event) {
		if (event.ask() == -707) {
			final var player = event.player();
			switch (event.reply()) {
				case 1 -> {
					if (!checkConditions(player, event.npc(), SKILL_TRANSFER_CONDITIONS)) {
						showPage(player, event.npc().getId() + "-noteach.htm");
						break;
					}
					
					if (!CategoryData.getInstance().isInCategory(CategoryType.FOURTH_CLASS_GROUP, player.getClassId())) {
						showPage(player, "data/html/skill_share_level_fail.htm");
						break;
					}
					
					if (player.getLevel() <= SKILL_TRANSFER_MINIMUM_LEVEL) {
						showPage(player, "data/html/skill_share_level_fail.htm");
						break;
					}
					
					if (TRANSFORM_IDS.contains(player.getTransformationId())) {
						showPage(player, "data/html/skillenchant_notvang.htm");
						break;
					}
					
					if (player.getClassId() != ClassId.cardinal &&
						player.getClassId() != ClassId.evaSaint &&
						player.getClassId() != ClassId.shillienSaint) {
						showPage(player, "data/html/skill_share_healer_fail.htm");
						break;
					}
					
					showEtcSkillList(player);
				}
				case 2 -> {
					if (player.getRace() == Race.HUMAN && player.getClassId() == ClassId.cardinal ||
						player.getRace() == Race.ELF && player.getClassId() == ClassId.evaSaint ||
						player.getRace() == Race.DARK_ELF && player.getClassId() == ClassId.shillienSaint) {
						showPage(player, "data/html/skill_share_reset_fail.htm");
						break;
					}
					
					final var skillSharingItemId = event.npc().getTemplate().getParameters().getInt("skill_sharing_item");
					if (hasQuestItems(player, skillSharingItemId)) {
						showPage(player, "data/html/skill_share_healer_have.htm");
						break;
					}
					
					if (player.getAdena() < character().getFeeDeleteTransferSkills()) {
						player.sendPacket(CANNOT_RESET_SKILL_LINK_BECAUSE_NOT_ENOUGH_ADENA);
						break;
					}
					
					deleteAcquireSkills(player);
					takeItems(player, skillSharingItemId, -1);
					player.reduceAdena("skill_share_healer", character().getFeeDeleteTransferSkills(), event.npc(), true);
					final var tokenNum = event.npc().getTemplate().getParameters().getInt("token_num");
					player.addItem("skill_share_healer", skillSharingItemId, tokenNum, event.npc(), true);
				}
			}
		}
	}
	
	private void showEtcSkillList(L2PcInstance player) {
		final var skills = SkillTreesData.getInstance().getAvailableTransferSkills(player);
		if (!skills.isEmpty()) {
			player.sendPacket(new AcquireSkillList(TRANSFER, skills));
		} else {
			player.sendPacket(NO_MORE_SKILLS_TO_LEARN);
		}
	}
	
	private static void deleteAcquireSkills(L2PcInstance player) {
		for (var skillLearn : SkillTreesData.getInstance().getTransferSkillTree(player.getClassId()).values()) {
			player.removeSkill(skillLearn.getSkillId());
		}
	}
}
