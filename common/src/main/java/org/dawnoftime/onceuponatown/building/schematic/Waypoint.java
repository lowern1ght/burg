package org.dawnoftime.onceuponatown.building.schematic;

import net.minecraft.util.FastColor;

public record Waypoint(String id, int fastColor) {
    public static final Waypoint BED = new Waypoint("bed", FastColor.ARGB32.color(255, 100, 100, 100));
    public static final Waypoint ROAD_CONNEXION = new Waypoint("road_connexion", FastColor.ARGB32.color(255, 100, 100, 100));
    public static final Waypoint FIELD_CENTER = new Waypoint("field_center", FastColor.ARGB32.color(255, 100, 100, 100));
    public static final Waypoint FISHING = new Waypoint("fishing", FastColor.ARGB32.color(255, 100, 100, 100));
    public static final Waypoint CRAFTING = new Waypoint("crafting", FastColor.ARGB32.color(255, 100, 100, 100));
}
