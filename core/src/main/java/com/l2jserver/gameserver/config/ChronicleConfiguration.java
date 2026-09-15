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
package com.l2jserver.gameserver.config;

import static org.aeonbits.owner.Config.LoadType.MERGE;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;

/**
 * Chronicle Configuration.<br>
 * Switches for content added after Interlude. Read once at server start.
 */
@Sources({
	"file:${L2J_HOME}/custom/game/config/chronicle.properties",
	"file:./config/chronicle.properties",
	"classpath:config/chronicle.properties"
})
@LoadPolicy(MERGE)
public interface ChronicleConfiguration extends Config {

	@Key("EnableInstances")
	boolean enableInstances();

	@Key("EnableGracia")
	boolean enableGracia();

	@Key("EnableHellbound")
	boolean enableHellbound();

	@Key("EnableTerritoryWar")
	boolean enableTerritoryWar();

	@Key("EnableAttributes")
	boolean enableAttributes();
}
