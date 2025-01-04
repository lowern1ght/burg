package org.dawnoftime.onceuponatown.building.type;

public enum BuildingPurpose {
    INFRASTRUCTURE, // Roads, bridges
    DWELLING, // Buildings with dwelling slots but no working slots
    WORK, // Buildings with working slots that may have dwelling slots as wel
    SPECIAL,
    DECORATION, // Fountains, statues, gardens...
    TREASURE,
    MISCELLANEOUS,
}
