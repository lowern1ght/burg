package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.function.BiConsumer;

public class DropdownButton extends LeftAlignTextButton {
    private @Nullable String currentChoiceKey;

    public DropdownButton(int x, int y, int width, int height, Screen screen, Component message, LinkedHashMap<String, String> optionsMap, BiConsumer<String, String> storeChoiceFunction) {
        super(x, y, width, height, message, btn -> {
            DropdownOverlayScreen overlay = new DropdownOverlayScreen(screen, (DropdownButton) btn, optionsMap, storeChoiceFunction);
            Minecraft.getInstance().setScreen(overlay);
        });
    }

    public void setCurrentChoiceKey(@Nullable String key) {
        currentChoiceKey = key;
    }

    public @NotNull String getCurrentChoiceKey() {
        return currentChoiceKey == null ? "" : currentChoiceKey;
    }
}
