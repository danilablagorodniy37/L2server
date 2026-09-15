"""Removes AI scripts of post-Interlude NPCs and zones from AILoader.java.

The list was built from the NPC ids each script references: scripts whose
NPCs are all absent from aCis (and not Kamael), plus mixed scripts that
clearly belong to later chronicles. Fortress scripts stay until fortresses
are decided on. Script sources stay in place.

Usage: python tools/interlude/build_ai.py
"""

import re

import datasets as ds

LOADER = ds.GAME / "script" / "com" / "l2jserver" / "datapack" / "ai" / "AILoader.java"

REMOVED = {
	# Zones and monsters reworked after Interlude
	"BeastFarm", "DragonValley", "GiantsCave", "IsleOfPrayer", "LairOfAntharas", "MonasteryOfSilence",
	"PavelArchaic", "PlainsOfLizardman", "PrisonGuards", "RangeGuard", "Remnants", "Sandstorms",
	"SelMahumDrill", "SelMahumSquad", "DenOfEvil", "StakatoNest", "ForgeOfTheGods", "Rooney", "TarBeetle",
	"Anais", "BlackdaggerWing", "BleedingFly", "BloodyBerserker", "BloodyKarik", "BloodyKarinness",
	"CrimsonHatuOtis", "DarkWaterDragon", "DivineBeast", "DrakosWarrior", "DustRider", "EmeraldHorn",
	"EvasGiftBox", "FrightenedRagnaOrc", "GraveRobbers", "Knoriks", "MuscleBomber", "NecromancerOfTheValley",
	"QueenShyeed", "RagnaOrcCommander", "RagnaOrcHero", "RagnaOrcSeer", "ShadowSummoner", "SinWardens",
	"DragonVortex", "SeparatedSoul",
	# Bosses added after Interlude
	"Beleth", "Epidos", "GiganticGolem",
	# NPCs of later systems: Nevit, fame, transformations, subclass certification, territory war,
	# Freya, Gracia, Hellbound, Kamaloka and other instances, block checker, minigames
	"NevitsHerald", "PriestOfBlessing", "FameManager", "AvantGarde", "SubclassCertification",
	"TerritoryManagers", "MercenaryCaptain", "Jinia", "Sirra", "Rafforty", "Alarm", "Minigame", "Rignos",
	"GolemTrader", "Asher", "Klemis", "MithrilMinesTeleporter", "StakatoNestTeleporter",
	"SteelCitadelTeleport", "Survivor", "TeleportToUndergroundColiseum", "Tolonis", "WeaverOlf",
	"DelusionTeleport", "ToIVortex", "HandysBlockCheckerEvent",
	# Beast Farm: H5 Beast Handler's Whip (Tunatun keeps his Interlude dialog)
	"Tunatun",
}


def main():
	text = LOADER.read_text(encoding="utf-8").replace("\r\n", "\n")
	lines = []
	removed = set()
	for line in text.split("\n"):
		m = re.match(r"import com\.l2jserver\.datapack\.ai\.[\w.]+\.(\w+);$", line) or re.match(r"\t+(\w+)\.class,?\s*(//.*)?$", line)
		if m and m.group(1) in REMOVED:
			removed.add(m.group(1))
			continue
		lines.append(line)
	text = re.sub(r",(\s*\n\t\};)", r"\1", "\n".join(lines))
	LOADER.write_text(text, encoding="utf-8", newline="\n")
	missing = REMOVED - removed
	print(f"Removed {len(removed)} AI scripts" + (f", not found: {sorted(missing)}" if missing else ""))


if __name__ == "__main__":
	main()
