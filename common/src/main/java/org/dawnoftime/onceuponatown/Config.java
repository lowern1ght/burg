package org.dawnoftime.onceuponatown;

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

    // Generation
    /**
     * Minimal size of paths when they are extended.
     */
    public static final int MINI_PATH_LENGTH = 3;
    /**
     * Default size of paths, as well as the minimum size below which an area is transformed into a garden.
     */
    public static final int DEFAULT_PATH_LENGTH = 10;
    /**
     * Maximum vertical difference accepted between the door Y value, and any block Y value, on the border of the Building.
     */
    public static final int MAXI_Y_DIFFERENCE = 10;
    /**
     * Maximum size of the side of a squared garden. If a Bud available space in one of the 2 directions is smaller, it creates a MapGarden.
     */
    public static final int BUD_MINIMAL_SPACE = 10;
    /**
     * Minimum spacing between paths when the central building is placed.
     */
    public static final int MINI_PATH_SPACE = 20;
    /**
     * Probability of placing a path when adding a bud.
     */
    public static final float ROAD_SPAWN_RATE = 0.25F;
    /**
     * Probability that a new path is a big path.
     */
    public static final float WIDE_ROAD_SPAWN_RATE = 0.3F;
    /**
     * Probability that a path stops growing when a building is placed next to it.
     */
    public static final float PATH_STOP_RATE = 0.15F;
    /**
     * Tick rate of the towns per second.
     */
    public static final int TOWN_TICK_RATE_SECONDS = 5;
    /**
     * Maximal amount of an item a town can have.
     */
    public static int MAX_TOWN_INVENTORY_STACK_SIZE = 10000;

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
