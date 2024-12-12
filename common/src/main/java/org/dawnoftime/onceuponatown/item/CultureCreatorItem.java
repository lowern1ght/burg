package org.dawnoftime.onceuponatown.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class CultureCreatorItem extends Item {

    public CultureCreatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            //player.openMenu(new S2COpenCultureCreatorScreenPacket(stack.getTag()));
        }
        return InteractionResultHolder.pass(stack);
    }

    public static abstract class CultureCreatorPage{
        private CultureCreatorPage(){

        }

        //public
    }
}
