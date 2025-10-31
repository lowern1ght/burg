package org.dawnoftime.onceuponatown.town;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TownInventory {
    private final List<ItemStack> content = new ArrayList<>();

    public TownInventory(List<ItemStack> initialContent) {
        content.addAll(initialContent);
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

    public boolean add(ItemStack toAdd) {
        if (toAdd.isEmpty()) {
            return false;
        }
        for (ItemStack member : content) {
            if (ItemStack.isSameItemSameTags(member, toAdd)) {
                member.grow(toAdd.getCount());
                return true;
            }
        }
        content.add(toAdd);
        return true;
    }

    public boolean remove(ItemStack toRemove) {
        if (toRemove.isEmpty()) {
            return false;
        }
        ItemStack shrinked = null;
        for (ItemStack member : content) {
            if (ItemStack.isSameItemSameTags(member, toRemove)) {
                member.shrink(toRemove.getCount());
                shrinked = member;
                break;
            }
        }
        if (shrinked != null) {
            if (shrinked.isEmpty()) {
                content.remove(shrinked);
            }
            return true;
        }
        return false;
    }

    public void clear() {
        content.clear();
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

    public List<ItemStack> getContent() {
        return content;
    }
}
