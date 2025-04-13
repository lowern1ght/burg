package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;

public class LevelTowns extends SavedData {
    private final ServerLevel level;
    private final HashMap<Integer, Town> towns = new HashMap<>();

    public static @NotNull LevelTowns of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent((tag) -> new LevelTowns(level, tag), () -> new LevelTowns(level), "ouat_towns");
    }

    private LevelTowns(ServerLevel level) {
        this.level = level;
    }

    private LevelTowns(ServerLevel level, CompoundTag tag) {
        this.level = level;
        ListTag townsTag = tag.getList("Towns", Tag.TAG_COMPOUND);
        for (int i = 0; i < townsTag.size(); i++) {
            Town town = Town.loadNbt(level, townsTag.getCompound(i));
            int id = town.getId();
            if (id < 1) {
                Ouat.error(new CorruptedTownException(town, "Could not load town '%s'. Its id '%s' is invalid.".formatted(town.getName(), id)).getMessage());
            } else if (towns.containsKey(id)) {
                Ouat.error(new CorruptedTownException(town, "Could not load town '%s'. Another town was already loaded with the same id '%s'.".formatted(town.getName(), id)).getMessage());
            } else {
                towns.put(id, town);
            }
        }
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag) {
        ListTag townsTag = new ListTag();
        for (Town town : towns.values()) {
            townsTag.add(town.saveNbt());
        }
        tag.put("Towns", townsTag);
        return tag;
    }

    public @Nullable Town getTownById(int townId) {
        return towns.getOrDefault(townId, null);
    }

    public @Nullable Town getTownByFancyId(String townFancyId) {
        Town town = null;
        try {
            town = towns.getOrDefault(Integer.parseInt(townFancyId.substring(5)), null);
        } catch (NumberFormatException e) {
            try {
                town = towns.getOrDefault(Integer.parseInt(townFancyId), null);
            } catch (NumberFormatException ignored) {
            }
        }
        return town;
    }

    public @NotNull Collection<Town> getAll() {
        return towns.values();
    }

    /**
     * Tries to spawn a Town at the desired location. Returns null if generation was impossible.
     */
    public Town trySpawnTown(Culture townCulture, BlockPos townPosition, @Nullable Component townName) {
        Town town = Town.trySpawnAtPosition(townCulture, level, createId(), townPosition, townName);
        if (town != null) {
            towns.put(town.getId(), town);
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
        Town town = Town.createFromProtoTown(level, createId(), protoTownTag);
        towns.put(town.getId(), town);
    }

    public boolean deleteTown(int townId, boolean demolish) {
        Town town = towns.remove(townId);
        if (town != null) {
            town.delete(demolish);
            return true;
        } else {
            return false;
        }
    }

    public void tickTowns() {
        for (Town town : towns.values()) {
            town.tick();
        }
    }

    private int createId() {
        return towns.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
    }

    @Override
    public boolean isDirty() {
        // TODO Improve this function !
        return true;
    }
}

