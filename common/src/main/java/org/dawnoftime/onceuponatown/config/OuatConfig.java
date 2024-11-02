package org.dawnoftime.onceuponatown.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class OuatConfig {

    private static final ForgeConfigSpec.BooleanValue DISABLE_VANILLA_VILLAGES = BUILDER
            .comment("Prevent vanilla villages from spawning. Existing villages will be untouched, new ones won't generate")
            .define("disableVanillaVillages", true);

    private static final ForgeConfigSpec.IntValue EXAMPLE_INT = BUILDER
            .comment("Example int")
            .defineInRange("exampleInt", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> EXAMPLE_STRING = BUILDER
            .comment("Example string")
            .define("exampleString", "huh?");

    // a list of strings that are treated as resource locations for items
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), OuatConfig::validateItemName);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean disableVanillaVillages;
    public static int exampleInt;
    public static String exampleString;
    public static Set<Item> items;

    private static boolean validateItemName(final Object obj) {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
    }
}
