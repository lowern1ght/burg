package org.lowern1ght.burg.building.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record JigsawConnector(
    BlockPos posInTemplate,
    Direction facing,
    String name,
    String target,
    String pool
) {}
