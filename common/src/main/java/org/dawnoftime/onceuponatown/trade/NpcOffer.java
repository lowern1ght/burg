package org.dawnoftime.onceuponatown.trade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;

public class NpcOffer {
    private final ItemStack inputA;
    private final ItemStack inputB;
    private final ItemStack result;
    private final TradeType tradeType;

    private NpcOffer(Builder builder) {
        inputA = builder.inputA;
        inputB = builder.inputB;
        result = builder.result;
        tradeType = builder.tradeType;
    }

    public NpcOffer(CompoundTag tag) {
        inputA = ItemStack.of(tag.getCompound("inputA"));
        inputB = ItemStack.of(tag.getCompound("inputB"));
        result = ItemStack.of(tag.getCompound("result"));
        tradeType = TradeType.values()[tag.getInt("tradeType")];
    }

    public CompoundTag createTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("inputA", inputA.save(new CompoundTag()));
        tag.put("inputB", inputB.save(new CompoundTag()));
        tag.put("result", result.save(new CompoundTag()));
        tag.putInt("tradeType", tradeType.ordinal());
        return tag;
    }

    public boolean isValidInput(ItemStack stackA, ItemStack stackB) {
        return validInput(stackA, inputA) && validInput(stackB, inputB);
    }

    private boolean validInput(ItemStack stack, ItemStack required) {
        if (required.isEmpty() && stack.isEmpty()) {
            return true;
        } else {
            ItemStack copy = stack.copy();
            if (copy.getItem().canBeDepleted()) {
                copy.setDamageValue(copy.getDamageValue());
            }
            return ItemStack.isSameItem(copy, required)
                && (!required.hasTag() || copy.hasTag() && NbtUtils.compareNbt(required.getTag(), copy.getTag(), false))
                && copy.getCount() >= required.getCount();
        }
    }

    public boolean makeDeal(ItemStack stackA, ItemStack stackB) {
        if (!isValidInput(stackA, stackB)) {
            return false;
        } else {
            stackA.shrink(inputA.getCount());
            if (!inputB.isEmpty()) {
                stackB.shrink(inputB.getCount());
            }
            return true;
        }
    }

    public ItemStack assemble() {
        return result.copy();
    }

    public ItemStack getInputA() {
        return inputA.copy();
    }

    public ItemStack getInputB() {
        return inputB.copy();
    }

    public ItemStack getResult() {
        return result.copy();
    }

    public TradeType getTradeType() {
        return tradeType;
    }

    public static class Builder {
        private final TradeType tradeType;
        private final ItemStack inputA;
        private ItemStack inputB = ItemStack.EMPTY;
        private final ItemStack result;

        public static Builder buyOffer(ItemStack inputA, ItemStack result) {
            return new Builder(TradeType.BUY, inputA, result);
        }

        public static Builder sellOffer(ItemStack inputA, ItemStack result) {
            return new Builder(TradeType.SELL, inputA, result);
        }

        public Builder(TradeType tradeType, ItemStack inputA, ItemStack result) {
            this.inputA = inputA;
            this.result = result;
            this.tradeType = tradeType;
        }

        public Builder inputB(ItemStack inputB) {
            this.inputB = inputB;
            return this;
        }

        public NpcOffer build() {
            return new NpcOffer(this);
        }
    }

    public enum TradeType {
        BUY, SELL
    }
}
