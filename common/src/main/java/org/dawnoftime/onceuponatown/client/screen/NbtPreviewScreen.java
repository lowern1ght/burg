package org.dawnoftime.onceuponatown.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.client.gui.widgets.NbtIsoRenderer;

public class NbtPreviewScreen extends Screen {

    private final NbtIsoRenderer renderer;
    private final String title;

    public NbtPreviewScreen(CompoundTag data) {
        super(Component.empty());
        this.title = data.getString("title");
        this.renderer = new NbtIsoRenderer(data);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g);
        renderer.render(g, 0, 0, width, height);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        g.drawString(font, "blocks: " + renderer.getBlockCount(), 4, 24, 0xAAAAAA, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
