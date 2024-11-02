package org.dawnoftime.onceuponatown.town.building.placement;

import net.minecraft.core.BlockPos;
import org.dawnoftime.onceuponatown.town.TownMap;

import java.util.HashSet;

import static org.dawnoftime.onceuponatown.town.TownMapUtils.rectangularPosIterator;

public abstract class PlotPlacement extends BuildPlacement {
    public PlotPlacement(int sizeXNorth, int sizeZNorth) {
        super(sizeXNorth, sizeZNorth);
    }

    @Override
    protected void onAddedToMap(TownMap map) {
        // We try to find all the adjacent MapPath to extend them and add the Buds.
        // We will iterate on a one block bigger rectangle to find all the adjacent MapBuild.
        HashSet<Integer> ids = new HashSet<>();
        for(BlockPos.MutableBlockPos pos : rectangularPosIterator(this.getOriginPos().north().west(), this.getSizeX() + 2, this.getSizeZ() + 2)) {
            ids.add(map.getIDInMapPos(pos));
        }
        for(int id : ids){
            if(map.getBuild(id) instanceof RoadPlacement path){
                path.update(map);
            }
        }
    }
}
