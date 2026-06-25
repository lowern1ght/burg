package org.dawnoftime.onceuponatown.town;

import net.minecraft.world.item.Item;

public record ProductionEntry(Item item, int amount, int everyTicks, int capacityStacks, int unlockAtLevel) {
    // unlockAtLevel: -1 = always active; N = requires building upgrade level >= N.
    public int capacityItems() { return capacityStacks * 64; }
}
