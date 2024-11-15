package org.dawnoftime.onceuponatown.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.model.NpcModel;
import org.dawnoftime.onceuponatown.client.renderer.NpcFishingHookRenderer;
import org.dawnoftime.onceuponatown.client.renderer.NpcRenderer;
import org.dawnoftime.onceuponatown.client.screen.BuyScreen;
import org.dawnoftime.onceuponatown.client.screen.SellScreen;
import org.dawnoftime.onceuponatown.client.screen.tooltip.ClientTradeItemTooltip;
import org.dawnoftime.onceuponatown.client.screen.tooltip.TradeItemTooltip;
import org.dawnoftime.onceuponatown.network.PacketHandler;
import org.dawnoftime.onceuponatown.network.IOuatPacket;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.registry.ItemRegistry;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;

public class ClientAbstractionsImpl implements ClientAbstractions {
    @Override
    public void sendToServer(IOuatPacket packet) {
        PacketHandler.CHANNEL.sendToServer(packet);
    }
}