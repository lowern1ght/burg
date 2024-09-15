package com.dotteam.onceuponatown.world.structure;

import com.dotteam.onceuponatown.town.map.BuildBud;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;

public class ProtoTown {
    private final ArrayList<BuildBud> buildBuds = new ArrayList<>();
    private BlockPos townCenter;
    private final BlockPos.MutableBlockPos townNWCorner;
    private final BlockPos.MutableBlockPos townSECorner;
    private int[][] townMap;

    public ProtoTown(BlockPos townCenter, BlockPos.MutableBlockPos townNWCorner, BlockPos.MutableBlockPos townSECorner) {
        this.townCenter = townCenter;
        this.townNWCorner = townNWCorner;
        this.townSECorner = townSECorner;
    }
}
