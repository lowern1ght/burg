package org.dawnoftime.onceuponatown.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.network.S2CTownScrollScreenPacket;
import org.dawnoftime.onceuponatown.town.Town;

public class TownScrollItem extends Item {
    public static final int TOWN_VIEW_MAX_DIST = 100;

    public TownScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            Town town = Utils.getNearestTown(serverLevel, player.blockPosition(), TOWN_VIEW_MAX_DIST);
            if (town != null) {
                Ouat.COMMON.sendToClient(player, new S2CTownScrollScreenPacket(town.getTownMapData()));
            } else {
                player.displayClientMessage(Component.literal("There is no towns nearby"), true);
            }
        }
        return super.use(level, player, hand);
    }
}
