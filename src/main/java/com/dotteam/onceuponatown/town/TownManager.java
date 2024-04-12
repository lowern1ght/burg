package com.dotteam.onceuponatown.town;

import com.dotteam.onceuponatown.culture.Culture;
import com.dotteam.onceuponatown.town.map.TownMap;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.List;

public class TownManager {
    public static int TOWN_TICK_RATE = SharedConstants.TICKS_PER_SECOND * 5;

    public static void createTownFromWorldGen(ServerLevel level, Culture culture, TownMap townMap) {
        TownSavedData savedData = TownSavedData.get(level);
        if (savedData != null) {
            String name = "plains" + Mth.nextInt(RandomSource.create(), 0, 100);
            Town town = Town.create(level, culture, name, townMap);
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(town.getName() + " discovered at " + town.getCenterPosition().toShortString()), false);
            savedData.addtown(town);
        }
    }

    public static Town createTownFromCommand(ServerLevel level, Culture culture, String biome, TownMap townMap) {
        return null;
    }

    public static void removeTownByID(String townID) {

    }

    public static List<Town> getTownList(ServerLevel level) {
        TownSavedData savedData = TownSavedData.get(level);
        if (savedData != null) {
            return savedData.gettowns();
        } else {
            return null;
        }
    }

    public static void tickTowns(ServerLevel level) {
        if (level.getServer().getTickCount() % TOWN_TICK_RATE == 0) {
            List<Town> towns = getTownList(level);
            if (towns != null && !towns.isEmpty()) {
                for (Town town : towns) {
                    town.tick();
                }
            }
        }
    }
}

