package org.dawnoftime.onceuponatown.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.dawnoftime.onceuponatown.block.SlabPathBlock;

import java.util.function.Function;
import java.util.function.Supplier;

public abstract class BlockRegistry {
    public static BlockRegistry REGISTRY;

    public final Supplier<Block> DIRT_PATH_SLAB = register("dirt_path_slab", () -> new SlabPathBlock(Block.Properties.copy(Blocks.DIRT_PATH)));

    public <T extends Block> Supplier<T> register(String id, Supplier<T> block) {
        return this.registerWithItem(id, block, (T blockObject) -> new BlockItem(blockObject, new Item.Properties()));
    }

    public abstract <T extends Block, Y extends Item> Supplier<T> registerWithItem(String id, Supplier<T> block, Function<T, Y> item);
}