package org.dawnoftime.onceuponatown.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.Registry;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.block.TownAnchorBlock;

public class BlockRegistry {
    public static Block TOWN_ANCHOR;

    public static void register() {
        TOWN_ANCHOR = Registry.register(
            BuiltInRegistries.BLOCK,
            new ResourceLocation(Ouat.MOD_ID, "town_anchor"),
            new TownAnchorBlock(TownAnchorBlock.defaultProperties())
        );
    }
}
