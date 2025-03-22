package org.dawnoftime.onceuponatown.menu;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.trade.NpcOffer;

import javax.annotation.Nullable;
import java.util.List;

public interface InteractingNpc {
    @Nullable
    Player getInteractingPlayer();

    void setInteractingPlayer(@Nullable Player player);

    List<NpcOffer> getOffers();

    default Npc getNpc() {
        return null;
    }

    void notifyDealMade(NpcOffer deal);

    default SoundEvent getDealSound() {
        return null;
    }

    default boolean isClientSide() {
        return (this instanceof ClientSideInteractingNpc);
    }
}
