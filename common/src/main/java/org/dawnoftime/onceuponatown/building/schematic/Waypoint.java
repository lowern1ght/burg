package org.dawnoftime.onceuponatown.building.schematic;

import net.minecraft.util.FastColor;

import java.util.Arrays;

public enum Waypoint {
    BED("bed", FastColor.ARGB32.color(255, 100, 100, 100)),
    ROAD_CONNEXION("road_connexion", FastColor.ARGB32.color(255, 100, 100, 100)),
    FIELD_CENTER("field_center", FastColor.ARGB32.color(255, 100, 100, 100)),
    FISHING("fishing", FastColor.ARGB32.color(255, 100, 100, 100)),
    CRAFTING("crafting", FastColor.ARGB32.color(255, 100, 100, 100));

    private final String id;
    private final int fastColor;

    Waypoint(String id, int fastColor){
        this.id = id;
        this.fastColor = fastColor;
    }

    public String getId(){
        return this.id;
    }

    public int getFastColor(){
        return this.fastColor;
    }

    public static boolean exists(String id){
        return Arrays.stream(Waypoint.values()).anyMatch(wp -> wp.id.equals(id));
    }
}
