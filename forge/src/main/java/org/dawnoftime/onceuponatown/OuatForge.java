package org.dawnoftime.onceuponatown;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import org.dawnoftime.onceuponatown.client.model.CitizenModel;
import org.dawnoftime.onceuponatown.client.renderer.CitizenRenderer;
import org.dawnoftime.onceuponatown.client.screen.BuyScreen;
import org.dawnoftime.onceuponatown.client.screen.SellScreen;
import org.dawnoftime.onceuponatown.client.screen.tooltip.ClientTradeItemTooltip;
import org.dawnoftime.onceuponatown.client.screen.tooltip.TradeItemTooltip;
import org.dawnoftime.onceuponatown.culture.CultureManager;
import org.dawnoftime.onceuponatown.entity.Citizen;
import org.dawnoftime.onceuponatown.registry.*;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.dawnoftime.onceuponatown.town.TownManager;

@Mod(Constants.MOD_ID)
public class OuatForge {
    /*
    TODO use this code when we will add the configs.
    public static final ConfigClassHandler<DoTBConfig> HANDLER = ConfigClassHandler.createBuilder(DoTBConfig.class)
            .id(new ResourceLocation(DoTBCommon.MOD_ID, "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(YACLPlatform.getConfigDir().resolve("dawnoftimebuilder-config.json5"))
                    .setJson5(true)
                    .build())
            .build();

    // The handler must be loaded in the mod constructor before initializing anything else.
    HANDLER.load();
    */

    public OuatForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        Common.init();
        RegistryImpls.init(modEventBus);

        //modEventBus.register(DoTBForgeClient.class);
        //modEventBus.register(DataGenerators.class);
    }

    @Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModCommonEvents {
        @SubscribeEvent
        public static void commonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(OuatNetwork::init);
        }

        @SubscribeEvent
        public static void createEntityAttributes(EntityAttributeCreationEvent event) {
            event.put(OuatEntitiesRegistry.ENTITY_REGISTRY.CITIZEN.get(), Citizen.createAttributes().build());
        }

        @SubscribeEvent
        public static void addReloadListener(AddReloadListenerEvent event) {
            //event.addListener(CultureManager.instance());
        }

        //@SubscribeEvent
        public static void onLevelTick(TickEvent.LevelTickEvent event) {
            if (event.level instanceof ServerLevel level && event.phase.equals(TickEvent.Phase.END)) {
                TownManager.tickTowns(level);
            }
        }

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            CultureManager.loadCultures(event.getServer().getResourceManager());
        }

        @SubscribeEvent
        public static void registerCommands(RegisterCommandsEvent event) {
            OuatCommands.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void finalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
            if (event.getEntity() instanceof Citizen citizen) {
                citizen.onFinalizeSpawnEvent();
            }
        }
    }

    @Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModClientEvents {
        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(
                    () -> {
                        MenuScreens.register(OuatMenusRegistry.MENU_REGISTRY.BUY_MENU.get(), BuyScreen::new);
                        MenuScreens.register(OuatMenusRegistry.MENU_REGISTRY.SELL_MENU.get(), SellScreen::new);
                    }
            );
        }

        @SubscribeEvent
        public static void addItemsToCreativeModeTabs(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
                event.accept(OuatItemsRegistry.ITEM_REGISTRY.CITIZEN_SPAWN_EGG);
            }
            if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
                event.accept(OuatItemsRegistry.ITEM_REGISTRY.EMERALD_SHARD);
            }
        }

        @SubscribeEvent
        public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(OuatEntitiesRegistry.ENTITY_REGISTRY.CITIZEN.get(), CitizenRenderer::new);
        }

        @SubscribeEvent
        public static void registerEntityLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(CitizenModel.LAYER_LOCATION, CitizenModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void registerClientTooltips(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(TradeItemTooltip.class, ClientTradeItemTooltip::new);
        }
    }
}
