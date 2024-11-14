package org.dawnoftime.onceuponatown.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.dawnoftime.onceuponatown.Common;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.S2COpenTownMapScreenPacket;

public class TownMapItem extends Item {
    public TownMapItem(Properties properties) {
        super(properties);
    }
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (!level.isClientSide) {
            System.out.println("test");
            ServerPlayer serverPlayer = (ServerPlayer) player;
            int[] map = {2,5,8};
            Ouat.COMMON.sendToClient(serverPlayer, new S2COpenTownMapScreenPacket(map));
        }
        return super.use(level, player, usedHand);
    }
}
