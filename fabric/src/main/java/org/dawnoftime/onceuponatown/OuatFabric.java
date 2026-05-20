package org.dawnoftime.onceuponatown;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.dawnoftime.onceuponatown.command.TownCommand;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.item.TownScrollItem;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.network.S2CTownScrollScreenPacket;
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
    }
}
