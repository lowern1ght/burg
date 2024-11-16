package org.dawnoftime.onceuponatown.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.dawnoftime.onceuponatown.Ouat;

public class TownMapItemScreen extends Screen {
    private static final ResourceLocation TEXTURE = Ouat.createOuatResource("textures/gui/town_map_item_screen.png");
    private static final int TEXTURE_WIDTH = 128;
    private static final int TEXTURE_HEIGHT = 128;
    private static final int MAP_MARGIN = 6;
    private int leftPos;
    private int topPos;
    private int mapMinX;
    private int mapMinY;
    private int mapMaxX;
    private int mapMaxY;
    private int mapZoom = 1;
    private boolean draggingMap;
    private int xDrag;
    private int yDrag;
    private int[][] map;

    public TownMapItemScreen(int[] map) {
        super(Component.literal("Town map"));
    }

    @Override
    protected void init() {
        leftPos = (width - TEXTURE_WIDTH) / 2;
        topPos = (height - TEXTURE_HEIGHT) / 2;
        mapMinX = leftPos + MAP_MARGIN;
        mapMinY = topPos + MAP_MARGIN;
        mapMaxX = leftPos + TEXTURE_WIDTH - MAP_MARGIN;
        mapMaxY = topPos + TEXTURE_HEIGHT - MAP_MARGIN;
        map = getExampleMap();
        mapZoom = getMapBestScale();
        // Reset view button
        int buttonWidth = 12;
        int buttonHeight = 12;
        addRenderableWidget(new ReleaseFocusButton.Builder(Component.literal("\u229E"), (b) -> {
            mapZoom = getMapBestScale();
            xDrag = 0;
            yDrag = 0;})
                .bounds(leftPos + (TEXTURE_WIDTH - buttonWidth) / 2, topPos + TEXTURE_HEIGHT + 7, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.literal("Center map")))
                .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.blit(TEXTURE,leftPos, topPos, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        drawCenteredMap(graphics, map, mapMinX, mapMinY, mapMaxX, mapMaxY, mapZoom, xDrag, yDrag);
        graphics.drawString(this.font, Component.literal("Town map"), leftPos + 42, topPos + 11,4210752, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCenteredMap(GuiGraphics graphics, int[][] map, int mapMinX, int mapMinY, int mapMaxX, int mapMaxY, int scale, int offsetX, int offsetY) {
        int mapWidth = map.length * scale;
        int mapHeight = map.length * scale;
        int mapX = mapMinX + ((mapMaxX - mapMinX - mapWidth) / 2) + offsetX;
        int mapY = mapMinY + ((mapMaxY - mapMinY - mapHeight) / 2) + offsetY;

        for (int i = 0; i < map.length; ++i) {
            for (int j = 0; j < map[i].length; ++j) {
                int squareMinX = mapX + (j * scale);
                int squareMinY = mapY + (i * scale);
                int squareMaxX = squareMinX + scale;
                int squareMaxY = squareMinY + scale;
                int color = map[i][j];
                boolean noBuild = (color == 0);
                boolean squareInsideBoundaries = squareMinX > mapMinX && squareMaxX < mapMaxX && squareMinY > mapMinY && squareMaxY < mapMaxY;
                if (!noBuild && squareInsideBoundaries) {
                    graphics.fill(squareMinX, squareMinY, squareMaxX, squareMaxY, color);
                }
            }
        }
    }

    private int getMapBestScale() {
        return Math.max(1, (mapMaxX - mapMinX) / map.length);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        draggingMap = mouseInGui(mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingMap = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingMap) {
            if(dragX > 0.4D && dragX < 1.0D) {
                dragX = 1.0D;
            } else if (dragX < -0.4D && dragX > -1.0D) {
                dragX = -1.0D;
            }
            if(dragY > 0.4D && dragY < 1.0D) {
                dragY = 1.0D;
            } else if (dragY < -0.4D && dragY > -1.0D) {
                dragY = -1.0D;
            }
            this.xDrag += (int)dragX;
            this.yDrag += (int)dragY;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseInGui(mouseX, mouseY)) {
            mapZoom = Math.max(1, mapZoom + (int) delta);
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean mouseInGui(double mouseX, double mouseY) {
        return mouseX > leftPos && mouseX < leftPos + TEXTURE_WIDTH && mouseY > topPos && mouseY < topPos + TEXTURE_HEIGHT;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int[][] getExampleMap() {
        int _0 = 0;
        int _p = FastColor.ARGB32.color(150, 136, 136, 136);
        int _1 = FastColor.ARGB32.color(150, 207, 98, 0);
        int _2 = FastColor.ARGB32.color(100, 207, 98, 0);
        int _3 = FastColor.ARGB32.color(200, 207, 98, 0);
        int _4 = FastColor.ARGB32.color(250, 207, 98, 0);
        return new int[][]{
                {_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _1, _1, _1, _1, _1, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _1, _1, _1, _1, _1, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _1, _1, _1, _1, _1, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _p, _p, _p, _p, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _2, _2, _2, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _2, _2, _2, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _2, _2, _2, _p, _0, _0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _2, _2, _2, _p, _0, _0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p ,_p, _p, _p, _p, _p, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _p, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p},
                {_0, _0, _p, _p, _p, _p, _0, _0, _0, _0, _0, _0, _p, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _4, _4, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _p, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _4, _4, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _p, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _4, _4, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _p, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _4, _4, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _1, _1, _1, _1, _1, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _1, _1, _1, _1, _1, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _1, _1, _1, _1, _1, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _p, _p, _p, _p, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _2, _2, _2, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _2, _2, _2, _p, _0, _0, _3, _3, _3, _3, _p, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _2, _2, _2, _p, _0, _0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _2, _2, _2, _p, _0, _0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _p, _p, _p, _p, _p, _p, _p, _p, _p, _p, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _p, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _p, _p, _p, _p, _0, _0, _0, _0, _0, _0, _p, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _p, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _p, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _p, _4, _4, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0},
                {_0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0 ,_0, _0, _0, _0, _0, _p, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0, _0}
        };
    }
}