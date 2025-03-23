package org.dawnoftime.onceuponatown.menu;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.trade.NpcOffer;

import javax.annotation.Nullable;
import java.util.List;

public class TradeContainer implements Container {
    private final TradeMenu menu;
    private final NonNullList<ItemStack> itemStacks = NonNullList.withSize(3, ItemStack.EMPTY);
    @Nullable
    private NpcOffer activeDeal;
    private static final int INPUT_A = 0;
    private static final int INPUT_B = 1;
    private static final int RESULT = 2;

    public TradeContainer(TradeMenu menu) {
        this.menu = menu;
    }

    public int getContainerSize() {
        return this.itemStacks.size();
    }

    public boolean isEmpty() {
        for (ItemStack stack : this.itemStacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public ItemStack getItem(int index) {
        return this.itemStacks.get(index);
    }

    public ItemStack removeItem(int index, int count) {
        ItemStack stack = this.itemStacks.get(index);
        if (index == RESULT && !stack.isEmpty()) {
            return ContainerHelper.removeItem(this.itemStacks, index, stack.getCount());
        } else {
            ItemStack itemstack1 = ContainerHelper.removeItem(this.itemStacks, index, count);
            if (!itemstack1.isEmpty() && this.isPaymentSlot(index)) {
                this.updateResultItem();
            }

            return itemstack1;
        }
    }

    private boolean isPaymentSlot(int slot) {
        return slot == INPUT_A || slot == INPUT_B;
    }

    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(this.itemStacks, index);
    }

    public void setItem(int index, ItemStack stack) {
        this.itemStacks.set(index, stack);
        if (!stack.isEmpty() && stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        if (this.isPaymentSlot(index)) {
            this.updateResultItem();
        }

    }

    public boolean stillValid(Player player) {
        return menu.interactingNpc.getInteractingPlayer() == player;
    }

    public void setChanged() {
        this.updateResultItem();
    }

    public void updateResultItem() {
        this.activeDeal = null;
        ItemStack stackInSlotA = this.itemStacks.get(INPUT_A);
        ItemStack stackInSlotB = this.itemStacks.get(INPUT_B);

        if (stackInSlotA.isEmpty() && stackInSlotB.isEmpty()) {
            this.setItem(RESULT, ItemStack.EMPTY);
        } else {
            List<NpcOffer> deals = menu.getOffers();
            if (!deals.isEmpty()) {
                NpcOffer d = null;
                if (menu.getActiveOffer() > 0 && menu.getActiveOffer() < deals.size()) {
                    NpcOffer deal = deals.get(menu.getActiveOffer());
                    d = deal.isSatisfiedBy(stackInSlotA, stackInSlotB) || deal.isSatisfiedBy(stackInSlotB, stackInSlotA) ? deal : null;
                } else {
                    for (NpcOffer deal : deals) {
                        if (deal.isSatisfiedBy(stackInSlotA, stackInSlotB) || deal.isSatisfiedBy(stackInSlotB, stackInSlotA)) {
                            d = deal;
                            break;
                        }
                    }
                }
                if (d != null) {
                    this.activeDeal = d;
                    this.setItem(RESULT, d.assemble());
                } else {
                    this.setItem(RESULT, ItemStack.EMPTY);
                }
            }
            //this.traderComponent.notifyTradeUpdated(this.getItem(2));
        }
    }

    @Nullable
    public NpcOffer getActiveDeal() {
        return this.activeDeal;
    }

    public void clearContent() {
        this.itemStacks.clear();
    }

}
