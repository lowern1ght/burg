package org.dawnoftime.onceuponatown.registry;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.dawnoftime.onceuponatown.entity.Npc;

import java.util.function.Supplier;

public abstract class OuatEntitiesRegistry {
    public static OuatEntitiesRegistry ENTITY_REGISTRY;
    public abstract <T extends Entity> Supplier<EntityType<T>> register(String name, Supplier<EntityType.Builder<T>> builder);
    public final Supplier<EntityType<Npc>> NPC = register("citizen", () -> EntityType.Builder.of(Npc::new, MobCategory.MISC).sized(0.6F, 1.95F).clientTrackingRange(10));
}