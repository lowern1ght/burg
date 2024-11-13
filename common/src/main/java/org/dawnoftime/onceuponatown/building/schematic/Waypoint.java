package org.dawnoftime.onceuponatown.building.schematic;

public record Waypoint(String id, int red, int green, int blue) {
    public static Waypoint BED_WP = new Waypoint("bed", 100, 200, 255);
    public static Waypoint ENTRANCE_WP = new Waypoint("entrance", 255, 200, 100);
    // TODO Add some code to be able to load custom waypoints from the client.
}
