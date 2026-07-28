package org.dawnoftime.onceuponatown.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.entity.Citizen;
import org.dawnoftime.onceuponatown.entity.Npc;

public class EntityRegistry {
    public static EntityType<Npc> NPC;
    public static EntityType<Citizen> CITIZEN;

    public static void register() {
        NPC = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Ouat.MOD_ID, "npc"),
            EntityType.Builder.<Npc>of(Npc::new, MobCategory.MISC)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(10)
                .build(ResourceLocation.fromNamespaceAndPath(Ouat.MOD_ID, "npc").toString())
        );
        // MobCategory.MISC, like the builder: a citizen is placed by the town, never by
        // natural spawning, and MISC keeps it out of the mob cap and the spawn cycle.
        CITIZEN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Ouat.MOD_ID, "citizen"),
            EntityType.Builder.<Citizen>of(Citizen::new, MobCategory.MISC)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(10)
                .build(ResourceLocation.fromNamespaceAndPath(Ouat.MOD_ID, "citizen").toString())
        );
    }
}
