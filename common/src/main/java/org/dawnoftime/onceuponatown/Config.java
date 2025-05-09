package org.dawnoftime.onceuponatown;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;

public class Config {
    // Prevent vanilla villages from spawning. Existing villages will be untouched, new ones won't generate
    public static final FakeConfig<Boolean> DISABLE_ALL_VANILLA_VILLAGES = new FakeConfig<>(false);
    public static final FakeConfig<Boolean> DISABLE_VANILLA_PLAINS_VILLAGE = new FakeConfig<>(true);
    public static final FakeConfig<Boolean> DISABLE_VANILLA_DESERT_VILLAGE = new FakeConfig<>(false);
    public static final FakeConfig<Boolean> DISABLE_VANILLA_TAIGA_VILLAGE = new FakeConfig<>(false);
    public static final FakeConfig<Boolean> DISABLE_VANILLA_SNOW_VILLAGE = new FakeConfig<>(false);
    public static final FakeConfig<Boolean> DISABLE_VANILLA_SAVANNA_VILLAGE = new FakeConfig<>(false);

    // Debug
    public static final boolean DEBUG_PATHFINDING = false;
    public static final boolean DEBUG_GOALS = false;
    public static final boolean DEBUG_BRAINS = false;

    // Generation
    /** Minimal size of roads when they are extended.*/
    public static final int MINI_ROAD_LENGTH = 3;
    /** Default size of roads, as well as the minimum size below which an area is transformed into a garden.*/
    public static final int DEFAULT_ROAD_LENGTH = 10;
    /** Maximum vertical difference accepted between the door Y value, and any block Y value, on the border of the Building.*/
    public static final int MAXI_Y_DIFFERENCE = 10;
    /** Maximum size of the side of a squared garden. If a Bud available space in one of the 2 directions is smaller, it creates a MapGarden.*/
    public static final int BUD_MINIMAL_SPACE = 10;
    /** Minimum spacing between roads when the central building is placed.*/
    public static final int MINI_ROAD_SPACE = 20;
    /** Probability of placing a road when adding a bud.*/
    public static final float ROAD_SPAWN_RATE = 0.25F;
    /** Probability that a new road is a big road.*/
    public static final float WIDE_ROAD_SPAWN_RATE = 0.3F;
    /** Probability that a road stops growing when a building is placed next to it.*/
    public static final float ROAD_STOP_RATE = 0.15F;
    /** Probability that a road turns when a building is placed next to it.*/
    public static final float ROAD_TURN_RATE = 0.15F;
    /** Probability that a wide road turns when a building is placed next to it.*/
    public static final float WIDE_ROAD_TURN_RATE = 0.15F;
    /** Minimum number of Buds to activate the mandatory road generation and prevent road growths stop.*/
    public static final int CRITICAL_BUDS_NUMBER = 4;
    /** Tick rate of the towns per second.*/
    public static final int TOWN_TICK_RATE_SECONDS = 1;
    /** Maximal amount of an item a town can have. */
    public static int MAX_TOWN_INVENTORY_STACK_SIZE = 10000;

    // Towns
    public static final int ACTIVE_AREA_RADIUS = 80;
    public static final int PRODUCTION_HARVEST_RATE = SharedConstants.TICKS_PER_GAME_DAY;

    public record FakeConfig<T>(T configValue) {
        public T get() {
            return this.configValue;
        }
    }

    public static List<ResourceKey<Structure>> getDisabledVillages() {
        List<ResourceKey<Structure>> villages = new ArrayList<>();
        if (Config.DISABLE_ALL_VANILLA_VILLAGES.get() || Config.DISABLE_VANILLA_PLAINS_VILLAGE.get()) {
            villages.add(BuiltinStructures.VILLAGE_PLAINS);
        }
        if (Config.DISABLE_ALL_VANILLA_VILLAGES.get() || Config.DISABLE_VANILLA_DESERT_VILLAGE.get()) {
            villages.add(BuiltinStructures.VILLAGE_DESERT);
        }
        if (Config.DISABLE_ALL_VANILLA_VILLAGES.get() || Config.DISABLE_VANILLA_TAIGA_VILLAGE.get()) {
            villages.add(BuiltinStructures.VILLAGE_TAIGA);
        }
        if (Config.DISABLE_ALL_VANILLA_VILLAGES.get() || Config.DISABLE_VANILLA_SNOW_VILLAGE.get()) {
            villages.add(BuiltinStructures.VILLAGE_SNOWY);
        }
        if (Config.DISABLE_ALL_VANILLA_VILLAGES.get() || Config.DISABLE_VANILLA_SAVANNA_VILLAGE.get()) {
            villages.add(BuiltinStructures.VILLAGE_SAVANNA);
        }
        return villages;
    }
}
