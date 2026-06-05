package org.dawnoftime.onceuponatown.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.ClientBuildingDefsRegistry;
import org.dawnoftime.onceuponatown.client.VillageHubClientState;
import org.dawnoftime.onceuponatown.client.gui.tooltip.BuildingProductionTooltip;
import org.dawnoftime.onceuponatown.client.gui.widgets.BuildingProductionDraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.DraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.MapDraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.VillageSummaryDraggableWidget;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.screen.VillageChestMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VillageChestScreen extends AbstractContainerScreen<VillageChestMenu> {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(Ouat.MOD_ID, "textures/gui/village_chest.png");
    private static final ResourceLocation TEXTURE_CONSTRUCTION =
        new ResourceLocation(Ouat.MOD_ID, "textures/gui/village_construction.png");
    private static final ResourceLocation TEXTURE_UPGRADE =
        new ResourceLocation(Ouat.MOD_ID, "textures/gui/village_upgrade.png");

    private static final int COLOR_TOWN_CENTER = 0xFFFFAA00;
    private static final int COLOR_JOBS        = 0xFFCC3333;
    private static final int COLOR_GARDENS     = 0xFF33AA33;
    private static final int COLOR_BUILDINGS   = 0xFF885533;
    private static final int COLOR_UNKNOWN     = 0xFF888888;

    private static final int COLOR_SLOT_HOVER  = 0x40FFFFFF;
    private static final int COLOR_SLOT_EMPTY  = 0x20FFFFFF;
    private static final int COLOR_TAB_ACTIVE  = 0xFFD0C080;
    private static final int COLOR_TAB_INACTIVE = 0xFF807850;
    private static final int COLOR_TAB_TEXT    = 0xFF1A1A1A;

    private static final int QUEUE_GRID_X      = 8;
    private static final int QUEUE_GRID_Y      = 18;
    private static final int QUEUE_ROWS        = 6;
    private static final int QUEUE_COLS        = 9;
    private static final int AVAIL_GRID_Y_ROW0 = 140;
    private static final int AVAIL_ROWS        = 4;
    private static final int AVAIL_COLS        = 9;
    private static final int CELL              = 18;

    // Session-persistent widget layout (reset on Minecraft restart)
    private static int savedMapX = -1, savedMapY = -1;
    private static int savedSummaryX = -1, savedSummaryY = -1;
    private static boolean savedMapOpen = true;
    private static boolean savedSummaryOpen = true;
    private static int savedActiveTab = 0;
    private static final List<String> savedWidgetOrder = new ArrayList<>();

    private final List<DraggableWidget> widgets = new ArrayList<>();
    private boolean mapWidgetCreated = false;
    private BuildingProductionDraggableWidget productionPopup = null;
    private static int lastProductionPopupX = -1;
    private static int lastProductionPopupY = -1;
    private int mapInitialHeight = 0;

    private CompoundTag cachedHubData;
    private boolean mapClosed = false;
    private boolean summaryClosed = false;

    private int activeTab = 0; // 0 = Stock, 1 = Construction, 2 = Upgrade
    private BlockPos anchorPos = BlockPos.ZERO;
    private final List<BuildingEntry> buildingCatalog = new ArrayList<>();
    private final List<ClientQueueEntry> constructionQueueClient = new ArrayList<>();
    private final Map<String, Integer> stockSnapshot = new HashMap<>();
    private int catalogScrollOffset = 0;

    private int hoveredQueueSlot = -1;
    private int hoveredCatalogSlot = -1;

    private final List<UpgradeBuildingEntry> upgradeBuildingsList = new ArrayList<>();
    private long selectedUpgradeBuildingPos = -1L;
    private int upgradeGridScrollOffset = 0;
    private int hoveredUpgradeSlot = -1;

    private record BuildingEntry(String id, String category, String iconItem,
                                 List<CostEntry> cost,
                                 List<BuildingProductionTooltip.Row> productionRows) {}
    private record CostEntry(String itemId, int amount) {}
    private record UpgradeBuildingEntry(String defId, long worldPosLong, int upgradeLevel,
                                        String category, String iconItem) {}
    private record ClientQueueEntry(String type, String defId, long buildingWorldPos) {
        boolean isUpgrade() { return "upgrade".equals(type); }
    }

    public VillageChestScreen(VillageChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = this.width - this.imageWidth - 10;
        activeTab = savedActiveTab;
        if (VillageHubClientState.pendingHubData != null) {
            CompoundTag hub = VillageHubClientState.pendingHubData;
            VillageHubClientState.pendingHubData = null;
            parseHubData(hub);
            tryCreateMapWidget(hub);
        }
    }

    private void tryCreateMapWidget(CompoundTag hub) {
        if (mapWidgetCreated) return;
        cachedHubData = hub;
        int freeZoneW = this.leftPos;
        int initialW = Math.min(160, freeZoneW - 20);
        if (initialW > 50) {
            mapInitialHeight = initialW + DraggableWidget.TITLE_BAR_H;
            List<DraggableWidget> newWidgets = new ArrayList<>();
            if (savedMapOpen) {
                int startX = (savedMapX >= 0) ? Math.min(savedMapX, Math.max(0, freeZoneW - initialW)) : centerX(freeZoneW, initialW);
                int startY = (savedMapY >= 0) ? Math.min(savedMapY, Math.max(0, this.height - mapInitialHeight)) : centerY(this.height, mapInitialHeight);
                MapDraggableWidget mapWidget = new MapDraggableWidget(startX, startY, initialW, mapInitialHeight, freeZoneW, this.height, hub.getCompound("MapData"));
                mapWidget.setOnBuildingClicked(pos -> { activeTab = 2; selectedUpgradeBuildingPos = pos; });
                newWidgets.add(mapWidget);
            }
            if (hub.contains("SummaryData") && savedSummaryOpen) {
                int summaryH = DraggableWidget.TITLE_BAR_H + VillageSummaryDraggableWidget.VISIBLE_H;
                int startX = (savedSummaryX >= 0) ? Math.min(savedSummaryX, Math.max(0, freeZoneW - VillageSummaryDraggableWidget.WIDGET_W)) : centerX(freeZoneW, VillageSummaryDraggableWidget.WIDGET_W);
                int startY = (savedSummaryY >= 0) ? Math.min(savedSummaryY, Math.max(0, this.height - summaryH)) : centerY(this.height, summaryH);
                newWidgets.add(new VillageSummaryDraggableWidget(hub.getCompound("MapData"), hub.getCompound("SummaryData"), startX, startY, freeZoneW, this.height));
            }
            if (!savedWidgetOrder.isEmpty()) {
                newWidgets.sort((a, b) -> {
                    int ia = savedWidgetOrder.indexOf(a.getClass().getSimpleName());
                    int ib = savedWidgetOrder.indexOf(b.getClass().getSimpleName());
                    if (ia < 0) ia = Integer.MAX_VALUE;
                    if (ib < 0) ib = Integer.MAX_VALUE;
                    return Integer.compare(ia, ib);
                });
            }
            widgets.addAll(newWidgets);
            mapWidgetCreated = true;
        }
    }

    private void parseHubData(CompoundTag hub) {
        anchorPos = NbtUtils.readBlockPos(hub.getCompound("AnchorPos"));

        constructionQueueClient.clear();
        hub.getList("ConstructionQueue", Tag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag qt = (CompoundTag) raw;
            String type = qt.getString("Type");
            String defId = qt.getString("DefId");
            long worldPos = "upgrade".equals(type) ? qt.getLong("BuildingWorldPos") : 0L;
            constructionQueueClient.add(new ClientQueueEntry(type, defId, worldPos));
        });

        buildingCatalog.clear();
        hub.getList("BuildingCatalog", Tag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag dt = (CompoundTag) raw;
            String id = dt.getString("Id");
            String category = dt.getString("Category");
            String iconItem = dt.getString("IconItem");
            List<CostEntry> cost = new ArrayList<>();
            dt.getList("ConstructionCost", Tag.TAG_COMPOUND)
                .forEach(cr -> {
                    CompoundTag ct = (CompoundTag) cr;
                    cost.add(new CostEntry(ct.getString("Item"), ct.getInt("Amount")));
                });

            List<BuildingProductionTooltip.Row> productionRows = new ArrayList<>();
            ListTag prod = dt.getList("Production", Tag.TAG_COMPOUND);
            if (!prod.isEmpty()) {
                productionRows.add(new BuildingProductionTooltip.Row(null,
                    Component.translatable("onceuponatown.tooltip.produces")));
                for (Tag t : prod) {
                    CompoundTag pt = (CompoundTag) t;
                    String itemId = pt.getString("Item");
                    int amount    = pt.getInt("Amount");
                    int seconds   = pt.getInt("EveryTicks") / 20;
                    Item item     = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
                    MutableComponent text = Component.literal("x" + amount + " ")
                        .append(Component.translatable(item.getDescriptionId()))
                        .append(Component.literal(" / " + seconds + "s"))
                        .withStyle(ChatFormatting.GRAY);
                    productionRows.add(new BuildingProductionTooltip.Row(new ItemStack(item), text));
                }
            }
            ListTag transforms = dt.getList("Transformations", Tag.TAG_COMPOUND);
            if (!transforms.isEmpty()) {
                productionRows.add(new BuildingProductionTooltip.Row(null,
                    Component.translatable("onceuponatown.tooltip.transforms")));
                for (Tag t : transforms) {
                    CompoundTag tt = (CompoundTag) t;
                    String outputId   = tt.getString("OutputItem");
                    int outputAmount  = tt.getInt("OutputAmount");
                    int seconds       = tt.getInt("EveryTicks") / 20;
                    Item outputItem   = BuiltInRegistries.ITEM.get(new ResourceLocation(outputId));
                    MutableComponent text = Component.literal("x" + outputAmount + " ")
                        .append(Component.translatable(outputItem.getDescriptionId()))
                        .append(Component.literal(" / " + seconds + "s"))
                        .withStyle(ChatFormatting.GRAY);
                    productionRows.add(new BuildingProductionTooltip.Row(new ItemStack(outputItem), text));
                }
            }
            double productionBonus = dt.getDouble("ProductionBonus");
            if (productionBonus > 0) {
                productionRows.add(new BuildingProductionTooltip.Row(null,
                    Component.translatable("onceuponatown.tooltip.perks")));
                int percent = (int) Math.round(productionBonus * 100);
                productionRows.add(new BuildingProductionTooltip.Row(new ItemStack(Items.NETHER_STAR),
                    Component.translatable("onceuponatown.tooltip.production_bonus", percent)
                        .withStyle(ChatFormatting.GRAY)));
            }
            int residents = dt.getInt("Residents");
            if (residents > 0) {
                productionRows.add(new BuildingProductionTooltip.Row(null,
                    Component.translatable("onceuponatown.tooltip.adds")));
                net.minecraft.world.item.Item villagerEgg = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    new ResourceLocation("minecraft:villager_spawn_egg"));
                productionRows.add(new BuildingProductionTooltip.Row(new ItemStack(villagerEgg),
                    Component.translatable("onceuponatown.tooltip.residents", residents)
                        .withStyle(ChatFormatting.GRAY)));
            }

            buildingCatalog.add(new BuildingEntry(id, category, iconItem, cost, productionRows));
        });

        stockSnapshot.clear();
        CompoundTag stockTag = hub.getCompound("StockSnapshot");
        for (String key : stockTag.getAllKeys()) {
            stockSnapshot.put(key, stockTag.getInt(key));
        }

        upgradeBuildingsList.clear();
        hub.getList("UpgradeBuildings", Tag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag ubt = (CompoundTag) raw;
            upgradeBuildingsList.add(new UpgradeBuildingEntry(
                ubt.getString("DefId"),
                ubt.getLong("WorldPos"),
                ubt.getInt("UpgradeLevel"),
                ubt.getString("Category"),
                ubt.getString("IconItem")
            ));
        });
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        ResourceLocation tex = switch (activeTab) {
            case 1  -> TEXTURE_CONSTRUCTION;
            case 2  -> TEXTURE_UPGRADE;
            default -> TEXTURE;
        };
        guiGraphics.blit(tex, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (VillageHubClientState.pendingHubData != null) {
            CompoundTag hub = VillageHubClientState.pendingHubData;
            VillageHubClientState.pendingHubData = null;
            parseHubData(hub);
            tryCreateMapWidget(hub);
        }

        this.renderBackground(guiGraphics);

        if (activeTab == 1 || activeTab == 2) {
            // Bypass super.render() entirely so inventory slots (rendered at z=232) never reach the frame
            renderBg(guiGraphics, partialTick, mouseX, mouseY);
        } else {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        widgets.removeIf(w -> {
            if (w.isClosed()) {
                if (w == productionPopup) {
                    lastProductionPopupX = productionPopup.getX();
                    lastProductionPopupY = productionPopup.getY();
                    productionPopup = null;
                }
                if (w instanceof MapDraggableWidget) {
                    savedMapX = w.getX();
                    savedMapY = w.getY();
                    savedMapOpen = false;
                }
                if (w instanceof VillageSummaryDraggableWidget) {
                    savedSummaryX = w.getX();
                    savedSummaryY = w.getY();
                    savedSummaryOpen = false;
                }
            }
            return w.isClosed();
        });
        mapClosed     = widgets.stream().noneMatch(w -> w instanceof MapDraggableWidget);
        summaryClosed = widgets.stream().noneMatch(w -> w instanceof VillageSummaryDraggableWidget);

        // Each widget is rendered at a higher z-band (500 units apart) so that
        // fills AND items (which Minecraft auto-translates to z+232) from a later
        // widget always appear in front of everything from an earlier widget.
        for (int i = 0; i < widgets.size(); i++) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, i * 500.0f);
            widgets.get(i).render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().popPose();
        }

        renderReopenButtons(guiGraphics, mouseX, mouseY);
        renderTabs(guiGraphics, mouseX, mouseY);

        if (activeTab == 1) {
            renderConstructionTab(guiGraphics, mouseX, mouseY);
        } else if (activeTab == 2) {
            renderUpgradeTab(guiGraphics, mouseX, mouseY);
        }

        // Render tooltips above all draggable widgets by pushing z higher than the topmost widget.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, (widgets.size() + 1) * 500.0f);
        if (activeTab == 1) {
            renderConstructionTooltips(guiGraphics, mouseX, mouseY);
        } else if (activeTab == 2) {
            renderUpgradeTooltips(guiGraphics, mouseX, mouseY);
        } else {
            this.renderTooltip(guiGraphics, mouseX, mouseY);
        }
        guiGraphics.pose().popPose();
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        int tabY = topPos + 2;
        int tabH = 12;
        int tabW = 56;
        int tab0X = leftPos + 4;
        int tab1X = leftPos + 60;
        int tab2X = leftPos + 116;

        g.fill(tab0X, tabY, tab0X + tabW, tabY + tabH, activeTab == 0 ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
        g.fill(tab1X, tabY, tab1X + tabW, tabY + tabH, activeTab == 1 ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
        g.fill(tab2X, tabY, tab2X + tabW, tabY + tabH, activeTab == 2 ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);

        g.drawString(font, "Stock",   tab0X + 4, tabY + 2, COLOR_TAB_TEXT, false);
        g.drawString(font, "Build",   tab1X + 4, tabY + 2, COLOR_TAB_TEXT, false);
        g.drawString(font, "Upgrade", tab2X + 4, tabY + 2, COLOR_TAB_TEXT, false);
    }

    private void renderConstructionTab(GuiGraphics g, int mx, int my) {
        hoveredQueueSlot = -1;
        hoveredCatalogSlot = -1;

        for (int i = 0; i < QUEUE_ROWS * QUEUE_COLS; i++) {
            int col = i % QUEUE_COLS;
            int row = i / QUEUE_COLS;
            int sx = leftPos + QUEUE_GRID_X + col * CELL;
            int sy = topPos + QUEUE_GRID_Y + row * CELL;

            if (i < constructionQueueClient.size()) {
                ClientQueueEntry qe = constructionQueueClient.get(i);
                BuildingEntry entry = findCatalogEntry(qe.defId());
                int color = entry != null ? categoryColor(entry.category()) : COLOR_UNKNOWN;
                g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, color);
                if (entry != null) renderItemIcon(g, entry.iconItem(), sx, sy);
                if (qe.isUpgrade()) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    String badge = "UP";
                    g.drawString(font, badge, sx + CELL - 2 - font.width(badge) - 1, sy + CELL - 10, 0xFF55FFFF, true);
                    g.pose().popPose();
                }
            } else {
                g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, COLOR_SLOT_EMPTY);
            }

            if (mx >= sx && mx < sx + CELL && my >= sy && my < sy + CELL) {
                g.fill(sx, sy, sx + CELL, sy + CELL, COLOR_SLOT_HOVER);
                hoveredQueueSlot = i;
            }
        }

        int visibleStart = catalogScrollOffset * AVAIL_COLS;
        int[] rowYOffsets = {AVAIL_GRID_Y_ROW0, AVAIL_GRID_Y_ROW0 + 18, AVAIL_GRID_Y_ROW0 + 36, 198};
        for (int row = 0; row < AVAIL_ROWS; row++) {
            for (int col = 0; col < AVAIL_COLS; col++) {
                int catalogIdx = visibleStart + row * AVAIL_COLS + col;
                int sx = leftPos + QUEUE_GRID_X + col * CELL;
                int sy = topPos + rowYOffsets[row];

                if (catalogIdx < buildingCatalog.size()) {
                    BuildingEntry entry = buildingCatalog.get(catalogIdx);
                    boolean affordable = isAffordable(entry);
                    int color = affordable ? categoryColor(entry.category()) : dim(categoryColor(entry.category()));
                    g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, color);
                    renderItemIcon(g, entry.iconItem(), sx, sy);
                    if (!affordable) {
                        g.fill(sx, sy, sx + CELL - 2, sy + 1, 0x88CC0000);
                    }
                } else {
                    g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, COLOR_SLOT_EMPTY);
                }

                if (mx >= sx && mx < sx + CELL && my >= sy && my < sy + CELL) {
                    g.fill(sx, sy, sx + CELL, sy + CELL, COLOR_SLOT_HOVER);
                    hoveredCatalogSlot = visibleStart + row * AVAIL_COLS + col;
                }
            }
        }

        int totalRows = (buildingCatalog.size() + AVAIL_COLS - 1) / AVAIL_COLS;
        if (totalRows > AVAIL_ROWS) {
            String scrollText = (catalogScrollOffset + 1) + "/" + (totalRows - AVAIL_ROWS + 1);
            g.drawString(font, scrollText, leftPos + imageWidth - 4 - font.width(scrollText),
                topPos + 210, 0xFFAAAAAA, false);
        }
    }

    private void renderConstructionTooltips(GuiGraphics g, int mx, int my) {
        if (hoveredQueueSlot >= 0 && hoveredQueueSlot < constructionQueueClient.size()) {
            ClientQueueEntry qe = constructionQueueClient.get(hoveredQueueSlot);
            BuildingEntry entry = findCatalogEntry(qe.defId());
            List<Component> lines = new ArrayList<>();
            if (qe.isUpgrade()) {
                lines.add(Component.literal("Upgrade: " + formatId(qe.defId())).withStyle(s -> s.withBold(true)));
            } else {
                lines.add(Component.literal(formatId(qe.defId())).withStyle(s -> s.withBold(true)));
            }
            if (entry != null) {
                lines.add(Component.literal("Category: " + formatId(entry.category()))
                    .withStyle(s -> s.withColor(0xAAAAAA)));
            }
            lines.add(Component.literal("Shift + Right-click to remove").withStyle(s -> s.withColor(0x888888)));
            g.renderComponentTooltip(font, lines, mx, my);
        } else if (hoveredCatalogSlot >= 0 && hoveredCatalogSlot < buildingCatalog.size()) {
            BuildingEntry entry = buildingCatalog.get(hoveredCatalogSlot);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(formatId(entry.id())).withStyle(s -> s.withBold(true)));
            lines.add(Component.literal("Category: " + formatId(entry.category()))
                .withStyle(s -> s.withColor(0xAAAAAA)));
            if (!entry.cost().isEmpty()) {
                lines.add(Component.literal("Construction cost:").withStyle(s -> s.withColor(0xCCCCCC)));
                for (CostEntry ce : entry.cost()) {
                    int have = stockSnapshot.getOrDefault(ce.itemId(), 0);
                    boolean ok = have >= ce.amount();
                    int color = ok ? 0x55FF55 : 0xFF5555;
                    String itemName = ce.itemId().contains(":")
                        ? ce.itemId().substring(ce.itemId().indexOf(':') + 1) : ce.itemId();
                    lines.add(Component.literal("  " + ce.amount() + "x " + formatId(itemName))
                        .withStyle(s -> s.withColor(color)));
                }
            } else {
                lines.add(Component.literal("Free").withStyle(s -> s.withColor(0x55FF55)));
            }
            if (!isAffordable(entry)) {
                lines.add(Component.literal("Not enough resources").withStyle(s -> s.withColor(0xFF5555)));
            }
            if (!entry.productionRows().isEmpty()) {
                lines.add(Component.literal("Right-click: View production").withStyle(s -> s.withColor(0x888888)));
            }
            lines.add(Component.literal("Left-click to queue").withStyle(s -> s.withColor(0x888888)));
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }

    private void renderUpgradeTab(GuiGraphics g, int mx, int my) {
        hoveredUpgradeSlot = -1;
        int panelX = leftPos + 11;
        int panelW = imageWidth - 22;

        // Vertical padding inside the dark info rectangle
        final int vPad = 3;

        UpgradeBuildingEntry sel = getSelectedUpgradeEntry();
        if (sel == null) {
            g.drawString(font, "Select a building below", panelX, topPos + 19 + vPad, 0xFF888888, false);
        } else {
            ClientBuildingDefsRegistry.DefEntry defEntry = ClientBuildingDefsRegistry.get(sel.defId());
            int maxLevel = defEntry != null ? defEntry.upgrades().size() : 0;

            String name = formatId(sel.defId());
            String levelStr = sel.upgradeLevel() >= maxLevel && maxLevel > 0
                ? "Level " + maxLevel + "/" + maxLevel + " (Max)"
                : "Level " + sel.upgradeLevel() + "/" + maxLevel;
            g.drawString(font, name, panelX, topPos + 19 + vPad, 0xFFFFFFFF, false);
            int levelColor = (sel.upgradeLevel() >= maxLevel && maxLevel > 0) ? 0xFFFFDD44 : 0xFFCCCCCC;
            g.drawString(font, levelStr, leftPos + imageWidth - 11 - font.width(levelStr), topPos + 19 + vPad, levelColor, false);

            if (defEntry != null && maxLevel > 0) {
                double maxCadence = 0, curCadence = 0, nextCadence = 0;
                int maxCapAdd = 0, curCapAdd = 0, nextCapAdd = 0;
                int maxAmountAdd = 0, curAmountAdd = 0, nextAmountAdd = 0;
                for (int i = 0; i < defEntry.upgrades().size(); i++) {
                    ClientBuildingDefsRegistry.UpgradeLevelClient u = defEntry.upgrades().get(i);
                    maxCadence    += u.cadenceMultiplier();
                    maxCapAdd     += u.capacityStacksAdd();
                    maxAmountAdd  += u.amountAdd();
                    if (i < sel.upgradeLevel()) {
                        curCadence    += u.cadenceMultiplier();
                        curCapAdd     += u.capacityStacksAdd();
                        curAmountAdd  += u.amountAdd();
                    }
                }
                boolean atMax = sel.upgradeLevel() >= maxLevel;
                if (!atMax) {
                    ClientBuildingDefsRegistry.UpgradeLevelClient next = defEntry.upgrades().get(sel.upgradeLevel());
                    nextCadence    = next.cadenceMultiplier();
                    nextCapAdd     = next.capacityStacksAdd();
                    nextAmountAdd  = next.amountAdd();
                }

                int barW = panelW - 6;
                int barX = panelX + 2;

                // Units bar (+amount_add cumulated)
                g.drawString(font, "Units", panelX, topPos + 32 + vPad, 0xFFFFAA33, false);
                int baseAmt = defEntry.baseAmount();
                float unitMax  = baseAmt + maxAmountAdd;
                float unitFill  = unitMax > 0 ? (baseAmt + curAmountAdd) / unitMax : 1f;
                float unitGhost = unitMax > 0 && !atMax ? nextAmountAdd / unitMax : 0f;
                renderStatBar(g, barX, topPos + 42 + vPad, barW, 4, unitFill, unitGhost, 0xFFCC7700);

                // Speed bar (cadence_multiplier cumulated)
                g.drawString(font, "Speed", panelX, topPos + 51 + vPad, 0xFF55EE55, false);
                float speedFill  = maxCadence > 0 ? (float)(curCadence / maxCadence) : 0f;
                float speedGhost = maxCadence > 0 && !atMax ? (float)(nextCadence / maxCadence) : 0f;
                renderStatBar(g, barX, topPos + 61 + vPad, barW, 4, speedFill, speedGhost, 0xFF226622);

                // Capacity bar (capacity_stacks_add cumulated)
                g.drawString(font, "Capacity", panelX, topPos + 70 + vPad, 0xFF8888FF, false);
                int baseCap = defEntry.baseCapacity();
                float capMax = baseCap + maxCapAdd;
                float capFill  = capMax > 0 ? (baseCap + curCapAdd) / capMax : 1f;
                float capGhost = capMax > 0 && !atMax ? nextCapAdd / capMax : 0f;
                renderStatBar(g, barX, topPos + 80 + vPad, barW, 4, capFill, capGhost, 0xFF3355DD);

                if (atMax) {
                    g.drawString(font, "Fully upgraded", panelX, topPos + 89 + vPad, 0xFFFFDD44, false);
                } else {
                    boolean canAfford = canAffordUpgrade(sel, defEntry);
                    int btnX = panelX;
                    int btnY = topPos + 89 + vPad;
                    int btnW = 46;
                    int btnH = 11;
                    boolean btnHover = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH;
                    g.fill(btnX, btnY, btnX + btnW, btnY + btnH,
                        canAfford ? (btnHover ? 0xFF55BB55 : 0xFF337733) : 0xFF444444);
                    String btnText = "Upgrade";
                    g.drawString(font, btnText, btnX + (btnW - font.width(btnText)) / 2, btnY + 2,
                        canAfford ? 0xFFFFFFFF : 0xFF888888, false);
                }
            } else if (defEntry != null) {
                g.drawString(font, "No upgrades available", panelX, topPos + 31 + vPad, 0xFF666666, false);
            }
        }

        renderUpgradeBuildingGrid(g, mx, my);
    }

    private void renderStatBar(GuiGraphics g, int x, int y, int w, int h,
                                float fill, float ghost, int color) {
        g.fill(x, y, x + w, y + h, 0xFF222222);
        int fillW = (int)(w * Math.min(1f, Math.max(0f, fill)));
        if (fillW > 0) g.fill(x, y, x + fillW, y + h, color);
        if (ghost > 0f) {
            int ghostW = (int)(w * Math.min(1f - fill, Math.max(0f, ghost)));
            if (ghostW > 0) {
                int ghostColor = (color & 0x00FFFFFF) | 0x66000000;
                g.fill(x + fillW, y, x + fillW + ghostW, y + h, ghostColor);
            }
        }
    }

    private void renderUpgradeBuildingGrid(GuiGraphics g, int mx, int my) {
        int[] rowYOffsets = { AVAIL_GRID_Y_ROW0, AVAIL_GRID_Y_ROW0 + 18, AVAIL_GRID_Y_ROW0 + 36, 198 };
        int visibleStart = upgradeGridScrollOffset * AVAIL_COLS;

        for (int row = 0; row < AVAIL_ROWS; row++) {
            for (int col = 0; col < AVAIL_COLS; col++) {
                int idx = visibleStart + row * AVAIL_COLS + col;
                int sx = leftPos + QUEUE_GRID_X + col * CELL;
                int sy = topPos + rowYOffsets[row];

                if (idx < upgradeBuildingsList.size()) {
                    UpgradeBuildingEntry entry = upgradeBuildingsList.get(idx);
                    ClientBuildingDefsRegistry.DefEntry defEntry = ClientBuildingDefsRegistry.get(entry.defId());
                    boolean upgradable = defEntry != null && !defEntry.upgrades().isEmpty();
                    boolean atMax = upgradable && entry.upgradeLevel() >= defEntry.upgrades().size();
                    boolean affordable = !upgradable || atMax || canAffordUpgrade(entry, defEntry);
                    int color = affordable ? categoryColor(entry.category()) : dim(categoryColor(entry.category()));
                    g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, color);
                    renderItemIcon(g, entry.iconItem(), sx, sy);
                    if (entry.upgradeLevel() > 0) {
                        String lvl = "+" + entry.upgradeLevel();
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 300);
                        g.drawString(font, lvl, sx + CELL - 2 - font.width(lvl) - 1, sy + CELL - 10, 0xFFFFFF55, true);
                        g.pose().popPose();
                    }
                } else {
                    g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, COLOR_SLOT_EMPTY);
                }

                if (mx >= sx && mx < sx + CELL && my >= sy && my < sy + CELL) {
                    g.fill(sx, sy, sx + CELL, sy + CELL, COLOR_SLOT_HOVER);
                    hoveredUpgradeSlot = idx;
                }
            }
        }

        int totalRows = (upgradeBuildingsList.size() + AVAIL_COLS - 1) / AVAIL_COLS;
        if (totalRows > AVAIL_ROWS) {
            String scrollText = (upgradeGridScrollOffset + 1) + "/" + (totalRows - AVAIL_ROWS + 1);
            g.drawString(font, scrollText, leftPos + imageWidth - 4 - font.width(scrollText),
                topPos + 210, 0xFFAAAAAA, false);
        }
    }

    private void renderUpgradeTooltips(GuiGraphics g, int mx, int my) {
        if (hoveredUpgradeSlot < 0 || hoveredUpgradeSlot >= upgradeBuildingsList.size()) return;
        UpgradeBuildingEntry entry = upgradeBuildingsList.get(hoveredUpgradeSlot);
        ClientBuildingDefsRegistry.DefEntry defEntry = ClientBuildingDefsRegistry.get(entry.defId());
        int maxLevel = defEntry != null ? defEntry.upgrades().size() : 0;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(formatId(entry.defId()))
            .withStyle(s -> s.withBold(true)));
        lines.add(Component.literal("Level " + entry.upgradeLevel() + "/" + maxLevel)
            .withStyle(s -> s.withColor(0xAAAAAA)));

        if (defEntry != null && maxLevel > 0 && entry.upgradeLevel() < maxLevel) {
            List<ClientBuildingDefsRegistry.CostEntry> costs = defEntry.upgrades().get(entry.upgradeLevel()).upgradeCost();
            if (!costs.isEmpty()) {
                lines.add(Component.literal("Upgrade cost:").withStyle(s -> s.withColor(0xCCCCCC)));
                for (ClientBuildingDefsRegistry.CostEntry ce : costs) {
                    int have = stockSnapshot.getOrDefault(ce.itemId(), 0);
                    boolean ok = have >= ce.amount();
                    String itemName = ce.itemId().contains(":")
                        ? ce.itemId().substring(ce.itemId().indexOf(':') + 1) : ce.itemId();
                    lines.add(Component.literal("  " + ce.amount() + "x " + formatId(itemName))
                        .withStyle(s -> s.withColor(ok ? 0x55FF55 : 0xFF5555)));
                }
                if (!canAffordUpgrade(entry, defEntry)) {
                    lines.add(Component.literal("Not enough resources").withStyle(s -> s.withColor(0xFF5555)));
                }
            }
        } else if (maxLevel > 0 && entry.upgradeLevel() >= maxLevel) {
            lines.add(Component.literal("Fully upgraded").withStyle(s -> s.withColor(0xFFFFDD44)));
        } else if (maxLevel == 0) {
            lines.add(Component.literal("Cannot be upgraded").withStyle(s -> s.withColor(0x666666)));
        }
        lines.add(Component.literal("Click to select").withStyle(s -> s.withColor(0x888888)));
        g.renderComponentTooltip(font, lines, mx, my);
    }

    private void handleUpgradeTabClick(double mX, double mY, int button) {
        if (button != 0) return;

        UpgradeBuildingEntry sel = getSelectedUpgradeEntry();
        if (sel != null) {
            ClientBuildingDefsRegistry.DefEntry defEntry = ClientBuildingDefsRegistry.get(sel.defId());
            if (defEntry != null && sel.upgradeLevel() < defEntry.upgrades().size()) {
                int btnX = leftPos + 11;
                int btnY = topPos + 92;
                if (mX >= btnX && mX < btnX + 46 && mY >= btnY && mY < btnY + 11) {
                    if (canAffordUpgrade(sel, defEntry)) {
                        NetworkHelper.sendUpgradeBuildingPacket.accept(anchorPos, sel.worldPosLong());
                    }
                    return;
                }
            }
        }

        int[] rowYOffsets = { AVAIL_GRID_Y_ROW0, AVAIL_GRID_Y_ROW0 + 18, AVAIL_GRID_Y_ROW0 + 36, 198 };
        int visibleStart = upgradeGridScrollOffset * AVAIL_COLS;
        for (int row = 0; row < AVAIL_ROWS; row++) {
            for (int col = 0; col < AVAIL_COLS; col++) {
                int idx = visibleStart + row * AVAIL_COLS + col;
                int sx = leftPos + QUEUE_GRID_X + col * CELL;
                int sy = topPos + rowYOffsets[row];
                if (mX >= sx && mX < sx + CELL && mY >= sy && mY < sy + CELL) {
                    if (idx < upgradeBuildingsList.size()) {
                        selectedUpgradeBuildingPos = upgradeBuildingsList.get(idx).worldPosLong();
                    }
                    return;
                }
            }
        }
    }

    private UpgradeBuildingEntry getSelectedUpgradeEntry() {
        if (selectedUpgradeBuildingPos == -1L) return null;
        for (UpgradeBuildingEntry e : upgradeBuildingsList) {
            if (e.worldPosLong() == selectedUpgradeBuildingPos) return e;
        }
        return null;
    }

    private boolean canAffordUpgrade(UpgradeBuildingEntry entry, ClientBuildingDefsRegistry.DefEntry defEntry) {
        if (defEntry == null || entry.upgradeLevel() >= defEntry.upgrades().size()) return false;
        for (ClientBuildingDefsRegistry.CostEntry ce : defEntry.upgrades().get(entry.upgradeLevel()).upgradeCost()) {
            if (stockSnapshot.getOrDefault(ce.itemId(), 0) < ce.amount()) return false;
        }
        return true;
    }

    private void renderItemIcon(GuiGraphics g, String iconItemId, int x, int y) {
        try {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(iconItemId));
            if (item != net.minecraft.world.item.Items.AIR) {
                g.renderFakeItem(new ItemStack(item), x, y);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Texture has no title bar
    }

    @Override
    public void onClose() {
        savedActiveTab = activeTab;
        savedWidgetOrder.clear();
        for (DraggableWidget w : widgets) {
            if (w instanceof MapDraggableWidget) {
                savedMapX = w.getX();
                savedMapY = w.getY();
                savedWidgetOrder.add(w.getClass().getSimpleName());
            } else if (w instanceof VillageSummaryDraggableWidget) {
                savedSummaryX = w.getX();
                savedSummaryY = w.getY();
                savedWidgetOrder.add(w.getClass().getSimpleName());
            }
        }
        super.onClose();
    }

    @Override
    public boolean mouseClicked(double mX, double mY, int button) {
        // Reopen buttons
        if (button == 0 && cachedHubData != null) {
            int btnX = leftPos - 18;
            int btnY = topPos + 10;
            if (mapClosed && mX >= btnX && mX < btnX + 14 && mY >= btnY && mY < btnY + 14) {
                int freeZoneW = this.leftPos;
                int startX = (savedMapX >= 0) ? savedMapX : centerX(freeZoneW, mapInitialHeight);
                int startY = (savedMapY >= 0) ? savedMapY : centerY(this.height, mapInitialHeight);
                MapDraggableWidget reopenedMap = new MapDraggableWidget(startX, startY, Math.min(160, freeZoneW - 20), mapInitialHeight, freeZoneW, this.height, cachedHubData.getCompound("MapData"));
                reopenedMap.setOnBuildingClicked(pos -> { activeTab = 2; selectedUpgradeBuildingPos = pos; });
                widgets.add(0, reopenedMap);
                savedMapOpen = true;
                mapClosed = false;
                return true;
            }
            int iBtnY = btnY + (mapClosed ? 18 : 0);
            if (summaryClosed && cachedHubData.contains("SummaryData") && mX >= btnX && mX < btnX + 14 && mY >= iBtnY && mY < iBtnY + 14) {
                int freeZoneW = this.leftPos;
                int summaryH = DraggableWidget.TITLE_BAR_H + VillageSummaryDraggableWidget.VISIBLE_H;
                int startX = (savedSummaryX >= 0) ? savedSummaryX : centerX(freeZoneW, VillageSummaryDraggableWidget.WIDGET_W);
                int startY = (savedSummaryY >= 0) ? savedSummaryY : centerY(this.height, summaryH);
                widgets.add(0, new VillageSummaryDraggableWidget(cachedHubData.getCompound("MapData"), cachedHubData.getCompound("SummaryData"), startX, startY, freeZoneW, this.height));
                savedSummaryOpen = true;
                summaryClosed = false;
                return true;
            }
        }

        int tabY = topPos + 2;
        int tabH = 12;
        if (mY >= tabY && mY < tabY + tabH) {
            if (mX >= leftPos + 4 && mX < leftPos + 60) {
                activeTab = 0;
                catalogScrollOffset = 0;
                return true;
            }
            if (mX >= leftPos + 60 && mX < leftPos + 116) {
                activeTab = 1;
                return true;
            }
            if (mX >= leftPos + 116 && mX < leftPos + 172) {
                activeTab = 2;
                return true;
            }
        }

        // Only route clicks to widgets when outside the inventory panel area
        if (mX < leftPos) {
            for (int i = widgets.size() - 1; i >= 0; i--) {
                DraggableWidget w = widgets.get(i);
                if (w.mouseClicked(mX, mY, button)) {
                    if (i != widgets.size() - 1) {
                        widgets.remove(i);
                        widgets.add(w);
                    }
                    return true;
                }
            }
        }

        if (activeTab == 2) {
            handleUpgradeTabClick(mX, mY, button);
            return true;
        }

        if (activeTab == 1) {
            if (button == 1 && hasShiftDown() && hoveredQueueSlot >= 0
                    && hoveredQueueSlot < constructionQueueClient.size()) {
                NetworkHelper.sendRemoveQueuedBuildingPacket.accept(anchorPos, hoveredQueueSlot);
                return true;
            }
            if (button == 1 && !hasShiftDown() && hoveredCatalogSlot >= 0 && hoveredCatalogSlot < buildingCatalog.size()) {
                BuildingEntry entry = buildingCatalog.get(hoveredCatalogSlot);
                if (!entry.productionRows().isEmpty()) {
                    if (productionPopup != null) {
                        lastProductionPopupX = productionPopup.getX();
                        lastProductionPopupY = productionPopup.getY();
                        widgets.remove(productionPopup);
                    }
                    int popupH = BuildingProductionDraggableWidget.computeHeight(entry.productionRows());
                    int popupX = (lastProductionPopupX >= 0) ? lastProductionPopupX : centerX(leftPos, BuildingProductionDraggableWidget.WIDGET_W);
                    int popupY = (lastProductionPopupY >= 0) ? lastProductionPopupY : centerY(this.height, popupH);
                    productionPopup = new BuildingProductionDraggableWidget(
                        formatId(entry.id()), entry.productionRows(),
                        popupX, popupY, leftPos, this.height
                    );
                    widgets.add(productionPopup);
                }
                return true;
            }
            if (button == 0 && hoveredCatalogSlot >= 0 && hoveredCatalogSlot < buildingCatalog.size()) {
                BuildingEntry entry = buildingCatalog.get(hoveredCatalogSlot);
                if (isAffordable(entry)) {
                    NetworkHelper.sendQueueBuildingPacket.accept(anchorPos, entry.id());
                }
                return true;
            }
            return true;
        }

        return super.mouseClicked(mX, mY, button);
    }

    @Override
    public boolean mouseDragged(double mX, double mY, int button, double dX, double dY) {
        for (DraggableWidget w : widgets) {
            if (w.isDragging()) return w.mouseDragged(mX, mY, button, dX, dY);
        }
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).mouseDragged(mX, mY, button, dX, dY)) return true;
        }
        if (activeTab == 1 || activeTab == 2) return true;
        return super.mouseDragged(mX, mY, button, dX, dY);
    }

    @Override
    public boolean mouseReleased(double mX, double mY, int button) {
        for (DraggableWidget w : widgets) {
            if (w.isDragging()) return w.mouseReleased(mX, mY, button);
        }
        for (DraggableWidget w : widgets) {
            if (w.mouseReleased(mX, mY, button)) return true;
        }
        if (activeTab == 1 || activeTab == 2) return true;
        return super.mouseReleased(mX, mY, button);
    }

    @Override
    public boolean mouseScrolled(double mX, double mY, double delta) {
        // Widgets always get priority (VillageSummaryDraggableWidget scroll must work on all tabs)
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).mouseScrolled(mX, mY, delta)) return true;
        }
        if (activeTab == 1) {
            int totalRows = (buildingCatalog.size() + AVAIL_COLS - 1) / AVAIL_COLS;
            int maxOffset = Math.max(0, totalRows - AVAIL_ROWS);
            catalogScrollOffset = Math.max(0, Math.min(maxOffset,
                catalogScrollOffset - (int) Math.signum(delta)));
            return true;
        }
        if (activeTab == 2) {
            int totalRows = (upgradeBuildingsList.size() + AVAIL_COLS - 1) / AVAIL_COLS;
            int maxOffset = Math.max(0, totalRows - AVAIL_ROWS);
            upgradeGridScrollOffset = Math.max(0, Math.min(maxOffset,
                upgradeGridScrollOffset - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mX, mY, delta);
    }

    private BuildingEntry findCatalogEntry(String defId) {
        for (BuildingEntry e : buildingCatalog) {
            if (e.id().equals(defId)) return e;
        }
        return null;
    }

    private boolean isAffordable(BuildingEntry entry) {
        for (CostEntry ce : entry.cost()) {
            if (stockSnapshot.getOrDefault(ce.itemId(), 0) < ce.amount()) return false;
        }
        return true;
    }

    private static int categoryColor(String category) {
        return switch (category) {
            case "town_center" -> COLOR_TOWN_CENTER;
            case "jobs"        -> COLOR_JOBS;
            case "gardens"     -> COLOR_GARDENS;
            case "buildings"   -> COLOR_BUILDINGS;
            default            -> COLOR_UNKNOWN;
        };
    }

    private static int centerX(int freeZoneW, int widgetW) {
        return Math.max(0, (freeZoneW - widgetW) / 2);
    }

    private static int centerY(int screenH, int widgetH) {
        return Math.max(0, (screenH - widgetH) / 2);
    }

    private static int dim(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = ((argb >> 16) & 0xFF) / 2;
        int g = ((argb >> 8) & 0xFF) / 2;
        int b = (argb & 0xFF) / 2;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String formatId(String id) {
        if (id == null || id.isEmpty()) return id;
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private void renderReopenButtons(GuiGraphics g, int mx, int my) {
        if (cachedHubData == null) return;
        int btnX = leftPos - 18;
        int btnY = topPos + 10;
        if (mapClosed) {
            boolean hover = mx >= btnX && mx < btnX + 14 && my >= btnY && my < btnY + 14;
            g.fill(btnX, btnY, btnX + 14, btnY + 14, hover ? 0xFF555555 : 0xFF333333);
            drawHouseIcon(g, btnX + 2, btnY + 3);
        }
        if (summaryClosed && cachedHubData.contains("SummaryData")) {
            int iBtnY = btnY + (mapClosed ? 18 : 0);
            boolean hover = mx >= btnX && mx < btnX + 14 && my >= iBtnY && my < iBtnY + 14;
            g.fill(btnX, iBtnY, btnX + 14, iBtnY + 14, hover ? 0xFF555555 : 0xFF333333);
            drawInfoIcon(g, btnX + 2, iBtnY + 2);
        }
    }

    // Pixel-art house icon (10x7 px)
    private static void drawHouseIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFFFFFFF;
        g.fill(bx + 4, by,     bx + 6, by + 1, c); // row 0: roof peak
        g.fill(bx + 3, by + 1, bx + 7, by + 2, c); // row 1
        g.fill(bx + 2, by + 2, bx + 8, by + 3, c); // row 2
        g.fill(bx + 1, by + 3, bx + 9, by + 4, c); // row 3: roof base
        g.fill(bx + 2, by + 4, bx + 4, by + 5, c); // row 4: left wall
        g.fill(bx + 6, by + 4, bx + 8, by + 5, c); // row 4: right wall
        g.fill(bx + 2, by + 5, bx + 4, by + 6, c); // row 5: left wall
        g.fill(bx + 6, by + 5, bx + 8, by + 6, c); // row 5: right wall
        g.fill(bx + 2, by + 6, bx + 8, by + 7, c); // row 6: floor
    }

    // Pixel-art info 'i' icon (2x5 px)
    private static void drawInfoIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFFFFFFF;
        g.fill(bx + 2, by,     bx + 8, by + 1, c); // top serif
        g.fill(bx + 4, by + 1, bx + 6, by + 6, c); // stem
        g.fill(bx + 2, by + 6, bx + 8, by + 7, c); // bottom serif
    }
}
