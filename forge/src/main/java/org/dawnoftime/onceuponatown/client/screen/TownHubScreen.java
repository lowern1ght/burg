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
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.client.TownHubClientState;
import org.dawnoftime.onceuponatown.client.gui.tooltip.BuildingProductionTooltip;
import org.dawnoftime.onceuponatown.client.gui.widgets.DraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.EraProgressDraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.MapDraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.NbtPreviewWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.QuestDraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.TownSummaryWidget;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.screen.TownHubMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TownHubScreen extends AbstractContainerScreen<TownHubMenu> {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(Ouat.MOD_ID, "textures/gui/town_hub.png");
    private static final ResourceLocation TEXTURE_CONSTRUCTION =
        new ResourceLocation(Ouat.MOD_ID, "textures/gui/town_construction.png");
    private static final ResourceLocation TEXTURE_UPGRADE =
        new ResourceLocation(Ouat.MOD_ID, "textures/gui/town_upgrade.png");

    private static final int COLOR_TOWN_CENTER = 0xFFFFAA00;
    private static final int COLOR_JOBS        = 0xFFCC3333;
    private static final int COLOR_GARDENS     = 0xFF33AA33;
    private static final int COLOR_NATURALS     = 0xFF666666;
    private static final int COLOR_BUILDINGS   = 0xFF885533;
    private static final int COLOR_UNKNOWN     = 0xFF888888;

    private static final int COLOR_SLOT_HOVER  = 0x40FFFFFF;
    private static final int COLOR_SLOT_EMPTY  = 0x20FFFFFF;
    private static final int COLOR_TAB_ACTIVE  = 0xFFD0C080;
    private static final int COLOR_TAB_INACTIVE = 0xFF807850;
    private static final int COLOR_TAB_TEXT    = 0xFF1A1A1A;

    private static final int QUEUE_GRID_X      = 8;
    private static final int QUEUE_GRID_Y      = 8;
    private static final int QUEUE_ROWS        = 6;
    private static final int QUEUE_COLS        = 9;
    private static final int AVAIL_GRID_Y_ROW0 = 140;
    private static final int AVAIL_ROWS        = 3;   // upgrade building grid rows
    private static final int CATALOG_ROWS      = 3;   // construction tab: player inv rows only (no hotbar)
    private static final int AVAIL_COLS        = 9;
    private static final int CELL              = 18;

    // Session-persistent widget layout (reset on Minecraft restart)
    private static int savedMapX = -1, savedMapY = -1;
    private static int savedSummaryX = -1, savedSummaryY = -1;
    private static int savedEraX = -1, savedEraY = -1;
    private static boolean savedMapOpen = true;
    private static boolean savedSummaryOpen = true;
    private static boolean savedEraOpen = false;
    private static int savedActiveTab = 0;
    private static final List<String> savedWidgetOrder = new ArrayList<>();
    private static final Map<String, int[]> savedQuestPositions = new HashMap<>();
    private static final List<String> savedQuestOrder = new ArrayList<>();

    private static final float L1_Z_BASE =    0f;
    private static final float L1_Z_STEP =  500f;
    private static final int   L1_MAX    =   10;
    private static final float L2_Z_BASE = L1_Z_BASE + L1_MAX * L1_Z_STEP; // 5000
    private static final float L3_Z_BASE = L2_Z_BASE + L1_Z_STEP;          // 5500
    private static final float L3_Z_STEP =  500f;

    private final List<DraggableWidget> layer1Widgets = new ArrayList<>();
    private final List<DraggableWidget> layer3Widgets = new ArrayList<>();
    private boolean mapWidgetCreated = false;
    private int mapInitialHeight = 0;

    private CompoundTag cachedHubData;
    private boolean mapClosed = false;
    private boolean summaryClosed = false;
    private boolean eraClosed = true;
    private EraProgressDraggableWidget eraWidget = null;

    private int currentEra = 0;
    private int totalResidents = 0;
    private int activeResidents = 0;
    private int totalFoodDemand = 0;
    private int currentWeight = 0;
    private int maxWeight = 20;
    private List<EraProgressDraggableWidget.EraPathOption> eraTransitions = new ArrayList<>();
    private int lastKnownEra = -1;

    private int activeTab = 0; // 0 = Stock, 1 = Construction, 2 = Upgrade
    private BlockPos anchorPos = BlockPos.ZERO;
    private final List<BuildingEntry> buildingCatalog = new ArrayList<>();
    private final List<ClientQueueEntry> constructionQueueClient = new ArrayList<>();
    private final Map<String, Integer> stockSnapshot = new HashMap<>();
    private final List<String> boostedBuildingIds = new ArrayList<>();
    private int catalogScrollOffset = 0;

    private int hoveredQueueSlot = -1;
    private int hoveredCatalogSlot = -1;
    private String selectedCatalogBuildingId = null;
    private NbtPreviewWidget constructionPreview = null;

    private final List<UpgradeBuildingEntry> upgradeBuildingsList = new ArrayList<>();
    private long selectedUpgradeBuildingPos = -1L;
    private int upgradeGridScrollOffset = 0;
    private int hoveredUpgradeSlot = -1;

    // Expanded NBT view overlay state
    private boolean expandedViewOpen = false;
    private NbtPreviewWidget expandedWidget = null;
    private int expandedViewLevel = 0;
    private int expandedPanelX = 0, expandedPanelY = 0, expandedPanelSize = 0;

    // Upgrade tab bar hover state (populated each frame in renderUpgradeTab, read in renderUpgradeTooltips)
    private final int[] barLabelYs = new int[6];
    private final String[] barTooltips = new String[6];
    private int numActiveBars = 0;
    // Unlock icon hover state
    private final List<int[]> unlockIconBounds = new ArrayList<>(); // {x, y}
    private final List<String> unlockIconItemIds = new ArrayList<>();

    private record BuildingEntry(String id, String category, String iconItem,
                                 List<CostEntry> cost,
                                 List<BuildingProductionTooltip.Row> productionRows,
                                 List<ProductionCell> productionCells,
                                 int requiredResidents,
                                 List<ReqBuildingEntry> requiredBuildings,
                                 double productionBonus,
                                 float baseConsumption,
                                 float maxConsumption,
                                 int maxResidents,
                                 boolean nextEra,
                                 String nbtPath,
                                 boolean hasBuilt,
                                 List<String> nbtLevels) {}
    private record CostEntry(String itemId, int amount) {}
    private record ReqBuildingEntry(String defId, int required, int have) {}
    private record ProductionCell(Item item, int amount) {}
    private record UpgradeBuildingEntry(String defId, long worldPosLong, int upgradeLevel,
                                        String category, String iconItem) {}
    private record ClientQueueEntry(String type, String defId, long buildingWorldPos) {
        boolean isUpgrade() { return "upgrade".equals(type); }
    }

    public TownHubScreen(TownHubMenu menu, Inventory playerInventory, Component title) {
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
        if (TownHubClientState.pendingHubData != null) {
            CompoundTag hub = TownHubClientState.pendingHubData;
            TownHubClientState.pendingHubData = null;
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
                mapWidget.setOnBuildingClicked((pos, defId) -> {
                    activeTab = 1;
                    BuildingEntry catalogEntry = findCatalogEntry(defId);
                    if (catalogEntry != null) {
                        selectedCatalogBuildingId = defId;
                        updateConstructionPreview(catalogEntry);
                    }
                });
                mapWidget.setOnBuildingRightClicked(pos -> { activeTab = 2; selectedUpgradeBuildingPos = pos; });
                newWidgets.add(mapWidget);
            }
            if (hub.contains("SummaryData") && savedSummaryOpen) {
                int summaryH = DraggableWidget.TITLE_BAR_H + TownSummaryWidget.VISIBLE_H;
                int startX = (savedSummaryX >= 0) ? Math.min(savedSummaryX, Math.max(0, freeZoneW - TownSummaryWidget.WIDGET_W)) : centerX(freeZoneW, TownSummaryWidget.WIDGET_W);
                int startY = (savedSummaryY >= 0) ? Math.min(savedSummaryY, Math.max(0, this.height - summaryH)) : centerY(this.height, summaryH);
                newWidgets.add(new TownSummaryWidget(hub.getCompound("MapData"), hub.getCompound("SummaryData"), startX, startY, freeZoneW, this.height));
            }
            if (savedEraOpen) {
                int startX = (savedEraX >= 0) ? Math.min(savedEraX, Math.max(0, freeZoneW - EraProgressDraggableWidget.WIDGET_W)) : centerX(freeZoneW, EraProgressDraggableWidget.WIDGET_W);
                int startY = (savedEraY >= 0) ? savedEraY : centerY(this.height, DraggableWidget.TITLE_BAR_H + 80);
                eraWidget = new EraProgressDraggableWidget(startX, startY, freeZoneW, this.height,
                    currentEra, eraTransitions,
                    pathId -> NetworkHelper.sendAdvanceEraPacket.accept(anchorPos, pathId));
                newWidgets.add(eraWidget);
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
            layer1Widgets.addAll(newWidgets);
            mapWidgetCreated = true;
        }
    }

    private void parseHubData(CompoundTag hub) {
        anchorPos = NbtUtils.readBlockPos(hub.getCompound("AnchorPos"));
        int prevEra = currentEra;
        currentEra      = hub.getInt("CurrentEra");
        totalResidents  = hub.getInt("TotalResidents");
        activeResidents = hub.getInt("ActiveResidents");
        totalFoodDemand = hub.getInt("TotalFoodDemand");
        currentWeight  = hub.getInt("CurrentWeight");
        maxWeight      = hub.getInt("MaxWeight");
        eraTransitions = parseEraTransitions(hub);

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
            List<ProductionCell> productionCells = new ArrayList<>();
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
                    productionCells.add(new ProductionCell(item, amount));
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
                    productionCells.add(new ProductionCell(outputItem, outputAmount));
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
                productionCells.add(new ProductionCell(Items.NETHER_STAR, percent));
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
                productionCells.add(new ProductionCell(villagerEgg, residents));
            }

            int requiredResidents = dt.getInt("RequiredResidents");
            List<ReqBuildingEntry> requiredBuildings = new ArrayList<>();
            dt.getList("RequiredBuildings", Tag.TAG_COMPOUND).forEach(rt -> {
                CompoundTag req = (CompoundTag) rt;
                requiredBuildings.add(new ReqBuildingEntry(
                    req.getString("DefId"), req.getInt("Count"), req.getInt("Have")));
            });

            float baseConsumption = dt.getFloat("BaseConsumptionPerResident");
            float maxConsumption  = dt.getFloat("MaxConsumptionPerResident");
            int maxResidents      = dt.getInt("MaxResidents");
            boolean nextEra       = dt.getBoolean("NextEra");
            String nbtPath        = dt.getString("Nbt");
            boolean hasBuilt      = dt.getBoolean("HasBuilt");
            List<String> nbtLevels = new ArrayList<>();
            dt.getList("NbtLevels", Tag.TAG_STRING).forEach(t -> nbtLevels.add(t.getAsString()));

            buildingCatalog.add(new BuildingEntry(id, category, iconItem, cost, productionRows,
                productionCells, requiredResidents, requiredBuildings,
                productionBonus, baseConsumption, maxConsumption, maxResidents, nextEra,
                nbtPath, hasBuilt, nbtLevels));
        });

        stockSnapshot.clear();
        CompoundTag stockTag = hub.getCompound("StockSnapshot");
        for (String key : stockTag.getAllKeys()) {
            stockSnapshot.put(key, stockTag.getInt(key));
        }

        boostedBuildingIds.clear();
        hub.getList("BoostedBuildings", Tag.TAG_STRING).forEach(t -> boostedBuildingIds.add(t.getAsString()));

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

        if (eraWidget != null) {
            eraWidget.updateData(currentEra, eraTransitions);
        }
        if (lastKnownEra >= 0 && currentEra > prevEra) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                mc.level.playLocalSound(mc.player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f, false);
            }
        }
        lastKnownEra = currentEra;

        // Sync quest cards: add/update/remove based on what the hub packet contains
        List<CompoundTag> questTagList = new ArrayList<>();
        hub.getList("Quests", Tag.TAG_COMPOUND).forEach(t -> questTagList.add((CompoundTag) t));
        syncQuestWidgets(questTagList);
    }

    private void syncQuestWidgets(List<CompoundTag> questTags) {
        Set<String> incomingIds = new HashSet<>();
        for (CompoundTag qt : questTags) incomingIds.add(qt.getString("QuestId"));

        // Remove widgets for quests that are gone (claimed or expired), save their positions
        layer3Widgets.removeIf(w -> {
            if (w instanceof QuestDraggableWidget qw && !incomingIds.contains(qw.getQuestId())) {
                savedQuestPositions.put(qw.getQuestId(), new int[]{ qw.getX(), qw.getY() });
                return true;
            }
            return false;
        });

        // Collect IDs of quest widgets already present
        Set<String> existingIds = new HashSet<>();
        for (DraggableWidget w : layer3Widgets) {
            if (w instanceof QuestDraggableWidget qw) existingIds.add(qw.getQuestId());
        }

        // Update existing widgets; collect new ones for ordered insertion
        List<QuestDraggableWidget> newQuests = new ArrayList<>();
        int questIdx = 0;
        for (CompoundTag qt : questTags) {
            String questId = qt.getString("QuestId");
            if (existingIds.contains(questId)) {
                for (DraggableWidget w : layer3Widgets) {
                    if (w instanceof QuestDraggableWidget qw && qw.getQuestId().equals(questId)) {
                        qw.update(qt);
                        break;
                    }
                }
            } else {
                int[] pos = savedQuestPositions.get(questId);
                int startX, startY;
                if (pos != null) {
                    startX = pos[0];
                    startY = pos[1];
                } else {
                    int maxX = Math.max(1, leftPos - QuestDraggableWidget.WIDGET_W - 10);
                    int maxY = Math.max(1, height - 80);
                    startX = (int)(Math.random() * maxX);
                    startY = (int)(Math.random() * maxY);
                }
                QuestDraggableWidget qw = new QuestDraggableWidget(qt,
                    id -> NetworkHelper.sendClaimQuestPacket.accept(anchorPos, id),
                    startX, startY, leftPos, height);
                qw.setMoveCallback(() -> savedQuestPositions.put(qw.getQuestId(), new int[]{ qw.getX(), qw.getY() }));
                newQuests.add(qw);
            }
            questIdx++;
        }

        // Restore saved z-order for newly added quests
        if (!savedQuestOrder.isEmpty() && !newQuests.isEmpty()) {
            newQuests.sort((a, b) -> {
                int ia = savedQuestOrder.indexOf(a.getQuestId());
                int ib = savedQuestOrder.indexOf(b.getQuestId());
                if (ia < 0) ia = Integer.MAX_VALUE;
                if (ib < 0) ib = Integer.MAX_VALUE;
                return Integer.compare(ia, ib);
            });
        }
        layer3Widgets.addAll(newQuests);
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
        if (TownHubClientState.pendingHubData != null) {
            CompoundTag hub = TownHubClientState.pendingHubData;
            TownHubClientState.pendingHubData = null;
            parseHubData(hub);
            tryCreateMapWidget(hub);
        }
        if (TownHubClientState.pendingStockUpdate != null) {
            CompoundTag data = TownHubClientState.pendingStockUpdate;
            TownHubClientState.pendingStockUpdate = null;
            applyStockUpdate(data);
        }
        if (TownHubClientState.pendingBuildingList != null) {
            CompoundTag data = TownHubClientState.pendingBuildingList;
            TownHubClientState.pendingBuildingList = null;
            applyBuildingListUpdate(data);
        }
        if (!expandedViewOpen && TownHubClientState.pendingQuestUpdate != null) {
            CompoundTag data = TownHubClientState.pendingQuestUpdate;
            TownHubClientState.pendingQuestUpdate = null;
            applyQuestUpdate(data);
        }
        if (TownHubClientState.pendingEraUpdate != null) {
            CompoundTag data = TownHubClientState.pendingEraUpdate;
            TownHubClientState.pendingEraUpdate = null;
            applyEraUpdate(data);
        }
        if (TownHubClientState.pendingCitizenUpdate != null) {
            CompoundTag data = TownHubClientState.pendingCitizenUpdate;
            TownHubClientState.pendingCitizenUpdate = null;
            applyCitizenUpdate(data);
        }

        this.renderBackground(guiGraphics);

        // Remove closed layer 1 widgets, saving their last-known state
        layer1Widgets.removeIf(w -> {
            if (w.isClosed()) {
                if (w instanceof MapDraggableWidget) {
                    savedMapX = w.getX();
                    savedMapY = w.getY();
                    savedMapOpen = false;
                }
                if (w instanceof TownSummaryWidget) {
                    savedSummaryX = w.getX();
                    savedSummaryY = w.getY();
                    savedSummaryOpen = false;
                }
                if (w instanceof EraProgressDraggableWidget) {
                    savedEraX = w.getX();
                    savedEraY = w.getY();
                    savedEraOpen = false;
                    eraWidget = null;
                }
            }
            return w.isClosed();
        });
        mapClosed     = layer1Widgets.stream().noneMatch(w -> w instanceof MapDraggableWidget);
        summaryClosed = layer1Widgets.stream().noneMatch(w -> w instanceof TownSummaryWidget);
        eraClosed     = layer1Widgets.stream().noneMatch(w -> w instanceof EraProgressDraggableWidget);

        // Layer 1: left draggable widgets (Map, Summary, Era, Production popup)
        for (int i = 0; i < layer1Widgets.size(); i++) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, L1_Z_BASE + i * L1_Z_STEP);
            layer1Widgets.get(i).render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().popPose();
        }

        // Layer 2: right fixed panel -- single z-band always above Layer 1
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, L2_Z_BASE);
        if (activeTab == 1 || activeTab == 2) {
            // Skip inventory slot rendering for non-stock tabs
            renderBg(guiGraphics, partialTick, mouseX, mouseY);
        } else {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        renderReopenButtons(guiGraphics, mouseX, mouseY);
        renderTabs(guiGraphics, mouseX, mouseY);
        if (activeTab == 1) {
            renderConstructionTab(guiGraphics, mouseX, mouseY);
            renderWeightBar(guiGraphics, mouseX, mouseY);
        } else if (activeTab == 2) {
            renderUpgradeTab(guiGraphics, mouseX, mouseY);
        }
        guiGraphics.pose().popPose();

        // Layer 3: quest cards -- always above the right panel
        for (int i = 0; i < layer3Widgets.size(); i++) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, L3_Z_BASE + i * L3_Z_STEP);
            layer3Widgets.get(i).render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().popPose();
        }

        // Tooltips above everything
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, L3_Z_BASE + (layer3Widgets.size() + 1) * L3_Z_STEP);
        if (activeTab == 1) {
            renderConstructionTooltips(guiGraphics, mouseX, mouseY);
        } else if (activeTab == 2) {
            renderUpgradeTooltips(guiGraphics, mouseX, mouseY);
        } else {
            this.renderTooltip(guiGraphics, mouseX, mouseY);
        }

        guiGraphics.pose().popPose();

        // Expanded NBT view: fullscreen overlay above everything
        if (expandedViewOpen) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, L3_Z_BASE + (layer3Widgets.size() + 5) * L3_Z_STEP);
            renderExpandedView(guiGraphics, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }
    }

    private void applyStockUpdate(CompoundTag data) {
        stockSnapshot.clear();
        CompoundTag stockTag = data.getCompound("StockSnapshot");
        for (String key : stockTag.getAllKeys()) {
            stockSnapshot.put(key, stockTag.getInt(key));
        }
        if (cachedHubData != null) cachedHubData.put("StockSnapshot", stockTag);
        this.menu.rebuildFromStock(stockTag);
    }

    private void applyBuildingListUpdate(CompoundTag data) {
        CompoundTag mapData = data.getCompound("MapData");
        for (DraggableWidget w : layer1Widgets) {
            if (w instanceof MapDraggableWidget mdw) {
                mdw.updateMapData(mapData);
                break;
            }
        }
        if (cachedHubData != null) cachedHubData.put("MapData", mapData);
        constructionQueueClient.clear();
        data.getList("ConstructionQueue", Tag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag qt = (CompoundTag) raw;
            String type = qt.getString("Type");
            String defId = qt.getString("DefId");
            long worldPos = "upgrade".equals(type) ? qt.getLong("BuildingWorldPos") : 0L;
            constructionQueueClient.add(new ClientQueueEntry(type, defId, worldPos));
        });
        upgradeBuildingsList.clear();
        data.getList("UpgradeBuildings", Tag.TAG_COMPOUND).forEach(raw -> {
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

    private void applyQuestUpdate(CompoundTag data) {
        List<CompoundTag> questTagList = new ArrayList<>();
        data.getList("Quests", Tag.TAG_COMPOUND).forEach(t -> questTagList.add((CompoundTag) t));
        syncQuestWidgets(questTagList);
    }

    private void applyEraUpdate(CompoundTag data) {
        int prevEra = currentEra;
        currentEra     = data.getInt("CurrentEra");
        currentWeight  = data.getInt("CurrentWeight");
        maxWeight      = data.getInt("MaxWeight");
        eraTransitions = parseEraTransitions(data);
        if (eraWidget != null) eraWidget.updateData(currentEra, eraTransitions);
        if (lastKnownEra >= 0 && currentEra > prevEra) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                mc.level.playLocalSound(mc.player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f, false);
            }
        }
        lastKnownEra = currentEra;
        if (cachedHubData != null) {
            cachedHubData.putInt("CurrentEra", currentEra);
            cachedHubData.putInt("CurrentWeight", currentWeight);
            cachedHubData.putInt("MaxWeight", maxWeight);
        }
    }

    private void applyCitizenUpdate(CompoundTag data) {
        totalResidents  = data.getInt("TotalResidents");
        activeResidents = data.getInt("ActiveResidents");
        totalFoodDemand = data.getInt("TotalFoodDemand");
        if (cachedHubData != null) {
            cachedHubData.putInt("TotalResidents", totalResidents);
            cachedHubData.putInt("ActiveResidents", activeResidents);
            cachedHubData.putInt("TotalFoodDemand", totalFoodDemand);
            // Keep SummaryData in sync so the widget reflects correct values on next open
            CompoundTag summary = cachedHubData.getCompound("SummaryData");
            summary.putInt("TotalResidents", totalResidents);
            summary.putInt("ActiveResidents", activeResidents);
            summary.putInt("TotalFoodDemand", totalFoodDemand);
        }
        for (DraggableWidget w : layer1Widgets) {
            if (w instanceof TownSummaryWidget sw) {
                sw.updateCitizenData(totalResidents, activeResidents, totalFoodDemand);
                break;
            }
        }
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        int btnX = leftPos - 16;
        int[] tabYs = { topPos + 20, topPos + 46, topPos + 72 };
        int btnW = 14, btnH = 24;

        for (int i = 0; i < 3; i++) {
            int btnY = tabYs[i];
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, activeTab == i ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
        }

        drawChestIcon(g, btnX + 2, tabYs[0] + 8);
        drawHouseIcon(g, btnX + 2, tabYs[1] + 8);
        drawUpArrowIcon(g, btnX + 3, tabYs[2] + 8);
    }

    private void renderConstructionTab(GuiGraphics g, int mx, int my) {
        hoveredQueueSlot = -1;
        hoveredCatalogSlot = -1;

        // Zone C: single queue row at row 1 (row 0 = weight bar)
        for (int col = 0; col < QUEUE_COLS; col++) {
            int sx = leftPos + QUEUE_GRID_X + col * CELL;
            int sy = topPos + QUEUE_GRID_Y + CELL - 5;

            if (col < constructionQueueClient.size()) {
                ClientQueueEntry qe = constructionQueueClient.get(col);
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
                hoveredQueueSlot = col;
            }
        }

        // Zone B: building info panel
        renderConstructionInfoPanel(g, mx, my);

        // Zone A: catalog grid
        int visibleStart = catalogScrollOffset * AVAIL_COLS;
        int[] rowYOffsets = {AVAIL_GRID_Y_ROW0, AVAIL_GRID_Y_ROW0 + 18, AVAIL_GRID_Y_ROW0 + 36};
        for (int row = 0; row < CATALOG_ROWS; row++) {
            for (int col = 0; col < AVAIL_COLS; col++) {
                int catalogIdx = visibleStart + row * AVAIL_COLS + col;
                int sx = leftPos + QUEUE_GRID_X + col * CELL;
                int sy = topPos + rowYOffsets[row];

                if (catalogIdx < buildingCatalog.size()) {
                    BuildingEntry entry = buildingCatalog.get(catalogIdx);
                    boolean affordable = isAffordable(entry);
                    boolean selected = entry.id().equals(selectedCatalogBuildingId);
                    int color = affordable ? categoryColor(entry.category()) : dim(categoryColor(entry.category()));
                    g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, color);
                    if (selected) g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, 0x40FFFFFF);
                    renderItemIcon(g, entry.iconItem(), sx, sy);
                    if (entry.nextEra()) {
                        g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, 0x99111111);
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 200);
                        drawPadlockIcon(g, sx + 4, sy + 2);
                        g.pose().popPose();
                    } else if (!affordable) {
                        g.fill(sx, sy, sx + CELL - 2, sy + 1, 0x88CC0000);
                    }
                    if (!entry.nextEra() && !meetsPrerequisites(entry)) {
                        g.fill(sx, sy, sx + 3, sy + 3, 0xFF886600);
                    }
                    if (boostedBuildingIds.contains(entry.id())) {
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 300);
                        int bx = sx + CELL - 6;
                        int by = sy + CELL - 6;
                        int bc = 0xFFFFDD44;
                        g.fill(bx + 1, by,     bx + 2, by + 1, bc);
                        g.fill(bx,     by + 1, bx + 3, by + 2, bc);
                        g.fill(bx + 1, by + 2, bx + 2, by + 3, bc);
                        g.pose().popPose();
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

        // Scroll indicator
        int totalRows = (buildingCatalog.size() + AVAIL_COLS - 1) / AVAIL_COLS;
        if (totalRows > CATALOG_ROWS) {
            String scrollText = (catalogScrollOffset + 1) + "/" + (totalRows - CATALOG_ROWS + 1);
            g.drawString(font, scrollText, leftPos + imageWidth - 4 - font.width(scrollText),
                topPos + 201, 0xFFAAAAAA, false);
        }
    }

    private void renderConstructionInfoPanel(GuiGraphics g, int mx, int my) {
        int panelX = leftPos + QUEUE_GRID_X;
        int panelY = topPos + QUEUE_GRID_Y + CELL * 2;
        int panelW = AVAIL_COLS * CELL;                // 162
        int panelH = 82;
        int leftW  = 76;
        int rightX = panelX + leftW;
        int rightW = panelW - leftW;

        BuildingEntry sel = selectedCatalogBuildingId != null ? findCatalogEntry(selectedCatalogBuildingId) : null;

        if (sel != null) {
            // 3D NBT preview fills the left zone
            if (constructionPreview != null) {
                constructionPreview.render(g, mx, my, 0f);
            }

            // Right zone: 5-column x 4-row production grid
            int[] colXs = { leftPos + 80, leftPos + 98, leftPos + 116, leftPos + 134, leftPos + 152 };
            int[] rowYs = { topPos + 54,  topPos + 72,  topPos + 90,   topPos + 108 };
            List<ProductionCell> cells = sel.productionCells();
            int cellIdx = 0;
            outer:
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 5; col++) {
                    if (cellIdx >= cells.size()) break outer;
                    ProductionCell cell = cells.get(cellIdx++);
                    ItemStack stack = new ItemStack(cell.item(), cell.amount());
                    g.renderFakeItem(stack, colXs[col], rowYs[row]);
                    g.renderItemDecorations(font, stack, colXs[col], rowYs[row]);
                }
            }
        }

        // Construct button
        int btnW = 46;
        int btnX = leftPos + QUEUE_GRID_X + (AVAIL_COLS * CELL) - 2 - btnW;
        int btnY = topPos + 126;
        int btnH = 11;
        boolean canConstruct = sel != null && !sel.nextEra() && isAffordable(sel) && meetsPrerequisites(sel);
        boolean btnHover = canConstruct && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH;
        int btnColor = canConstruct ? (btnHover ? 0xFF55BB55 : 0xFF337733) : 0xFF444444;
        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnColor);
        String btnText = (sel != null && sel.nextEra()) ? "Next era" : "Build";
        g.drawString(font, btnText, btnX + (btnW - font.width(btnText)) / 2, btnY + 2,
            canConstruct ? 0xFFFFFFFF : 0xFF888888, false);

        // Expand button: bottom-right corner of the NBT preview zone
        if (sel != null && constructionPreview != null) {
            int expBtnX = leftPos + 64;
            int expBtnY = topPos + 110;
            boolean expHover = mx >= expBtnX && mx < expBtnX + 12 && my >= expBtnY && my < expBtnY + 12;
            g.fill(expBtnX, expBtnY, expBtnX + 12, expBtnY + 12, expHover ? 0xFF666666 : 0xFF333333);
            drawExpandIcon(g, expBtnX + 2, expBtnY + 2);
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
            lines.add(Component.literal("Shift + Right-click to remove").withStyle(s -> s.withColor(0x888888)));
            g.renderComponentTooltip(font, lines, mx, my);
        } else if (hoveredCatalogSlot >= 0 && hoveredCatalogSlot < buildingCatalog.size()) {
            BuildingEntry entry = buildingCatalog.get(hoveredCatalogSlot);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(formatId(entry.id())).withStyle(s -> s.withBold(true)));
            if (entry.nextEra()) {
                lines.add(Component.literal("Unlocks at next era").withStyle(s -> s.withColor(0xAAAAAA)));
                g.renderComponentTooltip(font, lines, mx, my);
                return;
            }
            for (CostEntry ce : entry.cost()) {
                int have = stockSnapshot.getOrDefault(ce.itemId(), 0);
                boolean ok = have >= ce.amount();
                int color = ok ? 0x55FF55 : 0xFF5555;
                String itemName = ce.itemId().contains(":")
                    ? ce.itemId().substring(ce.itemId().indexOf(':') + 1) : ce.itemId();
                lines.add(Component.literal(ce.amount() + "x " + formatId(itemName))
                    .withStyle(s -> s.withColor(color)));
            }
            if (entry.requiredResidents() > 0) {
                boolean met = activeResidents >= entry.requiredResidents();
                lines.add(Component.literal(activeResidents + "/" + entry.requiredResidents() + " active residents")
                    .withStyle(s -> s.withColor(met ? 0x55FF55 : 0xFF5555)));
            }
            for (ReqBuildingEntry req : entry.requiredBuildings()) {
                boolean met = req.have() >= req.required();
                lines.add(Component.literal(req.have() + "/" + req.required() + "x " + formatId(req.defId()))
                    .withStyle(s -> s.withColor(met ? 0x55FF55 : 0xFF5555)));
            }
            int wCost = BuildingDef.weightForCategory(entry.category());
            boolean weightOk = currentWeight + wCost <= maxWeight;
            lines.add(Component.literal(wCost + " weight")
                .withStyle(s -> s.withColor(weightOk ? 0x55FF55 : 0xFF5555)));
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }

    private void renderUpgradeTab(GuiGraphics g, int mx, int my) {
        hoveredUpgradeSlot = -1;
        numActiveBars = 0;
        unlockIconBounds.clear();
        unlockIconItemIds.clear();

        UpgradeBuildingEntry sel = getSelectedUpgradeEntry();
        if (sel != null) {
            ClientBuildingDefsRegistry.DefEntry defEntry = ClientBuildingDefsRegistry.get(sel.defId());
            int maxLevel = defEntry != null ? defEntry.upgrades().size() : 0;
            boolean atMax = maxLevel > 0 && sel.upgradeLevel() >= maxLevel;

            if (defEntry != null && maxLevel > 0) {
                boolean canAfford = !atMax && canAffordUpgrade(sel, defEntry);

                int btnW = 46;
                int btnX = leftPos + QUEUE_GRID_X + (AVAIL_COLS * CELL) - 2 - btnW;
                int btnY = topPos + 126;
                int btnH = 11;
                boolean btnHover = !atMax && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH;

                // Right zone: stat gauges (ghost fill shows next-level delta on button hover)
                renderUpgradeGaugeBars(g, sel, defEntry, btnHover && !atMax, canAfford);

                // Unlock row: items unlocked at the next upgrade level
                if (!atMax) renderUnlockRow(g, mx, my, sel, defEntry);

                // Upgrade button
                int btnColor = atMax ? 0xFF444444 : (canAfford ? (btnHover ? 0xFF55BB55 : 0xFF337733) : 0xFF444444);
                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnColor);
                String btnText = atMax ? "MAX" : "Upgrade";
                int btnTextColor = (atMax || !canAfford) ? 0xFF888888 : 0xFFFFFFFF;
                g.drawString(font, btnText, btnX + (btnW - font.width(btnText)) / 2, btnY + 2, btnTextColor, false);
            }
        }

        renderUpgradeBuildingGrid(g, mx, my);

        if (currentEra == 0) {
            g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0222222);
            int lockX = leftPos + imageWidth / 2 - 4;
            int lockY = topPos + imageHeight / 2 - 5;
            drawPadlockIcon(g, lockX, lockY);
        }
    }

    // Renders stat gauge bars spanning the full gauge zone (x=8, y=19, w=158, h=76).
    // Bars are only created for stats that have a non-zero total range across all upgrade levels.
    // Ghost fill extends each bar to show the next level delta when the upgrade button is hovered.
    private void renderUpgradeGaugeBars(GuiGraphics g, UpgradeBuildingEntry sel,
            ClientBuildingDefsRegistry.DefEntry defEntry, boolean showGhost, boolean canAfford) {
        int barX = leftPos + 12;
        int barW = 152;
        int barH = 3;
        int gaugeY = topPos + 22;
        int ghostColor = canAfford ? 0x6655BB55 : 0x66BB5555;

        int currentLevel = sel.upgradeLevel();

        float totalCadence = 0f, curCadence = 0f, ghostCadence = 0f;
        int totalAmount = 0, curAmount = 0, ghostAmount = 0;
        int totalCapacity = 0, curCapacity = 0, ghostCapacity = 0;
        int totalResidents = 0, curResidents = 0, ghostResidentsVal = 0;
        float totalFood = 0f, curFood = 0f, ghostFood = 0f;
        double totalStock = 0.0, curStock = 0.0, ghostStock = 0.0;

        for (int i = 0; i < defEntry.upgrades().size(); i++) {
            var lvl = defEntry.upgrades().get(i);
            totalCadence   += lvl.cadenceMultiplier();
            totalAmount    += lvl.amountAdd();
            totalCapacity  += lvl.capacityStacksAdd();
            totalResidents += lvl.residentsAdd();
            totalFood      += lvl.consumptionPerResidentAdd();
            totalStock     += lvl.productionBonusAdd();
            if (i < currentLevel) {
                curCadence   += lvl.cadenceMultiplier();
                curAmount    += lvl.amountAdd();
                curCapacity  += lvl.capacityStacksAdd();
                curResidents += lvl.residentsAdd();
                curFood      += lvl.consumptionPerResidentAdd();
                curStock     += lvl.productionBonusAdd();
            }
            if (showGhost && i == currentLevel) {
                ghostCadence      = lvl.cadenceMultiplier();
                ghostAmount       = lvl.amountAdd();
                ghostCapacity     = lvl.capacityStacksAdd();
                ghostResidentsVal = lvl.residentsAdd();
                ghostFood         = lvl.consumptionPerResidentAdd();
                ghostStock        = lvl.productionBonusAdd();
            }
        }

        if (totalCadence > 0.001f) {
            float fill = curCadence / totalCadence;
            float ghost = showGhost ? ghostCadence / totalCadence : 0f;
            g.drawString(font, "Speed", barX, gaugeY, 0xFFFF8800, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost,0xFFFF8800, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = (int)(curCadence * 100) + "% faster  (max " + (int)(totalCadence * 100) + "%)";
            numActiveBars++;
            gaugeY += barH + 6;
        }
        if (totalAmount > 0) {
            float fill = (float) curAmount / totalAmount;
            float ghost = showGhost ? (float) ghostAmount / totalAmount : 0f;
            g.drawString(font, "Output", barX, gaugeY, 0xFFFFFF00, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost,0xFFFFFF00, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = "+" + curAmount + " output  (max +" + totalAmount + ")";
            numActiveBars++;
            gaugeY += barH + 6;
        }
        if (totalCapacity > 0) {
            float fill = (float) curCapacity / totalCapacity;
            float ghost = showGhost ? (float) ghostCapacity / totalCapacity : 0f;
            g.drawString(font, "Capacity", barX, gaugeY, 0xFF4488FF, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost,0xFF4488FF, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = "+" + curCapacity + " stacks  (max +" + totalCapacity + ")";
            numActiveBars++;
            gaugeY += barH + 6;
        }
        if (totalResidents > 0) {
            float fill = (float) curResidents / totalResidents;
            float ghost = showGhost ? (float) ghostResidentsVal / totalResidents : 0f;
            g.drawString(font, "Residents", barX, gaugeY, 0xFFAA7744, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost,0xFFAA7744, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = "+" + curResidents + " residents  (max +" + totalResidents + ")";
            numActiveBars++;
            gaugeY += barH + 6;
        }
        if (totalFood > 0.001f) {
            float fill = curFood / totalFood;
            float ghost = showGhost ? ghostFood / totalFood : 0f;
            g.drawString(font, "Food", barX, gaugeY, 0xFFCC3333, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost,0xFFCC3333, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = String.format("+%.2f food/res  (max +%.2f)", curFood, totalFood);
            numActiveBars++;
            gaugeY += barH + 6;
        }
        if (totalStock > 0.001) {
            float fill = (float)(curStock / totalStock);
            float ghost = showGhost ? (float)(ghostStock / totalStock) : 0f;
            g.drawString(font, "Stock", barX, gaugeY, 0xFFFFCC00, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost,0xFFFFCC00, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = "+" + (int)(curStock * 100) + "% village stock  (max +" + (int)(totalStock * 100) + "%)";
            numActiveBars++;
        }
    }

    // Renders the unlock row at y=107: items from unlocks_display of the NEXT upgrade level.
    // Shows item icons with their production amount (looked up from the building catalog).
    // Skipped when the building is at max level or the next level has nothing to unlock.
    private void renderUnlockRow(GuiGraphics g, int mx, int my,
            UpgradeBuildingEntry sel, ClientBuildingDefsRegistry.DefEntry defEntry) {
        int nextLevel = sel.upgradeLevel();
        if (nextLevel >= defEntry.upgrades().size()) return;
        List<String> unlocks = defEntry.upgrades().get(nextLevel).unlockedDisplay();
        if (unlocks.isEmpty()) return;

        int rowX = leftPos + QUEUE_GRID_X;
        int labelY = topPos + 99;
        int rowY = labelY + font.lineHeight;

        g.drawString(font, "Unlocks:", rowX, labelY, 0xFFFFFFFF, false);

        BuildingEntry catalogEntry = findCatalogEntry(sel.defId());
        for (int i = 0; i < unlocks.size() && i < AVAIL_COLS; i++) {
            int sx = rowX + i * CELL;
            String itemId = unlocks.get(i);
            try {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
                if (item != Items.AIR) {
                    int amount = 0;
                    if (catalogEntry != null) {
                        for (ProductionCell pc : catalogEntry.productionCells()) {
                            if (pc.item() == item) { amount = pc.amount(); break; }
                        }
                    }
                    ItemStack stack = amount > 0 ? new ItemStack(item, amount) : new ItemStack(item);
                    g.renderFakeItem(stack, sx, rowY);
                    if (amount > 0) g.renderItemDecorations(font, stack, sx, rowY);
                    unlockIconBounds.add(new int[]{sx, rowY});
                    unlockIconItemIds.add(itemId);
                }
            } catch (Exception ignored) {}
        }
    }

    // Returns only the placed buildings that have at least one upgrade level defined.
    // Buildings without upgrades are excluded from the upgrade catalog per the spec.
    private List<UpgradeBuildingEntry> getUpgradeableBuildings() {
        List<UpgradeBuildingEntry> result = new ArrayList<>();
        for (UpgradeBuildingEntry e : upgradeBuildingsList) {
            ClientBuildingDefsRegistry.DefEntry def = ClientBuildingDefsRegistry.get(e.defId());
            if (def != null && !def.upgrades().isEmpty()) result.add(e);
        }
        return result;
    }

    private void renderStatBar(GuiGraphics g, int x, int y, int w, int h,
                                float fill, float ghost, int barColor, int ghostColor) {
        g.fill(x, y, x + w, y + h, 0xFF222222);
        int fillW = (int)(w * Math.min(1f, Math.max(0f, fill)));
        if (fillW > 0) g.fill(x, y, x + fillW, y + h, barColor);
        if (ghost > 0f) {
            int ghostW = (int)(w * Math.min(1f - fill, Math.max(0f, ghost)));
            if (ghostW > 0) g.fill(x + fillW, y, x + fillW + ghostW, y + h, ghostColor);
        }
    }

    private void renderUpgradeBuildingGrid(GuiGraphics g, int mx, int my) {
        List<UpgradeBuildingEntry> list = getUpgradeableBuildings();
        int[] rowYOffsets = { AVAIL_GRID_Y_ROW0, AVAIL_GRID_Y_ROW0 + 18, AVAIL_GRID_Y_ROW0 + 36 };
        int visibleStart = upgradeGridScrollOffset * AVAIL_COLS;

        for (int row = 0; row < AVAIL_ROWS; row++) {
            for (int col = 0; col < AVAIL_COLS; col++) {
                int idx = visibleStart + row * AVAIL_COLS + col;
                int sx = leftPos + QUEUE_GRID_X + col * CELL;
                int sy = topPos + rowYOffsets[row];

                if (idx < list.size()) {
                    UpgradeBuildingEntry entry = list.get(idx);
                    ClientBuildingDefsRegistry.DefEntry defEntry = ClientBuildingDefsRegistry.get(entry.defId());
                    boolean atMax = defEntry != null && entry.upgradeLevel() >= defEntry.upgrades().size();
                    boolean affordable = atMax || canAffordUpgrade(entry, defEntry);
                    int color = affordable ? categoryColor(entry.category()) : dim(categoryColor(entry.category()));
                    if (atMax) color = dim(color);
                    g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, color);
                    renderItemIcon(g, entry.iconItem(), sx, sy);
                    if (entry.upgradeLevel() > 0) {
                        String lvl = "+" + entry.upgradeLevel();
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 300);
                        int lvlColor = atMax ? 0xFF888844 : 0xFFFFFF55;
                        g.drawString(font, lvl, sx + CELL - 2 - font.width(lvl) - 1, sy + CELL - 10, lvlColor, true);
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

        int totalRows = (list.size() + AVAIL_COLS - 1) / AVAIL_COLS;
        if (totalRows > AVAIL_ROWS) {
            String scrollText = (upgradeGridScrollOffset + 1) + "/" + (totalRows - AVAIL_ROWS + 1);
            g.drawString(font, scrollText, leftPos + imageWidth - 4 - font.width(scrollText),
                topPos + 201, 0xFFAAAAAA, false);
        }
    }

    private void renderUpgradeTooltips(GuiGraphics g, int mx, int my) {
        if (currentEra == 0
                && mx >= leftPos && mx < leftPos + imageWidth
                && my >= topPos + 16 && my < topPos + imageHeight) {
            g.renderTooltip(font, Component.literal("Unlockable at Era 1"), mx, my);
            return;
        }

        // Per-bar hover tooltips (right zone, y=20 to y=93)
        for (int i = 0; i < numActiveBars; i++) {
            int labelY = barLabelYs[i];
            if (my >= labelY && my < labelY + 13 && mx >= leftPos + 12 && mx < leftPos + imageWidth - 8) {
                g.renderTooltip(font, Component.literal(barTooltips[i]).withStyle(s -> s.withColor(0xCCCCCC)), mx, my);
                return;
            }
        }

        for (int i = 0; i < unlockIconBounds.size(); i++) {
            int[] b = unlockIconBounds.get(i);
            if (mx >= b[0] && mx < b[0] + 16 && my >= b[1] && my < b[1] + 16) {
                try {
                    Item hovItem = BuiltInRegistries.ITEM.get(new ResourceLocation(unlockIconItemIds.get(i)));
                    if (hovItem != net.minecraft.world.item.Items.AIR) {
                        g.renderTooltip(font, Component.translatable(hovItem.getDescriptionId()), mx, my);
                    }
                } catch (Exception ignored) {}
                return;
            }
        }

        List<UpgradeBuildingEntry> list = getUpgradeableBuildings();
        if (hoveredUpgradeSlot < 0 || hoveredUpgradeSlot >= list.size()) return;
        UpgradeBuildingEntry entry = list.get(hoveredUpgradeSlot);
        ClientBuildingDefsRegistry.DefEntry defEntry = ClientBuildingDefsRegistry.get(entry.defId());
        int maxLevel = defEntry != null ? defEntry.upgrades().size() : 0;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(formatId(entry.defId()))
            .withStyle(s -> s.withBold(true)));

        if (defEntry != null && maxLevel > 0 && entry.upgradeLevel() < maxLevel) {
            List<ClientBuildingDefsRegistry.CostEntry> costs = defEntry.upgrades().get(entry.upgradeLevel()).upgradeCost();
            for (ClientBuildingDefsRegistry.CostEntry ce : costs) {
                int have = stockSnapshot.getOrDefault(ce.itemId(), 0);
                boolean ok = have >= ce.amount();
                String itemName = ce.itemId().contains(":")
                    ? ce.itemId().substring(ce.itemId().indexOf(':') + 1) : ce.itemId();
                lines.add(Component.literal(ce.amount() + "x " + formatId(itemName))
                    .withStyle(s -> s.withColor(ok ? 0x55FF55 : 0xFF5555)));
            }
            if (!canAffordUpgrade(entry, defEntry)) {
                lines.add(Component.literal("Not enough resources").withStyle(s -> s.withColor(0xFF5555)));
            }
        } else if (maxLevel > 0 && entry.upgradeLevel() >= maxLevel) {
            lines.add(Component.literal("Fully upgraded").withStyle(s -> s.withColor(0xFFFFDD44)));
        }
        g.renderComponentTooltip(font, lines, mx, my);
    }

    private void handleUpgradeTabClick(double mX, double mY, int button) {
        if (button != 0) return;
        if (currentEra == 0) return;

        UpgradeBuildingEntry sel = getSelectedUpgradeEntry();
        if (sel != null) {
            ClientBuildingDefsRegistry.DefEntry defEntry = ClientBuildingDefsRegistry.get(sel.defId());
            if (defEntry != null && sel.upgradeLevel() < defEntry.upgrades().size()) {
                int btnX = leftPos + QUEUE_GRID_X + (AVAIL_COLS * CELL) - 2 - 46;
                int btnY = topPos + 126;
                if (mX >= btnX && mX < btnX + 46 && mY >= btnY && mY < btnY + 11) {
                    if (canAffordUpgrade(sel, defEntry)) {
                        NetworkHelper.sendUpgradeBuildingPacket.accept(anchorPos, sel.worldPosLong());
                    }
                    return;
                }
            }
        }

        List<UpgradeBuildingEntry> list = getUpgradeableBuildings();
        int[] rowYOffsets = { AVAIL_GRID_Y_ROW0, AVAIL_GRID_Y_ROW0 + 18, AVAIL_GRID_Y_ROW0 + 36 };
        int visibleStart = upgradeGridScrollOffset * AVAIL_COLS;
        for (int row = 0; row < AVAIL_ROWS; row++) {
            for (int col = 0; col < AVAIL_COLS; col++) {
                int idx = visibleStart + row * AVAIL_COLS + col;
                int sx = leftPos + QUEUE_GRID_X + col * CELL;
                int sy = topPos + rowYOffsets[row];
                if (mX >= sx && mX < sx + CELL && mY >= sy && mY < sy + CELL) {
                    if (idx < list.size()) {
                        selectedUpgradeBuildingPos = list.get(idx).worldPosLong();
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
        for (DraggableWidget w : layer1Widgets) {
            if (w instanceof MapDraggableWidget) {
                savedMapX = w.getX();
                savedMapY = w.getY();
                savedWidgetOrder.add(w.getClass().getSimpleName());
            } else if (w instanceof TownSummaryWidget) {
                savedSummaryX = w.getX();
                savedSummaryY = w.getY();
                savedWidgetOrder.add(w.getClass().getSimpleName());
            } else if (w instanceof EraProgressDraggableWidget) {
                savedEraX = w.getX();
                savedEraY = w.getY();
                savedWidgetOrder.add(w.getClass().getSimpleName());
            }
        }
        savedQuestOrder.clear();
        for (DraggableWidget w : layer3Widgets) {
            if (w instanceof QuestDraggableWidget qw) {
                savedQuestOrder.add(qw.getQuestId());
            }
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (expandedViewOpen && keyCode == 256) { // GLFW_KEY_ESCAPE
            expandedViewOpen = false;
            expandedWidget = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mX, double mY, int button) {
        // Expanded view intercepts all clicks
        if (expandedViewOpen) {
            if (button == 0) handleExpandedViewClick(mX, mY);
            return true;
        }

        // Reopen buttons (anchored at bottom-left, stacking upward)
        if (button == 0 && cachedHubData != null) {
            int btnX = leftPos - 18;
            int btnY = topPos + imageHeight - 16;
            if (mapClosed) {
                if (mX >= btnX && mX < btnX + 14 && mY >= btnY && mY < btnY + 14) {
                    int freeZoneW = this.leftPos;
                    int startX = (savedMapX >= 0) ? savedMapX : centerX(freeZoneW, mapInitialHeight);
                    int startY = (savedMapY >= 0) ? savedMapY : centerY(this.height, mapInitialHeight);
                    MapDraggableWidget reopenedMap = new MapDraggableWidget(startX, startY, Math.min(160, freeZoneW - 20), mapInitialHeight, freeZoneW, this.height, cachedHubData.getCompound("MapData"));
                    reopenedMap.setOnBuildingClicked((pos, defId) -> {
                        activeTab = 1;
                        BuildingEntry catalogEntry = findCatalogEntry(defId);
                        if (catalogEntry != null) {
                            selectedCatalogBuildingId = defId;
                            updateConstructionPreview(catalogEntry);
                        }
                    });
                    reopenedMap.setOnBuildingRightClicked(pos -> { activeTab = 2; selectedUpgradeBuildingPos = pos; });
                    layer1Widgets.add(0, reopenedMap);
                    savedMapOpen = true;
                    mapClosed = false;
                    return true;
                }
                btnY -= 18;
            }
            if (summaryClosed && cachedHubData.contains("SummaryData")) {
                if (mX >= btnX && mX < btnX + 14 && mY >= btnY && mY < btnY + 14) {
                    int freeZoneW = this.leftPos;
                    int summaryH = DraggableWidget.TITLE_BAR_H + TownSummaryWidget.VISIBLE_H;
                    int startX = (savedSummaryX >= 0) ? savedSummaryX : centerX(freeZoneW, TownSummaryWidget.WIDGET_W);
                    int startY = (savedSummaryY >= 0) ? savedSummaryY : centerY(this.height, summaryH);
                    layer1Widgets.add(0, new TownSummaryWidget(cachedHubData.getCompound("MapData"), cachedHubData.getCompound("SummaryData"), startX, startY, freeZoneW, this.height));
                    savedSummaryOpen = true;
                    summaryClosed = false;
                    return true;
                }
                btnY -= 18;
            }
            if (eraClosed) {
                if (mX >= btnX && mX < btnX + 14 && mY >= btnY && mY < btnY + 14) {
                    int freeZoneW = this.leftPos;
                    int startX = (savedEraX >= 0) ? savedEraX : centerX(freeZoneW, EraProgressDraggableWidget.WIDGET_W);
                    int startY = (savedEraY >= 0) ? savedEraY : centerY(this.height, DraggableWidget.TITLE_BAR_H + 80);
                    eraWidget = new EraProgressDraggableWidget(startX, startY, freeZoneW, this.height,
                        currentEra, eraTransitions,
                        pathId -> NetworkHelper.sendAdvanceEraPacket.accept(anchorPos, pathId));
                    layer1Widgets.add(0, eraWidget);
                    savedEraOpen = true;
                    eraClosed = false;
                    return true;
                }
            }
        }

        // Tab switching (vertical left-side tabs)
        int tabBtnX = leftPos - 16;
        int[] tabBtnYs = { topPos + 20, topPos + 46, topPos + 72 };
        if (mX >= tabBtnX && mX < tabBtnX + 14) {
            for (int i = 0; i < 3; i++) {
                if (mY >= tabBtnYs[i] && mY < tabBtnYs[i] + 24) {
                    if (i == 0 && activeTab != 0) {
                        catalogScrollOffset = 0;
                        constructionPreview = null;
                        NetworkHelper.sendRequestStockPacket.accept(anchorPos);
                    } else if (i == 2 && activeTab != 2) {
                        constructionPreview = null;
                    }
                    activeTab = i;
                    return true;
                }
            }
        }

        // Layer 3: quest cards cover the full screen
        for (int i = layer3Widgets.size() - 1; i >= 0; i--) {
            DraggableWidget w = layer3Widgets.get(i);
            if (w.mouseClicked(mX, mY, button)) {
                if (i != layer3Widgets.size() - 1) { layer3Widgets.remove(i); layer3Widgets.add(w); }
                return true;
            }
        }

        // Layer 1: only route clicks inside the left free zone
        if (mX < leftPos) {
            for (int i = layer1Widgets.size() - 1; i >= 0; i--) {
                DraggableWidget w = layer1Widgets.get(i);
                if (w.mouseClicked(mX, mY, button)) {
                    if (i != layer1Widgets.size() - 1) {
                        layer1Widgets.remove(i);
                        layer1Widgets.add(w);
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
            // Expand button: opens fullscreen NBT view for selected building
            if (button == 0 && selectedCatalogBuildingId != null && constructionPreview != null) {
                int expBtnX = leftPos + 64;
                int expBtnY = topPos + 110;
                if (mX >= expBtnX && mX < expBtnX + 12 && mY >= expBtnY && mY < expBtnY + 12) {
                    openExpandedView();
                    return true;
                }
            }
            // Construction preview: forward clicks so drag-to-rotate works
            if (constructionPreview != null && constructionPreview.mouseClicked(mX, mY, button)) return true;
            // Catalog slot: left-click selects and populates Zone B
            if (button == 0 && hoveredCatalogSlot >= 0 && hoveredCatalogSlot < buildingCatalog.size()) {
                BuildingEntry catalogEntry = buildingCatalog.get(hoveredCatalogSlot);
                selectedCatalogBuildingId = catalogEntry.id();
                updateConstructionPreview(catalogEntry);
                return true;
            }
            // Construct button: left-click queues the selected building
            if (button == 0) {
                int btnW = 46;
                int btnX = leftPos + QUEUE_GRID_X + (AVAIL_COLS * CELL) - 2 - btnW;
                int btnY = topPos + 126;
                if (mX >= btnX && mX < btnX + btnW && mY >= btnY && mY < btnY + 11) {
                    BuildingEntry sel = selectedCatalogBuildingId != null ? findCatalogEntry(selectedCatalogBuildingId) : null;
                    if (sel != null && isAffordable(sel) && meetsPrerequisites(sel)) {
                        NetworkHelper.sendQueueBuildingPacket.accept(anchorPos, sel.id());
                    }
                    return true;
                }
            }
            return true;
        }

        // Send button on Stock tab: click the arrow already drawn in the texture
        if (activeTab == 0 && button == 0) {
            int sendBtnX = leftPos + 152;
            int sendBtnY = topPos + 108;
            if (mX >= sendBtnX && mX < sendBtnX + 18 && mY >= sendBtnY && mY < sendBtnY + 18) {
                NetworkHelper.sendDepositPacket.accept(anchorPos);
                return true;
            }
        }

        return super.mouseClicked(mX, mY, button);
    }

    @Override
    public boolean mouseDragged(double mX, double mY, int button, double dX, double dY) {
        if (expandedViewOpen && expandedWidget != null) {
            expandedWidget.mouseDragged(mX, mY, button, dX, dY);
            return true;
        }
        // Route exclusively to the widget that owns the current drag (layer 3 has priority)
        for (DraggableWidget w : layer3Widgets) {
            if (w.isDragging()) return w.mouseDragged(mX, mY, button, dX, dY);
        }
        for (DraggableWidget w : layer1Widgets) {
            if (w.isDragging()) return w.mouseDragged(mX, mY, button, dX, dY);
        }
        // No drag active: route in z-order (top first)
        for (int i = layer3Widgets.size() - 1; i >= 0; i--) {
            if (layer3Widgets.get(i).mouseDragged(mX, mY, button, dX, dY)) return true;
        }
        for (int i = layer1Widgets.size() - 1; i >= 0; i--) {
            if (layer1Widgets.get(i).mouseDragged(mX, mY, button, dX, dY)) return true;
        }
        if (activeTab == 1 && constructionPreview != null && constructionPreview.mouseDragged(mX, mY, button, dX, dY)) return true;
        if (activeTab == 1 || activeTab == 2) return true;
        return super.mouseDragged(mX, mY, button, dX, dY);
    }

    @Override
    public boolean mouseReleased(double mX, double mY, int button) {
        if (expandedViewOpen && expandedWidget != null) {
            expandedWidget.mouseReleased(mX, mY, button);
            return true;
        }
        // Route release to the dragging widget first (layer 3 has priority)
        for (DraggableWidget w : layer3Widgets) {
            if (w.isDragging()) return w.mouseReleased(mX, mY, button);
        }
        for (DraggableWidget w : layer1Widgets) {
            if (w.isDragging()) return w.mouseReleased(mX, mY, button);
        }
        for (DraggableWidget w : layer3Widgets) {
            if (w.mouseReleased(mX, mY, button)) return true;
        }
        for (DraggableWidget w : layer1Widgets) {
            if (w.mouseReleased(mX, mY, button)) return true;
        }
        if (activeTab == 1 && constructionPreview != null && constructionPreview.mouseReleased(mX, mY, button)) return true;
        if (activeTab == 1 || activeTab == 2) return true;
        return super.mouseReleased(mX, mY, button);
    }

    @Override
    public boolean mouseScrolled(double mX, double mY, double delta) {
        if (expandedViewOpen && expandedWidget != null) {
            expandedWidget.mouseScrolled(mX, mY, delta);
            return true;
        }
        // Layer 3 (quests) has scroll priority, then layer 1
        for (int i = layer3Widgets.size() - 1; i >= 0; i--) {
            if (layer3Widgets.get(i).mouseScrolled(mX, mY, delta)) return true;
        }
        for (int i = layer1Widgets.size() - 1; i >= 0; i--) {
            if (layer1Widgets.get(i).mouseScrolled(mX, mY, delta)) return true;
        }
        if (activeTab == 1) {
            if (constructionPreview != null && constructionPreview.mouseScrolled(mX, mY, delta)) return true;
            int totalRows = (buildingCatalog.size() + AVAIL_COLS - 1) / AVAIL_COLS;
            int maxOffset = Math.max(0, totalRows - CATALOG_ROWS);
            catalogScrollOffset = Math.max(0, Math.min(maxOffset,
                catalogScrollOffset - (int) Math.signum(delta)));
            return true;
        }
        if (activeTab == 2) {
            int totalRows = (getUpgradeableBuildings().size() + AVAIL_COLS - 1) / AVAIL_COLS;
            int maxOffset = Math.max(0, totalRows - AVAIL_ROWS);
            upgradeGridScrollOffset = Math.max(0, Math.min(maxOffset,
                upgradeGridScrollOffset - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mX, mY, delta);
    }

    private static List<EraProgressDraggableWidget.EraPathOption> parseEraTransitions(CompoundTag hub) {
        List<EraProgressDraggableWidget.EraPathOption> result = new ArrayList<>();
        int cw = hub.getInt("CurrentWeight");
        int mw = hub.getInt("MaxWeight");
        hub.getList("EraTransitions", Tag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag tt = (CompoundTag) raw;
            String id = tt.getString("Id");
            String orientationLabel = tt.getString("OrientationLabel");
            String iconItem = tt.getString("IconItem");
            boolean prereqsMet = tt.getBoolean("PrereqsMet");
            int reqWeight = tt.getInt("RequiredWeight");
            boolean weightMet = tt.getBoolean("WeightMet");
            List<EraProgressDraggableWidget.CostRow> costRows = new ArrayList<>();
            tt.getList("ResourceCost", Tag.TAG_COMPOUND).forEach(cr -> {
                CompoundTag c = (CompoundTag) cr;
                costRows.add(new EraProgressDraggableWidget.CostRow(
                    c.getString("Item"), c.getInt("Amount"), c.getInt("Have")));
            });
            int reqRes = tt.getInt("RequiredResidents");
            int activeRes = tt.getInt("ActiveResidents");
            boolean resMet = tt.getBoolean("ResidentsMet");
            List<EraProgressDraggableWidget.ReqBuildRow> reqBuilds = new ArrayList<>();
            tt.getList("RequiredBuildings", Tag.TAG_COMPOUND).forEach(rb -> {
                CompoundTag r = (CompoundTag) rb;
                reqBuilds.add(new EraProgressDraggableWidget.ReqBuildRow(
                    r.getString("DefId"), r.getInt("Count"), r.getInt("Have")));
            });
            List<EraProgressDraggableWidget.UnlockEntry> unlocked = new ArrayList<>();
            tt.getList("UnlockedBuildings", Tag.TAG_COMPOUND).forEach(u -> {
                CompoundTag ut = (CompoundTag) u;
                List<EraProgressDraggableWidget.SimpleCost> cost = new ArrayList<>();
                ut.getList("Cost", Tag.TAG_COMPOUND).forEach(cr -> {
                    CompoundTag ct = (CompoundTag) cr;
                    cost.add(new EraProgressDraggableWidget.SimpleCost(ct.getString("Item"), ct.getInt("Amount")));
                });
                List<EraProgressDraggableWidget.SimpleReq> unlockReqBuilds = new ArrayList<>();
                ut.getList("RequiredBuildings", Tag.TAG_COMPOUND).forEach(rb -> {
                    CompoundTag rt = (CompoundTag) rb;
                    unlockReqBuilds.add(new EraProgressDraggableWidget.SimpleReq(rt.getString("DefId"), rt.getInt("Count")));
                });
                unlocked.add(new EraProgressDraggableWidget.UnlockEntry(
                    ut.getString("DefId"), ut.getString("IconItem"), ut.getString("Category"),
                    cost, ut.getInt("RequiredResidents"), unlockReqBuilds, ut.getBoolean("HasProduction")));
            });
            result.add(new EraProgressDraggableWidget.EraPathOption(
                id, orientationLabel, iconItem, prereqsMet, reqWeight, cw, mw, weightMet,
                costRows, reqRes, activeRes, resMet, reqBuilds, unlocked));
        });
        return result;
    }

    private void updateConstructionPreview(BuildingEntry entry) {
        if (constructionPreview == null) {
            constructionPreview = new NbtPreviewWidget(leftPos + 8, topPos + 44, 76, 82);
        }
        if (entry == null) {
            constructionPreview = null;
            return;
        }
        constructionPreview.setStructure(entry.nbtPath());
        constructionPreview.setLocked(entry.nextEra() || !entry.hasBuilt());
    }

    private BuildingEntry findCatalogEntry(String defId) {
        for (BuildingEntry e : buildingCatalog) {
            if (e.id().equals(defId)) return e;
        }
        return null;
    }

    private boolean meetsPrerequisites(BuildingEntry entry) {
        if (entry.requiredResidents() > 0 && activeResidents < entry.requiredResidents()) return false;
        for (ReqBuildingEntry req : entry.requiredBuildings()) {
            if (req.have() < req.required()) return false;
        }
        return true;
    }

    private boolean isAffordable(BuildingEntry entry) {
        for (CostEntry ce : entry.cost()) {
            if (stockSnapshot.getOrDefault(ce.itemId(), 0) < ce.amount()) return false;
        }
        return currentWeight + BuildingDef.weightForCategory(entry.category()) <= maxWeight;
    }

    private static int categoryColor(String category) {
        return switch (category) {
            case "town_center" -> COLOR_TOWN_CENTER;
            case "jobs"        -> COLOR_JOBS;
            case "gardens"     -> COLOR_GARDENS;
            case "naturals"     -> COLOR_NATURALS;
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

    // Weight bar rendered inside the construction tab, occupying the first chest row.
    // Shows current era, weight fill, and a ghost preview when hovering a catalog slot.
    private void renderWeightBar(GuiGraphics g, int mx, int my) {
        int barX = leftPos + QUEUE_GRID_X - 1;
        int barH = 9;
        int barY = topPos + QUEUE_GRID_Y + (CELL - 7) / 2 - 5;
        int barW = QUEUE_COLS * CELL;

        g.fill(barX, barY, barX + barW, barY + barH, 0xFF111111);

        float fill = maxWeight > 0 ? Math.min(1f, (float) currentWeight / maxWeight) : 0f;
        int fillPx = (int)(barW * fill);
        int fillColor = (currentWeight >= maxWeight) ? 0xFF884400 : 0xFF335533;
        if (fillPx > 0) g.fill(barX, barY, barX + fillPx, barY + barH, fillColor);

        // Ghost preview when hovering a catalog slot (Build tab only)
        if (activeTab == 1 && hoveredCatalogSlot >= 0 && hoveredCatalogSlot < buildingCatalog.size()) {
            BuildingEntry hov = buildingCatalog.get(hoveredCatalogSlot);
            int weightDelta = BuildingDef.weightForCategory(hov.category());
            if (weightDelta > 0 && maxWeight > 0) {
                float ghostFrac = (float) weightDelta / maxWeight;
                int ghostPx = (int)(barW * Math.min(1f - fill, ghostFrac));
                if (ghostPx > 0) {
                    // Transparent stripe so the dark background shows through, distinguishing ghost from solid fill
                    int ghostColor = isAffordable(hov) ? 0x2255BB55 : 0x22BB5555;
                    g.fill(barX + fillPx, barY, barX + fillPx + ghostPx, barY + barH, ghostColor);
                }
            }
        }

        String label = "ERA " + currentEra + "  " + currentWeight + "/" + maxWeight;
        g.drawString(font, label, barX + 3, barY + 1, 0xFFCCCCCC, false);
    }

    private void renderReopenButtons(GuiGraphics g, int mx, int my) {
        if (cachedHubData == null) return;
        int btnX = leftPos - 18;
        int btnY = topPos + imageHeight - 16;
        if (mapClosed) {
            boolean hover = mx >= btnX && mx < btnX + 14 && my >= btnY && my < btnY + 14;
            g.fill(btnX, btnY, btnX + 14, btnY + 14, hover ? 0xFF555555 : 0xFF333333);
            drawHouseIcon(g, btnX + 2, btnY + 3);
            btnY -= 18;
        }
        if (summaryClosed && cachedHubData.contains("SummaryData")) {
            boolean hover = mx >= btnX && mx < btnX + 14 && my >= btnY && my < btnY + 14;
            g.fill(btnX, btnY, btnX + 14, btnY + 14, hover ? 0xFF555555 : 0xFF333333);
            drawInfoIcon(g, btnX + 2, btnY + 2);
            btnY -= 18;
        }
        if (eraClosed) {
            boolean hover = mx >= btnX && mx < btnX + 14 && my >= btnY && my < btnY + 14;
            g.fill(btnX, btnY, btnX + 14, btnY + 14, hover ? 0xFF555555 : 0xFF333333);
            drawEraIcon(g, btnX + 2, btnY + 2);
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

    private static void drawEraIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFFFDD44;
        g.fill(bx + 4, by,     bx + 6, by + 3, c);
        g.fill(bx + 4, by + 7, bx + 6, by + 10, c);
        g.fill(bx,     by + 4, bx + 3, by + 6, c);
        g.fill(bx + 7, by + 4, bx + 10, by + 6, c);
        g.fill(bx + 3, by + 3, bx + 7, by + 7, c);
    }

    // Pixel-art chest icon (10x9 px)
    private static void drawChestIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFFFFFFF;
        g.fill(bx,     by,     bx + 10, by + 1,  c); // lid top
        g.fill(bx,     by + 1, bx + 1,  by + 4,  c); // lid left
        g.fill(bx + 9, by + 1, bx + 10, by + 4,  c); // lid right
        g.fill(bx + 1, by + 3, bx + 9,  by + 4,  c); // lid bottom
        g.fill(bx + 4, by + 2, bx + 6,  by + 3,  c); // lid latch
        g.fill(bx,     by + 4, bx + 10, by + 5,  c); // body top
        g.fill(bx,     by + 5, bx + 1,  by + 9,  c); // body left
        g.fill(bx + 9, by + 5, bx + 10, by + 9,  c); // body right
        g.fill(bx,     by + 8, bx + 10, by + 9,  c); // body bottom
        g.fill(bx + 4, by + 6, bx + 6,  by + 7,  c); // body latch
    }

    // Pixel-art up-arrow icon (8x8 px)
    private static void drawUpArrowIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFFFFFFF;
        g.fill(bx + 3, by,     bx + 5, by + 1, c); // tip
        g.fill(bx + 2, by + 1, bx + 6, by + 2, c); // row 1
        g.fill(bx + 1, by + 2, bx + 7, by + 3, c); // row 2
        g.fill(bx,     by + 3, bx + 8, by + 4, c); // arrowhead base
        g.fill(bx + 3, by + 4, bx + 5, by + 8, c); // shaft
    }

    private void openExpandedView() {
        BuildingEntry sel = selectedCatalogBuildingId != null ? findCatalogEntry(selectedCatalogBuildingId) : null;
        if (sel == null) return;
        expandedPanelSize = Math.min(this.width, this.height) - 60;
        expandedPanelX = (this.width - expandedPanelSize) / 2;
        expandedPanelY = (this.height - expandedPanelSize) / 2 - 15;
        expandedWidget = new NbtPreviewWidget(expandedPanelX, expandedPanelY, expandedPanelSize, expandedPanelSize);
        expandedWidget.setScale(expandedPanelSize / 10.0f);
        int maxUnlocked = getMaxUnlockedForSelected(sel);
        int totalLevels = 1 + sel.nbtLevels().size();
        expandedViewLevel = sel.hasBuilt() ? Math.min(maxUnlocked, totalLevels - 1) : 0;
        applyExpandedLevel(sel);
        expandedViewOpen = true;
    }

    private int getMaxUnlockedForSelected(BuildingEntry sel) {
        if (!sel.hasBuilt()) return 0;
        int max = 0;
        for (UpgradeBuildingEntry u : upgradeBuildingsList) {
            if (u.defId().equals(sel.id())) max = Math.max(max, u.upgradeLevel());
        }
        return max;
    }

    private void applyExpandedLevel(BuildingEntry sel) {
        if (expandedWidget == null) return;
        int maxUnlocked = getMaxUnlockedForSelected(sel);
        boolean locked = !sel.hasBuilt() || expandedViewLevel > maxUnlocked;
        String nbt = (expandedViewLevel == 0 || sel.nbtLevels().isEmpty())
            ? sel.nbtPath()
            : sel.nbtLevels().get(Math.min(expandedViewLevel - 1, sel.nbtLevels().size() - 1));
        expandedWidget.setStructure(nbt);
        expandedWidget.setLocked(locked);
    }

    private void handleExpandedViewClick(double mX, double mY) {
        BuildingEntry sel = selectedCatalogBuildingId != null ? findCatalogEntry(selectedCatalogBuildingId) : null;
        if (sel == null) return;
        int totalLevels = 1 + sel.nbtLevels().size();
        if (totalLevels > 1) {
            int navY = expandedPanelY + expandedPanelSize + 12;
            int cx = this.width / 2;
            if (mX >= cx - 44 && mX < cx - 28 && mY >= navY && mY < navY + 16 && expandedViewLevel > 0) {
                expandedViewLevel--;
                applyExpandedLevel(sel);
                return;
            }
            if (mX >= cx + 28 && mX < cx + 44 && mY >= navY && mY < navY + 16 && expandedViewLevel < totalLevels - 1) {
                expandedViewLevel++;
                applyExpandedLevel(sel);
                return;
            }
        }
        if (expandedWidget != null) expandedWidget.mouseClicked(mX, mY, 0);
    }

    private void renderExpandedView(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, this.height, 0xC0000000);
        if (expandedWidget != null) expandedWidget.render(g, mx, my, 0f);

        BuildingEntry sel = selectedCatalogBuildingId != null ? findCatalogEntry(selectedCatalogBuildingId) : null;
        if (sel == null) return;

        int totalLevels = 1 + sel.nbtLevels().size();
        if (totalLevels > 1) {
            int navY = expandedPanelY + expandedPanelSize + 12;
            int cx = this.width / 2;

            boolean leftEnabled = expandedViewLevel > 0;
            boolean leftHover = leftEnabled && mx >= cx - 44 && mx < cx - 28 && my >= navY && my < navY + 16;
            g.fill(cx - 44, navY, cx - 28, navY + 16, leftHover ? 0xFF666666 : (leftEnabled ? 0xFF444444 : 0xFF222222));
            if (leftEnabled) drawLeftArrowIcon(g, cx - 40, navY + 4);

            String levelText = "Level " + (expandedViewLevel + 1) + " / " + totalLevels;
            g.drawCenteredString(font, levelText, cx, navY + 4, 0xFFCCCCCC);

            boolean rightEnabled = expandedViewLevel < totalLevels - 1;
            boolean rightHover = rightEnabled && mx >= cx + 28 && mx < cx + 44 && my >= navY && my < navY + 16;
            g.fill(cx + 28, navY, cx + 44, navY + 16, rightHover ? 0xFF666666 : (rightEnabled ? 0xFF444444 : 0xFF222222));
            if (rightEnabled) drawRightArrowIcon(g, cx + 32, navY + 4);
        }

        g.drawCenteredString(font, "ESC to close", this.width / 2,
            expandedPanelY + expandedPanelSize + (totalLevels > 1 ? 34 : 14), 0xFF666666);
    }

    // Pixel-art expand icon (8x8 px) -- corner brackets pointing outward
    private static void drawExpandIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFCCCCCC;
        g.fill(bx,     by,     bx + 3, by + 1, c);
        g.fill(bx,     by,     bx + 1, by + 3, c);
        g.fill(bx + 5, by,     bx + 8, by + 1, c);
        g.fill(bx + 7, by,     bx + 8, by + 3, c);
        g.fill(bx,     by + 7, bx + 3, by + 8, c);
        g.fill(bx,     by + 5, bx + 1, by + 8, c);
        g.fill(bx + 5, by + 7, bx + 8, by + 8, c);
        g.fill(bx + 7, by + 5, bx + 8, by + 8, c);
    }

    // Pixel-art left-arrow icon (8x7 px)
    private static void drawLeftArrowIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFCCCCCC;
        g.fill(bx + 5, by,     bx + 6, by + 1, c);
        g.fill(bx + 4, by + 1, bx + 6, by + 2, c);
        g.fill(bx + 3, by + 2, bx + 6, by + 3, c);
        g.fill(bx + 2, by + 3, bx + 6, by + 4, c);
        g.fill(bx + 3, by + 4, bx + 6, by + 5, c);
        g.fill(bx + 4, by + 5, bx + 6, by + 6, c);
        g.fill(bx + 5, by + 6, bx + 6, by + 7, c);
    }

    // Pixel-art right-arrow icon (8x7 px)
    private static void drawRightArrowIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFCCCCCC;
        g.fill(bx + 2, by,     bx + 3, by + 1, c);
        g.fill(bx + 2, by + 1, bx + 4, by + 2, c);
        g.fill(bx + 2, by + 2, bx + 5, by + 3, c);
        g.fill(bx + 2, by + 3, bx + 6, by + 4, c);
        g.fill(bx + 2, by + 4, bx + 5, by + 5, c);
        g.fill(bx + 2, by + 5, bx + 4, by + 6, c);
        g.fill(bx + 2, by + 6, bx + 3, by + 7, c);
    }

    private static void drawPadlockIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFCCCCCC;
        int k = 0xFF111111;
        g.fill(bx + 2, by,     bx + 6, by + 1, c);
        g.fill(bx + 1, by + 1, bx + 2, by + 4, c);
        g.fill(bx + 6, by + 1, bx + 7, by + 4, c);
        g.fill(bx,     by + 4, bx + 8, by + 10, c);
        g.fill(bx + 3, by + 5, bx + 5, by + 7, k);
        g.fill(bx + 3, by + 7, bx + 5, by + 9, k);
    }
}
