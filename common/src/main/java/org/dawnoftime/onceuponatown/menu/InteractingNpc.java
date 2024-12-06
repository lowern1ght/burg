package org.dawnoftime.onceuponatown.menu;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.trade.BuyDeal;
import org.dawnoftime.onceuponatown.trade.MerchantDeal;
import org.dawnoftime.onceuponatown.trade.SellDeal;

import javax.annotation.Nullable;
import java.util.List;

public interface InteractingNpc {
    @Nullable
    Player getInteractingPlayer();

    void setInteractingPlayer(@Nullable Player player);

    List<BuyDeal> getBuyDeals();

    default List<SellDeal> getSellDeals() {
        return null;
    }

    List<MerchantDeal> getMerchantDeals();

    default Npc getNpc() {
        return null;
    }

    void notifyDealMade(BuyDeal deal);


    default SoundEvent getDealSound() {
        return null;
    }

    default boolean isClientSide() {
        return (this instanceof ClientSideInteractingNpc);
    }
}
