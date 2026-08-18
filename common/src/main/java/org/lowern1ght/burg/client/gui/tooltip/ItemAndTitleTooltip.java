package org.lowern1ght.burg.client.gui.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record ItemAndTitleTooltip(Component title, ItemStack stack) implements TooltipComponent {
}
