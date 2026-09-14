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
import static com.l2jserver.gameserver.model.base.AcquireSkillType.CLASS;
import static com.l2jserver.gameserver.network.SystemMessageId.DO_NOT_HAVE_FURTHER_SKILLS_TO_LEARN_S1;
import static com.l2jserver.gameserver.network.SystemMessageId.NO_MORE_SKILLS_TO_LEARN;
import static com.l2jserver.gameserver.network.SystemMessageId.NO_SKILLS_TO_LEARN_RETURN_AFTER_S1_CLASS_CHANGE;

import java.util.HashSet;

import com.l2jserver.datapack.ai.npc.AbstractNpcAI;
import com.l2jserver.gameserver.data.xml.impl.CategoryData;
import com.l2jserver.gameserver.data.xml.impl.SkillTreesData;
import com.l2jserver.gameserver.enums.CategoryType;
import com.l2jserver.gameserver.enums.Race;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.base.ClassId;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerLearnSkillRequested;
import com.l2jserver.gameserver.model.events.impl.character.player.PlayerSkillLearned;
import com.l2jserver.gameserver.network.serverpackets.AcquireSkillList;
import com.l2jserver.gameserver.network.serverpackets.NpcHtmlMessage;
import com.l2jserver.gameserver.network.serverpackets.SystemMessage;

/**
 * Guild Coach.
 * @author Zoey76
 * @version 2.6.3.0
 */
public abstract class GuildCoach extends AbstractNpcAI {
	
	protected static final Integer MALE = 1;
	
	protected static final Integer FEMALE = 2;
	
	private final Condition[] conditions;
	
	public GuildCoach(String name, int[] coaches, Condition[] conditions) {
		super(name, "ai/npc/coach");
		this.conditions = conditions;
		bindStartNpc(coaches);
		bindTalk(coaches);
		bindFirstTalk(coaches);
		bindLearnSkillRequested(coaches);
		bindSkillLearned(coaches);
	}
	
	public record Condition(Race race, CategoryType category, Integer gender) {
		public Condition(Race race, CategoryType category) {
			this(race, category, null);
		}
	}
	
	@Override
	public void onLearnSkillRequested(PlayerLearnSkillRequested event) {
		if (character().skillLearn() && event.classId() == null) {
			showCustomLearnSkill(event.player(), event.npc(), conditions);
			return;
		}
		
		if (!checkConditions(event.player(), event.npc(), conditions)) {
			showPage(event.player(), event.npc().getId() + "-noteach.htm");
			return;
		}
		
		showSkillList(event.player(), character().skillLearn() ? event.classId() : event.player().getLearningClass());
	}
	
	@Override
	public void onSkillLearned(PlayerSkillLearned event) {
		showSkillList(event.player(), event.player().getLearningClass());
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player) {
		return npc.getId() + ".htm";
	}
	
	@Override
	public String onEvent(String event, L2Npc npc, L2PcInstance player) {
		return switch (event) {
			case "0" -> npc.getId() + ".htm";
			case "1", "1a", "1b", "1t", "2", "3", "4", "5" -> npc.getId() + "-" + event + ".htm";
			default -> super.onEvent(event, npc, player);
		};
	}
	
	/**
	 * Checks if the given conditions are met for a player and NPC.
	 * @param player the player
	 * @param npc the NPC
	 * @param conditions an array of conditions to check
	 * @return {@code true} if at least one condition is met, {@code false} otherwise
	 */
	protected static boolean checkConditions(L2PcInstance player, L2Npc npc, Condition[] conditions) {
		for (var condition : conditions) {
			if (checkCondition(player, npc, condition)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Checks if the given condition is met for a player and NPC.
	 * @param player the player
	 * @param npc the NPC
	 * @param condition the condition to check
	 * @return {@code true} if the condition is met, {@code false} otherwise
	 */
	private static boolean checkCondition(L2PcInstance player, L2Npc npc, Condition condition) {
		return npc.getRace() == condition.race() &&
			CategoryData.getInstance().isInCategory(condition.category(), player.getClassId()) &&
			(condition.gender() == null || npc.getTemplate().getParameters().getInt("gender", MALE) == condition.gender());
	}
	
	/**
	 * Displays the Skill Tree for a given player and class ID.
	 * @param player the player
	 * @param classId the class ID
	 */
	private static void showSkillList(L2PcInstance player, ClassId classId) {
		final var skills = SkillTreesData.getInstance().getAvailableSkills(player, classId, false, false);
		player.setLearningClass(classId);
		
		if (skills.isEmpty()) {
			final var skillTree = SkillTreesData.getInstance().getCompleteClassSkillTree(classId);
			final int minLevel = SkillTreesData.getInstance().getMinLevelForNewSkill(player, skillTree);
			if (minLevel > 0) {
				final var sm = SystemMessage.getSystemMessage(DO_NOT_HAVE_FURTHER_SKILLS_TO_LEARN_S1);
				sm.addInt(minLevel);
				player.sendPacket(sm);
			} else {
				if (player.getClassId().level() == 1) {
					final var sm = SystemMessage.getSystemMessage(NO_SKILLS_TO_LEARN_RETURN_AFTER_S1_CLASS_CHANGE);
					sm.addInt(2);
					player.sendPacket(sm);
				} else {
					player.sendPacket(NO_MORE_SKILLS_TO_LEARN);
				}
			}
		} else {
			player.sendPacket(new AcquireSkillList(CLASS, skills));
		}
	}
	
	// TODO(Zoey76): Test this custom code.
	private static void showCustomLearnSkill(L2PcInstance player, L2Npc npc, Condition[] conditions) {
		final var text = new StringBuilder("<html><body><center>Skill Learning:</center><br>")
			.append("Skills of your class are the easiest to learn.<br>")
			.append("Skills of another class of your race are a little harder.<br>")
			.append("Skills for classes of another race are extremely difficult.<br>")
			.append("But the hardest of all to learn are the ")
			.append(player.getClassId().isMage() ? "fighter" : "mage")
			.append("skills!<br>");
		
		final var classes = new HashSet<ClassId>();
		for (var condition : conditions) {
			for (var classId : CategoryData.getInstance().getCategoryByType(condition.category())) {
				classes.add(ClassId.getClassId(classId));
			}
		}
		
		if (classes.isEmpty()) {
			text.append("No Skills.<br>");
		} else {
			int count = 0;
			var classCheck = player.getClassId();
			
			while ((count == 0) && (classCheck != null)) {
				for (var classId : classes) {
					if (classId.level() > classCheck.level()) {
						continue;
					}
					
					if (SkillTreesData.getInstance().getAvailableSkills(player, classId, false, false).isEmpty()) {
						continue;
					}
					
					text.append("<a action=\"bypass -h learn_skill ")
						.append(classId.getId())
						.append("\">Learn ")
						.append(classId)
						.append("'s class Skills</a><br>\n");
					count++;
				}
				classCheck = classCheck.getParent();
			}
		}
		text.append("</body></html>");
		
		player.sendPacket(new NpcHtmlMessage(npc.getObjectId(), text.toString()));
	}
}
