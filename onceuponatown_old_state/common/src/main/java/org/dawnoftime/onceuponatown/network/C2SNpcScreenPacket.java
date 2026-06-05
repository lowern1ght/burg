package org.dawnoftime.onceuponatown.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.client.gui.screens.NpcScreen;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.menu.*;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.trade.TradeUtils;
import org.slf4j.Logger;

import static org.dawnoftime.onceuponatown.Ouat.modResource;

public record C2SNpcScreenPacket(int newTab) implements OuatPacket {
    public static final ResourceLocation ID = modResource("c2s_npc_screen");

    public static C2SNpcScreenPacket decode(FriendlyByteBuf buf) {
        return new C2SNpcScreenPacket(buf.readVarInt());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(newTab);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public void handle(MinecraftServer server, ServerPlayer player) {
        NpcScreen.Tab tab = NpcScreen.Tab.values()[newTab];
        AbstractContainerMenu containerMenu = player.containerMenu;
        if (containerMenu instanceof NpcMenu menu) {
            if (!menu.stillValid(player)) {
                Logger LOGGER = LogUtils.getLogger();
                LOGGER.debug("Player {} interacted with invalid menu {}", player, menu);
            } else {
                Npc npc = menu.getNpc().getNpc();
                Town town = npc.getTown();
                if (town == null) {
                    return;
                }
                switch (tab) {
                    case TRADE -> {
                        Ouat.COMMON.openMenu(player, new SimpleMenuProvider((containerID, playerInventory, p) ->
                            new TradeMenu(containerID, playerInventory, npc), Ouat.translatable("trade")), buffer -> {
                            buffer.writeInt(npc.getId());
                            TradeUtils.writeOffersToStream(npc.getOffers(), buffer);
                        });
                    }
                    case QUESTS -> {
                        Ouat.COMMON.openMenu(player, new SimpleMenuProvider((containerID, playerInventory, p) ->
                            new QuestsMenu(containerID, playerInventory, npc), Ouat.translatable("quests")), buffer -> {
                            buffer.writeInt(npc.getId());
                        });
                    }
                    case BUILDINGS -> {
                        Ouat.COMMON.openMenu(player, new SimpleMenuProvider((containerID, playerInventory, p) ->
                            new BuildingsMenu(containerID, playerInventory, npc, npc.getTown().getTownMapData()), Ouat.translatable("buildings")), buffer -> {
                            buffer.writeInt(npc.getId());
                            buffer.writeNbt(npc.getTown().getTownMapData());
                        });
                    }
                    case PROGRESSION -> {
                        Ouat.COMMON.openMenu(player, new SimpleMenuProvider((containerID, playerInventory, p) ->
                            new ProgressionMenu(containerID, playerInventory, npc), Ouat.translatable("progression")), buffer -> {
                            buffer.writeInt(npc.getId());
                        });
                    }
                    case PROJECTS -> {
                        Ouat.COMMON.openMenu(player, new SimpleMenuProvider((containerID, playerInventory, p) ->
                            new ProjectsMenu(containerID, playerInventory, npc), Ouat.translatable("project")), buffer -> {
                            buffer.writeInt(npc.getId());
                            buffer.writeCollection(town.getCulture().getBuildingTypes(),
                                (buf, buildingType) -> {
                                buf.writeUtf(buildingType.getId());
                                buf.writeItem(buildingType.getIconItem().getDefaultInstance());

                            });
                            buffer.writeCollection(town.getProjects(),
                                (buf, project) -> {
                                    buf.writeUtf(project.toSafeString());
                                    Item item;
                                    if (project.getBuild().getBuildType() instanceof BuildingType building) {
                                        item = building.getIconItem();
                                    } else {
                                        item = Items.DIRT_PATH;
                                    }
                                    buf.writeItem(item.getDefaultInstance());
                                    /*
                                    buf.writeUtf(project.toSafeString());
                                    buf.writeUtf(project.getProgression());
                                    var build = project.getBuild();
                                    buf.writeUtf(build.getBuildType().getId());
                                    Item item;
                                    if (project.getBuild().getBuildType() instanceof BuildingType building) {
                                        item = building.getIconItem();
                                    } else {
                                        item = Items.DIRT_PATH;
                                    }
                                   buf.writeItem(item.getDefaultInstance());

                                     */
                            });
                        });
                    }
                    case DIPLOMACY -> {
                        Ouat.COMMON.openMenu(player, new SimpleMenuProvider((containerID, playerInventory, p) ->
                            new DiplomacyMenu(containerID, playerInventory, npc), Ouat.translatable("diplomacy")), buffer -> {
                            buffer.writeInt(npc.getId());
                        });
                    }
                }
            }
        }
    }
}
