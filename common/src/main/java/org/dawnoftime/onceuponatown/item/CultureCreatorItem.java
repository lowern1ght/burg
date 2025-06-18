package org.dawnoftime.onceuponatown.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

import javax.annotation.Nullable;

public class CultureCreatorItem extends Item {

    public CultureCreatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            return switch (getState(stack)) {
                case 1 -> this.useCultureCreatorSelect(serverPlayer, stack);
                case 2 -> this.useCultureCreatorPaste(serverPlayer, stack);
                case 3 -> this.useCultureCreatorWaypoint(serverPlayer, stack);
                default -> this.useCultureCreatorDefault(serverPlayer, stack);
            };
        } else {
            return InteractionResultHolder.pass(stack);
        }
    }

    private @NotNull InteractionResultHolder<ItemStack> useCultureCreatorSelect(@NotNull ServerPlayer serverPlayer, @NotNull ItemStack stack) {
        System.out.println("Mode Select");
        return InteractionResultHolder.success(stack);
    }

    private @NotNull InteractionResultHolder<ItemStack> useCultureCreatorPaste(@NotNull ServerPlayer serverPlayer, @NotNull ItemStack stack) {
        System.out.println("Mode Paste");
        return InteractionResultHolder.success(stack);
    }

    private @NotNull InteractionResultHolder<ItemStack> useCultureCreatorWaypoint(@NotNull ServerPlayer serverPlayer, @NotNull ItemStack stack) {
        System.out.println("Mode Waypoint");
        return InteractionResultHolder.success(stack);
    }

    private @NotNull InteractionResultHolder<ItemStack> useCultureCreatorDefault(@NotNull ServerPlayer serverPlayer, @NotNull ItemStack stack) {
        MinecraftServer server = serverPlayer.server;
        if (server.getPlayerList().isOp(serverPlayer.getGameProfile())) {
            Ouat.COMMON.sendToClient(serverPlayer, this.openStoredScreen(serverPlayer, stack.getOrCreateTag()));
            return InteractionResultHolder.success(stack);
        } else {
            Ouat.clientChat(serverPlayer, "cc", "error_need_admin_rights");
            return InteractionResultHolder.fail(stack);
        }
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
                    case "s2c_open_variant_level_screen_cc" -> S2COpenVariantLevelCCScreenPacket.decode(contentTag);
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

    public static byte getState(ItemStack creatorState) {
        return creatorState.getOrCreateTag().getByte("culture_creator_state");
    }

    /**
     * Call this function to switch the state of the culture creator held in the hand of the client player.
     * @param state : 0 (default), 1 (select), 2 (paste), 3 (waypoint)
     */
    public static void setClientPlayerCCState(@Nullable Player player, byte state) {
        if (player != null) {
            ItemStack stack = player.getItemInHand(player.getUsedItemHand());
            if (!stack.isEmpty() && stack.getItem() instanceof CultureCreatorItem) {
                CompoundTag tag = stack.getOrCreateTag();
                tag.putByte("culture_creator_state", state);
                stack.setTag(tag);
                System.out.println(state);
            }
        }
    }
}
