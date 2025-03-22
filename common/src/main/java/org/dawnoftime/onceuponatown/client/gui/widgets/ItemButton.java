package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.ResourceLocationException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ItemButton extends Button {
    private final ResourceLocation texture;
    private final int uOffset;
    private final int vOffset;
    private final int textureWidth;
    private final int textureHeight;
    private ItemStack itemStack;

    public ItemButton(int x, int y, int size, ResourceLocation texture, int uOffset, int vOffset, int textureWidth, int textureHeight, @Nullable Item item, @Nullable Button.OnPress onPress) {
        super(x, y, size, size, Component.empty(), onPress == null ? btn -> {} : onPress, DEFAULT_NARRATION);
        this.texture = texture;
        this.uOffset = uOffset;
        this.vOffset = vOffset;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.itemStack = item == null ? ItemStack.EMPTY : new ItemStack(item);
        if (onPress == null) {
            this.active = false;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (this.visible) {
            if (this.itemStack.isEmpty()) {
                guiGraphics.blit(texture, this.getX(), this.getY(), uOffset, vOffset, this.getWidth(), this.getHeight(), textureWidth, textureHeight);
            } else {
                int xOffset = this.getWidth() / 2 - 8;
                int yOffset = this.getHeight() / 2 - 8;
                guiGraphics.renderItem(this.itemStack, this.getX() + xOffset, this.getY() + yOffset);
            }
        }
    }

    public void updateDisplayedItem(@Nullable String itemId) {
        if (itemId == null) {
            this.itemStack = ItemStack.EMPTY;
        } else {
            try {
                ResourceLocation resourceLocation = new ResourceLocation(itemId);
                Item item = BuiltInRegistries.ITEM.get(resourceLocation);
                this.itemStack = new ItemStack(item);
            } catch (ResourceLocationException ignored) {
                this.itemStack = ItemStack.EMPTY;
            }
        }
    }
}