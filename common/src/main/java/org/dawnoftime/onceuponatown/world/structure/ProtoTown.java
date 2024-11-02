package org.dawnoftime.onceuponatown.world.structure;

import org.dawnoftime.onceuponatown.town.building.placement.BuildBud;
import net.minecraft.core.BlockPos;

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
