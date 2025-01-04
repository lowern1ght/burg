package org.dawnoftime.onceuponatown;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.dawnoftime.onceuponatown.culture.ServerCultures;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.network.PacketHandler;
import org.dawnoftime.onceuponatown.registry.CommandRegistry;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;

public class CommonEvents {
    @Mod.EventBusSubscriber(modid = Ouat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusCommonEvents {
        @SubscribeEvent
        public static void commonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(PacketHandler::init);
        }

        @SubscribeEvent
        public static void createEntityAttributes(EntityAttributeCreationEvent event) {
            event.put(EntityRegistry.REGISTRY.NPC.get(), Npc.createAttributes().build());
        }
    }

    @Mod.EventBusSubscriber(modid = Ouat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeBusCommonEvents {
        @SubscribeEvent
        public static void registerCommands(RegisterCommandsEvent event) {
            CommandRegistry.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onServerStarting(ServerAboutToStartEvent event) {
            ServerCultures.loadCultures(event.getServer().getResourceManager());
        }

        @SubscribeEvent
        public static void onLevelTick(TickEvent.LevelTickEvent event) {
            if (event.level instanceof ServerLevel level && event.phase.equals(TickEvent.Phase.END)) {
                //TownManager.tickTowns(level);
            }
        }

        @SubscribeEvent
        public static void addReloadListener(AddReloadListenerEvent event) {
            //event.addListener(CultureManager.instance());
        }

        @SubscribeEvent
        public static void finalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
            if (event.getEntity() instanceof Npc npc) {
                npc.onFinalizeSpawnEvent();
            }
        }
    }
}
