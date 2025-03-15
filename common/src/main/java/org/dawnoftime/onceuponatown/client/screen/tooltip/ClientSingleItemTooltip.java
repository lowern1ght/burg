package org.dawnoftime.onceuponatown.client.screen.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ClientSingleItemTooltip implements ClientTooltipComponent {
    private final ItemStack stack;

    public ClientSingleItemTooltip(SingleItemTooltip tooltip) {
        stack = tooltip.stack();
    }

    @Override
    public void renderImage(@NotNull Font font, int x, int y, @NotNull GuiGraphics graphics) {
        graphics.renderItem(stack, x - 3, y, 1);
        int offset = (stack.getCount() < 99) ? 0 : (stack.getCount() < 999 ? 6 : 12);
        graphics.renderItemDecorations(font, stack, x - 3 + offset, y);
    }

    @Override
    public int getHeight() {
        return 18;
    }

    @Override
    public int getWidth(Font font) {
        return 20;
    }
}
