package org.dawnoftime.onceuponatown.menu;

import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;
import org.dawnoftime.onceuponatown.trade.NpcOffer;
import org.dawnoftime.onceuponatown.trade.TradeUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

public class TradeMenu extends NpcBaseMenu {
    private static final int INPUT_A = 0;
    private static final int INPUT_B = 1;
    private static final int RESULT = 2;
    private static final int INV_START = 3;
    private static final int INV_END = 29;
    private static final int HOT_BAR_START = 30;
    private static final int HOT_BAR_END = 38;
    private static final int INPUT_A_X = 134;
    private static final int INPUT_B_X = 164;
    private static final int RESULT_X = 232;
    private static final int ROW_Y = 38;
    private final TradeContainer tradeContainer;
    private boolean sellMode;
    private int selectedOffer = -1;
    private NpcOffer activeOffer;

    public TradeMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, new InteractingNpcClient
            .Builder((Npc) (inventory.player.level().getEntity(buf.readInt())), inventory.player)
            .offers(TradeUtils.createOffersFromStream(buf))
            .build());
    }

    public TradeMenu(int containerId, Inventory inventory, InteractingNpc npc) {
        super(MenuRegistry.REGISTRY.TRADE_MENU.get(), containerId, npc);
        this.npc = npc;
        npc.setInteractingPlayer(inventory.player);
        tradeContainer = new TradeContainer();
        // Payment
        addSlot(new Slot(tradeContainer, INPUT_A, INPUT_A_X, ROW_Y));
        addSlot(new Slot(tradeContainer, INPUT_B, INPUT_B_X, ROW_Y));
        // Result
        addSlot(new TradeResultSlot(RESULT, RESULT_X, ROW_Y));
        // Inventory
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(inventory, j + i * 9 + 9, 113 + j * 18, 84 + i * 18));
            }
        }
        // Hot bar
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(inventory, k, 113 + k * 18, 142));
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int slotIndex) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            stack = stackInSlot.copy();
            if (slotIndex == RESULT) {
                // da function has start index included but end index excluded
                if (!moveItemStackTo(stackInSlot, INV_START, HOT_BAR_END + 1, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stackInSlot, stack);
                playThankYouSound();
            } else if (slotIndex != INPUT_A && slotIndex != INPUT_B) {
                if (slotIndex >= INV_START && slotIndex <= INV_END) {
                    if (!moveItemStackTo(stackInSlot, HOT_BAR_START, HOT_BAR_END + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= HOT_BAR_START && slotIndex <= HOT_BAR_END) {
                    if (!moveItemStackTo(stackInSlot, INV_START, INV_END + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (!moveItemStackTo(stackInSlot, INV_START, HOT_BAR_END + 1, false)) {
                return ItemStack.EMPTY;
            }
            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stackInSlot.getCount() == stack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stackInSlot);
        }
        return stack;
    }

    private void suggestPayment() {
        if (selectedOffer >= 0 && selectedOffer < getOffers().size()) {
            moveItemsBackInInventory();
            if (tradeContainer.getItem(INPUT_A).isEmpty() && tradeContainer.getItem(INPUT_B).isEmpty()) {
                NpcOffer offer = getOffers().get(selectedOffer);
                moveFromInventoryToPaymentSlot(INPUT_A, offer.getInputA());
                moveFromInventoryToPaymentSlot(INPUT_B, offer.getInputB());
            }
        }
    }

    private void moveFromInventoryToPaymentSlot(int paymentSlot, ItemStack input) {
        if (!input.isEmpty()) {
            for (int i = INV_START; i < HOT_BAR_END + 1; ++i) {
                ItemStack stack = slots.get(i).getItem();
                if (!stack.isEmpty() && ItemStack.isSameItemSameTags(input, stack)) {
                    ItemStack stackInSlot = tradeContainer.getItem(paymentSlot);
                    int j = stackInSlot.isEmpty() ? 0 : stackInSlot.getCount();
                    int k = Math.min(input.getMaxStackSize() - j, stack.getCount());
                    ItemStack payment = stack.copy();
                    int l = j + k;
                    stack.shrink(k);
                    payment.setCount(l);
                    tradeContainer.setItem(paymentSlot, payment);
                    if (l >= input.getMaxStackSize()) {
                        break;
                    }
                }
            }
        }
    }

    private void moveItemsBackInInventory() {
        ItemStack inputAStack = tradeContainer.getItem(INPUT_A);
        if (!inputAStack.isEmpty()) {
            if (!moveItemStackTo(inputAStack, INV_START, HOT_BAR_END + 1, true)) {
                return;
            }
            tradeContainer.setItem(INPUT_A, inputAStack);
        }
        ItemStack inputBStack = tradeContainer.getItem(INPUT_B);
        if (!inputBStack.isEmpty()) {
            if (!moveItemStackTo(inputBStack, INV_START, HOT_BAR_END + 1, true)) {
                return;
            }
            tradeContainer.setItem(INPUT_B, inputBStack);
        }
    }

    public void selectOffer(int selectedOffer, boolean switchMode) {
        if (switchMode) {
            sellMode = !sellMode;
            moveItemsBackInInventory();
        } else {
            this.selectedOffer = selectedOffer;
            suggestPayment();
            tradeContainer.updateResult();
        }
    }

    public List<NpcOffer> getOffers() {
        return npc.getOffers()
            .stream()
            .filter((offer -> offer.getTradeType() == (sellMode ? NpcOffer.TradeType.SELL : NpcOffer.TradeType.BUY)))
            .toList();
    }

    @Override
    public void slotsChanged(@NotNull Container container) {
        tradeContainer.updateResult();
        super.slotsChanged(container);
    }

    private void playThankYouSound() {
        npc.getNpc().playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        npc.setInteractingPlayer(null);
        if (!npc.isClientSide()) {
            if (!player.isAlive() || player instanceof ServerPlayer serverPlayer && serverPlayer.hasDisconnected()) {
                ItemStack itemstack = tradeContainer.removeItemNoUpdate(INPUT_A);
                if (!itemstack.isEmpty()) {
                    player.drop(itemstack, false);
                }
                itemstack = tradeContainer.removeItemNoUpdate(INPUT_B);
                if (!itemstack.isEmpty()) {
                    player.drop(itemstack, false);
                }
            } else if (player instanceof ServerPlayer) {
                player.getInventory().placeItemBackInInventory(tradeContainer.removeItemNoUpdate(INPUT_A));
                player.getInventory().placeItemBackInInventory(tradeContainer.removeItemNoUpdate(INPUT_B));
            }
        }
    }

    @Override
    public boolean canTakeItemForPickAll(@NotNull ItemStack stack, @NotNull Slot slot) {
        return false;
    }

    public int getSelectedOffer() {
        return selectedOffer;
    }

    private class TradeResultSlot extends Slot {
        private int removeCount;

        public TradeResultSlot(int slot, int posX, int posY) {
            super(tradeContainer, slot, posX, posY);
        }

        @Override
        public void onTake(@NotNull Player player, ItemStack stack) {
            checkTakeAchievements(stack);
            NpcOffer offer = tradeContainer.getActiveDeal();
            if (offer != null) {
                ItemStack stackA = tradeContainer.getItem(0);
                ItemStack stackB = tradeContainer.getItem(1);
                if (offer.makeDeal(stackA, stackB) || offer.makeDeal(stackB, stackA)) {
                    //this.npc.notifyDealMade(deal);
                    //pPlayer.awardStat(Stats.TRADED_WITH_VILLAGER);
                    tradeContainer.setItem(0, stackA);
                    tradeContainer.setItem(1, stackB);
                }
            }
        }

        @Override
        protected void onQuickCraft(ItemStack stack, int amount) {
            removeCount += amount;
            checkTakeAchievements(stack);
        }

        @Override
        protected void checkTakeAchievements(ItemStack stack) {
            Player player = npc.getInteractingPlayer();
            stack.onCraftedBy(player.level(), player, removeCount);
            removeCount = 0;
        }

        @Override
        public @NotNull ItemStack remove(int amount) {
            if (hasItem()) {
                removeCount += Math.min(amount, getItem().getCount());
            }
            return super.remove(amount);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }
    }

    public class TradeContainer implements Container {
        private final NonNullList<ItemStack> itemStacks = NonNullList.withSize(3, ItemStack.EMPTY);

        public void updateResult() {
            TradeMenu.this.activeOffer = null;
            ItemStack stackInSlotA = itemStacks.get(INPUT_A);
            ItemStack stackInSlotB = itemStacks.get(INPUT_B);

            if (stackInSlotA.isEmpty() && stackInSlotB.isEmpty()) {
                setItem(RESULT, ItemStack.EMPTY);
            } else {
                List<NpcOffer> offers = TradeMenu.this.getOffers();
                if (!offers.isEmpty()) {
                    NpcOffer newOffer = null;
                    if (TradeMenu.this.getSelectedOffer() > 0 && TradeMenu.this.getSelectedOffer() < offers.size()) {
                        NpcOffer deal = offers.get(TradeMenu.this.getSelectedOffer());
                        newOffer = deal.isValidInput(stackInSlotA, stackInSlotB) || deal.isValidInput(stackInSlotB, stackInSlotA) ? deal : null;
                    } else {
                        for (NpcOffer deal : offers) {
                            if (deal.isValidInput(stackInSlotA, stackInSlotB) || deal.isValidInput(stackInSlotB, stackInSlotA)) {
                                newOffer = deal;
                                break;
                            }
                        }
                    }
                    if (newOffer != null) {
                        TradeMenu.this.activeOffer = newOffer;
                        this.setItem(RESULT, newOffer.assemble());
                    } else {
                        this.setItem(RESULT, ItemStack.EMPTY);
                    }
                }
            }
        }

        @Override
        public int getContainerSize() {
            return itemStacks.size();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : itemStacks) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public @NotNull ItemStack getItem(int index) {
            return itemStacks.get(index);
        }

        @Override
        public @NotNull ItemStack removeItem(int index, int count) {
            ItemStack stack = itemStacks.get(index);
            if (index == RESULT && !stack.isEmpty()) {
                return ContainerHelper.removeItem(itemStacks, index, stack.getCount());
            } else {
                ItemStack itemstack1 = ContainerHelper.removeItem(itemStacks, index, count);
                if (!itemstack1.isEmpty() && isPaymentSlot(index)) {
                    updateResult();
                }
                return itemstack1;
            }
        }

        private boolean isPaymentSlot(int slot) {
            return slot == INPUT_A || slot == INPUT_B;
        }

        @Override
        public @NotNull ItemStack removeItemNoUpdate(int index) {
            return ContainerHelper.takeItem(itemStacks, index);
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            itemStacks.set(index, stack);
            if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
                stack.setCount(getMaxStackSize());
            }
            if (isPaymentSlot(index)) {
                updateResult();
            }

        }

        @Override
        public boolean stillValid(Player player) {
            return TradeMenu.this.npc.getInteractingPlayer() == player;
        }

        @Override
        public void setChanged() {
            updateResult();
        }


        @Nullable
        public NpcOffer getActiveDeal() {
            return TradeMenu.this.activeOffer;
        }

        @Override
        public void clearContent() {
            this.itemStacks.clear();
        }

    }
}
