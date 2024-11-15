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
