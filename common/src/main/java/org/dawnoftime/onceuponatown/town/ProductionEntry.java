package org.dawnoftime.onceuponatown.town;

import net.minecraft.world.item.Item;

public record ProductionEntry(Item item, int amount, int everyTicks, int capacityStacks) {
    public int capacityItems() { return capacityStacks * 64; }
}
