package org.lowern1ght.burg.building.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

// Represents an entity from a parsed structure NBT: absolute world position and full entity tag (with "id").
public record SchematicEntity(Vec3 worldPos, CompoundTag nbt) {}
