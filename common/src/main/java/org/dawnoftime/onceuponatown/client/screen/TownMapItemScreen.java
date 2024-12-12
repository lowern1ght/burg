package org.dawnoftime.onceuponatown.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FastColor;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.building.Build;
import org.dawnoftime.onceuponatown.client.screen.tooltip.ItemAndTitleTooltip;
import org.dawnoftime.onceuponatown.client.screen.tooltip.SingleItemTooltip;
import org.jetbrains.annotations.NotNull;
import oshi.util.tuples.Triplet;

import java.util.*;
import java.util.List;

public class TownMapItemScreen extends Screen {
    private static final ResourceLocation TEXTURE = Ouat.createOuatResource("textures/gui/town_map_item_screen.png");
    private static final int TEXTURE_WIDTH = 192;
    private static final int TEXTURE_HEIGHT = 164;
    private static final int BACKGROUND_WIDTH = 192;
    private static final int BACKGROUND_HEIGHT = 128;
    private static final int BUTTON_WIDTH = 25;
    private static final int BUTTON_HEIGHT = 15;
    private static final int MAP_MARGIN = 6;
    private static final Triplet<Integer, Integer, Integer> BUILDING_COLOR = new Triplet<>(190, 160, 115);
    private static final Triplet<Integer, Integer, Integer> ROAD_COLOR = new Triplet<>(186, 186, 186);
    private static final Triplet<Integer, Integer, Integer> BUD_COLOR = new Triplet<>(100, 100, 100);
    private static int xDrag;
    private static int yDrag;
    private static int mapZoom = 1;
    private static boolean debugView;
    private int backGroundLeftPos;
    private int backGroundTopPos;
    private int mapWindowLeftBound;
    private int mapWindowRightBound;
    private int mapWindowTopBound;
    private int mapWindowBottomBound;
    private boolean draggingMap;
    private final List<DrawnBuild> drawnBuilds = new ArrayList<>();
    private int mapInitialWidth;
    private int mapInitialHeight;
    private int soundTick;

    public TownMapItemScreen(CompoundTag mapData) {
        super(Component.nullToEmpty(mapData.getString("TownName")));
        setupMap(mapData);
    }

