package org.dawnoftime.onceuponatown.client.screen.tooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ClientItemAndTitleTooltip implements ClientTooltipComponent {
    private final Component title;
    private final ItemStack stack;

    public ClientItemAndTitleTooltip(ItemAndTitleTooltip tooltip) {
        title = tooltip.title();
        stack = tooltip.stack();
    }

    @Override
    public void renderImage(@NotNull Font font, int x, int y, @NotNull GuiGraphics graphics) {
        if (stack != ItemStack.EMPTY) {
            graphics.renderItem(stack, x, y - 11, 1);
            graphics.drawString(font, title, x + 20, y - 6, ChatFormatting.WHITE.getColor());
        }
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
