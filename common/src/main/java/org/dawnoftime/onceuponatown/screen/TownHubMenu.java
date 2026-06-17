package org.dawnoftime.onceuponatown.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.town.TownInventory;

import java.util.LinkedHashSet;
import java.util.Set;

public class TownHubMenu extends AbstractContainerMenu {

    public static final int ROWS = 4;
    public static final int COLS = 9;
    public static final int CHEST_SIZE = ROWS * COLS;   // 36
    public static final int DEPOSIT_SLOTS = 8;

    private final SimpleContainer chestContainer;
    private final SimpleContainer depositContainer;
    private final BlockPos anchorPos;

    public TownHubMenu(int syncId, Inventory playerInventory) {
        super(MenuRegistry.TOWN_HUB, syncId);
        this.chestContainer = new SimpleContainer(CHEST_SIZE);
        this.depositContainer = new SimpleContainer(DEPOSIT_SLOTS);
        this.anchorPos = null;
        addSlots(playerInventory);
    }

    public TownHubMenu(int syncId, Inventory playerInventory, Town town, BlockPos anchorPos) {
        super(MenuRegistry.TOWN_HUB, syncId);
        this.chestContainer = buildContainer(town);
        this.depositContainer = new SimpleContainer(DEPOSIT_SLOTS);
        this.anchorPos = anchorPos;
        addSlots(playerInventory);
    }

    public BlockPos getAnchorPos() { return anchorPos; }

    public void rebuildFromStock(CompoundTag stockTag) {
        int slot = 0;
        for (String itemId : stockTag.getAllKeys()) {
            if (slot >= CHEST_SIZE) break;
            int count = stockTag.getInt(itemId);
            if (count <= 0) continue;
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) continue;
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == Items.AIR) continue;
            while (count > 0 && slot < CHEST_SIZE) {
                int stackCount = Math.min(count, item.getMaxStackSize());
                chestContainer.setItem(slot++, new ItemStack(item, stackCount));
                count -= stackCount;
            }
        }
        while (slot < CHEST_SIZE) {
            chestContainer.setItem(slot++, ItemStack.EMPTY);
        }
    }

    public SimpleContainer getDepositContainer() {
        return depositContainer;
    }

    private void addSlots(Inventory playerInventory) {
        checkContainerSize(chestContainer, CHEST_SIZE);

        // Village chest slots (0-35): 4 rows, read-only display
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                addSlot(new Slot(chestContainer, row * COLS + col, 8 + col * 18, 18 + row * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean mayPickup(Player player) { return false; }
                });
            }
        }

        // Blue exchange zone (36-43): 8 slots at Y=90
        for (int col = 0; col < DEPOSIT_SLOTS; col++) {
            addSlot(new Slot(depositContainer, col, 8 + col * 18, 90));
        }

        // Player inventory (44-70)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        // Hotbar (71-79)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

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
        // Return any items left in deposit slots to the player
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

        // Slot boundaries: chest 0-35, deposit 36-43, player 44-79
        int depositStart = CHEST_SIZE;                            // 36
        int depositEnd   = CHEST_SIZE + DEPOSIT_SLOTS;            // 44
        int playerStart  = depositEnd;                            // 44
        int playerEnd    = depositEnd + 36;                       // 80

        if (slotIndex < CHEST_SIZE) {
            return ItemStack.EMPTY; // chest is read-only
        } else if (slotIndex < depositEnd) {
            // Deposit -> player inventory
            if (!this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inv/hotbar -> deposit slots only (never into chest)
            if (!this.moveItemStackTo(stack, depositStart, depositEnd, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return result;
    }
}
