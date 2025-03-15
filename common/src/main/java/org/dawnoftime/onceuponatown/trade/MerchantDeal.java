package org.dawnoftime.onceuponatown.trade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;

public class MerchantDeal {
    private final ItemStack requiredA;
    private final ItemStack requiredB;
    private final ItemStack result;
    private final TradeType tradeType;
    private int merchantXpGain;
    private int discount;

    public MerchantDeal(CompoundTag tag) {
        requiredA = ItemStack.of(tag.getCompound("requiredA"));
        requiredB = ItemStack.of(tag.getCompound("requiredB"));
        result = ItemStack.of(tag.getCompound("result"));
        tradeType = TradeType.values()[tag.getInt("tradeType")];
    }

    private MerchantDeal(Builder builder) {
        requiredA = builder.requiredA;
        requiredB = builder.requiredB;
        result = builder.result;
        tradeType = builder.tradeType;
    }

    public boolean isSatisfiedBy(ItemStack inputA, ItemStack inputB) {
        return isValidInput(inputA, getRequiredA()) && isValidInput(inputB, getRequiredB());
    }

    private boolean isValidInput(ItemStack input, ItemStack required) {
        if (required.isEmpty() && input.isEmpty()) {
            return true;
        } else {
            ItemStack stack = input.copy();
            if (stack.getItem().canBeDepleted()) {
                stack.setDamageValue(stack.getDamageValue());
            }
            return ItemStack.isSameItem(stack, required)
                    && (!required.hasTag() || stack.hasTag() && NbtUtils.compareNbt(required.getTag(), stack.getTag(), false))
                    && stack.getCount() >= required.getCount();
        }
    }

    public boolean makeDeal(ItemStack inputA, ItemStack inputB) {
        if (!isSatisfiedBy(inputA, inputB)) {
            return false;
        } else {
            inputA.shrink(getRequiredA().getCount());
            if (!getRequiredB().isEmpty()) {
                inputB.shrink(getRequiredB().getCount());
            }
            return true;
        }
    }

    public ItemStack assemble() {
        return result.copy();
    }

    public CompoundTag createTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("requiredA", requiredA.save(new CompoundTag()));
        tag.put("requiredB", requiredB.save(new CompoundTag()));
        tag.put("result", result.save(new CompoundTag()));
        tag.putInt("tradeType", tradeType.ordinal());
        return tag;
    }

    public ItemStack getRequiredA() {
        return requiredA;
    }

    public ItemStack getRequiredB() {
        return requiredB;
    }

    public ItemStack getResult() {
        return result;
    }

    public TradeType getTradeType() {
        return tradeType;
    }

    public static class Builder {
        private ItemStack requiredA;
        private ItemStack requiredB;
        private ItemStack result;
        private TradeType tradeType;

        public static Builder buyDeal(ItemStack requiredA, ItemStack result) {
            return new Builder(requiredA, result, TradeType.BUY);
        }

        public static Builder sellDeal(ItemStack requiredA, ItemStack result) {
            return new Builder(requiredA, result, TradeType.SELL);
        }

        public Builder(TradeType tradeType, ItemStack requiredA, ItemStack result) {
            this(requiredA, result, tradeType);
        }

        private Builder(ItemStack requiredA, ItemStack result, TradeType tradeType) {
            this.requiredA = requiredA;
            requiredB = ItemStack.EMPTY;
            this.result = result;
            this.tradeType = tradeType;
        }

        public Builder requiredB(ItemStack requiredB) {
            this.requiredB = requiredB;
            return this;
        }

        public MerchantDeal build() {
            return new MerchantDeal(this);
        }
    }

    public enum TradeType {
        BUY, SELL
    }
}
