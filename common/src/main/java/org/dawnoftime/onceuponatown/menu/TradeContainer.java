package org.dawnoftime.onceuponatown.menu;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.ObjectUtils;
import org.dawnoftime.onceuponatown.trade.BuyDeal;
import org.dawnoftime.onceuponatown.trade.MerchantDeal;
import org.dawnoftime.onceuponatown.trade.TradeUtils;

import javax.annotation.Nullable;
import java.util.List;

public class TradeContainer implements Container {
    private final InteractingNpc interactingNpc;
    private final NonNullList<ItemStack> itemStacks = NonNullList.withSize(3, ItemStack.EMPTY);
    @Nullable
    private MerchantDeal activeDeal;
    private int selectedDealIndex;
    private static final int INPUT_A = 0;
    private static final int INPUT_B = 1;
    private static final int RESULT = 2;

    public TradeContainer(InteractingNpc interactingNpc) {
        this.interactingNpc = interactingNpc;
    }

    public int getContainerSize() {
        return this.itemStacks.size();
    }

    public boolean isEmpty() {
        for(ItemStack stack : this.itemStacks) {
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

    public boolean stillValid(Player pPlayer) {
        return this.interactingNpc.getInteractingPlayer() == pPlayer;
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
            List<MerchantDeal> deals = this.interactingNpc.getMerchantDeals();
            if (!deals.isEmpty()) {
                MerchantDeal d = null;
                if (selectedDealIndex > 0 && selectedDealIndex < deals.size()) {
                    MerchantDeal deal = deals.get(selectedDealIndex);
                    d = deal.isSatisfiedBy(stackInSlotA, stackInSlotB) || deal.isSatisfiedBy(stackInSlotB, stackInSlotA) ? deal : null;
                } else {
                    for (MerchantDeal deal : deals) {
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
    public MerchantDeal getActiveDeal() {
        return this.activeDeal;
    }

    public void setSelectedDealIndex(int pCurrentRecipeIndex) {
        this.selectedDealIndex = pCurrentRecipeIndex;
        this.updateResultItem();
    }

    public void clearContent() {
        this.itemStacks.clear();
    }

}
