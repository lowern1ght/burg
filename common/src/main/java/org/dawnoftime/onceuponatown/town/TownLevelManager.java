package org.dawnoftime.onceuponatown.town;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.dawnoftime.onceuponatown.Config;
import org.dawnoftime.onceuponatown.Ouat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.dawnoftime.onceuponatown.town.CorruptedTownException.str;

public class TownLevelManager extends SavedData {
    private final Level level;
    private final HashMap<UUID, Town> towns = new HashMap<>();

    public static @Nullable TownLevelManager get(ServerLevel level) {
        return level.isClientSide() ? null : level.getDataStorage().computeIfAbsent((tag) -> new TownLevelManager(level, tag), () -> new TownLevelManager(level), "ouat_towns");
    }

    private TownLevelManager(ServerLevel level) {
        this.level = level;
    }

    private TownLevelManager(ServerLevel level, CompoundTag tag) {
        this(level);
        this.loadTowns(tag);
    }

    public boolean isDirty() {
        return true;
    }

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
        for(String key: townsTag.getAllKeys()) {
            Town town = Town.readNBT(this.level, townsTag.getCompound(key));
            if(this.towns.containsKey(town.getUuid())) {
                Ouat.error(new CorruptedTownException(town, "Impossible de register the town '%s'. A town was already loaded with the exact same UUID.".formatted(key)).getMessage());
            }else{
                this.towns.put(town.getUuid(), town);
            }
        }
    }

    public void addTown(Town town) {
        this.towns.put(town.getUuid(), town);
    }

    public void removeTown(Town town) {
        this.towns.remove(town.getUuid());
    }

    public @NotNull Collection<Town> getTowns() {
        return this.towns.values();
    }

    public void initProtoTown(ServerLevel level, @NotNull CompoundTag townTag) {
        UUID townUUID = townTag.getUUID("UUID");
        // Avoid creating the same town several times when loading the same BuildPiece in different chunks.
        if(!this.towns.containsKey(townUUID)){
            String name = "plains" + Mth.nextInt(RandomSource.create(), 0, 100);
            Town town = Town.createWorldGenOld(level, culture, name, townMap);
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(town.getName() + " discovered at " + str(town.getCenter())), false);
            this.addTown(town);
        }
    }

    /**
     * Delete town instance, keep structures, convert npcs to wanderers
     * @param townUUID UUID of the town to delete
     */
    public void softDeleteTown(UUID townUUID) {
        Town town = this.towns.get(townUUID);
        if (town != null) {
            town.softDelete();
            this.removeTown(town);
        }
    }

    /**
     * Delete town instance, destroy structures, kill npcs
     * @param townUUID UUID of the town to delete
     */
    public void hardDeleteTown(UUID townUUID) {
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
                for (Town town : this.getTowns()) {
                    if(dayTime == 0) {
                        town.ringTownBell(Town.TownBellRingType.DAWN);
                    } else if (dayTime == 6000) {
                        town.ringTownBell(Town.TownBellRingType.NOON);
                    } else {
                        town.ringTownBell(Town.TownBellRingType.DUSK);
                    }
                }
            }
        }
        if(this.level.getServer() != null){
            if ((level.getServer().getTickCount() % Config.TOWN_TICK_RATE_SECONDS * SharedConstants.TICKS_PER_SECOND) == 0) {
                if (!this.towns.isEmpty()) {
                    this.getTowns().forEach(Town::tick);
                }
            }
        }
    }
}

