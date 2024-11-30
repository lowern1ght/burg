package org.dawnoftime.onceuponatown.town;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class LevelTowns extends SavedData {
    private final List<Town> towns = new ArrayList<>();
    private final Level level;

    public static LevelTowns get(ServerLevel level) {
        return level.isClientSide() ? null : level.getDataStorage().computeIfAbsent((tag) -> new LevelTowns(level, tag), () -> new LevelTowns(level), "ouat_towns");
    }

    private LevelTowns(ServerLevel level) {
        this.level = level;
    }

    private LevelTowns(ServerLevel level, CompoundTag tag) {
        this(level);
        loadTowns(tag);
    }

    public boolean isDirty() {
        return true;
    }

    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag townsTag = new ListTag();
        for (Town town : this.towns) {
            CompoundTag townTag = new CompoundTag();
            town.writeNBT(townTag);
            townsTag.add(townTag);
        }
        tag.put("Towns", townsTag);
        return tag;
    }

    public void loadTowns(CompoundTag tag) {
        ListTag townsTag = tag.getList("Towns", 10);
        for(int i = 0; i < townsTag.size(); ++i) {
            CompoundTag townTag = townsTag.getCompound(i);
            loadTown(townTag);
        }
    }

    private void loadTown(CompoundTag tag) {
        this.towns.add(Town.readNBT(this.level, tag));
    }

    public void addTown(Town town) {
        this.towns.add(town);
    }

    public void removeTown(Town town) {
        this.towns.remove(town);
    }

    public List<Town> getTowns() {
        return this.towns;
    }
}
