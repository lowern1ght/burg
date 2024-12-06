package org.dawnoftime.onceuponatown.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.apache.commons.lang3.ArrayUtils;
import org.dawnoftime.onceuponatown.Ouat;

import java.util.Arrays;

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
    private float[][] floatMap;

    public TownMapItemScreen(Component townName, float[][] map) {
        super(townName);
        //floatMap = map;
    }

    @Override
    protected void init() {
        leftPos = (width - TEXTURE_WIDTH) / 2;
        topPos = (height - TEXTURE_HEIGHT) / 2;
        mapMinX = leftPos + MAP_MARGIN;
        mapMinY = topPos + MAP_MARGIN;
        mapMaxX = leftPos + TEXTURE_WIDTH - MAP_MARGIN;
        mapMaxY = topPos + TEXTURE_HEIGHT - MAP_MARGIN;
        //map = getExampleMap();

        floatMap = getExampleFloatMap();
        Ouat.info("Map :\n" + Arrays.deepToString(floatMap));

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
        //drawCenteredSquareMap(graphics, map, mapMinX, mapMinY, mapMaxX, mapMaxY, mapZoom, xDrag, yDrag);
        drawCenteredFloatMap(graphics, floatMap, mapMinX, mapMinY, mapMaxX, mapMaxY, mapZoom, xDrag, yDrag);
        graphics.drawString(this.font, Component.literal("Map of ").append(title).append(" | zoom " + mapZoom), leftPos + 42, topPos + 11,4210752, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCenteredFloatMap(GuiGraphics graphics, float[][] map, int mapMinX, int mapMinY, int mapMaxX, int mapMaxY, int scale, int offsetX, int offsetY) {
        int mapWidth = getMapWidth(map) * scale;
        int mapX = mapMinX + ((mapMaxX - mapMinX - mapWidth) / 2) + offsetX;
        int mapHeight = map.length * scale;
        int mapY = mapMinY + ((mapMaxY - mapMinY - mapHeight) / 2) + offsetY;
        Ouat.info("X : " + mapX + " Width : " + mapWidth + " Y : " + mapY + " Height : " + mapHeight);

        for (int i = 0; i < map.length; ++i) {
            for (int j = 0; j < map[i].length; ++j) {
                int squareMinX = mapX + (j * scale);
                int squareMaxX = squareMinX + scale;

                int squareMinY = mapY + (i * scale);
                int squareMaxY = squareMinY + scale;

                float block = map[i][j];
                int blockType = (int)block;
                float fAlpha = 255.0F * (block - blockType) / Float.MAX_VALUE;
                int color = 0;
                if (blockType == 1) {
                    color = FastColor.ARGB32.color((int)fAlpha, 136, 109, 42);
                } else if (blockType == 2) {
                    color = FastColor.ARGB32.color((int)fAlpha, 100, 100, 100);
                }

                boolean noBuild = (blockType == 0);
                boolean squareInsideBoundaries = squareMinX > mapMinX && squareMaxX < mapMaxX && squareMinY > mapMinY && squareMaxY < mapMaxY;
                if (!noBuild && squareInsideBoundaries) {

                }
                graphics.fill(squareMinX, squareMinY, squareMaxX, squareMaxY, FastColor.ARGB32.color((int)255, 136, 109, 42));
            }
        }
    }

    private void drawCenteredSquareMap(GuiGraphics graphics, int[][] map, int mapMinX, int mapMinY, int mapMaxX, int mapMaxY, int scale, int offsetX, int offsetY) {
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
        if (floatMap.length == 0) {
            return 1;
        }
        int mapMaxSizeX = getMapWidth(floatMap);
        return Math.max(1, Math.max(((mapMaxX - mapMinX) / mapMaxSizeX), (mapMaxY - mapMinY) / floatMap.length));
        //return Math.max(1, (mapMaxX - mapMinX) / map.length);
    }

    private int getMapWidth(float[][] map) {
        if (map == null || map.length == 0) {
            return 0;
        }
        int width = map[0].length;
        for (int i = 1; i < map.length; ++i) {
            int l = map[i].length;
            if (l > width) {
                width = l;
            }
        }
        return width;
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

    private float[][] getExampleFloatMap() {
        return new float[][] {
                {0.0F, 0.0F, 2.0F, 1.6525F, 1.6525F, 1.6525F, 0.0F, 0.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 2.0F, 1.6525F, 1.6525F, 1.6525F, 0.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 2.0F, 1.6525F, 1.6525F, 1.6525F, 0.0F, 0.0F},
                {2.0F, 2.0F, 2.0F, 2.0F, 0.0F, 0.0F, 0.0F},
                {1.153F, 1.153F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
                {1.153F, 1.153F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
                {1.153F, 1.153F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 2.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 2.0F, 0.0F, 0.0F}
        };
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