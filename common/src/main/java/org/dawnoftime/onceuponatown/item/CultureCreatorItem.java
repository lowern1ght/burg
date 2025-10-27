package org.dawnoftime.onceuponatown.item;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.OuatPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.*;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class CultureCreatorItem extends BlockItem {
    public CultureCreatorItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        return handleUse(context.getLevel(), context.getPlayer(), context.getClickedPos(), context.getClickedFace(), context.getHand()).getResult();
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        return handleUse(level, player, player.getOnPos(), Direction.NORTH, hand);
    }

    private InteractionResultHolder<ItemStack> handleUse(Level level, Player player, @NotNull BlockPos pos, @NotNull Direction direction, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        return switch (getState(stack)) {
            case 1 -> this.useCultureCreatorSelect(level, player, stack, pos, direction);
            case 2 -> this.useCultureCreatorPaste(level, player, stack);
            case 3 -> this.useCultureCreatorWaypoint(level, player, stack);
            default -> this.useCultureCreatorDefault(level, player, stack);
        };
    }

    private @NotNull InteractionResultHolder<ItemStack> useCultureCreatorDefault(Level level, @NotNull Player player, @NotNull ItemStack stack) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MinecraftServer server = serverPlayer.server;
            if (server.getPlayerList().isOp(serverPlayer.getGameProfile())) {
                Ouat.COMMON.sendToClient(serverPlayer, this.openStoredScreen(serverPlayer, stack.getOrCreateTag()));
            } else {
                Ouat.clientChat(serverPlayer, "cc", "error_need_admin_rights");
            }
        }
        return InteractionResultHolder.success(stack);
    }

    private @NotNull InteractionResultHolder<ItemStack> useCultureCreatorSelect(Level level, @NotNull Player player, @NotNull ItemStack stack, @NotNull BlockPos pos, @NotNull Direction direction) {
        if (level.isClientSide()) {
            CompoundTag currentScreenTag = stack.getOrCreateTag().getCompound("ouat_packet");
            String cultureId = currentScreenTag.getString("culture_id");
            String buildingId = currentScreenTag.getString("building_id");
            String variantId = currentScreenTag.getString("variant_id");
            int buildingLevel = currentScreenTag.getInt("level");
            Ouat.CLIENT.sendToServer(new C2SUseSelectCCPacket(player.isShiftKeyDown(), Screen.hasControlDown(), pos, direction, cultureId, buildingId, variantId, buildingLevel));
        }
        return InteractionResultHolder.success(stack);
    }

    private @NotNull InteractionResultHolder<ItemStack> useCultureCreatorPaste(Level level, @NotNull Player player, @NotNull ItemStack stack) {
        System.out.println("Mode Paste Server");
        return InteractionResultHolder.success(stack);
    }

    private @NotNull InteractionResultHolder<ItemStack> useCultureCreatorWaypoint(Level level, @NotNull Player player, @NotNull ItemStack stack) {
        System.out.println("Mode Waypoint Server");
        return InteractionResultHolder.success(stack);
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

    public static @Nullable BlockPos getPairedCCBlockPos(ItemStack ccStack) {
        try {
            return NbtUtils.readBlockPos(ccStack.getOrCreateTag().getCompound("ouat_paired_cc_block"));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void setPairedCCBlockPos(ItemStack ccStack, @Nullable BlockPos pos) {
        var tag = ccStack.getOrCreateTag();
        if (pos == null) {
            tag.remove("ouat_paired_cc_block");
        } else {
            tag.put("ouat_paired_cc_block", NbtUtils.writeBlockPos(pos));
        }
    }

    public static byte getState(ItemStack ccStack) {
        return ccStack.getOrCreateTag().getByte("ouat_culture_creator_state");
    }

    /**
     * Call this function to switch the state of the culture creator held in the hand of the client player.
     * @param state : 0 (default), 1 (select), 2 (paste), 3 (waypoint)
     */
    public static void setClientPlayerCCState(@Nullable Player player, byte state) {
        if (player != null) {
            ItemStack stack = player.getItemInHand(player.getUsedItemHand());
            if (!stack.isEmpty() && stack.getItem() instanceof CultureCreatorItem) {
                stack.getOrCreateTag().putByte("ouat_culture_creator_state", state);
            }
        }
    }

    public static void changeSelectedPage(ItemStack ccStack, @NotNull String cultureId, @NotNull String buildingId, @NotNull String variantId, int buildingLevel) {
        CompoundTag tag = ccStack.getOrCreateTag();
        CompoundTag packetTag = new CompoundTag();
        packetTag.putString("id", "s2c_open_variant_level_screen_cc");
        packetTag.putString("culture_id", cultureId);
        packetTag.putString("building_id", buildingId);
        packetTag.putString("variant_id", variantId);
        packetTag.putInt("level", buildingLevel);
        tag.put("ouat_packet", packetTag);
    }
}
