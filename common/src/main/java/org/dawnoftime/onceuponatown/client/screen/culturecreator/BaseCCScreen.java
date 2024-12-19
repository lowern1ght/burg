package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.jetbrains.annotations.NotNull;

public abstract class BaseCCScreen extends Screen {
    private static final ResourceLocation BACKGROUND_TEXTURE = Ouat.createOuatResource("textures/gui/culture_creator.png");
    private static final int TEXTURE_TOTAL_WIDTH = 281;
    private static final int TEXTURE_TOTAL_HEIGHT = 217;
    private static final int TEXTURE_TAB_HEIGHT = 166;
    private int backGroundLeftPos;
    private int backGroundTopPos;

    public BaseCCScreen(Component title) {
        super(title);
        System.out.println(this.font);
    }

    @Override
    protected void init() {
        backGroundLeftPos = (width - TEXTURE_TOTAL_WIDTH) / 2;
        backGroundTopPos = (height - TEXTURE_TOTAL_HEIGHT) / 2;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.blit(BACKGROUND_TEXTURE, backGroundLeftPos, backGroundTopPos, 0, 0, TEXTURE_TOTAL_WIDTH, TEXTURE_TAB_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
