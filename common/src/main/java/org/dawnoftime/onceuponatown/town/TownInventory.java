package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.Config;

import java.util.HashMap;

public class TownInventory {
    private final HashMap<Item, Integer> inventory = new HashMap<>();

    public TownInventory() {
    }

    /**
     * Constructor used to create a TownInventory instance from the information stored in the NBT.
     * @param inventoryTag ListTag that holds all the information.
     */
    public TownInventory(ListTag inventoryTag) {
        for(int i = 0; i < inventoryTag.size(); ++i) {
            CompoundTag entryTag = inventoryTag.getCompound(i);
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(entryTag.getString("item")));
            this.inventory.put(item, entryTag.getInt("amount"));
        }
    }

    /**
     * Function used to save the content of this Town's inventory.
     * @return A Tag with all the information.
     */
    public ListTag writeNBT() {
        ListTag inventoryTag = new ListTag();
        this.inventory.forEach((key, value) -> {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("item", BuiltInRegistries.ITEM.getKey(key).toString());
            entryTag.putInt("amount", value);
            inventoryTag.add(entryTag);
        });
        return inventoryTag;
    }


    public <T extends Item> boolean hasAny(T item) {
        return this.inventory.containsKey(item);
    }

    public <T extends Item> boolean hasEnough(T item, int amount) {
        return !((amount <= 0) || !hasAny(item) || (this.inventory.get(item) < amount));
    }

    public <T extends Item> int get(T item) {
        return hasAny(item) ? this.inventory.get(item) : 0;
    }

    public <T extends Item> boolean add(T item) {
        return add(item, 1);
    }

    public <T extends Item> boolean add(T item, int amount) {
        if (amount <= 0 || amount > Config.MAX_TOWN_INVENTORY_STACK_SIZE) {
            return false;
        } else {
            if (hasAny(item)) {
                this.inventory.put(item, this.inventory.get(item) + amount);
            } else {
                this.inventory.put(item, amount);
            }
            return true;
        }
    }

    public <T extends Item> boolean remove(T item, int amount) {
        if (amount <= 0 || amount > Config.MAX_TOWN_INVENTORY_STACK_SIZE) {
            return false;
        } else {
            if (hasAny(item)) {
                this.inventory.put(item, this.inventory.get(item) - amount);
                if (this.inventory.get(item) <= 0) {
                    removeAll(item);
                }
            }
            return true;
        }
    }

    public <T extends Item> void removeAll(T item) {
        this.inventory.remove(item);
    }
}
