package org.dawnoftime.onceuponatown;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import org.dawnoftime.onceuponatown.client.gui.tooltip.BuildingProductionTooltip;
import org.dawnoftime.onceuponatown.client.gui.tooltip.ClientBuildingProductionTooltip;
import org.dawnoftime.onceuponatown.client.gui.tooltip.ClientItemAndTitleTooltip;
import org.dawnoftime.onceuponatown.client.gui.tooltip.ItemAndTitleTooltip;
import org.dawnoftime.onceuponatown.client.model.NpcModel;
import org.dawnoftime.onceuponatown.client.renderer.NpcRenderer;
import org.dawnoftime.onceuponatown.client.screen.VillageChestScreen;
import org.dawnoftime.onceuponatown.network.S2CTownScrollScreenPacket;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;

public class OuatFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(EntityRegistry.NPC, NpcRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(NpcModel.LAYER_LOCATION, NpcModel::createBodyLayer);
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.TOWN_ANCHOR, RenderType.translucent());
        MenuScreens.register(MenuRegistry.VILLAGE_CHEST, VillageChestScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(S2CTownScrollScreenPacket.ID,
            (client, handler, buf, responseSender) -> {
                S2CTownScrollScreenPacket packet = S2CTownScrollScreenPacket.decode(buf);
                S2CTownScrollScreenPacket.Handler.handle(packet);
            });
        TooltipComponentCallback.EVENT.register(component -> {
            if (component instanceof ItemAndTitleTooltip t) return new ClientItemAndTitleTooltip(t);
            if (component instanceof BuildingProductionTooltip t) return new ClientBuildingProductionTooltip(t);
            return null;
        });
    }
}
