package org.dawnoftime.onceuponatown.registry;

import net.minecraft.world.entity.Entity;
import org.dawnoftime.onceuponatown.entity.Citizen;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.function.Supplier;

public abstract class OuatEntitiesRegistry {
    public static OuatEntitiesRegistry INSTANCE;
    public abstract <T extends Entity> Supplier<EntityType<T>> register(String name, Supplier<EntityType.Builder<T>> builder);
    public final Supplier<EntityType<Citizen>> CITIZEN = register("citizen", () -> EntityType.Builder.of(Citizen::new, MobCategory.MISC).sized(0.6F, 1.95F).clientTrackingRange(10));
}