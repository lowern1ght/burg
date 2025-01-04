package org.dawnoftime.onceuponatown.town;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.dawnoftime.onceuponatown.Config;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;

public class LevelTowns extends SavedData {
    private final ServerLevel level;
    private final HashMap<UUID, Town> towns = new HashMap<>();

    public static @NotNull LevelTowns of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                (tag) -> new LevelTowns(level, tag),
                () -> new LevelTowns(level),
                "ouat_towns");
    }

    private LevelTowns(ServerLevel level) {
        this.level = level;
    }

    private LevelTowns(ServerLevel level, CompoundTag tag) {
        this(level);
        this.loadTowns(tag);
    }

    @Override
    public boolean isDirty() {
        // TODO Improve this function !
        return true;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        CompoundTag townsTag = new CompoundTag();
        for (UUID townUUID : this.towns.keySet()) {
            townsTag.put(townUUID.toString(), this.towns.get(townUUID).writeNBT());
        }
        tag.put("Towns", townsTag);
        return tag;
    }

    public void loadTowns(CompoundTag tag) {
        CompoundTag townsTag = tag.getCompound("Towns");
        for (String key : townsTag.getAllKeys()) {
            Town town = new Town(this.level, townsTag.getCompound(key));
            if (this.towns.containsKey(town.getUuid())) {
                Ouat.error(new CorruptedTownException(town, "Impossible de register the town '%s'. A town was already loaded with the exact same UUID.".formatted(key)).getMessage());
            } else {
                this.towns.put(town.getUuid(), town);
            }
        }
    }

    public @Nullable Town getTown(String townName) {
        Town town = null;
        for (Town t : towns.values()) {
            if (t.getName().equals(townName)) {
                town = t;
                break;
            }
        }
        return town;
    }

    public void addTown(Town town) {
        this.towns.put(town.getUuid(), town);
    }

    public void removeTown(Town town) {
        this.towns.remove(town.getUuid());
    }

    public @NotNull Collection<Town> getAllTowns() {
        return this.towns.values();
    }

    public void initProtoTown(@NotNull CompoundTag townTag) {
        UUID townUUID = townTag.getUUID("UUID");
        // Avoid creating the same town several times when loading the same BuildPiece in different chunks.
        if (!this.towns.containsKey(townUUID)) {
            Town town = new Town(this.level, townTag);
            this.level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(town.getName() + " discovered at " + Utils.blockPosToString(town.getCenter())), false);
            this.addTown(town);
        }
    }

    /**
     * Delete town instance, keep structures, convert npcs to wanderers
     *
     * @param townUUID UUID of the town to delete
     */
    public void deleteTown(UUID townUUID) {
        Town town = this.towns.get(townUUID);
        if (town != null) {
            town.softDelete();
            this.removeTown(town);
        }
    }

    /**
     * Delete town instance, destroy structures, kill npcs
     *
     * @param townUUID UUID of the town to delete
     */
    public void deleteAndDemolishTown(UUID townUUID) {
        Town town = this.towns.get(townUUID);
        if (town != null) {
            town.hardDelete();
            this.removeTown(town);
        }
    }

    public void tickTowns() {
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
        if ((level.getServer().getTickCount() % Config.TOWN_TICK_RATE_SECONDS * SharedConstants.TICKS_PER_SECOND) == 0) {
            if (!this.towns.isEmpty()) {
                this.getAllTowns().forEach(Town::tick);
            }
        }
    }
}

