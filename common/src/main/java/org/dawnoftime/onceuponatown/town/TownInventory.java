package org.dawnoftime.onceuponatown.town;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class TownInventory {
    private final List<ItemStack> content = new ArrayList<>();

    public TownInventory() {
        content.add(new ItemStack(Items.BREAD, 64));
    }

    public TownInventory(CompoundTag inventoryTag) {
        ListTag contentTag = inventoryTag.getList("content", Tag.TAG_COMPOUND);
        for (int i = 0; i < contentTag.size(); ++i) {
            ItemStack stack = ItemStack.of(contentTag.getCompound(i));
            add(stack);
        }
    }

    public CompoundTag save() {
        ListTag contentTag = new ListTag();
        for (ItemStack member : content) {
            contentTag.add(member.save(new CompoundTag()));
        }
        CompoundTag inventoryTag = new CompoundTag();
        inventoryTag.put("content", contentTag);
        return inventoryTag;
    }

    public void add(ItemStack toAdd) {
        if (toAdd.isEmpty()) {
            return;
        }
        for (ItemStack member : content) { // Merge
            if (ItemStack.isSameItemSameTags(member, toAdd)) {
                member.grow(toAdd.getCount());
                return;
            }
        }
        content.add(toAdd);
    }

    public void remove(ItemStack toRemove) {
        if (toRemove.isEmpty()) {
            return;
        }
        for (ItemStack member : content) {
            if (ItemStack.isSameItemSameTags(member, toRemove)) {
                member.shrink(toRemove.getCount());
                return;
            }
        }
        clean();
    }

    public boolean has(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (ItemStack member : content) {
            if (ItemStack.isSameItemSameTags(member, stack)) {
                return member.getCount() >= stack.getCount();
            }
        }
        return false;
    }

    private void clean() {
        List<ItemStack> list = new ArrayList<>(content);
        for (ItemStack stack : list) {
            if (stack.isEmpty()) {
                content.remove(stack);
            }
        }
    }
}
