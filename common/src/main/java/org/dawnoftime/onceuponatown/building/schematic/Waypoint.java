package org.dawnoftime.onceuponatown.building.schematic;

import net.minecraft.util.FastColor;

import java.util.Arrays;

public enum Waypoint {
    BED (100, 100, 100),
    MAIN_ENTRANCE (100, 100, 100),
    FIELD_CENTER (100, 100, 100),
    FISHER_POS (100, 100, 250),
    FISHING_WATER (100, 100, 200),
    CRAFTING (100, 100, 100);

    private final float red;
    private final float green;
    private final float blue;

    Waypoint(int red, int green, int blue){
        this.red = (float) red / 255;
        this.green = (float) green / 255;
        this.blue = (float) blue / 255;
    }

    public float getRed() {
        return red;
    }

    public float getGreen() {
        return green;
    }

    public float getBlue() {
        return blue;
    }

    public static boolean exists(String name){
        return Arrays.stream(Waypoint.values()).anyMatch(wp -> wp.name().equals(name));
    }
}
