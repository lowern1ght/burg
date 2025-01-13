package org.dawnoftime.onceuponatown.client.screen.widgets;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;

public class EditBoxIconButton extends IconButton{
    private EditBox editBox;
    private final boolean naturalLanguage;

    public EditBoxIconButton(int x, int y, int size, ResourceLocation texture, int uOffset, int vOffset, int textureWidth, int textureHeight, boolean naturalLanguage, Button.OnPress onPress) {
        super(x, y, size, texture, uOffset, vOffset, textureWidth, textureHeight, onPress);
        this.naturalLanguage = naturalLanguage;
        this.active = false;
    }

    public void setEditBox(EditBox editBox) {
        this.editBox = editBox;
    }

    public String getContent() {
        if (editBox == null) {
            return "";
        }
        return naturalLanguage ? editBox.getValue().trim() : editBox.getValue().trim().replace(" ", "_").toLowerCase();
    }
}
