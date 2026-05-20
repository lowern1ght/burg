package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.dawnoftime.onceuponatown.client.gui.tooltip.BuildingProductionTooltip;
import org.dawnoftime.onceuponatown.town.MapCategory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class TownMapWidget extends AbstractWidget {
    private record Rgb(int r, int g, int b) {}

    private static final Rgb BUILDING_RGB    = new Rgb(99, 83, 49);
    private static final Rgb JOB_RGB         = new Rgb(180, 50, 50);
    private static final Rgb GARDEN_RGB      = new Rgb(55, 130, 55);
    private static final Rgb ROAD_RGB        = new Rgb(147, 147, 147);
    private static final Rgb HOVER_RGB       = new Rgb(234, 200, 190);
    private static final Rgb TOWN_CENTER_RGB = new Rgb(220, 180, 40);

    private static final int MAP_MARGIN = 6;
    private static int xDrag;
    private static int yDrag;
    private static int mapZoom = 1;

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
        BlockPos nwCorner = NbtUtils.readBlockPos(mapData.getCompound("NWCorner"));
        BlockPos seCorner = NbtUtils.readBlockPos(mapData.getCompound("SECorner"));
        Iterator<Tag> it = mapData.getList("Elements", 10).iterator();
        while (it.hasNext()) {
            Tag next = it.next();
            if (!(next instanceof CompoundTag tag)) continue;
            switch (tag.getByte("Category")) {
                case MapCategory.ROAD     -> mapElements.add(createRoadMapElement(tag, nwCorner));
                case MapCategory.BUILDING -> mapElements.add(createBuildingMapElement(tag, nwCorner));
            }
        }
        mapInitialWidth  = seCorner.getX() - nwCorner.getX();
        mapInitialHeight = seCorner.getZ() - nwCorner.getZ();
        mapWindowLeftBound   = getX() + MAP_MARGIN;
        mapWindowTopBound    = getY() + MAP_MARGIN;
        mapWindowRightBound  = getX() + width - MAP_MARGIN;
        mapWindowBottomBound = getY() + height - MAP_MARGIN;
        mapZoom = 1;
    }

    private MapElement createRoadMapElement(CompoundTag tag, BlockPos nwCorner) {
        String buildType = tag.getString("BuildType");
        Component name = Component.translatable("onceuponatown.building." + buildType);

        BlockPos originPos = NbtUtils.readBlockPos(tag.getCompound("OriginPos"));
        int sizeX = tag.getInt("SizeX");
        int sizeZ = tag.getInt("SizeZ");
        int minX = originPos.getX() - nwCorner.getX();
        int minZ = originPos.getZ() - nwCorner.getZ();

        List<String> footprint = null;
        if (tag.contains("Footprint")) {
            footprint = new ArrayList<>();
            for (Tag t : tag.getList("Footprint", Tag.TAG_STRING)) {
                footprint.add(t.getAsString());
            }
        }

        return new MapElement(MapCategory.ROAD, "", List.of(name), Optional.empty(),
            footprint, minX, minX + sizeX, minZ, minZ + sizeZ);
    }

    private MapElement createBuildingMapElement(CompoundTag tag, BlockPos nwCorner) {
        String buildType = tag.getString("BuildType");
        Component name = Component.translatable("onceuponatown.building." + buildType);
        String buildingCategory = tag.getString("BuildingCategory");

        BlockPos originPos = NbtUtils.readBlockPos(tag.getCompound("OriginPos"));
        int sizeX = tag.getInt("SizeX");
        int sizeZ = tag.getInt("SizeZ");

        List<BuildingProductionTooltip.Row> rows = new ArrayList<>();
        ListTag prod = tag.getList("Production", Tag.TAG_COMPOUND);
        for (Tag t : prod) {
            CompoundTag pt = (CompoundTag) t;
            String itemId = pt.getString("Item");
            String shortName = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
            int amount = pt.getInt("Amount");
            int seconds = pt.getInt("EveryTicks") / 20;
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(itemId)));
            Component text = Component.literal("x" + amount + " " + shortName + " / " + seconds + "s")
                .withStyle(ChatFormatting.GRAY);
            rows.add(new BuildingProductionTooltip.Row(stack, text));
        }

        Optional<TooltipComponent> productionTooltip = rows.isEmpty()
            ? Optional.empty()
            : Optional.of(new BuildingProductionTooltip(rows));

        int minX = originPos.getX() - nwCorner.getX();
        int minZ = originPos.getZ() - nwCorner.getZ();
        return new MapElement(MapCategory.BUILDING, buildingCategory, List.of(name), productionTooltip,
            null, minX, minX + sizeX, minZ, minZ + sizeZ);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int alpha = 255;
        for (MapElement mapElement : mapElements) {
            int buildWidth  = mapElement.maxX - mapElement.minX;
            int buildHeight = mapElement.maxZ - mapElement.minZ;
            int originX = scaledMapX() + mapElement.minX * mapZoom;
            int originZ = scaledMapY() + mapElement.minZ * mapZoom;
            int buildMinX = originX;
            int buildMaxX = originX + (buildWidth * mapZoom);
            int buildMinZ = originZ;
            int buildMaxZ = originZ + (buildHeight * mapZoom);

            boolean outside = buildMinX > mapWindowRightBound || buildMaxX < mapWindowLeftBound
                || buildMinZ > mapWindowBottomBound || buildMaxZ < mapWindowTopBound;
            if (!outside) {
                int clampedMinX = Math.max(mapWindowLeftBound,   buildMinX);
                int clampedMaxX = Math.min(mapWindowRightBound,  buildMaxX);
                int clampedMinZ = Math.max(mapWindowTopBound,    buildMinZ);
                int clampedMaxZ = Math.min(mapWindowBottomBound, buildMaxZ);

                boolean mouseOver = mouseX >= clampedMinX && mouseX < clampedMaxX
                    && mouseY >= clampedMinZ && mouseY < clampedMaxZ;

                if (mapElement.category == MapCategory.ROAD) {
                    if (mapElement.footprint != null) {
                        renderFootprint(graphics, mapElement.footprint, originX, originZ, mouseOver);
                    } else {
                        graphics.fill(clampedMinX, clampedMinZ, clampedMaxX, clampedMaxZ,
                            color(mouseOver ? 235 : 255, mouseOver ? HOVER_RGB : ROAD_RGB));
                    }
                } else if (mapElement.category == MapCategory.BUILDING) {
                    Rgb rgb = mouseOver ? HOVER_RGB : buildingColor(mapElement.buildingCategory);
                    drawRectangleWithShadow(graphics, clampedMinX, clampedMaxX, clampedMinZ, clampedMaxZ,
                        mouseOver ? 235 : alpha, rgb);
                }

                if (mouseOver) {
                    graphics.renderTooltip(Minecraft.getInstance().font,
                        mapElement.description,
                        mapElement.productionTooltip, mouseX, mouseY);
                }
            }

            alpha -= 5;
            if (alpha < 235) alpha = 240;
        }
    }

    private Rgb buildingColor(String category) {
        return switch (category) {
            case "jobs"        -> JOB_RGB;
            case "gardens"     -> GARDEN_RGB;
            case "town_center" -> TOWN_CENTER_RGB;
            default            -> BUILDING_RGB;
        };
    }

    private void renderFootprint(GuiGraphics graphics, List<String> footprint,
                                  int baseX, int baseZ, boolean mouseOver) {
        int roadColor = color(mouseOver ? 235 : 255, mouseOver ? HOVER_RGB : ROAD_RGB);
        for (int fz = 0; fz < footprint.size(); fz++) {
            String row = footprint.get(fz);
            for (int fx = 0; fx < row.length(); fx++) {
                if (row.charAt(fx) != '1') continue;
                int cx1 = baseX + fx * mapZoom;
                int cx2 = cx1 + mapZoom;
                int cz1 = baseZ + fz * mapZoom;
                int cz2 = cz1 + mapZoom;
                boolean cellOutside = cx1 > mapWindowRightBound || cx2 < mapWindowLeftBound
                    || cz1 > mapWindowBottomBound || cz2 < mapWindowTopBound;
                if (cellOutside) continue;
                cx1 = Math.max(mapWindowLeftBound,   cx1);
                cx2 = Math.min(mapWindowRightBound,  cx2);
                cz1 = Math.max(mapWindowTopBound,    cz1);
                cz2 = Math.min(mapWindowBottomBound, cz2);
                graphics.fill(cx1, cz1, cx2, cz2, roadColor);
            }
        }
    }

    private void drawRectangleWithShadow(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ,
                                          int alpha, Rgb rgb) {
        graphics.fill(minX, minZ, maxX - 1, maxZ - 1, color(alpha, rgb));
        int shadowColor = color(Math.min(255, alpha + 20), rgb);
        graphics.fill(minX, maxZ - 1, maxX, maxZ, shadowColor);
        graphics.fill(maxX - 1, minZ, maxX, maxZ - 1, shadowColor);
    }

    private int color(int alpha, Rgb rgb) {
        return FastColor.ARGB32.color(alpha, rgb.r(), rgb.g(), rgb.b());
    }

    private int scaledMapX() {
        return mapWindowLeftBound
            + ((mapWindowRightBound - mapWindowLeftBound - (mapInitialWidth * mapZoom)) / 2)
            + xDrag;
    }

    private int scaledMapY() {
        return mapWindowTopBound
            + ((mapWindowBottomBound - mapWindowTopBound - (mapInitialHeight * mapZoom)) / 2)
            + yDrag;
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
        if (!isHovered()) setFocused(false);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingMap) {
            if (dragX > 0.4D && dragX < 1.0D)   dragX = 1.0D;
            else if (dragX < -0.4D && dragX > -1.0D) dragX = -1.0D;
            if (dragY > 0.4D && dragY < 1.0D)   dragY = 1.0D;
            else if (dragY < -0.4D && dragY > -1.0D) dragY = -1.0D;
            xDrag += (int) dragX;
            yDrag += (int) dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= getX() && mouseX <= getX() + width
            && mouseY >= getY() && mouseY <= getY() + height) {
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
    public void playDownSound(SoundManager handler) {}

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}

    private record MapElement(byte category, String buildingCategory,
                               List<Component> description, Optional<TooltipComponent> productionTooltip,
                               List<String> footprint,
                               int minX, int maxX, int minZ, int maxZ) {}
}
