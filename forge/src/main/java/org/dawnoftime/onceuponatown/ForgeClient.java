package org.dawnoftime.onceuponatown;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.dawnoftime.onceuponatown.client.model.NpcModel;
import org.dawnoftime.onceuponatown.client.renderer.NpcFishingHookRenderer;
import org.dawnoftime.onceuponatown.client.renderer.NpcRenderer;
import org.dawnoftime.onceuponatown.client.screen.BuyScreen;
import org.dawnoftime.onceuponatown.client.screen.SellScreen;
import org.dawnoftime.onceuponatown.client.screen.tooltip.ClientTradeItemTooltip;
import org.dawnoftime.onceuponatown.client.screen.tooltip.TradeItemTooltip;
import org.dawnoftime.onceuponatown.registry.OuatEntitiesRegistry;
import org.dawnoftime.onceuponatown.registry.OuatItemsRegistry;
import org.dawnoftime.onceuponatown.registry.OuatMenusRegistry;

@Mod.EventBusSubscriber(modid = Ouat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ForgeClient extends Client {
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
        event.registerEntityRenderer(OuatEntitiesRegistry.ENTITY_REGISTRY.NPC.get(), NpcRenderer::new);
        event.registerEntityRenderer(OuatEntitiesRegistry.ENTITY_REGISTRY.NPC_FISHING_HOOK.get(), NpcFishingHookRenderer::new);
    }

    @SubscribeEvent
    public static void registerEntityLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NpcModel.LAYER_LOCATION, NpcModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerClientTooltips(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(TradeItemTooltip.class, ClientTradeItemTooltip::new);
    }
}