    private void setupMap(CompoundTag mapData) {
        drawnBuilds.clear();
        BlockPos NWCorner = NbtUtils.readBlockPos(mapData.getCompound("NWCorner"));
        BlockPos SECorner = NbtUtils.readBlockPos(mapData.getCompound("SECorner"));
        ListTag builds = mapData.getList("Builds", 10);
        Iterator<Tag> it = builds.iterator();
        int alpha = 235;
        while (it.hasNext() && it.next() instanceof CompoundTag tag) {
            List<Component> description = new ArrayList<>();
            List<Component> debugDescription = new ArrayList<>();
            int color = 0;
            boolean drawShadow = false;
            Build.BuildCategory buildCategory = Build.BuildCategory.valueOf(tag.getString("BuildCategory"));
            BlockPos originPos = NbtUtils.readBlockPos(tag.getCompound("OriginPos"));
            int sizeX = tag.getInt("SizeX");
            int sizeZ = tag.getInt("SizeZ");
            int level = tag.getInt("Level");
            Item iconItem = BuiltInRegistries.ITEM.get(new ResourceLocation(tag.getString("IconItem")));
            Component nameAndLevel = Component.translatable(tag.getString("BuildType")).append(" ")
                    .append(Component.literal(Utils.intToRoman(level)).withStyle(ChatFormatting.YELLOW));
            ItemAndTitleTooltip titleAndItem = new ItemAndTitleTooltip(nameAndLevel, new ItemStack(iconItem));
            Component newLine = CommonComponents.EMPTY;
            Component coordinates = Component.translatable("coordinates").append( " : " + originPos.toShortString()).withStyle(ChatFormatting.GRAY);
            Component plotSize = Component.translatable("plot_size").append(" : " + sizeX + "x" + sizeZ).withStyle(ChatFormatting.GRAY);
            description.add(newLine);
            description.add(coordinates);
            description.add(plotSize);
            debugDescription.add(newLine);
            debugDescription.add(coordinates);
            debugDescription.add(plotSize);
            if (buildCategory == Build.BuildCategory.BUILDING) {
                color = FastColor.ARGB32.color(alpha, BUILDING_COLOR.getA(), BUILDING_COLOR.getB(), BUILDING_COLOR.getC());
                alpha -= 40;
                if (alpha < 40) {
                    alpha = 240;
                }
                drawShadow = true;
            } else if (buildCategory == Build.BuildCategory.ROAD) {
                color = FastColor.ARGB32.color(255, ROAD_COLOR.getA(), ROAD_COLOR.getB(), ROAD_COLOR.getC());
                boolean isWide = tag.getBoolean("IsWide");
                boolean canGrow = tag.getBoolean("CanGrow");
                debugDescription.add(Component.literal("isWide = " + isWide).withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
                debugDescription.add(Component.literal("canGrow = " + canGrow).withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
            }
            int minX = originPos.getX() - NWCorner.getX();
            int maxX = minX + sizeX;
            int minZ = originPos.getZ() - NWCorner.getZ();
            int maxZ = minZ + sizeZ;
            drawnBuilds.add(new DrawnBuild(titleAndItem, description, debugDescription, color, drawShadow, minX, maxX, minZ, maxZ));
        }
        mapInitialWidth = SECorner.getX() - NWCorner.getX();
        mapInitialHeight = SECorner.getZ() - NWCorner.getZ();
    }

    @Override
    public void onClose() {
        super.onClose();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    protected void init() {
        backGroundLeftPos = (width - BACKGROUND_WIDTH) / 2;
        backGroundTopPos = (height - BACKGROUND_HEIGHT) / 2;
        mapWindowLeftBound = backGroundLeftPos + MAP_MARGIN;
        mapWindowTopBound = backGroundTopPos + MAP_MARGIN;
        mapWindowRightBound = backGroundLeftPos + BACKGROUND_WIDTH - MAP_MARGIN;
        mapWindowBottomBound = backGroundTopPos + BACKGROUND_HEIGHT - MAP_MARGIN;
        mapZoom = 1;
        // Center map button
        addRenderableWidget(new ReleaseFocusButton(backGroundLeftPos + (BACKGROUND_WIDTH / 2) - BUTTON_WIDTH - 2,
                backGroundTopPos + BACKGROUND_HEIGHT + 5, BUTTON_WIDTH, BUTTON_HEIGHT, Component.literal(""),
                (pressedButton -> {
                    mapZoom = 1;
                    xDrag = 0;
                    yDrag = 0;
                })) {
            public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (visible) {
                    isHovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;
                    graphics.blit(TEXTURE, getX(), getY(), 69, isHovered ? 149 : 133, width, height, TownMapItemScreen.TEXTURE_WIDTH, TownMapItemScreen.TEXTURE_HEIGHT);
                    if (isHoveredOrFocused()) {
                        graphics.renderTooltip(font, Component.translatable("center_map"), mouseX, mouseY);
                    }
                }
            }
        });
        // Toggle debug view button
        addRenderableWidget(new ReleaseFocusButton(backGroundLeftPos + (BACKGROUND_WIDTH / 2) + 2,
                backGroundTopPos + BACKGROUND_HEIGHT + 5, BUTTON_WIDTH, BUTTON_HEIGHT, Component.literal(""),
                (pressedButton -> debugView = !debugView)) {
            public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (visible) {
                    isHovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;
                    graphics.blit(TEXTURE, getX(), getY(), 98, isHovered ? 149 : 133, width, height, TownMapItemScreen.TEXTURE_WIDTH, TownMapItemScreen.TEXTURE_HEIGHT);
                    if (isHoveredOrFocused()) {
                        graphics.renderTooltip(font, Component.translatable("debug_view"), mouseX, mouseY);
                    }
                }
            }
        });
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.blit(TEXTURE, backGroundLeftPos, backGroundTopPos, 0, 0, BACKGROUND_WIDTH, BACKGROUND_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        renderMap(graphics, mouseX, mouseY);
        graphics.drawString(this.font, Component.translatable("map_of").append(" ").append(title),
                backGroundLeftPos + (BACKGROUND_WIDTH - (font.width(Component.translatable("map_of")) + font.width(title))) / 2,
                backGroundTopPos + 6, FastColor.ARGB32.color(255, 161, 28, 24), false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMap(GuiGraphics graphics, int mouseX, int mouseY) {
        for (DrawnBuild build : drawnBuilds) {
            int buildWidth = build.maxX - build.minX;
            int buildHeight = build.maxZ - build.minZ;
            int buildMinX = scaledMapX() + build.minX * mapZoom;
            int buildMaxX = buildMinX + (buildWidth * mapZoom);
            int buildMinZ = scaledMapY() + build.minZ * mapZoom;
            int buildMaxZ = buildMinZ + (buildHeight * mapZoom);

            boolean outsideMapWindow = buildMinX > mapWindowRightBound || buildMaxX < mapWindowLeftBound || buildMinZ > mapWindowBottomBound || buildMaxZ < mapWindowTopBound;
            if (!outsideMapWindow) {
                buildMinX = Math.max(mapWindowLeftBound, buildMinX);
                buildMaxX = Math.min(mapWindowRightBound, buildMaxX);
                buildMinZ = Math.max(mapWindowTopBound, buildMinZ);
                buildMaxZ = Math.min(mapWindowBottomBound, buildMaxZ);
                if (build.isBuilding()) {
                    graphics.fill(buildMinX, buildMinZ, buildMaxX - 1, buildMaxZ - 1, build.color); // Lighted part
                    int shadowColor = FastColor.ARGB32.color(Math.min(255, FastColor.ARGB32.alpha(build.color) + 20), FastColor.ARGB32.red(build.color), FastColor.ARGB32.green(build.color), FastColor.ARGB32.blue(build.color));
                    graphics.fill(buildMinX , buildMaxZ - 1 , buildMaxX, buildMaxZ, shadowColor); // Bottom shadow
                    graphics.fill(buildMaxX - 1 , buildMinZ , buildMaxX, buildMaxZ - 1, shadowColor); // Right Shadow
                } else {
                    graphics.fill(buildMinX, buildMinZ, buildMaxX, buildMaxZ, build.color);
                }
                if (mouseX >= buildMinX && mouseX < buildMaxX && mouseY >= buildMinZ && mouseY < buildMaxZ) {
                    Optional<TooltipComponent> opt = Optional.of(build.titleAndIcon());
                    graphics.renderTooltip(font, debugView ? build.debugDescription : build.description, opt, mouseX, mouseY);
                }
            }
        }
    }

    private int scaledMapX() {
        return mapWindowLeftBound + ((mapWindowRightBound - mapWindowLeftBound - (mapInitialWidth * mapZoom)) / 2) + xDrag;
    }

    private int scaledMapY() {
        return mapWindowTopBound + ((mapWindowBottomBound - mapWindowTopBound - (mapInitialHeight * mapZoom)) / 2) + yDrag;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        draggingMap = mouseInMainWindow(mouseX, mouseY);
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
            xDrag += (int)dragX;
            yDrag += (int)dragY;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseInMainWindow(mouseX, mouseY)) {
            int windowCenterX = (mapWindowRightBound - mapWindowLeftBound) / 2;
            int windowCenterY = (mapWindowBottomBound - mapWindowTopBound) / 2;
            if (delta > 0) {
                xDrag -= ((int)(mouseX - mapWindowLeftBound) - windowCenterX);
                yDrag -= ((int)(mouseY - mapWindowTopBound) - windowCenterY);
            } else if (mapZoom > 1){
                xDrag -= ((int)(mouseX - mapWindowLeftBound) - windowCenterX) / 4;
                yDrag -= ((int)(mouseY - mapWindowTopBound) - windowCenterY) / 4;
            }
            if (delta > 0 || mapZoom > 1) {
                if (soundTick > 3) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SPYGLASS_USE, delta > 0 ? 1.0F : 0.7F));
                    soundTick = 0;
                }
            }
            mapZoom = Math.max(1, mapZoom + (int)delta);
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean mouseInMainWindow(double mouseX, double mouseY) {
        return mouseX > backGroundLeftPos && mouseX < backGroundLeftPos + BACKGROUND_WIDTH && mouseY > backGroundTopPos && mouseY < backGroundTopPos + BACKGROUND_HEIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        if (soundTick < 4) {
            soundTick++;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record DrawnBuild(TooltipComponent titleAndIcon, List<Component> description, List<Component> debugDescription, int color, boolean isBuilding, int minX, int maxX, int minZ, int maxZ) {}
}