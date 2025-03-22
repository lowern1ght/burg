package org.dawnoftime.onceuponatown.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;
import org.dawnoftime.onceuponatown.trade.NpcOffer;
import org.dawnoftime.onceuponatown.trade.TradeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TradeMenu extends NpcBaseMenu {
    protected static final int INPUT_A_SLOT = 0;
    protected static final int INPUT_B_SLOT = 1;
    protected static final int RESULT_SLOT = 2;
    protected static final int INV_SLOT_START = 3;
    protected static final int INV_SLOT_END = 29;
    protected static final int HOT_BAR_SLOT_START = 30;
    protected static final int HOT_BAR_SLOT_END = 38;
    private static final int INPUT_A_X = 134;
    private static final int INPUT_B_X = 164;
    private static final int RESULT_X = 232;
    private static final int ROW_Y = 38;
    private final TradeContainer tradeContainer;
    private boolean sell;

    public TradeMenu(int containerId, Inventory playerInventory, FriendlyByteBuf friendlyByteBuf) {
        this(containerId, playerInventory, new ClientSideInteractingNpc.Builder(
                (Npc) (playerInventory.player.level().getEntity(friendlyByteBuf.readInt())), playerInventory.player)
            .merchantDeals(TradeUtils.createNpcOffersFromStream(friendlyByteBuf))
                .build());
    }

    public TradeMenu(int containerId, Inventory playerInventory, InteractingNpc npc) {
        super(MenuRegistry.REGISTRY.TRADE_MENU.get(), containerId, npc);
        this.interactingNpc = npc;
        npc.setInteractingPlayer(playerInventory.player);
        this.tradeContainer = new TradeContainer(this);
        this.addSlot(new Slot(this.tradeContainer, INPUT_A_SLOT, INPUT_A_X, ROW_Y));
        this.addSlot(new Slot(this.tradeContainer, INPUT_B_SLOT, INPUT_B_X, ROW_Y));
        this.addSlot(new TradeResultSlot(playerInventory.player, npc, this.tradeContainer, RESULT_SLOT, RESULT_X, ROW_Y));
        for (int i = 0; i < 3; ++i) { // Inventory
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 113 + j * 18, 84 + i * 18));
            }
        }
        for (int k = 0; k < 9; ++k) { // Hot bar
            this.addSlot(new Slot(playerInventory, k, 113 + k * 18, 142));
        }
    }

    @Override
    public void slotsChanged(@NotNull Container container) {
        this.tradeContainer.updateResultItem();
        super.slotsChanged(container);
    }

    public void selectDeal(int selectedDealIndex) {
        this.tradeContainer.setSelectedDealIndex(selectedDealIndex);
        trySuggestPayment(selectedDealIndex);
    }

    @Override
    public boolean canTakeItemForPickAll(@NotNull ItemStack stack, @NotNull Slot slot) {
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack stackCopy = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            stackCopy = stackInSlot.copy();
            if (slotIndex == RESULT_SLOT) {
                if (!this.moveItemStackTo(stackInSlot, INV_SLOT_START, HOT_BAR_SLOT_END + 1, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stackInSlot, stackCopy);
                this.playThankYouSound();
            } else if (slotIndex != INPUT_A_SLOT && slotIndex != INPUT_B_SLOT) { // IF SLOT IS IN PLAYER INV
                if (slotIndex >= INV_SLOT_START && slotIndex <= INV_SLOT_END) {
                    if (!this.moveItemStackTo(stackInSlot, HOT_BAR_SLOT_START, HOT_BAR_SLOT_END + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= HOT_BAR_SLOT_START && slotIndex <= HOT_BAR_SLOT_END && !this.moveItemStackTo(stackInSlot, INV_SLOT_START, INV_SLOT_END + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stackInSlot, INV_SLOT_START, HOT_BAR_SLOT_END + 1, false)) {
                return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stackInSlot.getCount() == stackCopy.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stackInSlot);
        }

        return stackCopy;
    }

    private void playThankYouSound() {
        interactingNpc.getNpc().playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        this.interactingNpc.setInteractingPlayer(null);
        if (!this.interactingNpc.isClientSide()) {
            if (!player.isAlive() || player instanceof ServerPlayer serverPlayer && serverPlayer.hasDisconnected()) {
                ItemStack itemstack = this.tradeContainer.removeItemNoUpdate(INPUT_A_SLOT);
                if (!itemstack.isEmpty()) {
                    player.drop(itemstack, false);
                }
                itemstack = this.tradeContainer.removeItemNoUpdate(INPUT_B_SLOT);
                if (!itemstack.isEmpty()) {
                    player.drop(itemstack, false);
                }
            } else if (player instanceof ServerPlayer) {
                player.getInventory().placeItemBackInInventory(this.tradeContainer.removeItemNoUpdate(INPUT_A_SLOT));
                player.getInventory().placeItemBackInInventory(this.tradeContainer.removeItemNoUpdate(INPUT_B_SLOT));
            }
        }
    }

    public void trySuggestPayment(int selectedDealIndex) {
        if (selectedDealIndex >= 0 && selectedDealIndex < this.getDeals().size()) {
            ItemStack stackInSlotA = this.tradeContainer.getItem(INPUT_A_SLOT);
            if (!stackInSlotA.isEmpty()) {
                if (!this.moveItemStackTo(stackInSlotA, INV_SLOT_START, HOT_BAR_SLOT_END + 1, true)) {
                    return;
                }
                this.tradeContainer.setItem(INPUT_A_SLOT, stackInSlotA);
            }

            ItemStack stackInSlotB = this.tradeContainer.getItem(INPUT_B_SLOT);
            if (!stackInSlotB.isEmpty()) {
                if (!this.moveItemStackTo(stackInSlotB, INV_SLOT_START, HOT_BAR_SLOT_END + 1, true)) {
                    return;
                }
                this.tradeContainer.setItem(INPUT_B_SLOT, stackInSlotB);
            }

            if (this.tradeContainer.getItem(INPUT_A_SLOT).isEmpty() && this.tradeContainer.getItem(INPUT_B_SLOT).isEmpty()) {
                ItemStack requiredA = this.getDeals().get(selectedDealIndex).getInputA();
                this.moveFromInventoryToPaymentSlot(INPUT_A_SLOT, requiredA);
                ItemStack requiredB = this.getDeals().get(selectedDealIndex).getInputB();
                this.moveFromInventoryToPaymentSlot(INPUT_B_SLOT, requiredB);
            }

        }
    }

    private void moveFromInventoryToPaymentSlot(int pPaymentSlotIndex, ItemStack pPaymentSlot) {
        if (!pPaymentSlot.isEmpty()) {
            for (int i = INV_SLOT_START; i < HOT_BAR_SLOT_END + 1; ++i) {
                ItemStack itemstack = this.slots.get(i).getItem();
                if (!itemstack.isEmpty() && ItemStack.isSameItemSameTags(pPaymentSlot, itemstack)) {
                    ItemStack itemstack1 = this.tradeContainer.getItem(pPaymentSlotIndex);
                    int j = itemstack1.isEmpty() ? 0 : itemstack1.getCount();
                    int k = Math.min(pPaymentSlot.getMaxStackSize() - j, itemstack.getCount());
                    ItemStack itemstack2 = itemstack.copy();
                    int l = j + k;
                    itemstack.shrink(k);
                    itemstack2.setCount(l);
                    this.tradeContainer.setItem(pPaymentSlotIndex, itemstack2);
                    if (l >= pPaymentSlot.getMaxStackSize()) {
                        break;
                    }
                }
            }
        }
    }

    public List<NpcOffer> getDeals() {
        return interactingNpc.getOffers()
            .stream()
            .filter((merchantDeal -> merchantDeal.getTradeType() == (sell ? NpcOffer.TradeType.SELL : NpcOffer.TradeType.BUY)))
            .toList();
    }

    public boolean isSelling() {
        return sell;
    }

    public void setSelling(boolean sell) {
        this.sell = sell;
        ItemStack stackInSlotA = this.tradeContainer.getItem(INPUT_A_SLOT);
        if (!stackInSlotA.isEmpty()) {
            if (!this.moveItemStackTo(stackInSlotA, INV_SLOT_START, HOT_BAR_SLOT_END + 1, true)) {
                return;
            }
            this.tradeContainer.setItem(INPUT_A_SLOT, stackInSlotA);
        }

        ItemStack stackInSlotB = this.tradeContainer.getItem(INPUT_B_SLOT);
        if (!stackInSlotB.isEmpty()) {
            if (!this.moveItemStackTo(stackInSlotB, INV_SLOT_START, HOT_BAR_SLOT_END + 1, true)) {
                return;
            }
            this.tradeContainer.setItem(INPUT_B_SLOT, stackInSlotB);
        }
    }

    public static class TradeResultSlot extends Slot {
        private final TradeContainer slots;
        private final Player player;
        private int removeCount;
        private final InteractingNpc npc;

        public TradeResultSlot(Player player, InteractingNpc npc, TradeContainer tradeContainer, int slot, int posX, int posY) {
            super(tradeContainer, slot, posX, posY);
            this.player = player;
            this.npc = npc;
            this.slots = tradeContainer;
        }

        /**
         * Check if the stack is allowed to be placed in this slot, used for armor slots as well as furnace fuel.
         */
        @Override
        public boolean mayPlace(ItemStack pStack) {
            return false;
        }

        /**
         * Decrease the size of the stack in slot (first int arg) by the amount of the second int arg. Returns the new stack.
         */
        @Override
        public ItemStack remove(int pAmount) {
            if (this.hasItem()) {
                this.removeCount += Math.min(pAmount, this.getItem().getCount());
            }

            return super.remove(pAmount);
        }

        /**
         * Typically increases an internal count, then calls {@code onCrafting(item)}.
         *
         * @param pStack the output - ie, iron ingots, and pickaxes, not ore and wood.
         */
        @Override
        protected void onQuickCraft(ItemStack pStack, int pAmount) {
            this.removeCount += pAmount;
            this.checkTakeAchievements(pStack);
        }

        /**
         * @param pStack the output - ie, iron ingots, and pickaxes, not ore and wood.
         */
        @Override
        protected void checkTakeAchievements(ItemStack pStack) {
            pStack.onCraftedBy(this.player.level(), this.player, this.removeCount);
            this.removeCount = 0;
        }

        @Override
        public void onTake(Player pPlayer, ItemStack pStack) {
            this.checkTakeAchievements(pStack);
            NpcOffer deal = this.slots.getActiveDeal();
            if (deal != null) {
                ItemStack stackA = this.slots.getItem(0);
                ItemStack stackB = this.slots.getItem(1);
                if (deal.makeDeal(stackA, stackB) || deal.makeDeal(stackB, stackA)) {
                    //this.npc.notifyDealMade(deal);
                    //pPlayer.awardStat(Stats.TRADED_WITH_VILLAGER);
                    this.slots.setItem(0, stackA);
                    this.slots.setItem(1, stackB);
                }
            }
        }
    }
}
