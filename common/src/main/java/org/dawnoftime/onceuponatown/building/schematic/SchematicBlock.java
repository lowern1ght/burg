package org.dawnoftime.onceuponatown.building.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

// Represents a single block from a parsed structure NBT: position (local/rotated), state, and optional block entity data.
public record SchematicBlock(BlockPos localPos, BlockState state, @Nullable CompoundTag nbt) {}
