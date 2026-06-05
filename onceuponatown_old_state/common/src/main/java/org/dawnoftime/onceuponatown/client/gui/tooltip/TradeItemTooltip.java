package org.dawnoftime.onceuponatown.client.gui.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record TradeItemTooltip(ItemStack a, ItemStack b, ItemStack c) implements TooltipComponent {

}
