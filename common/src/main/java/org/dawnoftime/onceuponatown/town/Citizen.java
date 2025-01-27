package org.dawnoftime.onceuponatown.town;

import net.minecraft.nbt.CompoundTag;
import org.dawnoftime.onceuponatown.building.Building;
import org.dawnoftime.onceuponatown.entity.Profession;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class Citizen {
    UUID entityUUID;
    Status status;
    Profession profession;
    Building residence;
    Building workplace;
    String residenceId;
    String workplaceId;

    Citizen(UUID entityUUID, Status status, @NotNull Profession profession, Building residence, Building workplace) {
        this.entityUUID = entityUUID;
        this.status = status;
        this.profession = profession;
        this.residence = residence;
        this.workplace = workplace;
        //this.residenceId = residence.toSafeString();
        //this.workplaceId = workplace.toSafeString();
    }

    Citizen(CompoundTag tag) {
        this.entityUUID = tag.getUUID("UUID");
        this.status = Status.valueOf(tag.getString("Status"));
        this.profession = Profession.of(tag.getString("Profession"));
        this.residenceId = tag.getString("Residence");
        this.workplaceId = tag.getString("Workplace");
    }

    public CompoundTag saveNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("UUID", entityUUID);
        tag.putString("Status", status.toString());
        tag.putString("Profession", profession.getId());
        tag.putString("Residence", residence.toSafeString());
        tag.putString("Workplace", residence.toSafeString());
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

    public enum Status {
        NOT_SPAWNED,
        LOADED,
        UNLOADED,
        DEAD,
        MISSING
    }
}
