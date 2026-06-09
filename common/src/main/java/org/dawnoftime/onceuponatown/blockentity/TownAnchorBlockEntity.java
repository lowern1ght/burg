package org.dawnoftime.onceuponatown.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.dawnoftime.onceuponatown.registry.BlockEntityRegistry;
import org.dawnoftime.onceuponatown.screen.VillageChestMenu;
import org.dawnoftime.onceuponatown.town.Town;

public class TownAnchorBlockEntity extends BlockEntity implements MenuProvider {
    private Town town = new Town();

    public TownAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.TOWN_ANCHOR, pos, state);
    }

    public Town getTown() { return town; }

    /**
     * Client-side tick registered via TownAnchorBlock.getTicker().
     * Mirrors CampfireBlockEntity.particleTick() - runs every client tick (20/s)
     * so the smoke column matches vanilla campfire density.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, TownAnchorBlockEntity be) {
        RandomSource random = level.random;
        // Thick smoke column: same rate/count as vanilla CampfireBlockEntity.particleTick
        if (random.nextFloat() < 0.11F) {
            for (int i = 0; i < random.nextInt(2) + 2; i++) {
                level.addAlwaysVisibleParticle(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                    pos.getX() + 0.5 + random.nextDouble() / 3.0 * (random.nextBoolean() ? 1 : -1),
                    pos.getY() + random.nextDouble() + random.nextDouble(),
                    pos.getZ() + 0.5 + random.nextDouble() / 3.0 * (random.nextBoolean() ? 1 : -1),
                    0.0, 0.07, 0.0
                );
            }
        }
        // Crackling fire sound
        if (random.nextInt(10) == 0) {
            level.playLocalSound(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                0.5f + random.nextFloat(), random.nextFloat() * 0.7f + 0.6f, false
            );
        }
        // Small lava sparkle at the base
        if (random.nextInt(10) == 0) {
            level.addParticle(
                ParticleTypes.LAVA,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                (double)(random.nextFloat() / 2.0f), 5.0E-5, (double)(random.nextFloat() / 2.0f)
            );
        }
    }

    public void setTown(Town town) {
        this.town = town;
        setChanged();
        // Notify nearby clients so they receive an up-to-date chunk data packet
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Called by the server when this block enters a client's view distance
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // Payload of the chunk sync packet - client only needs to know the block exists, not the full Town
    @Override
    public CompoundTag getUpdateTag() {
        return new CompoundTag();
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Village Chest");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new VillageChestMenu(syncId, playerInventory, town, getBlockPos());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Town", town.toNbt());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Town")) {
            this.town = Town.fromNbt(tag.getCompound("Town"));
        }
    }
}
