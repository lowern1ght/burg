package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.building.instance.Build;
import org.dawnoftime.onceuponatown.client.gui.tooltip.ItemAndTitleTooltip;
import oshi.util.tuples.Triplet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class TownMapWidget extends AbstractWidget {
    private static final Triplet<Integer, Integer, Integer> BUILDING_RGB = new Triplet<>(99, 83, 49);
    private static final Triplet<Integer, Integer, Integer> ROAD_RGB = new Triplet<>(147, 147, 147);
    private static final Triplet<Integer, Integer, Integer> BUD_RGB = new Triplet<>(201, 49, 43);
    private static final Triplet<Integer, Integer, Integer> HOVER_RGB = new Triplet<>(234, 200, 190);
    private static final int MAP_MARGIN = 6;
    private static int xDrag;
    private static int yDrag;
    private static int mapZoom = 1;
    private static boolean debugView;
    private int mapWindowLeftBound;
    private int mapWindowRightBound;
    private int mapWindowTopBound;
    private int mapWindowBottomBound;
    private boolean draggingMap;
    private final List<MapElement> mapElements = new ArrayList<>();
    private int mapInitialWidth;
    private int mapInitialHeight;

    public TownMapWidget(int x, int y, int width, int height, CompoundTag mapData) {
        super(x, y, width, height, Component.empty());
        setupMap(mapData);
    }

    private void setupMap(CompoundTag mapData) {
        mapElements.clear();
        var NWCorner = NbtUtils.readBlockPos(mapData.getCompound("NWCorner"));
        var SECorner = NbtUtils.readBlockPos(mapData.getCompound("SECorner"));
        Iterator<Tag> it = mapData.getList("Elements", 10).iterator();
        while (it.hasNext() && it.next() instanceof CompoundTag tag) {
            switch (tag.getByte("Category")) {
                case Build.BUD -> mapElements.add(createBudMapElement(tag, NWCorner));
                case Build.ROAD -> mapElements.add(createRoadMapElement(tag, NWCorner));
                case Build.BUILDING -> mapElements.add(createBuildingMapElement(tag, NWCorner));
            }
        }
        mapInitialWidth = SECorner.getX() - NWCorner.getX();
        mapInitialHeight = SECorner.getZ() - NWCorner.getZ();
        mapWindowLeftBound = getX() + MAP_MARGIN;
        mapWindowTopBound = getY() + MAP_MARGIN;
        mapWindowRightBound = getX() + width - MAP_MARGIN;
        mapWindowBottomBound = getY() + height - MAP_MARGIN;
        mapZoom = 1;
    }

    private MapElement createBudMapElement(CompoundTag tag, BlockPos NWCorner) {
        var realPos = NbtUtils.readBlockPos(tag.getCompound("Position"));
        var name = Ouat.translatable("bud");
        var position = Ouat.translatable("coordinates").append(" : ").append(realPos.toShortString()).withStyle(ChatFormatting.GRAY);
        int minX = realPos.getX() - NWCorner.getX();
        int minZ = realPos.getZ() - NWCorner.getZ();
        return new MapElement(Build.BUD, Optional.empty(), List.of(name, position), List.of(name, position), minX, minX + 1, minZ, minZ + 1);
    }

    private MapElement createRoadMapElement(CompoundTag tag, BlockPos NWCorner) {
        var nameAndLevel = Ouat.translatable(tag.getString("BuildType")).append(" ")
            .append(Component.literal(Utils.intToRoman(tag.getInt("Level"))).withStyle(ChatFormatting.YELLOW));
        var originPos = NbtUtils.readBlockPos(tag.getCompound("OriginPos"));
        int sizeX = tag.getInt("SizeX");
        int sizeZ = tag.getInt("SizeZ");
        var northWestCorner = Ouat.translatable("north_west_corner").append(" : " + originPos.toShortString()).withStyle(ChatFormatting.GRAY);
        var direction = Ouat.translatable("direction").append(" : " + tag.getString("Direction")).withStyle(ChatFormatting.GRAY);
        var length = Ouat.translatable("length").append(" : " + Math.max(sizeX, sizeZ)).withStyle(ChatFormatting.GRAY);
        var width = Ouat.translatable("width").append(" : " + Math.min(sizeX, sizeZ)).withStyle(ChatFormatting.GRAY);
        var isWide = Component.literal("isWide = " + tag.getBoolean("IsWide")).withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY);
        var canGrow = Component.literal("canGrow = " + tag.getBoolean("CanGrow")).withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY);
        int minX = originPos.getX() - NWCorner.getX();
        int minZ = originPos.getZ() - NWCorner.getZ();
        return new MapElement(Build.ROAD, Optional.empty(),
            List.of(nameAndLevel, CommonComponents.EMPTY, northWestCorner, direction, length, width),
            List.of(nameAndLevel, CommonComponents.EMPTY, northWestCorner, direction, length, width, isWide, canGrow), minX, minX + sizeX, minZ, minZ + sizeZ);
    }

    private MapElement createBuildingMapElement(CompoundTag tag, BlockPos NWCorner) {
        var nameAndLevel = Ouat.translatable("building", tag.getString("BuildType")).append(" ")
            .append(Component.literal(Utils.intToRoman(tag.getInt("Level"))).withStyle(ChatFormatting.YELLOW));
        var originPos = NbtUtils.readBlockPos(tag.getCompound("OriginPos"));
        var direction = Component.translatable("direction").append(" : " + tag.getString("Direction")).withStyle(ChatFormatting.GRAY);
        int sizeX = tag.getInt("SizeX");
        int sizeZ = tag.getInt("SizeZ");
        var coordinates = Ouat.translatable("coordinates").append(" : " + originPos.toShortString()).withStyle(ChatFormatting.GRAY);
        var plotSize = Ouat.translatable("plot_size").append(" : " + sizeX + "×" + sizeZ).withStyle(ChatFormatting.GRAY);
        int minX = originPos.getX() - NWCorner.getX();
        int minZ = originPos.getZ() - NWCorner.getZ();
        return new MapElement(Build.BUILDING,
            Optional.of(new ItemAndTitleTooltip(nameAndLevel, new ItemStack(Ouat.COMMON.getItem(new ResourceLocation(tag.getString("IconItem")))))),
            List.of(CommonComponents.EMPTY, coordinates, direction, plotSize),
            List.of(CommonComponents.EMPTY, coordinates, direction, plotSize), minX, minX + sizeX, minZ, minZ + sizeZ);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int alpha = 255;
        for (MapElement mapElement : mapElements) {
            int buildWidth = mapElement.maxX - mapElement.minX;
            int buildHeight = mapElement.maxZ - mapElement.minZ;
            int buildMinX = scaledMapX() + mapElement.minX * mapZoom;
            int buildMaxX = buildMinX + (buildWidth * mapZoom);
            int buildMinZ = scaledMapY() + mapElement.minZ * mapZoom;
            int buildMaxZ = buildMinZ + (buildHeight * mapZoom);
            boolean outsideMapWindow = buildMinX > mapWindowRightBound || buildMaxX < mapWindowLeftBound || buildMinZ > mapWindowBottomBound || buildMaxZ < mapWindowTopBound;
            if (!outsideMapWindow) {
                buildMinX = Math.max(mapWindowLeftBound, buildMinX);
                buildMaxX = Math.min(mapWindowRightBound, buildMaxX);
                buildMinZ = Math.max(mapWindowTopBound, buildMinZ);
                buildMaxZ = Math.min(mapWindowBottomBound, buildMaxZ);

                boolean mouseOver = mouseX >= buildMinX && mouseX < buildMaxX && mouseY >= buildMinZ && mouseY < buildMaxZ;
                if (mapElement.category == Build.BUD && debugView) {
                    graphics.fill(buildMinX, buildMinZ, buildMaxX, buildMaxZ, color(mouseOver ? 235 : 255, mouseOver ? HOVER_RGB : BUD_RGB));
                } else if (mapElement.category == Build.ROAD) {
                    graphics.fill(buildMinX, buildMinZ, buildMaxX, buildMaxZ, color(mouseOver ? 235 : 255, mouseOver ? HOVER_RGB : ROAD_RGB));
                } else if (mapElement.category == Build.BUILDING) {
                    drawRectangleWithShadow(graphics, buildMinX, buildMaxX, buildMinZ, buildMaxZ, mouseOver ? 235 : alpha, mouseOver ? HOVER_RGB : BUILDING_RGB);
                }
                if (mouseOver && !(mapElement.category == Build.BUD && !debugView)) {
                    graphics.renderTooltip(Minecraft.getInstance().font, debugView ? mapElement.debugDescription : mapElement.description, mapElement.titleWithIcon, mouseX, mouseY);
                }
            }
            alpha -= 5;
            if (alpha < 235) {
                alpha = 240;
            }
        }
    }

    private void drawRectangleWithShadow(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ, int alpha, Triplet<Integer, Integer, Integer> rgb) {
        graphics.fill(minX, minZ, maxX - 1, maxZ - 1, color(alpha, rgb)); // Lighted part
        int shadowColor = color(Math.min(255, alpha + 20), rgb);
        graphics.fill(minX, maxZ - 1, maxX, maxZ, shadowColor); // Bottom shadow
        graphics.fill(maxX - 1, minZ, maxX, maxZ - 1, shadowColor); // Right Shadow
    }

    private int color(int alpha, Triplet<Integer, Integer, Integer> rgb) {
        return FastColor.ARGB32.color(alpha, rgb.getA(), rgb.getB(), rgb.getC());
    }

    private int scaledMapX() {
        return mapWindowLeftBound + ((mapWindowRightBound - mapWindowLeftBound - (mapInitialWidth * mapZoom)) / 2) + xDrag;
    }

    private int scaledMapY() {
        return mapWindowTopBound + ((mapWindowBottomBound - mapWindowTopBound - (mapInitialHeight * mapZoom)) / 2) + yDrag;
    }

    public void centerMap() {
        mapZoom = 1;
        xDrag = 0;
        yDrag = 0;
    }

    public void toggleDebugView() {
        debugView = !debugView;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered()) {
            draggingMap = true;
            setFocused(true);
        } else {
            draggingMap = false;
            setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingMap = false;
        if (!isHovered()) {
            setFocused(false);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingMap) {
            if (dragX > 0.4D && dragX < 1.0D) {
                dragX = 1.0D;
            } else if (dragX < -0.4D && dragX > -1.0D) {
                dragX = -1.0D;
            }
            if (dragY > 0.4D && dragY < 1.0D) {
                dragY = 1.0D;
            } else if (dragY < -0.4D && dragY > -1.0D) {
                dragY = -1.0D;
            }
            xDrag += (int) dragX;
            yDrag += (int) dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= getX() && mouseX <= getX() + width && mouseY >= getY() && mouseY <= getY() + height) {
            int windowCenterX = (mapWindowRightBound - mapWindowLeftBound) / 2;
            int windowCenterY = (mapWindowBottomBound - mapWindowTopBound) / 2;
            if (delta > 0) {
                xDrag -= ((int) (mouseX - mapWindowLeftBound) - windowCenterX);
                yDrag -= ((int) (mouseY - mapWindowTopBound) - windowCenterY);
            } else if (mapZoom > 1) {
                xDrag += ((int) (mouseX - mapWindowLeftBound) - windowCenterX);
                yDrag += ((int) (mouseY - mapWindowTopBound) - windowCenterY);
            }
            mapZoom = Math.max(1, mapZoom + (int) delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void playDownSound(SoundManager handler) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    private record MapElement(byte category, Optional<TooltipComponent> titleWithIcon, List<Component> description,
                              List<Component> debugDescription, int minX, int maxX, int minZ, int maxZ) {
    }
}
