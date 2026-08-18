package org.lowern1ght.burg.town;

import net.minecraft.world.item.Item;

import java.util.List;

public record TransformationRecipe(
    List<ItemCost> inputs,
    Item outputItem,
    int outputAmount,
    int outputCapacityStacks,
    int unlockAtLevel
) {
    public int outputCapacityItems() { return outputCapacityStacks * 64; }
    // Returns false if this recipe is locked behind an upgrade level the building hasn't reached yet.
    public boolean isActive(int buildingLevel) { return unlockAtLevel < 0 || buildingLevel >= unlockAtLevel; }
}
