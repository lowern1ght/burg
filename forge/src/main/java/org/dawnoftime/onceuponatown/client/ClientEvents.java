package org.dawnoftime.onceuponatown.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.npc.TradeScreen;
import org.dawnoftime.onceuponatown.client.gui.tooltip.*;
import org.dawnoftime.onceuponatown.client.model.NpcModel;
import org.dawnoftime.onceuponatown.client.renderer.NpcFishingHookRenderer;
import org.dawnoftime.onceuponatown.client.renderer.NpcRenderer;
import org.dawnoftime.onceuponatown.item.EmeraldPouchItem;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.registry.ItemRegistry;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;

public class ClientEvents {
    @Mod.EventBusSubscriber(modid = Ouat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusClientEvents {
        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                    MenuScreens.register(MenuRegistry.REGISTRY.TRADE_MENU.get(), TradeScreen::new);
                    // Custom client item properties
                    ItemProperties.register(ItemRegistry.REGISTRY.EMERALD_POUCH.get(), Ouat.modResource("empty_pouch"), (emeraldPouchStack, clientLevel, livingEntity, id) -> EmeraldPouchItem.isEmpty(emeraldPouchStack) ? 1.0F : 0.0F);
                }
            );
        }

        @SubscribeEvent
        public static void addItemsToCreativeModeTabs(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
                event.accept(ItemRegistry.REGISTRY.NPC_SPAWN_EGG);
            }
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(ItemRegistry.REGISTRY.EMERALD_POUCH);
                event.accept(ItemRegistry.REGISTRY.TOWN_SCROLL);
            }
        }

        @SubscribeEvent
        public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityRegistry.REGISTRY.NPC.get(), NpcRenderer::new);
            event.registerEntityRenderer(EntityRegistry.REGISTRY.NPC_FISHING_HOOK.get(), NpcFishingHookRenderer::new);
        }

        @SubscribeEvent
        public static void registerEntityLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(NpcModel.LAYER_LOCATION, NpcModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void registerClientTooltips(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(TradeItemTooltip.class, ClientTradeItemTooltip::new);
            event.register(SingleItemTooltip.class, ClientSingleItemTooltip::new);
            event.register(ItemAndTitleTooltip.class, ClientItemAndTitleTooltip::new);
        }
    }

    @Mod.EventBusSubscriber(modid = Ouat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeBusClientEvents {
    }
}
