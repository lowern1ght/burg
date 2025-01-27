package org.dawnoftime.onceuponatown.town;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.dawnoftime.onceuponatown.Config;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;

public class LevelTowns extends SavedData {
    private final ServerLevel level;
    private final HashMap<Integer, Town> towns = new HashMap<>();
    private int nextAvailableId;

    public static @NotNull LevelTowns of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent((tag) -> new LevelTowns(level, tag), () -> new LevelTowns(level), "ouat_towns");
    }

    private LevelTowns(ServerLevel level) {
        this.level = level;
        this.nextAvailableId = 1;
    }

    private LevelTowns(ServerLevel level, CompoundTag tag) {
        this.level = level;
        this.nextAvailableId = tag.getInt("NextAvailableId");
        ListTag townsTag = tag.getList("Towns", 10);
        for (int i = 0; i < townsTag.size(); i++) {
            Town town = Town.loadNbt(level, townsTag.getCompound(i));
            // TODO check for incorrect id
            // Ouat.error(new CorruptedTownException(town, "Could not load town '%s'. Another town was already loaded with the same id.".formatted(key)).getMessage());
            towns.put(town.getId(), town);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextAvailableId", nextAvailableId);
        ListTag townsTag = new ListTag();
        for (Town town : towns.values()) {
            townsTag.add(town.saveNbt());
        }
        tag.put("Towns", townsTag);
        return tag;
    }

    public @Nullable Town getTown(String townName) {
        for (Town town : towns.values()) {
            if (town.getName().equals(townName)) {
                return town;
            }
        }
        return null;
    }

    public @NotNull Collection<Town> getAllTowns() {
        return towns.values();
    }

    /**
     * Tries to spawn a Town at the desired location. Returns null if generation was impossible.
     */
    public Town trySpawnTown(Culture townCulture, BlockPos townPosition) {
        Town town = Town.trySpawnAtPosition(townCulture, level, nextAvailableId, townPosition);
        if (town != null) {
            towns.put(nextAvailableId, town);
            ++nextAvailableId;
            return town;
        } else {
            return null;
        }
    }

    /**
     * Registers a Town after a TownStructure has been world generated.
     * WARNING : this method should be called only once for each TownStructure.
     */
    public void registerWorldGeneratedTown(CompoundTag protoTownTag) {
        Town town = Town.createFromProtoTown(level, nextAvailableId, protoTownTag);
        towns.put(nextAvailableId, town);
        ++nextAvailableId;
    }

    public boolean deleteTown(int townId) {
        Town town = towns.remove(townId);
        if (town != null) {
            town.delete();
            return true;
        } else {
            return false;
        }
    }

    public boolean deleteAndDemolishTown(int townId) {
        Town town = towns.remove(townId);
        if (town != null) {
            town.deleteAndDemolish();
            return true;
        } else {
            return false;
        }
    }

    public void tickTowns() {
        if ((level.getServer().getTickCount() % Config.TOWN_TICK_RATE_SECONDS * SharedConstants.TICKS_PER_SECOND) == 0) {
            towns.values().forEach(Town::tick);
        }
        /*
        long dayTime = this.level.getDayTime();
        if (dayTime == 0 || dayTime == 6000 || dayTime == 13000) {
            if (!this.towns.isEmpty()) {
                for (Town town : this.getAllTowns()) {
                    if (dayTime == 0) {
                        town.ringTownBell(Town.TownBellRingType.DAWN);
                    } else if (dayTime == 6000) {
                        town.ringTownBell(Town.TownBellRingType.NOON);
                    } else {
                        town.ringTownBell(Town.TownBellRingType.DUSK);
                    }
                }
            }
        }

         */
    }

    @Override
    public boolean isDirty() {
        // TODO Improve this function !
        return true;
    }
}

