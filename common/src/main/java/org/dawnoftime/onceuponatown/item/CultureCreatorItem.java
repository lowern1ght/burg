package org.dawnoftime.onceuponatown.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.OuatPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.*;
import org.jetbrains.annotations.NotNull;

public class CultureCreatorItem extends Item {

    public CultureCreatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (player instanceof ServerPlayer serverPlayer) {
                MinecraftServer server = serverPlayer.server;
                if (server.getPlayerList().isOp(serverPlayer.getGameProfile())) {
                    Ouat.COMMON.sendToClient(player, this.openStoredScreen(player, stack.getOrCreateTag()));
                    return InteractionResultHolder.success(player.getItemInHand(hand));
                } else {
                    Ouat.clientChat(serverPlayer, "cc", "error_need_admin_rights");
                    return InteractionResultHolder.fail(player.getItemInHand(hand));
                }
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    private OuatPacket openStoredScreen(@NotNull Player player, @NotNull CompoundTag tag) {
        OuatPacket packet = null;
        String packetId = OpenScreenPacket.decodePacketId(tag);
        if (packetId != null) {
            CompoundTag contentTag = OpenScreenPacket.getPacketTag(tag);
            if (contentTag != null) {
                packet = switch (packetId) {
                    case "s2c_open_culture_screen_cc" -> S2COpenCultureCCScreenPacket.decode(contentTag);
                    case "s2c_open_buildings_screen_cc" -> S2COpenBuildingsCCScreenPacket.decode(contentTag);
                    case "s2c_open_building_screen_cc" -> S2COpenBuildingCCScreenPacket.decode(contentTag);
                    case "s2c_open_levels_screen_cc" -> S2COpenLevelsCCScreenPacket.decode(contentTag);
                    case "s2c_open_level_screen_cc" -> S2COpenLevelCCScreenPacket.decode(contentTag);
                    case "s2c_open_variants_screen_cc" -> S2COpenVariantsCCScreenPacket.decode(contentTag);
                    case "s2c_open_variant_levels_screen_cc" -> S2COpenVariantLevelsCCScreenPacket.decode(contentTag);
                    // If the id is "s2c_open_cultures_screen_cc" or if it's wrong id :
                    default -> null;
                };
            }
        }
        if (packet == null) {
            packet = S2COpenCulturesCCScreenPacket.create(player);
        }
        return packet;
    }
}
