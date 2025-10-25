package org.dawnoftime.onceuponatown.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.dawnoftime.onceuponatown.blockentity.CultureCreatorBlockEntity;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public abstract class BlockEntityRegistry {
    public static BlockEntityRegistry REGISTRY;

    public final Supplier<BlockEntityType<CultureCreatorBlockEntity>> CULTURE_CREATOR = register("culture_creator", CultureCreatorBlockEntity::new, () -> BlockRegistry.REGISTRY.CULTURE_CREATOR_BLOCK.get());

    public abstract <T extends BlockEntity> Supplier<BlockEntityType<T>> register(String name, BiFunction<BlockPos, BlockState, T> factoryIn, Supplier<Block> validBlockSupplier);
}

