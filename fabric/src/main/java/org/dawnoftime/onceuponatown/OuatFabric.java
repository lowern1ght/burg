package org.dawnoftime.onceuponatown;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import org.dawnoftime.onceuponatown.command.TownCommand;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.item.TownScrollItem;
import org.dawnoftime.onceuponatown.network.C2SQueueBuildingPacket;
import org.dawnoftime.onceuponatown.network.C2SRemoveQueuedBuildingPacket;
import org.dawnoftime.onceuponatown.network.C2SUpgradeBuildingPacket;
import org.dawnoftime.onceuponatown.network.S2CBuildingDefsPacket;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.network.S2CTownScrollScreenPacket;
import org.dawnoftime.onceuponatown.network.S2CVillageHubPacket;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.registry.ItemRegistry;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;
import org.dawnoftime.onceuponatown.screen.VillageChestMenu;
import org.dawnoftime.onceuponatown.tick.TickScheduler;

public class OuatFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Ouat.init();
        MenuRegistry.VILLAGE_CHEST = ScreenHandlerRegistry.registerSimple(
            Ouat.modResource("village_chest"),
            (syncId, inv) -> new VillageChestMenu(syncId, inv)
        );
        ItemRegistry.TOWN_SCROLL = Registry.register(BuiltInRegistries.ITEM,
            Ouat.modResource("town_scroll"),
            new TownScrollItem(new net.minecraft.world.item.Item.Properties().stacksTo(1)));
        ItemRegistry.TOWN_ANCHOR = Registry.register(BuiltInRegistries.ITEM,
            Ouat.modResource("town_anchor"),
            new BlockItem(BlockRegistry.TOWN_ANCHOR, new net.minecraft.world.item.Item.Properties()));
        FabricDefaultAttributeRegistry.register(EntityRegistry.NPC, Npc.createAttributes());
        CommandRegistrationCallback.EVENT.register((dispatcher, context, env) ->
            TownCommand.register(dispatcher, context));
        ServerLifecycleEvents.SERVER_STARTING.register(BuildingDataHandler::reload);
        ServerTickEvents.END_SERVER_TICK.register(TickScheduler::tick);
        NetworkHelper.sendTownScrollPacket = (player, data) -> {
            var buf = PacketByteBufs.create();
            new S2CTownScrollScreenPacket(data).encode(buf);
            ServerPlayNetworking.send(player, S2CTownScrollScreenPacket.ID, buf);
        };
        NetworkHelper.sendVillageHubPacket = (player, data) -> {
            var buf = PacketByteBufs.create();
            new S2CVillageHubPacket(data).encode(buf);
            ServerPlayNetworking.send(player, S2CVillageHubPacket.ID, buf);
        };
        ServerPlayNetworking.registerGlobalReceiver(C2SQueueBuildingPacket.ID,
            (server, player, handler, buf, responseSender) -> {
                C2SQueueBuildingPacket packet = C2SQueueBuildingPacket.decode(buf);
                server.execute(() -> C2SQueueBuildingPacket.Handler.handle(packet, player));
            });
        ServerPlayNetworking.registerGlobalReceiver(C2SRemoveQueuedBuildingPacket.ID,
            (server, player, handler, buf, responseSender) -> {
                C2SRemoveQueuedBuildingPacket packet = C2SRemoveQueuedBuildingPacket.decode(buf);
                server.execute(() -> C2SRemoveQueuedBuildingPacket.Handler.handle(packet, player));
            });
        ServerPlayNetworking.registerGlobalReceiver(C2SUpgradeBuildingPacket.ID,
            (server, player, handler, buf, responseSender) -> {
                C2SUpgradeBuildingPacket packet = C2SUpgradeBuildingPacket.decode(buf);
                server.execute(() -> C2SUpgradeBuildingPacket.Handler.handle(packet, player));
            });
        NetworkHelper.sendBuildingDefsPacket = (player, data) -> {
            var buf = PacketByteBufs.create();
            new S2CBuildingDefsPacket(data).encode(buf);
            ServerPlayNetworking.send(player, S2CBuildingDefsPacket.ID, buf);
        };
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var buf = PacketByteBufs.create();
            new S2CBuildingDefsPacket(BuildingDataHandler.buildDefsPacketData()).encode(buf);
            ServerPlayNetworking.send(handler.getPlayer(), S2CBuildingDefsPacket.ID, buf);
        });
    }
}
