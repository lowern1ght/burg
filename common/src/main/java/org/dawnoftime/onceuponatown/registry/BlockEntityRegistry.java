package org.dawnoftime.onceuponatown.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;

import java.util.function.BiFunction;

public class BlockEntityRegistry {
    public static BlockEntityType<TownAnchorBlockEntity> TOWN_ANCHOR;

    public static void register() {
        // BiFunction::apply used as workaround for package-private BlockEntitySupplier in 1.20.1
        BiFunction<BlockPos, BlockState, TownAnchorBlockEntity> factory = TownAnchorBlockEntity::new;
        TOWN_ANCHOR = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            new ResourceLocation(Ouat.MOD_ID, "town_anchor"),
            BlockEntityType.Builder.of(factory::apply, BlockRegistry.TOWN_ANCHOR).build(null)
        );
    }
}
