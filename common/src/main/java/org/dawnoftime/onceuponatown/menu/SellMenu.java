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
import org.dawnoftime.onceuponatown.network.C2SSellScreenPacket;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;
import org.dawnoftime.onceuponatown.trade.SellDeal;
import org.dawnoftime.onceuponatown.trade.TradeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SellMenu extends NpcBaseMenu {
    protected static final int GOOD_SLOT_START = 0;
    protected static final int GOOD_SLOT_END = 7;
    protected static final int VALUE_SHARDS_SLOT = 8;
    protected static final int VALUE_EMERALDS_SLOT = 9;
    public static final int VALUE_BLOCKS_SLOT = 10;
    protected static final int INVENTORY_START_INDEX = 11;
    protected static final int INVENTORY_END_INDEX = 37;
    protected static final int HOTBAR_START_INDEX = 38;
    protected static final int HOTBAR_END_INDEX = 46;
    private static final int INPUT_START_X = 113;
    private static final int INPUT_START_Y = 40;
    private static final int RESULT_START_X = 220;
    private static final int RESULT_ROW_Y = 50;
    private final SellContainer sellContainer;

    public SellMenu(int containerId, Inventory playerInventory, FriendlyByteBuf friendlyByteBuf) {
        this(containerId, playerInventory,
                new ClientSideInteractingNpc.Builder(
                        (Npc) (playerInventory.player.level().getEntity(friendlyByteBuf.readInt())), playerInventory.player)
                        .sellDeals(TradeUtils.createSellDealsFromStream(friendlyByteBuf))
                        .build());
    }

    public SellMenu(int containerId, Inventory playerInventory, InteractingNpc npc) {
        super(MenuRegistry.REGISTRY.SELL_MENU.get(), containerId, npc);
        this.interactingNpc = npc;
        npc.setInteractingPlayer(playerInventory.player);
        this.sellContainer = new SellContainer(npc);
        int slotWidth = 18;
        int index = 0;
        for (int i = 0; i < 2; ++i) { // Goods
            for (int j = 0; j < 4; ++j) {
                this.addSlot(new GoodsGridSlot(this.sellContainer, index, INPUT_START_X + j * slotWidth, INPUT_START_Y + i * slotWidth));
                ++index;
            }
        }
        this.addSlot(new SellResultSlot(playerInventory.player, npc, this.sellContainer, VALUE_SHARDS_SLOT, RESULT_START_X + slotWidth * 2, RESULT_ROW_Y, VALUE_SHARDS_SLOT));
        this.addSlot(new SellResultSlot(playerInventory.player, npc, this.sellContainer, VALUE_EMERALDS_SLOT, RESULT_START_X + slotWidth, RESULT_ROW_Y, VALUE_EMERALDS_SLOT));
        this.addSlot(new SellResultSlot(playerInventory.player, npc, this.sellContainer, VALUE_BLOCKS_SLOT, RESULT_START_X, RESULT_ROW_Y, VALUE_BLOCKS_SLOT));
        for (int i = 0; i < 3; ++i) { // Inventory
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 113 + j * 18, 92 + i * 18));
            }
        }
        for (int k = 0; k < 9; ++k) { // Hot bar
            this.addSlot(new Slot(playerInventory, k, 113 + k * 18, 150));
        }
    }

    public void slotsChanged(Container container) {
        //this.sellContainer.updateValue();
        super.slotsChanged(container);
    }

    public void setSelectedDeal(int selectedDealIndex) {
        //this.sellContainer.setSelectedDealIndex(selectedDealIndex);
    }

    public boolean canTakeItemForPickAll(@NotNull ItemStack stack, @NotNull Slot slot) {
        return false;
    }

    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack stackCopy = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            stackCopy = stackInSlot.copy();
            if (slotIndex == VALUE_SHARDS_SLOT || slotIndex == VALUE_EMERALDS_SLOT || slotIndex == VALUE_BLOCKS_SLOT) {
                if (!this.moveItemStackTo(stackInSlot, INVENTORY_START_INDEX, HOTBAR_END_INDEX + 1, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stackInSlot, stackCopy);
                //this.playThankYouSound();
            } else if (!(slotIndex >= GOOD_SLOT_START && slotIndex <= GOOD_SLOT_END)) { // IF SLOT IS IN PLAYER INV
                if (slotIndex >= INVENTORY_START_INDEX && slotIndex <= HOTBAR_END_INDEX) {
                    if (!this.moveItemStackTo(stackInSlot, GOOD_SLOT_START, GOOD_SLOT_END + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                }
                /*
                if (slotIndex >= INVENTORY_START_INDEX && slotIndex <= INVENTORY_END_INDEX) {
                    if (!this.moveItemStackTo(stackInSlot, INVENTORY_END_INDEX, HOTBAR_END_INDEX + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= INVENTORY_END_INDEX && slotIndex <= HOTBAR_END_INDEX && !this.moveItemStackTo(stackInSlot, INVENTORY_START_INDEX, INVENTORY_END_INDEX + 1, false)) {
                    return ItemStack.EMPTY;
                }
                 */
            } else if (!this.moveItemStackTo(stackInSlot, INVENTORY_START_INDEX, HOTBAR_END_INDEX + 1, false)) {
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
        if (this.interactingNpc instanceof Npc) {
            Npc entity = interactingNpc.getNpc();
            entity.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
        }
    }

    public boolean isGoodsGridLocked() {
        return this.sellContainer.hasConcludedTrade;
    }

    public void removed(@NotNull Player player) {
        super.removed(player);
        this.interactingNpc.setInteractingPlayer(null);
        if (this.interactingNpc.isClientSide()) return;
        ;
        if (!player.isAlive() || player instanceof ServerPlayer serverPlayer && serverPlayer.hasDisconnected()) {
            for (int i = GOOD_SLOT_START; i <= GOOD_SLOT_END; ++i) {
                ItemStack stack = this.sellContainer.removeItemNoUpdate(i);
                if (!stack.isEmpty()) player.drop(stack, false);
            }
            if (this.sellContainer.hasConcludedTrade) {
                ItemStack stack = this.sellContainer.removeItemNoUpdate(SellContainer.VALUE_SHARDS_SLOT);
                if (!stack.isEmpty()) player.drop(stack, false);
                stack = this.sellContainer.removeItemNoUpdate(SellContainer.VALUE_EMERALDS_SLOT);
                if (!stack.isEmpty()) player.drop(stack, false);
                stack = this.sellContainer.removeItemNoUpdate(SellContainer.VALUE_BLOCKS_SLOT);
                if (!stack.isEmpty()) player.drop(stack, false);
            }
        } else if (player instanceof ServerPlayer) {
            //ModLogger.info("HERE");
            for (int i = GOOD_SLOT_START; i <= GOOD_SLOT_END; ++i) {
                player.getInventory().placeItemBackInInventory(this.sellContainer.removeItemNoUpdate(i));
            }
            if (this.sellContainer.hasConcludedTrade) {
                player.getInventory().placeItemBackInInventory(this.sellContainer.removeItemNoUpdate(SellContainer.VALUE_SHARDS_SLOT));
                player.getInventory().placeItemBackInInventory(this.sellContainer.removeItemNoUpdate(SellContainer.VALUE_EMERALDS_SLOT));
                player.getInventory().placeItemBackInInventory(this.sellContainer.removeItemNoUpdate(SellContainer.VALUE_BLOCKS_SLOT));
            }
        }
    }

    public void handleClientAction(int selectedDealIndex, C2SSellScreenPacket.RequestType requestType) {
        if (requestType == C2SSellScreenPacket.RequestType.ALL_TRADES_SELL_EVERYTHING) {
            sellEverythingSellable();
        } else if ((selectedDealIndex >= 0) && selectedDealIndex < getDeals().size()) {
            ItemStack good = this.getDeals().get(selectedDealIndex).getGood();
            if (good.isEmpty()) return;
            switch (requestType) {
                case ONE_TRADE_SELL_ONE -> sellOneMatchingItem(good);
                case ONE_TRADE_REMOVE_ONE -> removeOneMatchingItem(good);
                case ONE_TRADE_SELL_EVERYTHING -> sellAllMatchingItems(good);
            }
        }
    }

    private void sellEverythingSellable() {
        for (SellDeal deal : getDeals()) {
            sellAllMatchingItems(deal.getGood());
        }
    }

    private void sellOneMatchingItem(ItemStack wantedItem) {
        for (int i = INVENTORY_START_INDEX; i <= HOTBAR_END_INDEX; ++i) {
            ItemStack playerStack = this.slots.get(i).getItem();
            if (!playerStack.isEmpty() && ItemStack.isSameItemSameTags(wantedItem, playerStack)) {
                for (int goodIndex = 0; goodIndex < 8; ++goodIndex) {
                    ItemStack stackInGoodSlot = this.sellContainer.getItem(goodIndex);
                    if (ItemStack.isSameItemSameTags(stackInGoodSlot, wantedItem) || stackInGoodSlot.isEmpty()) {
                        if ((stackInGoodSlot.getCount() < wantedItem.getMaxStackSize())) {
                            if (!stackInGoodSlot.isEmpty()) {
                                ItemStack stackInGoodSlotCopy = stackInGoodSlot.copy();
                                stackInGoodSlotCopy.grow(1);
                                this.sellContainer.setItem(goodIndex, stackInGoodSlotCopy);
                                playerStack.shrink(1);
                            } else {
                                ItemStack playerStackCopy = playerStack.copy();
                                playerStackCopy.setCount(1);
                                this.sellContainer.setItem(goodIndex, playerStackCopy);
                                playerStack.shrink(1);
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    private void removeOneMatchingItem(ItemStack wantedItem) {
        for (int i = INVENTORY_START_INDEX; i <= HOTBAR_END_INDEX; ++i) {
            ItemStack playerStack = this.slots.get(i).getItem();
            if (playerStack.isEmpty() || ItemStack.isSameItemSameTags(wantedItem, playerStack)) {
                for (int goodIndex = 0; goodIndex < 8; ++goodIndex) {
                    ItemStack stackInGoodSlot = this.sellContainer.getItem(goodIndex);
                    if (ItemStack.isSameItemSameTags(stackInGoodSlot, wantedItem) && !stackInGoodSlot.isEmpty()) {
                        if ((playerStack.getCount() < wantedItem.getMaxStackSize())) {
                            if (!playerStack.isEmpty()) {
                                ItemStack stackInGoodSlotCopy = stackInGoodSlot.copy();
                                stackInGoodSlotCopy.shrink(1);
                                this.sellContainer.setItem(goodIndex, stackInGoodSlotCopy);
                                playerStack.grow(1);
                            } else {
                                ItemStack stackInGoodSlotCopy = stackInGoodSlot.copy();
                                stackInGoodSlotCopy.setCount(1);
                                this.slots.get(i).set(stackInGoodSlotCopy);
                                stackInGoodSlot.shrink(1);
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    private void sellAllMatchingItems(ItemStack wantedItem) {
        Set<Integer> ignoredSlots = new HashSet<>();
        playerInvLoop:
        for (int i = INVENTORY_START_INDEX; i <= HOTBAR_END_INDEX; ++i) {
            ItemStack playerStack = this.slots.get(i).getItem();
            if (!playerStack.isEmpty() && ItemStack.isSameItemSameTags(wantedItem, playerStack)) {
                for (int goodIndex = 0; goodIndex < 8; ++goodIndex) {
                    if (!ignoredSlots.contains(goodIndex)) {
                        ItemStack stackInGoodSlot = this.sellContainer.getItem(goodIndex);
                        if (ItemStack.isSameItemSameTags(stackInGoodSlot, wantedItem) || stackInGoodSlot.isEmpty()) {
                            int goodCount = stackInGoodSlot.isEmpty() ? 0 : stackInGoodSlot.getCount();
                            int shrinkable = Math.min(wantedItem.getMaxStackSize() - goodCount, playerStack.getCount());
                            ItemStack playerStackCopy = playerStack.copy();
                            int newStackCount = goodCount + shrinkable;
                            playerStack.shrink(shrinkable);
                            playerStackCopy.setCount(newStackCount);
                            this.sellContainer.setItem(goodIndex, playerStackCopy);
                            if (newStackCount >= wantedItem.getMaxStackSize()) {
                                ignoredSlots.add(goodIndex);
                            }
                            if (playerStack.isEmpty()) {
                                continue playerInvLoop;
                            }
                        } else {
                            ignoredSlots.add(goodIndex);
                        }
                    }
                }
            }
        }
    }

    public void setDeals(List<SellDeal> deals) {
    }

    public List<SellDeal> getDeals() {
        return this.interactingNpc.getSellDeals();
    }

    public static class GoodsGridSlot extends Slot {
        private final SellContainer sellContainer;

        public GoodsGridSlot(SellContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.sellContainer = container;
        }

        public boolean mayPlace(@NotNull ItemStack stack) {
            return !this.sellContainer.hasConcludedTrade;
        }

        @Override
        public boolean mayPickup(@NotNull Player player) {
            return !this.sellContainer.hasConcludedTrade;
        }
    }

    public static class SellResultSlot extends Slot {
        private final SellContainer sellContainer;
        private final Player player;
        private int removeCount;
        private final InteractingNpc npc;
        private final int index;
        private boolean requestQuickCraft;

        public SellResultSlot(Player player, InteractingNpc npc, SellContainer buyContainer, int slot, int posX, int posY, int index) {
            super(buyContainer, slot, posX, posY);
            this.player = player;
            this.npc = npc;
            this.sellContainer = buyContainer;
            this.index = index;
        }

        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }

        public @NotNull ItemStack remove(int pAmount) {
            if (this.hasItem()) {
                this.removeCount += Math.min(pAmount, this.getItem().getCount());
            }

            return super.remove(pAmount);
        }

        protected void onQuickCraft(@NotNull ItemStack stack, int pAmount) {
            this.requestQuickCraft = true;
            this.removeCount += pAmount;
            this.checkTakeAchievements(stack);
        }

        protected void checkTakeAchievements(ItemStack pStack) {
            pStack.onCraftedBy(this.player.level(), this.player, this.removeCount);
            this.removeCount = 0;
        }

        public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
            this.sellContainer.onValueSlotTake();
            /*
            if (!SellMenu.this.sellContainer.hasConcludedTrade) {
                SellMenu.this.sellContainer.hasConcludedTrade = true;
                this.sellContainer.clearContent();
                for (ItemStack stack : this.sellContainer.futureStacks) {
                    SellMenu.this.moveItemStackTo(stack, GOOD_SLOT_START, GOOD_SLOT_END + 1, false);
                }
            }
             */
        }
    }
}
