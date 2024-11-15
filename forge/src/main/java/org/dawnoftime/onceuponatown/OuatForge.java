package org.dawnoftime.onceuponatown;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.dawnoftime.onceuponatown.culture.CultureManager;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.network.PacketHandler;
import org.dawnoftime.onceuponatown.registry.CommandRegistry;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;

@Mod(Ouat.MOD_ID)
public class OuatForge {
    public OuatForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        RegistriesImpls.init(modEventBus);
    }
}
