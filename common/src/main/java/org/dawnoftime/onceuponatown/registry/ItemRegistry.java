package org.dawnoftime.onceuponatown.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.item.CultureCreatorItem;
import org.dawnoftime.onceuponatown.item.EmeraldPouchItem;
import org.dawnoftime.onceuponatown.item.TownMapItem;

import java.util.function.Supplier;

public abstract class ItemRegistry {
    public static ItemRegistry REGISTRY;

    public final Supplier<Item> NPC_SPAWN_EGG = registerSpawnEgg("npc_spawn_egg", EntityRegistry.REGISTRY.NPC, 0x96691f, 0x38b934);

    public final Supplier<Item> EMERALD_POUCH = register("emerald_pouch", () -> new EmeraldPouchItem(new Item.Properties().stacksTo(1)));

    public final Supplier<Item> TOWN_MAP = register("town_map", () -> new TownMapItem(new Item.Properties()));

    public final Supplier<Item> CULTURE_CREATOR = register("culture_creator", () -> new CultureCreatorItem(new Item.Properties()));

    public abstract <T extends Item> Supplier<Item> register(final String name, final Supplier<T> itemSupplier);

    public abstract <T extends Item> Supplier<Item> registerSpawnEgg(final String name, Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor);
}