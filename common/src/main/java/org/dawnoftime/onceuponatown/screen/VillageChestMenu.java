package org.dawnoftime.onceuponatown.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.town.TownInventory;

import java.util.LinkedHashSet;
import java.util.Set;

public class VillageChestMenu extends AbstractContainerMenu {

    static final int ROWS = 5;
    static final int COLS = 9;
    static final int CHEST_SIZE = ROWS * COLS;
    static final int DEPOSIT_SLOTS = 8;

    private final SimpleContainer chestContainer;
    // Separate container for the 8 deposit slots (indices 90-97 in the menu)
    private final SimpleContainer depositContainer;
    // Anchor block position used server-side to identify which town this menu belongs to.
    private final BlockPos anchorPos;

    // Called by MenuType factory on the client
    public VillageChestMenu(int syncId, Inventory playerInventory) {
        super(MenuRegistry.VILLAGE_CHEST, syncId);
        this.chestContainer = new SimpleContainer(CHEST_SIZE);
        this.depositContainer = new SimpleContainer(DEPOSIT_SLOTS);
        this.anchorPos = null;
        addSlots(playerInventory);
    }

    // Called server-side when the player opens the anchor block
    public VillageChestMenu(int syncId, Inventory playerInventory, Town town, BlockPos anchorPos) {
        super(MenuRegistry.VILLAGE_CHEST, syncId);
        this.chestContainer = buildContainer(town);
        this.depositContainer = new SimpleContainer(DEPOSIT_SLOTS);
        this.anchorPos = anchorPos;
        addSlots(playerInventory);
    }

    public BlockPos getAnchorPos() { return anchorPos; }

    public SimpleContainer getDepositContainer() {
        return depositContainer;
    }

    private void addSlots(Inventory playerInventory) {
        checkContainerSize(chestContainer, CHEST_SIZE);

        // Village chest slots (0-53): read-only display, no player interaction
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                addSlot(new Slot(chestContainer, row * COLS + col, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) { return false; }
                    @Override
                    public boolean mayPickup(Player player) { return false; }
                });
            }
        }

        // Player inventory (54-80)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                    8 + col * 18, 140 + row * 18));
            }
        }

        // Hotbar (81-89)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }

        // Deposit slots: 8 green slots aligned with texture row at Y=108
        for (int col = 0; col < DEPOSIT_SLOTS; col++) {
            addSlot(new Slot(depositContainer, col, 8 + col * 18, 108));
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

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Return any items left in deposit slots to the player (same behavior as crafting table grid).
        for (int i = 0; i < depositContainer.getContainerSize(); i++) {
            ItemStack stack = depositContainer.getItem(i);
            if (!stack.isEmpty()) {
                player.addItem(stack);
                depositContainer.setItem(i, ItemStack.EMPTY);
            }
        }
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

        int playerStart = CHEST_SIZE;             // 54
        int playerEnd   = CHEST_SIZE + 36;        // 90
        int depositStart = playerEnd;             // 90
        int depositEnd   = playerEnd + DEPOSIT_SLOTS; // 98

        if (slotIndex < CHEST_SIZE) {
            // Chest slots are read-only, cannot shift-click out
            return ItemStack.EMPTY;
        } else if (slotIndex < playerEnd) {
            // Player inv/hotbar -> deposit slots only (never into chest)
            if (!this.moveItemStackTo(stack, depositStart, depositEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Deposit slots -> player inventory
            if (!this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return result;
    }

}
