package org.dawnoftime.onceuponatown.town;

import net.minecraft.world.item.Item;

import java.util.List;

public record TransformationRecipe(
    List<ItemCost> inputs,
    Item outputItem,
    int outputAmount,
    int outputCapacityStacks
) {
    public int outputCapacityItems() { return outputCapacityStacks * 64; }
}
