package org.dawnoftime.onceuponatown.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.registry.BlockEntityRegistry;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TownAnchorBlock extends BaseEntityBlock {

    private static final Logger LOGGER = LoggerFactory.getLogger(TownAnchorBlock.class);

    public static final MapCodec<TownAnchorBlock> CODEC = simpleCodec(TownAnchorBlock::new);

    public static Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
            .noOcclusion()                     // campfire model is not a full cube visually
            .strength(-1.0f, Float.MAX_VALUE)  // indestructible except by creative players
            .lightLevel(s -> 15);              // emits warm light like a lit campfire
    }

    public TownAnchorBlock(Properties props) { super(props); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TownAnchorBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                             Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            TownAnchorBlockEntity be = (TownAnchorBlockEntity) level.getBlockEntity(pos);
            if (be != null) {
                Town town = LevelTowns.get((net.minecraft.server.level.ServerLevel) level).getTownAt(pos).orElse(null);
                if (town == null) {
                    // A campfire with no town behind it. Used to answer with nothing at all, which
                    // is indistinguishable from a broken screen; this is how the second anchor the
                    // spawn command placed managed to look identical to the real one.
                    if (player instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "onceuponatown.message.town_anchor.no_town", pos.toShortString()));
                    }
                    return InteractionResult.FAIL;
                }
                net.minecraft.nbt.CompoundTag hubData = town.getHubData(pos);
                hubData.putBoolean("ChatSubscribed", town.isChatSubscriber(player.getUUID()));
                NetworkHelper.sendTownHubPacket.accept((ServerPlayer) player, hubData);
                player.openMenu(be);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Tells the player why the campfire is about to come straight back. It does not stop the break.
     *
     * <p>Worth being exact about, because the old comment here — "only creative players can remove
     * it" — read as if returning the state cancelled the removal. It does not. Verified in the
     * bytecode of {@code ServerPlayerGameMode.destroyBlock}: the returned state is stored in a
     * local and {@code level.removeBlock(pos, false)} is then called <b>unconditionally</b>; the
     * return only feeds the drop and the effects. Survival is safe for a different reason
     * entirely — hardness {@code -1} means the mining never finishes. Creative skips mining, so in
     * creative the block always went.
     *
     * <p>The guard that actually holds is {@link #onRemove}.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && player instanceof ServerPlayer sp) {
            LevelTowns.get(serverLevel).getTownAt(pos).ifPresent(town -> {
                String townName = town.getName();
                int buildingCount = town.getBuildings().size();
                sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "onceuponatown.message.town_anchor.cant_remove", townName, buildingCount));
            });
        }
        if (!player.isCreative()) return state;
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Puts the anchor back whenever it is removed while a town is registered here.
     *
     * <p>This is the one hook every route to a missing anchor passes through — a creative click,
     * {@code /setblock}, {@code /fill}, a wither (blast resistance does not stop one), another mod
     * writing blocks. Guarding the player's break alone would have covered the least likely of
     * them, and the cost of missing one is not cosmetic: the record in {@link LevelTowns} survives
     * with no door, so the panel is unreachable, the builders go on building for a settlement
     * nobody can see, and {@code getNearestTown} keeps handing the ghost to every citizen that
     * spawns nearby.
     *
     * <p>Deferred by a tick because we are inside the removal: writing the block here would fight
     * the write that is already in progress. Nothing is lost by re-creating the block entity —
     * it holds no state of its own, the town is the record in {@code LevelTowns}.
     *
     * <p>Which also means this is not a lock. Unregister the town and the block breaks like any
     * other; the record is what makes the door permanent, so a deliberate removal starts there.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (newState.is(this)) return;   // a state change, not a removal
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        if (LevelTowns.get(serverLevel).getTownAt(pos).isEmpty()) return;

        serverLevel.getServer().execute(() -> {
            if (serverLevel.getBlockState(pos).is(this)) return;
            serverLevel.setBlockAndUpdate(pos, defaultBlockState());
            LOGGER.warn("[OUAT-INTEGRITY] Town anchor at {} was removed and has been restored"
                + " — a registered town lives there.", pos);
        });
    }

    // Register a client-side ticker so particles/sounds run every tick like vanilla CampfireBlockEntity
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
            ? createTickerHelper(type, BlockEntityRegistry.TOWN_ANCHOR, TownAnchorBlockEntity::clientTick)
            : null;
    }
}
