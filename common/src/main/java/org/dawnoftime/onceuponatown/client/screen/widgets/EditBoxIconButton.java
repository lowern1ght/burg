package org.dawnoftime.onceuponatown.client.screen.widgets;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;

public class EditBoxIconButton extends IconButton{
    private final EditBox editBox;

    public EditBoxIconButton(EditBox editBox, int x, int y, int size, ResourceLocation texture, int uOffset, int vOffset, int textureWidth, int textureHeight, Button.OnPress onPress) {
        super(x, y, size, texture, uOffset, vOffset, textureWidth, textureHeight, onPress);
        this.editBox = editBox;
    }

    public String getContent() {
        return this.editBox.getValue();
    }
}
