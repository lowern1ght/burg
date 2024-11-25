package org.dawnoftime.onceuponatown.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.dawnoftime.onceuponatown.building.type.SliceBuildType;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;

public class SliceBuild extends Build {
    private int width;
    private int length;
    private int[] yShape;

    public SliceBuild(int length, SliceBuildType build) {
        super(build);
        this.width = build.getWidth();
        this.length = length;
        this.yShape = new int[length];
    }

    @Override
    public int getNorthSizeX() {
        return this.length;
    }

    @Override
    public int getNorthSizeZ() {
        return this.width;
    }

    @Override
    public boolean canBeBuiltOnBud(ProtoTown map, BuildBud buildBud, Direction dir) {
        // In the case of MapPaths, we only checks the line of block. The Map size will be defined when it's placed on the Map.
        BlockPos testedOriginPos = buildBud.findOriginPos(this, dir);
        BlockPos cursor = testedOriginPos.mutable();
        // We check all the position from the Bud to the width.
        for(int offset = 0; offset < this.getWidth(); offset++){
            //TODO Replace with the real Y Map query function.
            if(!map.isEmpty(cursor)){// || (Math.abs(TownMapDisplay.getSurfaceY(cursor)) - this.getYOnPos(testedOriginPos, cursor)) > MAXI_Y_DIFFERENCE){
                return false;
            }
            cursor.relative(dir);
        }
        return true;
    }

    /**
     * @return The corresponding width of the path.
     */
    public int getWidth(){
        return this.width;
    }
}
