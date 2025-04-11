package org.dawnoftime.onceuponatown.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.widgets.ReleaseFocusButton;

import static org.dawnoftime.onceuponatown.client.gui.GuiUtils.drawCenteredString;

public class TownScrollScreen extends Screen {
    private static final int MAP_SIZE = 132;
    private TownMapWidget townMap;
    private final CompoundTag mapData;
    private int mapX;
    private int mapY;

    public TownScrollScreen(CompoundTag mapData) {
        super(Component.nullToEmpty(mapData.getString("TownName")));
        this.mapData = mapData;
    }

    @Override
    protected void init() {
        super.init();
        mapX = (width - MAP_SIZE) / 2;
        mapY = (height - MAP_SIZE) / 2;
        townMap = addRenderableWidget(new TownMapWidget(mapX, mapY, MAP_SIZE, MAP_SIZE, mapData));
        addRenderableWidget(new ReleaseFocusButton(mapX, mapY + MAP_SIZE + 4, 66, 16, Ouat.translatable("center_map"),
            pressed -> townMap.centerMap(), null));
        addRenderableWidget(new ReleaseFocusButton(mapX + 67, mapY + MAP_SIZE + 4, 65, 16, Ouat.translatable("debug_view"),
            pressed -> townMap.toggleDebugView(), null));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.blit(NpcBaseScreen.BACKGROUND_TEXTURE, mapX, mapY, 0, 399, MAP_SIZE, MAP_SIZE, NpcBaseScreen.BACKGROUND_ATLAS_WIDTH, NpcBaseScreen.BACKGROUND_ATLAS_HEIGHT);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawCenteredString(graphics, font, Ouat.translatable("map_of").append(" ").append(title), mapX, mapY - 12, MAP_SIZE, 16777215);
    }

    @Override
    public void onClose() {
        super.onClose();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
