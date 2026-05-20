package org.dawnoftime.onceuponatown.client.gui.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.dawnoftime.onceuponatown.client.gui.GuiUtils;
import org.dawnoftime.onceuponatown.client.gui.widgets.TownMapWidget;

import java.util.Objects;

public class TownScrollScreen extends Screen {
    private static final int MAP_SIZE = 132;
    private static final ResourceLocation MAP_TEXTURE =
        new ResourceLocation("onceuponatown", "textures/gui/npc_screen.png");
    private static final int ATLAS_W = 581;
    private static final int ATLAS_H = 531;

    private TownMapWidget townMap;
    private final CompoundTag mapData;
    private int mapX;
    private int mapY;

    public TownScrollScreen(CompoundTag mapData) {
        super(Component.literal(mapData.getString("Name")));
        this.mapData = mapData;
    }

    @Override
    protected void init() {
        super.init();
        mapX = (width - MAP_SIZE) / 2;
        mapY = (height - MAP_SIZE) / 2;
        townMap = addRenderableWidget(new TownMapWidget(mapX, mapY, MAP_SIZE, MAP_SIZE, mapData));
        Objects.requireNonNull(minecraft).getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.blit(MAP_TEXTURE, mapX, mapY, 0, 399, MAP_SIZE, MAP_SIZE, ATLAS_W, ATLAS_H);
        super.render(graphics, mouseX, mouseY, partialTick);
        GuiUtils.drawCenteredString(graphics, font,
            Component.translatable("onceuponatown.map_of").append(" ").append(title),
            mapX, mapY - 12, MAP_SIZE, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        super.onClose();
        Objects.requireNonNull(minecraft).getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
