package org.dawnoftime.onceuponatown.menu;

import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.trade.BuyDeal;
import org.dawnoftime.onceuponatown.trade.SellDeal;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ClientSideInteractingNpc implements NpcInteraction {
    private Npc npc;
    private Player interactingPlayer;
    private List<BuyDeal> buyDeals;
    private List<SellDeal> sellDeals;

    private ClientSideInteractingNpc(Builder builder) {
        this.npc = builder.npc;
        this.interactingPlayer = builder.interactingPlayer;
        this.buyDeals = builder.buyDeals;
        this.sellDeals = builder.sellDeals;
    }

    public static class Builder {
        private Npc npc;
        private Player interactingPlayer;
        private List<BuyDeal> buyDeals;
        private List<SellDeal> sellDeals;

        public Builder(Npc npc, Player interactingPlayer) {
            this.npc = npc;
            this.interactingPlayer = interactingPlayer;
        }

        public Builder buyDeals(List<BuyDeal> deals) {
            this.buyDeals = deals;
            return this;
        }

        public Builder sellDeals(List<SellDeal> deals) {
            this.sellDeals = deals;
            return this;
        }

        public Builder quests() {
            return this;
        }

        public ClientSideInteractingNpc build() {
            return new ClientSideInteractingNpc(this);
        }
    }

    @Nullable
    @Override
    public Player getInteractingPlayer() {
        return this.interactingPlayer;
    }

    @Override
    public void setInteractingPlayer(@Nullable Player player) {
        this.interactingPlayer = player;
    }

    public List<BuyDeal> getBuyDeals() {
        return this.buyDeals;
    }

    public List<SellDeal> getSellDeals() {
        return this.sellDeals;
    }



    @Override
    public void notifyDealMade(BuyDeal deal) {
        //deals.update(deal)
    }

    @Override
    public Npc getNpc() {
        return this.npc;
    }
}
