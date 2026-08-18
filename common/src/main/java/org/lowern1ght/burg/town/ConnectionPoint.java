package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record ConnectionPoint(BlockPos pos, Direction direction, String targetName, long insertionOrder) {
}
