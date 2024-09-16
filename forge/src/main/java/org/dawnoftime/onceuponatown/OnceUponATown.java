package org.dawnoftime.onceuponatown;

import org.dawnoftime.onceuponatown.client.model.CitizenModel;
import org.dawnoftime.onceuponatown.client.renderer.CitizenRenderer;
import org.dawnoftime.onceuponatown.client.screen.BuyScreen;
import org.dawnoftime.onceuponatown.client.screen.SellScreen;
import org.dawnoftime.onceuponatown.client.screen.tooltip.ClientTradeItemTooltip;
import org.dawnoftime.onceuponatown.client.screen.tooltip.TradeItemTooltip;
import org.dawnoftime.onceuponatown.config.OuatConfig;
import org.dawnoftime.onceuponatown.entity.Citizen;
import org.dawnoftime.onceuponatown.network.OuatNetwork;
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
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Constants.MOD_ID)
public class OnceUponATown {
    public OnceUponATown() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        OuatEntities.register(modEventBus);
        OuatItems.register(modEventBus);
        OuatCreativeModeTabs.register(modEventBus);
        OuatMenus.register(modEventBus);
        OuatStructures.register(modEventBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, OuatConfig.SPEC);
    }

    @Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModCommonEvents {
        @SubscribeEvent
        public static void commonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(OuatNetwork::init);
        }

        @SubscribeEvent
        public static void createEntityAttributes(EntityAttributeCreationEvent event) {
            event.put(OuatEntities.CITIZEN.get(), Citizen.createAttributes().build());
        }
    }

    @Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModClientEvents {
        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(
                    () -> {
                        MenuScreens.register(OuatMenus.BUY_MENU.get(), BuyScreen::new);
                        MenuScreens.register(OuatMenus.SELL_MENU.get(), SellScreen::new);
                    }
            );
        }

        @SubscribeEvent
        public static void addItemsToCreativeModeTabs(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
                event.accept(OuatItems.CITIZEN_SPAWN_EGG);
            }
            if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
                event.accept(OuatItems.EMERALD_SHARD);
            }
        }

        @SubscribeEvent
        public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(OuatEntities.CITIZEN.get(), CitizenRenderer::new);
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
