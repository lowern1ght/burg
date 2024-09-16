package org.dawnoftime.onceuponatown.menu;

import org.dawnoftime.onceuponatown.entity.Citizen;
import org.dawnoftime.onceuponatown.trade.BuyDeal;
import org.dawnoftime.onceuponatown.trade.SellDeal;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.List;

public interface InteractableCitizen {
    @Nullable
    Player getInteractingPlayer();

    void setInteractingPlayer(@Nullable Player player);

    List<BuyDeal> getBuyDeals();

    default List<SellDeal> getSellDeals() {
        return null;
    }

    default Citizen getCitizen() {
        return null;
    }

    void notifyDealMade(BuyDeal deal);

    default SoundEvent getDealSound() {
        return null;
    }

    default boolean isClientSide() {
        return (this instanceof ClientSideInteractingCitizen);
    }
}
