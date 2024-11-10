package org.dawnoftime.onceuponatown.town;

import org.dawnoftime.onceuponatown.culture.Culture;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.dawnoftime.onceuponatown.town.generation.TownMap;

import java.util.List;
import java.util.UUID;

public class TownManager {
    public static int TOWN_TICK_RATE_SECONDS = 5;

    public static void createNewTownWorldGen(ServerLevel level, Culture culture, TownMap townMap) {
        TownSavedData savedData = TownSavedData.get(level);
        if (savedData != null) {
            String name = "plains" + Mth.nextInt(RandomSource.create(), 0, 100);
            Town town = Town.createWorldGenOld(level, culture, name, townMap);
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(town.getName() + " discovered at " + town.getCenterPosition().toShortString()), false);
            savedData.addTown(town);
        }
    }

    public static void createNewTownPlayerCommand(ServerLevel level, Culture culture, String biome, TownMap townMap) {
    }

    /**
     * Delete town instance, keep structures, convert npcs to wanderers
     * @param level Level of the town to delete
     * @param uuid UUID of the town to delete
     */
    public static void softDeleteTown(ServerLevel level, UUID uuid) {
        Town town = getTownById(level, uuid);
        if (town != null) {
            town.softDelete();
        }
        TownSavedData savedData = TownSavedData.get(level);
        if (savedData != null) {
            savedData.removeTown(town);
        }
    }

    /**
     * Delete town instance, destroy structures, kill npcs
     * @param level Level of the town to delete
     * @param uuid UUID of the town to delete
     */
    public static void hardDeleteTown(ServerLevel level, UUID uuid) {
        Town town = getTownById(level, uuid);
        if (town != null) {
            town.hardDelete();
        }
        TownSavedData savedData = TownSavedData.get(level);
        if (savedData != null) {
            savedData.removeTown(town);
        }
    }

    public static void tickTowns(ServerLevel level) {
        List<Town> towns;
        int dayTime = (int)level.getDayTime();
        if (dayTime == 0 || dayTime == 6000 || dayTime == 13000) {
            towns = getTowns(level);
            if (towns != null && !towns.isEmpty()) {
                for (Town town : towns) {
                    switch (dayTime) {
                        case 0 -> town.ringTownBell(Town.TownBellRingType.DAWN);
                        case 6000 -> town.ringTownBell(Town.TownBellRingType.NOON);
                        case 13000 -> town.ringTownBell(Town.TownBellRingType.DUSK);
                    }
                }
            }
        }

        if ((level.getServer().getTickCount() % TOWN_TICK_RATE_SECONDS * SharedConstants.TICKS_PER_SECOND) == 0) {
            towns = getTowns(level);
            if (towns != null && !towns.isEmpty()) {
                towns.forEach(Town::tick);
                for (Town town : towns) {
                    town.tick();
                }
            }
        }
    }

    public static List<Town> getTowns(ServerLevel level) {
        TownSavedData savedData = TownSavedData.get(level);
        return savedData != null ? savedData.getTowns() : null;
    }

    public static Town getTownById(ServerLevel level, UUID uuid) {
        var towns = getTowns(level);
        if (towns != null && !towns.isEmpty()) {
            for (Town town : towns) {
                if (town.getUuid().equals(uuid)) {
                    return town;
                }
            }
        }
        return null;
    }

    public static Town getTownByName(ServerLevel level, String name) {
        var towns = getTowns(level);
        if (towns != null && !towns.isEmpty()) {
            for (Town town : towns) {
                if (town.getName().equals(name)) {
                    return town;
                }
            }
        }
        return null;
    }
}

