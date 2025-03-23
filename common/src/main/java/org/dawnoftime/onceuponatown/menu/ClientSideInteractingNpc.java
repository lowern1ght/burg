package org.dawnoftime.onceuponatown.menu;

import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.trade.NpcOffer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ClientSideInteractingNpc implements InteractingNpc {
    private Npc npc;
    private Player interactingPlayer;
    private List<NpcOffer> npcOffers;

    private ClientSideInteractingNpc(Builder builder) {
        this.npc = builder.npc;
        this.interactingPlayer = builder.interactingPlayer;
        this.npcOffers = builder.npcOffers;
    }

    public static class Builder {
        private Npc npc;
        private Player interactingPlayer;
        private List<NpcOffer> npcOffers;

        public Builder(Npc npc, Player interactingPlayer) {
            this.npc = npc;
            this.interactingPlayer = interactingPlayer;
        }

        public Builder offers(List<NpcOffer> deals) {
            this.npcOffers = deals;
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

    @Override
    public List<NpcOffer> getOffers() {
        return npcOffers;
    }

    @Override
    public void notifyDealMade(NpcOffer deal) {
        //deals.update(deal)
    }

    @Override
    public Npc getNpc() {
        return this.npc;
    }
}
