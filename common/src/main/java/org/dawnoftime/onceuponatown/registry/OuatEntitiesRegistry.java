package org.dawnoftime.onceuponatown.registry;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.NpcFishingHook;

import java.util.function.Supplier;

public abstract class OuatEntitiesRegistry {
    public static OuatEntitiesRegistry ENTITY_REGISTRY;
    public abstract <T extends Entity> Supplier<EntityType<T>> register(String name, Supplier<EntityType.Builder<T>> builder);
    public final Supplier<EntityType<Npc>> NPC = register("citizen", () -> EntityType.Builder
            .of(Npc::new, MobCategory.MISC)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(10));
    public final Supplier<EntityType<NpcFishingHook>> NPC_FISHING_HOOK = register("npc_fishing_hook", () -> EntityType.Builder
            .<NpcFishingHook>of(NpcFishingHook::new, MobCategory.MISC)
            .noSave()
            .sized(0.25F, 0.25F)
            .clientTrackingRange(4)
            .updateInterval(5)
            //.build(Ouat.createOuatResource("npc_fishing_hook").toString())
    );
}