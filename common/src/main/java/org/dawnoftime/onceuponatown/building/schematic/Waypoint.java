package org.dawnoftime.onceuponatown.building.schematic;

public record Waypoint(String id, int red, int green, int blue) {
    public static final Waypoint BED = new Waypoint("bed", 100, 200, 255);
    public static final Waypoint ROAD_CONNEXION = new Waypoint("entrance", 255, 200, 100);
}
