package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;

public class DropdownButton extends LeftAlignTextButton {
    private String currentChoiceKey;

    public DropdownButton(int x, int y, int width, int height, Screen screen, Component message, LinkedHashMap<String, String> optionsMap) {
        super(x, y, width, height, message, btn -> {
            DropdownOverlayScreen overlay = new DropdownOverlayScreen(screen, (DropdownButton) btn, optionsMap);
            Minecraft.getInstance().setScreen(overlay);
        });
    }

    public void setCurrentChoiceKey(String key) {
        currentChoiceKey = key;
    }

    public String getCurrentChoiceKey() {
        return currentChoiceKey;
    }
}
