package org.dawnoftime.onceuponatown.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.tooltip.SingleItemTooltip;

import java.util.Optional;

public class EmeraldPouchItem extends Item {
    public static final int MAX_WEIGHT = 1024;

    public EmeraldPouchItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack pouchStack, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) {
            return false;
        } else {
            ItemStack slotStack = slot.getItem();
            if (slotStack.isEmpty()) {
                if (incrementCount(pouchStack, -1)) {
                    slot.safeInsert(new ItemStack(Items.EMERALD));
                    playRemoveOneSound(player);
                }
            } else if (slotStack.getItem() == Items.EMERALD) {
                int increment = Math.min(MAX_WEIGHT - getEmeraldCount(pouchStack), slotStack.getCount());
                if (increment > 0 && incrementCount(pouchStack, increment)) {
                    slot.safeTake(slotStack.getCount(), increment, player);
                    playInsertSound(player);
                }
            }
            return true;
        }
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack pouchStack, ItemStack otherStack, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        } else {
            if (otherStack.isEmpty()) {
                int amountOut = Math.min(getEmeraldCount(pouchStack), 64);
                if (incrementCount(pouchStack, -amountOut)) {
                    access.set(new ItemStack(Items.EMERALD, amountOut));
                    playRemoveOneSound(player);
                }
            } else if (otherStack.getItem() == Items.EMERALD) {
                int increment = Math.min(MAX_WEIGHT - getEmeraldCount(pouchStack), otherStack.getCount());
                if (increment > 0 && incrementCount(pouchStack, increment)) {
                    otherStack.shrink(increment);
                    playInsertSound(player);
                }
            }
            return true;
        }
    }

    private static boolean incrementCount(ItemStack pouchStack, int increment) {
        CompoundTag tag = pouchStack.getOrCreateTag();
        int newCount = tag.getInt("emerald_count") + increment;
        if (newCount >= 0 && newCount <= MAX_WEIGHT) {
            tag.putInt("emerald_count", newCount);
            return true;
        } else {
            return false;
        }
    }

    public static int getEmeraldCount(ItemStack pouchStack) {
        Ouat.debug(String.valueOf(pouchStack.getOrCreateTag().getInt("emerald_count")));
        return pouchStack.getOrCreateTag().getInt("emerald_count");
    }

    public static boolean isEmpty(ItemStack pouchStack) {
        return getEmeraldCount(pouchStack) == 0;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack pouchStack) {
        return (isEmpty(pouchStack)) ? Optional.empty() : Optional.of(new SingleItemTooltip(new ItemStack(Items.EMERALD, getEmeraldCount(pouchStack))));
    }

    private void playRemoveOneSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }
}
