package org.dawnoftime.onceuponatown.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;

public class TownMapItemScreen extends Screen {
    private static final ResourceLocation TEXTURE = Ouat.createOuatResource("textures/gui/town_map_item_screen.png");
    private final int imageWidth = 64;
    private final int imageHeight = 64;
    private int leftPos;
    private int topPos;
    private final int titleLabelX = 100;
    private final int titleLabelY = 10;

    public TownMapItemScreen(int[] map) {
        super(Component.literal("Town map"));
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        //renderBackground(guiGraphics);
        guiGraphics.blit(TEXTURE,leftPos, topPos, 0, 0, imageWidth, imageHeight);


        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
