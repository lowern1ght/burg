package org.dawnoftime.onceuponatown.client.gui.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record BuildingProductionTooltip(List<Row> rows) implements TooltipComponent {
    public record Row(ItemStack stack, Component text) {}
}
