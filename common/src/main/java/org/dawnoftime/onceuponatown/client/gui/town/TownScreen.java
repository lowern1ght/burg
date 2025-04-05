package org.dawnoftime.onceuponatown.client.gui.town;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.dawnoftime.onceuponatown.client.gui.widgets.ReleaseFocusButton;

public abstract class TownScreen extends Screen {
    private static final int BACKGD_WIDTH = 281;
    private static final int BACKGD_HEIGHT = 166;

    protected TownScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        int leftPos = (width - BACKGD_WIDTH) / 2;
        int topPos = (height - BACKGD_HEIGHT) / 2;
        String map = "Map";
        String inventory = "Inventory";
        addRenderableWidget(new ReleaseFocusButton(leftPos, topPos - 20, font.width(map) + 8, 16, Component.literal(map), button -> {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }, null
        ));
        addRenderableWidget(new ReleaseFocusButton(leftPos + font.width(map) + 12, topPos - 20, font.width(inventory) + 8, 16, Component.literal(inventory), button -> {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }, null
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
