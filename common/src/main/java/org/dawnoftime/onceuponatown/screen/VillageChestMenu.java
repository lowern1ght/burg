package org.dawnoftime.onceuponatown.screen;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;
import org.dawnoftime.onceuponatown.town.ItemCost;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.town.TownInventory;

import java.util.*;

public class VillageChestMenu extends AbstractContainerMenu {

    static final int ROWS = 6;
    static final int COLS = 9;
    static final int CHEST_SIZE = ROWS * COLS;

    private final SimpleContainer chestContainer;
    // Snapshot of what was placed in the container when the menu opened (server only)
    private final Map<Item, Integer> openSnapshot;
    // null on the client side
    private final TownInventory townInventory;

    // Called by MenuType factory on the client
    public VillageChestMenu(int syncId, Inventory playerInventory) {
        super(MenuRegistry.VILLAGE_CHEST, syncId);
        this.chestContainer = new SimpleContainer(CHEST_SIZE);
        this.townInventory = null;
        this.openSnapshot = Collections.emptyMap();
        addSlots(playerInventory);
    }

    // Called server-side when the player opens the anchor block
    public VillageChestMenu(int syncId, Inventory playerInventory, Town town) {
        super(MenuRegistry.VILLAGE_CHEST, syncId);
        this.chestContainer = buildContainer(town);
        this.townInventory = town.getTownInventory();
        this.openSnapshot = snapshotContainer(this.chestContainer);
        addSlots(playerInventory);
    }

    private void addSlots(Inventory playerInventory) {
        checkContainerSize(chestContainer, CHEST_SIZE);

        // Village chest slots - take-only
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                addSlot(new TakeOnlySlot(chestContainer, row * COLS + col,
                    8 + col * 18, 18 + row * 18));
            }
        }

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                    8 + col * 18, 140 + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    // Fills the container with village stock, multiple stacks per item if needed
    private static SimpleContainer buildContainer(Town town) {
        SimpleContainer container = new SimpleContainer(CHEST_SIZE);
        TownInventory inv = town.getTownInventory();

        Set<Item> producedItems = new LinkedHashSet<>();
        for (PlacedBuilding b : town.getBuildings()) {
            BuildingDataHandler.get(b.getDefId()).ifPresent(def -> {
                def.production.forEach(p -> producedItems.add(p.item()));
                def.transformations.forEach(t -> producedItems.add(t.outputItem()));
            });
        }

        int slot = 0;
        for (Item item : producedItems) {
            if (slot >= CHEST_SIZE) break;
            int stock = inv.getStock(item);
            if (stock <= 0) continue;
            while (stock > 0 && slot < CHEST_SIZE) {
                int count = Math.min(stock, item.getMaxStackSize());
                container.setItem(slot++, new ItemStack(item, count));
                stock -= count;
            }
        }
        return container;
    }

    // Records item counts present in the container at open time
    private static Map<Item, Integer> snapshotContainer(SimpleContainer c) {
        Map<Item, Integer> snap = new LinkedHashMap<>();
        for (int i = 0; i < CHEST_SIZE; i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty()) snap.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return snap;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Server-side only: drain TownInventory for what the player actually took
        if (townInventory == null) return;

        Map<Item, Integer> remaining = new HashMap<>();
        for (int i = 0; i < CHEST_SIZE; i++) {
            ItemStack s = chestContainer.getItem(i);
            if (!s.isEmpty()) remaining.merge(s.getItem(), s.getCount(), Integer::sum);
        }

        List<ItemCost> taken = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : openSnapshot.entrySet()) {
            int delta = entry.getValue() - remaining.getOrDefault(entry.getKey(), 0);
            if (delta > 0) taken.add(new ItemCost(entry.getKey(), delta));
        }
        if (!taken.isEmpty()) townInventory.removeStock(taken);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (slotIndex < CHEST_SIZE) {
            // Chest -> player inventory (shift-click)
            if (!this.moveItemStackTo(stack, CHEST_SIZE, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inventory -> chest: not allowed
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return result;
    }

    private static class TakeOnlySlot extends Slot {
        TakeOnlySlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
