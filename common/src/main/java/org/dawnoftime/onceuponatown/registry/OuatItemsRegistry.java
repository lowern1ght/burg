package org.dawnoftime.onceuponatown.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public abstract class OuatItemsRegistry {
    public static OuatItemsRegistry INSTANCE;

    public final Supplier<Item> CITIZEN_SPAWN_EGG = registerSpawnEgg("citizen_spawn_egg", OuatEntitiesRegistry.INSTANCE.CITIZEN, 0x96691f, 0x38b934/*51A03E*/);
    public final Supplier<Item> EMERALD_SHARD = register("emerald_shard", () -> new Item(new Item.Properties()));

    public abstract <T extends Item> Supplier<Item> register(final String name, final Supplier<T> itemSupplier);
    public abstract <T extends Item> Supplier<Item> registerSpawnEgg(final String name, Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor);
}