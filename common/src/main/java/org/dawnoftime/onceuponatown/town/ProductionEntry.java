package org.dawnoftime.onceuponatown.town;

import net.minecraft.world.item.Item;

public record ProductionEntry(Item item, int amount, int everyTicks, int capacityStacks, int unlockAtLevel) {
    // unlockAtLevel: -1 = always active; N = requires building upgrade level >= N.
    public ProductionEntry(Item item, int amount, int everyTicks, int capacityStacks) {
        this(item, amount, everyTicks, capacityStacks, -1);
    }
    public int capacityItems() { return capacityStacks * 64; }
}
