package com.dotteam.onceuponatown.registry;

import com.dotteam.onceuponatown.OuatConstants;
import com.dotteam.onceuponatown.entity.Npc;
import com.dotteam.onceuponatown.entity.NpcFishingHook;
import com.dotteam.onceuponatown.util.OuatUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class OuatEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, OuatConstants.MOD_ID);

    public static final RegistryObject<EntityType<Npc>> NPC = ENTITY_TYPES.register("npc", () -> EntityType.Builder
            .of(Npc::new, MobCategory.MISC)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(10)
            .build(OuatUtils.resource("npc").toString())
    );

    public static final RegistryObject<EntityType<NpcFishingHook>> NPC_FISHING_HOOK = ENTITY_TYPES.register("npc_fishing_hook", () -> EntityType.Builder
            .<NpcFishingHook>of(NpcFishingHook::new, MobCategory.MISC)
            .noSave()
            .sized(0.25F, 0.25F)
            .clientTrackingRange(4)
            .updateInterval(5)
            .build(OuatUtils.resource("npc_fishing_hook").toString())
    );

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}