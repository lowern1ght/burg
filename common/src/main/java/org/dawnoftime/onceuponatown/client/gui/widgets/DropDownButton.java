package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.network.chat.Component;

import java.util.HashMap;

public class DropDownButton extends LeftAlignTextButton {

    private final HashMap<String, String> optionsMap;

    public DropDownButton(int x, int y, int width, int height, Component message, HashMap<String, String> optionsMap) {
        super(x, y, width, height, message, btn -> {});
        this.optionsMap = optionsMap;
    }
}
