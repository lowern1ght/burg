package org.dawnoftime.onceuponatown.client.gui.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.client.gui.widgets.NbtPreviewWidget;

public class NbtPreviewScreen extends Screen {

    private static final String TEST_NBT = "onceuponatown:plains/jobs/merchant_shop_lvl6";
    private static final int ZONE_SIZE = 200;

    private NbtPreviewWidget preview;

    public NbtPreviewScreen() {
        super(Component.literal("NBT Preview Test"));
    }

    @Override
    protected void init() {
        int px = (this.width - ZONE_SIZE) / 2;
        int py = (this.height - ZONE_SIZE) / 2;
        preview = new NbtPreviewWidget(px, py, ZONE_SIZE, ZONE_SIZE);
        preview.setStructure(TEST_NBT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g);
        preview.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(this.font,
            Component.literal("Drag = rotate  |  Scroll = zoom  |  ESC = close"),
            this.width / 2, (this.height + ZONE_SIZE) / 2 + 6, 0xFF888888);
        g.drawCenteredString(this.font,
            Component.literal(TEST_NBT),
            this.width / 2, (this.height - ZONE_SIZE) / 2 - 12, 0xFF555555);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return preview.mouseScrolled(mx, my, delta) || super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return preview.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return preview.mouseDragged(mx, my, button, dx, dy) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return preview.mouseReleased(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
