package org.dawnoftime.onceuponatown;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
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
import org.dawnoftime.onceuponatown.network.C2SAdvanceEraPacket;
import org.dawnoftime.onceuponatown.network.C2SClaimQuestPacket;
import org.dawnoftime.onceuponatown.network.C2SDepositPacket;
import org.dawnoftime.onceuponatown.network.C2SQueueBuildingPacket;
import org.dawnoftime.onceuponatown.network.C2SRemoveQueuedBuildingPacket;
import org.dawnoftime.onceuponatown.network.C2SUpgradeBuildingPacket;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.network.S2CBuildingDefsPacket;
import org.dawnoftime.onceuponatown.network.S2CTownScrollScreenPacket;
import org.dawnoftime.onceuponatown.network.S2CVillageHubPacket;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;

public class OuatFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(EntityRegistry.NPC, NpcRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(NpcModel.LAYER_LOCATION, NpcModel::createBodyLayer);
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.TOWN_ANCHOR, RenderType.cutout());
        MenuScreens.register(MenuRegistry.VILLAGE_CHEST, VillageChestScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(S2CTownScrollScreenPacket.ID,
            (client, handler, buf, responseSender) -> {
                S2CTownScrollScreenPacket packet = S2CTownScrollScreenPacket.decode(buf);
                S2CTownScrollScreenPacket.Handler.handle(packet);
            });
        ClientPlayNetworking.registerGlobalReceiver(S2CVillageHubPacket.ID,
            (client, handler, buf, responseSender) -> {
                S2CVillageHubPacket packet = S2CVillageHubPacket.decode(buf);
                S2CVillageHubPacket.Handler.handle(packet);
            });
        ClientPlayNetworking.registerGlobalReceiver(S2CBuildingDefsPacket.ID,
            (client, handler, buf, responseSender) -> {
                S2CBuildingDefsPacket packet = S2CBuildingDefsPacket.decode(buf);
                S2CBuildingDefsPacket.Handler.handle(packet);
            });
        TooltipComponentCallback.EVENT.register(component -> {
            if (component instanceof ItemAndTitleTooltip t) return new ClientItemAndTitleTooltip(t);
            if (component instanceof BuildingProductionTooltip t) return new ClientBuildingProductionTooltip(t);
            return null;
        });
        NetworkHelper.sendQueueBuildingPacket = (pos, defId) -> {
            var buf = PacketByteBufs.create();
            new C2SQueueBuildingPacket(pos, defId).encode(buf);
            ClientPlayNetworking.send(C2SQueueBuildingPacket.ID, buf);
        };
        NetworkHelper.sendRemoveQueuedBuildingPacket = (pos, index) -> {
            var buf = PacketByteBufs.create();
            new C2SRemoveQueuedBuildingPacket(pos, index).encode(buf);
            ClientPlayNetworking.send(C2SRemoveQueuedBuildingPacket.ID, buf);
        };
        NetworkHelper.sendUpgradeBuildingPacket = (pos, worldPosLong) -> {
            var buf = PacketByteBufs.create();
            new C2SUpgradeBuildingPacket(pos, worldPosLong).encode(buf);
            ClientPlayNetworking.send(C2SUpgradeBuildingPacket.ID, buf);
        };
        NetworkHelper.sendAdvanceEraPacket = (pos, pathId) -> {
            var buf = PacketByteBufs.create();
            new C2SAdvanceEraPacket(pos, pathId).encode(buf);
            ClientPlayNetworking.send(C2SAdvanceEraPacket.ID, buf);
        };
        NetworkHelper.sendDepositPacket = pos -> {
            var buf = PacketByteBufs.create();
            new C2SDepositPacket(pos).encode(buf);
            ClientPlayNetworking.send(C2SDepositPacket.ID, buf);
        };
        NetworkHelper.sendClaimQuestPacket = (pos, questId) -> {
            var buf = PacketByteBufs.create();
            new C2SClaimQuestPacket(pos, questId).encode(buf);
            ClientPlayNetworking.send(C2SClaimQuestPacket.ID, buf);
        };
    }
}
