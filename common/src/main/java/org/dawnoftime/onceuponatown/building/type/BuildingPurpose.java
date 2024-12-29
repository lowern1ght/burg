package org.dawnoftime.onceuponatown.building.type;

public enum BuildingPurpose {
    INFRASTRUCTURE, // Roads, bridges and fortifications
    DWELLING, // Buildings with dwelling slots and no working slots
    WORK, // Buildings with working slots (may have dwelling slots as well)
    SPECIAL,
    DECORATION, // Fountains, statues, gardens ...
    TREASURE,
    MISCELLANEOUS, // Other
}
