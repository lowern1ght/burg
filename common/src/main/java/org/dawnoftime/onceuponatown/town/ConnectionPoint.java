package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record ConnectionPoint(BlockPos pos, Direction direction, String targetName, int failCount) {

    public static ConnectionPoint of(BlockPos pos, Direction direction, String targetName) {
        return new ConnectionPoint(pos, direction, targetName, 0);
    }
}
