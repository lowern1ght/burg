package org.dawnoftime.onceuponatown.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.dawnoftime.onceuponatown.menu.ProjectsMenu;
import org.slf4j.Logger;

import static org.dawnoftime.onceuponatown.Ouat.modResource;

public record C2SProjectsScreenPacket(String buildingId) implements OuatPacket {
    public static final ResourceLocation ID = modResource("c2s_projects_screen");

    public static C2SProjectsScreenPacket decode(FriendlyByteBuf buf) {
        return new C2SProjectsScreenPacket(buf.readUtf());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(buildingId);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public void handle(MinecraftServer server, ServerPlayer player) {
        AbstractContainerMenu containerMenu = player.containerMenu;
        if (containerMenu instanceof ProjectsMenu projectsMenu) {
            if (!projectsMenu.stillValid(player)) {
                Logger LOGGER = LogUtils.getLogger();
                LOGGER.debug("Player {} interacted with invalid menu {}", player, projectsMenu);
            } else {
                projectsMenu.startProject(buildingId);
            }
        }
    }
}