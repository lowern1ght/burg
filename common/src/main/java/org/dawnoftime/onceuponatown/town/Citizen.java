package org.dawnoftime.onceuponatown.town;

import net.minecraft.nbt.CompoundTag;
import org.dawnoftime.onceuponatown.building.instance.Building;
import org.dawnoftime.onceuponatown.culture.Profession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class Citizen {
    Status status;
    Profession profession;
    UUID entityUUID;
    Building residence;
    Building workplace;
    int level = 1;

    Citizen(@NotNull Status status, @NotNull Profession profession, @Nullable UUID entityUUID, @Nullable Building residence, @Nullable Building workplace) {
        this.status = status;
        this.profession = profession;
        this.entityUUID = entityUUID;
        this.residence = residence;
        this.workplace = workplace;
    }

    public CompoundTag saveNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Status", status.toString());
        tag.putString("Profession", profession.getId());
        if (entityUUID != null) {
            tag.putUUID("UUID", entityUUID);
        }
        if (residence != null) {
            tag.putString("Residence", residence.toSafeString());
        }
        if (workplace != null) {
            tag.putString("Workplace", workplace.toSafeString());
        }
        return tag;
    }

    public boolean isUnemployed() {
        return profession == Profession.UNEMPLOYED;
    }

    public Status getStatus() {
        return status;
    }

    public Profession getProfession() {
        return profession;
    }

    public Building getResidence() {
        return residence;
    }

    public Building getWorkplace() {
        return workplace;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public String toString() {
        return "Citizen(" + profession.getId() + ", "
            + status.name().toLowerCase() + ", "
            + (entityUUID == null ? "no entity)" : entityUUID + ")");
    }

    public enum Status {
        NOT_SPAWNED,
        LOADED,
        UNLOADED,
        DEAD,
        MISSING
    }
}
