package org.dawnoftime.onceuponatown.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;

public class TownAnchorBlock extends BaseEntityBlock {

    public static Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
            .noCollission()
            .noOcclusion()
            .strength(-1.0f, Float.MAX_VALUE)
            .lightLevel(s -> 0);
    }

    public TownAnchorBlock(Properties props) { super(props); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TownAnchorBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            TownAnchorBlockEntity be = (TownAnchorBlockEntity) level.getBlockEntity(pos);
            if (be != null) {
                player.openMenu(be);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // Prevent survival destruction - only creative players can remove it
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!player.isCreative()) return;
        super.playerWillDestroy(level, pos, state, player);
    }
}
