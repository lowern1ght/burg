package org.dawnoftime.onceuponatown.client.screen.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class IconButton extends Button {
    private final ResourceLocation texture;
    private final int uOffset;
    private final int vOffset;
    private final int textureWidth;
    private final int textureHeight;

    public IconButton(int x, int y, int sideLength, ResourceLocation texture, int uOffset, int vOffset, int textureWidth, int textureHeight, Button.OnPress onPress) {
        super(x, y, sideLength, sideLength, Component.empty(), onPress, DEFAULT_NARRATION);
        this.texture = texture;
        this.uOffset = uOffset;
        this.vOffset = vOffset;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (this.visible) {
            guiGraphics.blit(texture, this.getX(), this.getY(), uOffset, vOffset, this.getWidth(), this.getHeight(), textureWidth, textureHeight);
        }
    }
}