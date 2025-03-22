package org.dawnoftime.onceuponatown.trade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;

public class NpcOffer {
    private final ItemStack inputA;
    private final ItemStack inputB;
    private final ItemStack result;
    private final TradeType tradeType;
    private int npcXpGain;
    private int discount;

    public NpcOffer(CompoundTag tag) {
        inputA = ItemStack.of(tag.getCompound("inputA"));
        inputB = ItemStack.of(tag.getCompound("inputB"));
        result = ItemStack.of(tag.getCompound("result"));
        tradeType = TradeType.values()[tag.getInt("tradeType")];
    }

    private NpcOffer(Builder builder) {
        inputA = builder.inputA;
        inputB = builder.inputB;
        result = builder.result;
        tradeType = builder.tradeType;
    }

    public boolean isSatisfiedBy(ItemStack stackA, ItemStack stackB) {
        return isValidInput(stackA, inputA) && isValidInput(stackB, inputB);
    }

    private boolean isValidInput(ItemStack stack, ItemStack input) {
        if (input.isEmpty() && stack.isEmpty()) {
            return true;
        } else {
            ItemStack copy = stack.copy();
            if (copy.getItem().canBeDepleted()) {
                copy.setDamageValue(copy.getDamageValue());
            }
            return ItemStack.isSameItem(copy, input)
                && (!input.hasTag() || copy.hasTag() && NbtUtils.compareNbt(input.getTag(), copy.getTag(), false))
                && copy.getCount() >= input.getCount();
        }
    }

    public boolean makeDeal(ItemStack inputA, ItemStack inputB) {
        if (!isSatisfiedBy(inputA, inputB)) {
            return false;
        } else {
            inputA.shrink(getInputA().getCount());
            if (!getInputB().isEmpty()) {
                inputB.shrink(getInputB().getCount());
            }
            return true;
        }
    }

    public ItemStack assemble() {
        return result.copy();
    }

    public CompoundTag createTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("requiredA", inputA.save(new CompoundTag()));
        tag.put("requiredB", inputB.save(new CompoundTag()));
        tag.put("result", result.save(new CompoundTag()));
        tag.putInt("tradeType", tradeType.ordinal());
        return tag;
    }

    public ItemStack getInputA() {
        return inputA;
    }

    public ItemStack getInputB() {
        return inputB;
    }

    public ItemStack getResult() {
        return result;
    }

    public TradeType getTradeType() {
        return tradeType;
    }

    public static class Builder {
        private ItemStack inputA;
        private ItemStack inputB;
        private ItemStack result;
        private TradeType tradeType;

        public static Builder buyDeal(ItemStack requiredA, ItemStack result) {
            return new Builder(requiredA, result, TradeType.BUY);
        }

        public static Builder sellDeal(ItemStack requiredA, ItemStack result) {
            return new Builder(requiredA, result, TradeType.SELL);
        }

        public Builder(TradeType tradeType, ItemStack inputA, ItemStack result) {
            this(inputA, result, tradeType);
        }

        private Builder(ItemStack inputA, ItemStack result, TradeType tradeType) {
            this.inputA = inputA;
            inputB = ItemStack.EMPTY;
            this.result = result;
            this.tradeType = tradeType;
        }

        public Builder requiredB(ItemStack requiredB) {
            this.inputB = requiredB;
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
