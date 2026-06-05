package org.dawnoftime.onceuponatown.item;

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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.blockentity.CultureCreatorBlockEntity;
import org.dawnoftime.onceuponatown.building.schematic.Waypoint;
import org.dawnoftime.onceuponatown.network.OuatPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.*;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class CultureCreatorItem extends BlockItem {
    public CultureCreatorItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        return handleUse(context).getResult();
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        BlockPos pos = player.blockPosition();
        return handleUse(new UseOnContext(player, hand, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    private InteractionResultHolder<ItemStack> handleUse(@NotNull UseOnContext context) {
        return switch (getState(context.getItemInHand())) {
            case 1 -> this.useCultureCreatorSelect(context);
            case 2 -> this.useCultureCreatorPaste(context);
            case 3 -> this.useCultureCreatorWaypoint(context);
            default -> this.useCultureCreatorDefault(context);
        };
    }

    private @NotNull InteractionResultHolder<ItemStack> useCultureCreatorDefault(@NotNull UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!context.getLevel().isClientSide() && context.getPlayer() instanceof ServerPlayer serverPlayer) {
            MinecraftServer server = serverPlayer.server;
            if (server.getPlayerList().isOp(serverPlayer.getGameProfile())) {
                Ouat.COMMON.sendToClient(serverPlayer, this.openStoredScreen(serverPlayer, stack.getOrCreateTag()));
            } else {
                Ouat.clientChat(serverPlayer, "cc", "error_need_admin_rights");
            }
        }
        return InteractionResultHolder.success(stack);
    }

    private @NotNull InteractionResultHolder<ItemStack> useCultureCreatorSelect(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResultHolder.pass(ItemStack.EMPTY);
        }
        ItemStack stack = player.getItemInHand(context.getHand());

        if (stack.isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }

        // If shifting, open the GUI
        if (player.isShiftKeyDown()) {
            return InteractionResultHolder.success(stack);
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Block ccBlock = BlockRegistry.REGISTRY.CULTURE_CREATOR_BLOCK.get();
        BlockPos currentCCB = getPairedCCBlockPos(stack);
        if (currentCCB != null && !level.getBlockState(currentCCB).is(ccBlock)) {
            this.setPairedCCBlockPos(stack, null);
            currentCCB = null;
        }
        BlockPos targetCCB = level.getBlockState(pos).is(ccBlock) ? pos : null;
        if (targetCCB != null) {
            if (targetCCB.equals(currentCCB)) {
                // Open GUI
                setClientPlayerCCState(player, (byte) 0);
                this.useCultureCreatorDefault(context);
            } else {
                // If the target is a different CultureCreatorBlock, bind with it.
                this.setPairedCCBlockPos(stack, targetCCB);
                if (level.getBlockEntity(targetCCB) instanceof CultureCreatorBlockEntity ccBE) {
                    this.changeSelectedPage(stack, ccBE.getCultureId(), ccBE.getBuildingId(), ccBE.getVariantId(), ccBE.getBuildingLevel());
                }
            }
            return InteractionResultHolder.success(stack);
        }

        if (currentCCB == null) {
            // First corner : if the target is an empty block, place the ccBlock here.
            this.place(new BlockPlaceContext(context));
        } else {
            // Second corner.
            if (level.getBlockEntity(currentCCB) instanceof CultureCreatorBlockEntity ccBE && player instanceof ServerPlayer serverPlayer) {
                ccBE.setSize(serverPlayer, pos);
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    protected boolean placeBlock(@NotNull BlockPlaceContext context, @NotNull BlockState state) {
        if (super.placeBlock(context, state)) {
            this.setPairedCCBlockPos(context.getItemInHand(), context.getClickedPos());
            return true;
        }
        return false;
    }

    @Override
    protected boolean updateCustomBlockEntityTag(@NotNull BlockPos pos, @NotNull Level level, @Nullable Player player, @NotNull ItemStack stack, @NotNull BlockState state) {
        boolean updated = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CultureCreatorBlockEntity ccBE) {
            CompoundTag currentScreenTag = stack.getOrCreateTag().getCompound("ouat_packet");
            String cultureId = currentScreenTag.getString("culture_id");
            String buildingId = currentScreenTag.getString("building_id");
            String variantId = currentScreenTag.getString("variant_id");
            int buildingLevel = currentScreenTag.getInt("level");
            ccBE.setParameters(cultureId, buildingId, variantId, buildingLevel);
            return true;
        }
        return updated;
    }

    private @NotNull InteractionResultHolder<ItemStack> useCultureCreatorPaste(@NotNull UseOnContext context) {
        System.out.println("Mode Paste Server");
        return InteractionResultHolder.success(context.getItemInHand());
    }

    private @NotNull InteractionResultHolder<ItemStack> useCultureCreatorWaypoint(@NotNull UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!context.getLevel().isClientSide() && context.getPlayer() instanceof ServerPlayer serverPlayer) {
            // If shifting, opening the GUI. Otherwise, place waypoint.
            if (serverPlayer.isShiftKeyDown()) {
                MinecraftServer server = serverPlayer.server;
                if (server.getPlayerList().isOp(serverPlayer.getGameProfile())) {
                    CompoundTag contentTag = OpenScreenPacket.getPacketTag(stack.getOrCreateTag());
                    if (contentTag != null) {
                        Ouat.COMMON.sendToClient(serverPlayer, S2COpenWaypointsCCScreenPacket.decode(contentTag));
                    }
                } else {
                    Ouat.clientChat(serverPlayer, "cc", "error_need_admin_rights");
                    return InteractionResultHolder.pass(stack);
                }
            } else {
                Waypoint wp = getSelectedWaypoint(stack);
                if (wp == null) {
                    Ouat.clientChat(serverPlayer, "cc", "error");
                    return InteractionResultHolder.pass(stack);
                }
                Level level = context.getLevel();
                BlockPos currentCCB = getPairedCCBlockPos(stack);
                if (currentCCB != null && level.getBlockEntity(currentCCB) instanceof CultureCreatorBlockEntity ccBE) {
                    ccBE.setOrRemoveWaypoint(context.getClickedPos(), wp);
                } else {
                    Ouat.clientChat(serverPlayer, "cc", "error_missing_paired_block");

                    setClientPlayerCCState(serverPlayer, (byte) 0);
                    return this.useCultureCreatorDefault(context);
                }
            }
        }
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

    private void setPairedCCBlockPos(ItemStack ccStack, @Nullable BlockPos pos) {
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

    public static @Nullable Waypoint getSelectedWaypoint(ItemStack ccStack) {
        try {
            String wpName = ccStack.getOrCreateTag().getString("ouat_culture_creator_waypoint");
            return Waypoint.valueOf(wpName);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void setSelectedWaypoint(ItemStack ccStack,Waypoint wp) {
        ccStack.getOrCreateTag().putString("ouat_culture_creator_waypoint", wp.name());
    }

    private void changeSelectedPage(ItemStack ccStack, @NotNull String cultureId, @NotNull String buildingId, @NotNull String variantId, int buildingLevel) {
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
