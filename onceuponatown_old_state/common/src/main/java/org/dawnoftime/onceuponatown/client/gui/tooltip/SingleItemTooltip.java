package org.dawnoftime.onceuponatown.client.gui.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record SingleItemTooltip(ItemStack stack) implements TooltipComponent {
}
