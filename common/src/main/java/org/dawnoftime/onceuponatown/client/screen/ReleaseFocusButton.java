package org.dawnoftime.onceuponatown.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class ReleaseFocusButton extends Button {
    protected ReleaseFocusButton(int x, int y, int width, int height, Component message, OnPress onPress, Button.CreateNarration createNarration) {
        super(x, y, width, height, message, onPress, createNarration);
    }

    protected ReleaseFocusButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        this.setFocused(false);
        super.onRelease(mouseX, mouseY);
    }

    public static class Builder {
        private final Component message;
        private final Button.OnPress onPress;
        @Nullable
        private Tooltip tooltip;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private Button.CreateNarration createNarration = Button.DEFAULT_NARRATION;

        public Builder(Component message, Button.OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public ReleaseFocusButton.Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public ReleaseFocusButton.Builder width(int width) {
            this.width = width;
            return this;
        }

        public ReleaseFocusButton.Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public ReleaseFocusButton.Builder bounds(int x, int y, int width, int height) {
            return this.pos(x, y).size(width, height);
        }

        public ReleaseFocusButton.Builder tooltip(@Nullable Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public ReleaseFocusButton.Builder createNarration(Button.CreateNarration createNarration) {
            this.createNarration = createNarration;
            return this;
        }

        public ReleaseFocusButton build() {
            ReleaseFocusButton button = new ReleaseFocusButton(this.x, this.y, this.width, this.height, this.message, this.onPress, this.createNarration);
            button.setTooltip(this.tooltip);
            return button;
        }
    }
}
