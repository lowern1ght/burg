package org.dawnoftime.onceuponatown.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.town.LevelTowns;

public class TownScrollItem extends Item {
    public TownScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            LevelTowns.get((ServerLevel) level)
                .getNearestTown(player.blockPosition(), 256)
                .ifPresent(town ->
                    NetworkHelper.sendTownScrollPacket.accept(serverPlayer, town.getTownMapData()));
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
