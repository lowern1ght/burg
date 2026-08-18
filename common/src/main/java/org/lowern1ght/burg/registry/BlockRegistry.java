package org.lowern1ght.burg.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.Registry;
import org.lowern1ght.burg.Ouat;
import org.lowern1ght.burg.block.TownAnchorBlock;

public class BlockRegistry {
    public static Block TOWN_ANCHOR;

    public static void register() {
        TOWN_ANCHOR = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Ouat.MOD_ID, "town_anchor"),
            new TownAnchorBlock(TownAnchorBlock.defaultProperties())
        );
    }
}
