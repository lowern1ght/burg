package org.dawnoftime.onceuponatown.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.widgets.ReleaseFocusButton;
import org.dawnoftime.onceuponatown.menu.BuildingsMenu;

public class BuildingsScreen extends NpcBaseScreen<BuildingsMenu> {
    private static final int MAP_BACKGROUND_X = 21;
    private static final int MAP_BACKGROUND_Y = 19;
    private static final int MAP_SIZE = 132;
    private TownMapWidget townMap;

    public BuildingsScreen(BuildingsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        townMap = addRenderableWidget(new TownMapWidget(leftPos + MAP_BACKGROUND_X, topPos + MAP_BACKGROUND_Y, MAP_SIZE, MAP_SIZE, menu.getTownMapData()));
        addRenderableWidget(new ReleaseFocusButton(leftPos + 21, topPos + 154, 66, 16, Ouat.translatable("center_map"), pressed -> {
            townMap.centerMap();
        }, null));
        addRenderableWidget(new ReleaseFocusButton(leftPos + 88, topPos + 154, 65, 16, Ouat.translatable("debug_view"), pressed -> {
            townMap.toggleDebugView();
        }, null));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        graphics.blit(BACKGROUND_TEXTURE, leftPos + MAP_BACKGROUND_X, topPos + MAP_BACKGROUND_Y, 0, 399, MAP_SIZE, MAP_SIZE, imageWidth, imageHeight);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (getFocused() != null) {
            getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected Tab getTab() {
        return Tab.BUILDINGS;
    }
}
