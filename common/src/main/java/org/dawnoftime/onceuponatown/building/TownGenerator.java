package org.dawnoftime.onceuponatown.building;

import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.building.type.BuildingType;

import javax.annotation.Nullable;

public class TownGenerator {

    @Nullable
    public static boolean tryGenerateTown(){
        /*
         Parameters :
         - Culture so that I can get :
            - the list of the starter buildings loaded.
            - How do I get the building type of th path of the correspond culture ?
            - The random number of building that I have to build (comes from a config parameter in the culture datapack)
         - The chunk or the object that contains the map information.

         We might need another function that first chose the culture that will spawn at the given location.
         Then it triggers this function with the selected culture.

         At the end, saves a NBT in the town standard format.
         */

        return true;
    }

    public static boolean addBuilding(Town town, BuildingType buildingType){
        return true;
    }

}
