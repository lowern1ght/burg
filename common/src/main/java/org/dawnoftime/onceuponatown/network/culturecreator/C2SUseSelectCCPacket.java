package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.blockentity.CultureCreatorBlockEntity;
import org.dawnoftime.onceuponatown.item.CultureCreatorItem;
import org.dawnoftime.onceuponatown.network.OuatPacket;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static org.dawnoftime.onceuponatown.Ouat.modResource;

public record C2SUseSelectCCPacket(boolean shift, boolean ctrl, @NotNull BlockPos target, Direction clickedFace) implements OuatPacket {

    public static final ResourceLocation ID = modResource("c2s_use_select_cc");

    public static C2SUseSelectCCPacket decode(FriendlyByteBuf buf) {
        return new C2SUseSelectCCPacket(buf.readBoolean(), buf.readBoolean(), buf.readBlockPos(), buf.readEnum(Direction.class));
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(shift);
        buf.writeBoolean(ctrl);
        buf.writeBlockPos(target);
        buf.writeEnum(clickedFace);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public void handle(MinecraftServer server, ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof CultureCreatorItem && !stack.isEmpty()) {
            if (shift) {
                // Open GUI (code to do later...)
                return;
            }
            @SuppressWarnings("resource")
            ServerLevel level = player.serverLevel();
            Block ccBlock = BlockRegistry.REGISTRY.CULTURE_CREATOR_BLOCK.get();
            BlockPos currentCCB = CultureCreatorItem.getPairedCCBlockPos(stack);
            if (currentCCB != null && !level.getBlockState(currentCCB).is(ccBlock)) {
                CultureCreatorItem.setPairedCCBlockPos(stack, null);
                currentCCB = null;
            }
            BlockPos targetCCB = level.getBlockState(target).is(ccBlock) ? target : null;
            if (targetCCB != null) {
                if (targetCCB.equals(currentCCB)) {
                    // Open GUI (code to do later...)
                } else {
                    // If the target is a different CultureCreatorBlock, bind with it.
                    CultureCreatorItem.setPairedCCBlockPos(stack, targetCCB);
                }
                return;
            }

            if (ctrl) {
                // Second corner : impossible to set it if there is no first corner.
                if (currentCCB != null && level.getBlockEntity(currentCCB) instanceof CultureCreatorBlockEntity ccBE) {
                    ccBE.setSecondPos(target);
                }
            } else {
                // First corner : if the target is an empty block, place the ccBlock here (and remove previous one).
                BlockPos placePos = target;
                if (!level.getBlockState(placePos).isAir()) {
                    placePos = placePos.relative(clickedFace);
                    if (!level.getBlockState(placePos).isAir()) {
                        return;
                    }
                }
                level.setBlock(placePos, ccBlock.defaultBlockState(), 2);
                if (currentCCB != null) {
                    level.setBlock(currentCCB, Blocks.AIR.defaultBlockState(), 2);
                }
                CultureCreatorItem.setPairedCCBlockPos(stack, placePos);
            }
        }
    }

    /*
    On right-click (not shift, not ctrl)
    If target blockpos contains a culture creator entity not binded:
        Save entity UUID as binded entity in metadata
    If target blockpos contains a culture creator entity:
        if this entity is the binded one
            Delete the entity
    else
        if the currently binded entity is the same building variant than the current build variant
        of the culture creator item.
            Move the binded entity to the new location.
        else
            Create new entity at the location and save its UUID as binded entity in the culture creator item.
     */
}
