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
import org.dawnoftime.onceuponatown.client.VillageHubClientState;
import org.dawnoftime.onceuponatown.client.gui.tooltip.BuildingProductionTooltip;
import org.dawnoftime.onceuponatown.client.gui.widgets.BuildingProductionDraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.DraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.EraProgressDraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.MapDraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.QuestDraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.VillageSummaryDraggableWidget;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.screen.VillageChestMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final int COLOR_NATURAL     = 0xFF666666;
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
    private static final int AVAIL_ROWS        = 4;   // 3 player inv rows + 1 hotbar row
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
    private BuildingProductionDraggableWidget productionPopup = null;
    private static int lastProductionPopupX = -1;
    private static int lastProductionPopupY = -1;
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

    private final List<UpgradeBuildingEntry> upgradeBuildingsList = new ArrayList<>();
    private long selectedUpgradeBuildingPos = -1L;
    private int upgradeGridScrollOffset = 0;
    private int hoveredUpgradeSlot = -1;

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
                                 int requiredResidents,
                                 List<ReqBuildingEntry> requiredBuildings,
                                 double productionBonus,
                                 float baseConsumption,
                                 float maxConsumption,
                                 int maxResidents) {}
    private record CostEntry(String itemId, int amount) {}
    private record ReqBuildingEntry(String defId, int required, int have) {}
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

            buildingCatalog.add(new BuildingEntry(id, category, iconItem, cost, productionRows,
                requiredResidents, requiredBuildings,
                productionBonus, baseConsumption, maxConsumption, maxResidents));
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
        upgradeBuildingsList.sort((a, b) -> {
            ClientBuildingDefsRegistry.DefEntry da = ClientBuildingDefsRegistry.get(a.defId());
            ClientBuildingDefsRegistry.DefEntry db = ClientBuildingDefsRegistry.get(b.defId());
            int maxA = da != null ? da.upgrades().size() : 0;
            int maxB = db != null ? db.upgrades().size() : 0;
            boolean atMaxA = maxA > 0 && a.upgradeLevel() >= maxA;
            boolean atMaxB = maxB > 0 && b.upgradeLevel() >= maxB;
            if (atMaxA != atMaxB) return atMaxA ? 1 : -1;
            int catA = BuildingDef.weightForCategory(a.category());
            int catB = BuildingDef.weightForCategory(b.category());
            if (catA != catB) return Integer.compare(catA, catB);
            if (a.upgradeLevel() != b.upgradeLevel()) return Integer.compare(a.upgradeLevel(), b.upgradeLevel());
            return a.defId().compareTo(b.defId());
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
        if (VillageHubClientState.pendingHubData != null) {
            CompoundTag hub = VillageHubClientState.pendingHubData;
            VillageHubClientState.pendingHubData = null;
            parseHubData(hub);
            tryCreateMapWidget(hub);
        }

        this.renderBackground(guiGraphics);

        // Remove closed layer 1 widgets, saving their last-known state
        layer1Widgets.removeIf(w -> {
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
        summaryClosed = layer1Widgets.stream().noneMatch(w -> w instanceof VillageSummaryDraggableWidget);
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
        if (eraWidget != null) {
            List<Component> eraTooltip = eraWidget.getHoveredTooltip(mouseX, mouseY);
            if (!eraTooltip.isEmpty()) {
                guiGraphics.renderComponentTooltip(font, eraTooltip, mouseX, mouseY);
            }
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

        // Queue grid starts at row 1; row 0 is occupied by the weight bar
        for (int i = 0; i < (QUEUE_ROWS - 1) * QUEUE_COLS; i++) {
            int col = i % QUEUE_COLS;
            int row = i / QUEUE_COLS + 1;
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

        // Available buildings grid (player inventory area only, hotbar row removed)
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
                    int color = affordable ? categoryColor(entry.category()) : dim(categoryColor(entry.category()));
                    g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, color);
                    renderItemIcon(g, entry.iconItem(), sx, sy);
                    if (!affordable) {
                        g.fill(sx, sy, sx + CELL - 2, sy + 1, 0x88CC0000);
                    }
                    if (!meetsPrerequisites(entry)) {
                        g.fill(sx, sy, sx + 3, sy + 3, 0xFF886600);
                    }
                    // Yellow diamond star (bottom-right) for orientation-boosted buildings
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

        int totalRows = (buildingCatalog.size() + AVAIL_COLS - 1) / AVAIL_COLS;
        if (totalRows > CATALOG_ROWS) {
            String scrollText = (catalogScrollOffset + 1) + "/" + (totalRows - CATALOG_ROWS + 1);
            g.drawString(font, scrollText, leftPos + imageWidth - 4 - font.width(scrollText),
                topPos + 201, 0xFFAAAAAA, false);
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
            if (entry.requiredResidents() > 0 || !entry.requiredBuildings().isEmpty()) {
                lines.add(Component.literal("Requires:").withStyle(s -> s.withColor(0xCCCCCC)));
                if (entry.requiredResidents() > 0) {
                    boolean met = activeResidents >= entry.requiredResidents();
                    lines.add(Component.literal("  " + activeResidents + "/" + entry.requiredResidents() + " active residents")
                        .withStyle(s -> s.withColor(met ? 0x55FF55 : 0xFF5555)));
                }
                for (ReqBuildingEntry req : entry.requiredBuildings()) {
                    boolean met = req.have() >= req.required();
                    lines.add(Component.literal("  " + req.have() + "/" + req.required() + "x " + formatId(req.defId()))
                        .withStyle(s -> s.withColor(met ? 0x55FF55 : 0xFF5555)));
                }
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
        numActiveBars = 0;
        unlockIconBounds.clear();
        unlockIconItemIds.clear();

        int panelX = leftPos + 11;
        int panelW = imageWidth - 22;
        final int vPad = 3;
        final int BAR_SPACING = 19;

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
                int maxResidentsAdd = 0, curResidentsAdd = 0, nextResidentsAdd = 0;
                float maxConsumptionAdd = 0, curConsumptionAdd = 0, nextConsumptionAdd = 0;
                double maxBonusAdd = 0, curBonusAdd = 0, nextBonusAdd = 0;

                for (int i = 0; i < defEntry.upgrades().size(); i++) {
                    ClientBuildingDefsRegistry.UpgradeLevelClient u = defEntry.upgrades().get(i);
                    maxCadence       += u.cadenceMultiplier();
                    maxCapAdd        += u.capacityStacksAdd();
                    maxAmountAdd     += u.amountAdd();
                    maxResidentsAdd  += u.residentsAdd();
                    maxConsumptionAdd += u.consumptionPerResidentAdd();
                    maxBonusAdd      += u.productionBonusAdd();
                    if (i < sel.upgradeLevel()) {
                        curCadence       += u.cadenceMultiplier();
                        curCapAdd        += u.capacityStacksAdd();
                        curAmountAdd     += u.amountAdd();
                        curResidentsAdd  += u.residentsAdd();
                        curConsumptionAdd += u.consumptionPerResidentAdd();
                        curBonusAdd      += u.productionBonusAdd();
                    }
                }
                boolean atMax = sel.upgradeLevel() >= maxLevel;
                if (!atMax) {
                    ClientBuildingDefsRegistry.UpgradeLevelClient next = defEntry.upgrades().get(sel.upgradeLevel());
                    nextCadence       = next.cadenceMultiplier();
                    nextCapAdd        = next.capacityStacksAdd();
                    nextAmountAdd     = next.amountAdd();
                    nextResidentsAdd  = next.residentsAdd();
                    nextConsumptionAdd = next.consumptionPerResidentAdd();
                    nextBonusAdd      = next.productionBonusAdd();
                }

                boolean hasSpeed      = maxCadence > 0;
                boolean hasAmount     = maxAmountAdd > 0;
                boolean hasCapacity   = maxCapAdd > 0;
                boolean hasResidents  = maxResidentsAdd > 0;
                boolean hasConsumption = maxConsumptionAdd > 0;
                boolean hasGardenBonus = maxBonusAdd > 0;

                int barW = panelW - 6;
                int barX = panelX + 2;
                int barY = topPos + 32 + vPad;

                if (hasSpeed) {
                    barLabelYs[numActiveBars] = barY;
                    barTooltips[numActiveBars] = "Reduces the time between each production cycle.";
                    numActiveBars++;
                    g.drawString(font, "Speed", panelX, barY, 0xFF55EE55, false);
                    float fill  = maxCadence > 0 ? (float)(curCadence / maxCadence) : 0f;
                    float ghost = maxCadence > 0 && !atMax ? (float)(nextCadence / maxCadence) : 0f;
                    renderStatBar(g, barX, barY + 9, barW, 4, fill, ghost, 0xFF55EE55);
                    barY += BAR_SPACING;
                }
                if (hasAmount) {
                    barLabelYs[numActiveBars] = barY;
                    barTooltips[numActiveBars] = "Increases the number of items produced each cycle.";
                    numActiveBars++;
                    g.drawString(font, "Output", panelX, barY, 0xFFCC7700, false);
                    int baseAmt = defEntry.baseAmount();
                    float total = baseAmt + (float)maxAmountAdd;
                    float fill  = total > 0 ? (baseAmt + curAmountAdd) / total : 1f;
                    float ghost = total > 0 && !atMax ? nextAmountAdd / total : 0f;
                    renderStatBar(g, barX, barY + 9, barW, 4, fill, ghost, 0xFFCC7700);
                    barY += BAR_SPACING;
                }
                if (hasCapacity) {
                    barLabelYs[numActiveBars] = barY;
                    barTooltips[numActiveBars] = "Increases how many items this building can hold before it stops producing.";
                    numActiveBars++;
                    g.drawString(font, "Storage", panelX, barY, 0xFF8888FF, false);
                    int baseCap = defEntry.baseCapacity();
                    float total = baseCap + (float)maxCapAdd;
                    float fill  = total > 0 ? (baseCap + curCapAdd) / total : 1f;
                    float ghost = total > 0 && !atMax ? nextCapAdd / total : 0f;
                    renderStatBar(g, barX, barY + 9, barW, 4, fill, ghost, 0xFF8888FF);
                    barY += BAR_SPACING;
                }
                if (hasResidents) {
                    barLabelYs[numActiveBars] = barY;
                    barTooltips[numActiveBars] = "Increases the number of villagers that live in this building.";
                    numActiveBars++;
                    g.drawString(font, "Residents", panelX, barY, 0xFFAA88FF, false);
                    int baseRes = defEntry.baseResidents();
                    float total = baseRes + (float)maxResidentsAdd;
                    float fill  = total > 0 ? (baseRes + curResidentsAdd) / total : 1f;
                    float ghost = total > 0 && !atMax ? nextResidentsAdd / total : 0f;
                    renderStatBar(g, barX, barY + 9, barW, 4, fill, ghost, 0xFFAA88FF);
                    barY += BAR_SPACING;
                }
                if (hasConsumption) {
                    barLabelYs[numActiveBars] = barY;
                    barTooltips[numActiveBars] = "Increases how much food each resident consumes per day.";
                    numActiveBars++;
                    g.drawString(font, "Consumption", panelX, barY, 0xFFFF8833, false);
                    float baseCons = defEntry.baseConsumption();
                    float total = baseCons + maxConsumptionAdd;
                    float fill  = total > 0 ? (baseCons + curConsumptionAdd) / total : 1f;
                    float ghost = total > 0 && !atMax ? nextConsumptionAdd / total : 0f;
                    renderStatBar(g, barX, barY + 9, barW, 4, fill, ghost, 0xFFFF8833);
                    barY += BAR_SPACING;
                }
                if (hasGardenBonus) {
                    barLabelYs[numActiveBars] = barY;
                    barTooltips[numActiveBars] = "Increases the village-wide production multiplier applied to all buildings.";
                    numActiveBars++;
                    g.drawString(font, "Village Bonus", panelX, barY, 0xFFCCDD33, false);
                    double baseBonus = defEntry.baseProductionBonus();
                    double total = baseBonus + maxBonusAdd;
                    float fill  = total > 0 ? (float)((baseBonus + curBonusAdd) / total) : 1f;
                    float ghost = total > 0 && !atMax ? (float)(nextBonusAdd / total) : 0f;
                    renderStatBar(g, barX, barY + 9, barW, 4, fill, ghost, 0xFFCCDD33);
                    barY += BAR_SPACING;
                }

                if (!atMax) {
                    List<String> nextUnlocks = defEntry.upgrades().get(sel.upgradeLevel()).unlockedDisplay();
                    if (!nextUnlocks.isEmpty()) {
                        int iconRowY = topPos + 91;
                        g.drawString(font, "Unlocks:", panelX, iconRowY, 0xFFCCCCCC, false);
                        int iconX = panelX;
                        for (String itemId : nextUnlocks) {
                            renderItemIcon(g, itemId, iconX, iconRowY + 9);
                            unlockIconBounds.add(new int[]{ iconX, iconRowY + 9 });
                            unlockIconItemIds.add(itemId);
                            iconX += 18;
                        }
                    }
                }

                int btnW = 46;
                int btnX = leftPos + QUEUE_GRID_X + (AVAIL_COLS * CELL) - 2 - btnW;
                int btnY = topPos + 126;
                int btnH = 11;
                boolean canAfford = !atMax && canAffordUpgrade(sel, defEntry);
                boolean btnHover = !atMax && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH;
                int btnColor = atMax ? 0xFF444444 : (canAfford ? (btnHover ? 0xFF55BB55 : 0xFF337733) : 0xFF444444);
                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnColor);
                String btnText = atMax ? "Max level" : "Upgrade";
                int btnTextColor = (atMax || !canAfford) ? 0xFF888888 : 0xFFFFFFFF;
                g.drawString(font, btnText, btnX + (btnW - font.width(btnText)) / 2, btnY + 2, btnTextColor, false);

            } else if (defEntry != null) {
                g.drawString(font, "No upgrades available", panelX, topPos + 31 + vPad, 0xFF666666, false);
            }
        }

        renderUpgradeBuildingGrid(g, mx, my);

        if (currentEra == 0) {
            int lockTop = topPos + 16;
            g.fill(leftPos, lockTop, leftPos + imageWidth, topPos + imageHeight, 0xC0222222);
            int lockX = leftPos + imageWidth / 2 - 4;
            int lockY = topPos + imageHeight / 2 - 5;
            drawPadlockIcon(g, lockX, lockY);
        }
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

        for (int i = 0; i < numActiveBars; i++) {
            int labelY = barLabelYs[i];
            if (my >= labelY && my < labelY + 14 && mx >= leftPos + 11 && mx < leftPos + imageWidth - 11) {
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
        for (DraggableWidget w : layer1Widgets) {
            if (w instanceof MapDraggableWidget) {
                savedMapX = w.getX();
                savedMapY = w.getY();
                savedWidgetOrder.add(w.getClass().getSimpleName());
            } else if (w instanceof VillageSummaryDraggableWidget) {
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
    public boolean mouseClicked(double mX, double mY, int button) {
        // Reopen buttons
        if (button == 0 && cachedHubData != null) {
            int btnX = leftPos - 18;
            int btnY = topPos + 10;
            int offset = 0;
            if (mapClosed) {
                int ry = btnY + offset;
                if (mX >= btnX && mX < btnX + 14 && mY >= ry && mY < ry + 14) {
                    int freeZoneW = this.leftPos;
                    int startX = (savedMapX >= 0) ? savedMapX : centerX(freeZoneW, mapInitialHeight);
                    int startY = (savedMapY >= 0) ? savedMapY : centerY(this.height, mapInitialHeight);
                    MapDraggableWidget reopenedMap = new MapDraggableWidget(startX, startY, Math.min(160, freeZoneW - 20), mapInitialHeight, freeZoneW, this.height, cachedHubData.getCompound("MapData"));
                    reopenedMap.setOnBuildingClicked(pos -> { activeTab = 2; selectedUpgradeBuildingPos = pos; });
                    layer1Widgets.add(0, reopenedMap);
                    savedMapOpen = true;
                    mapClosed = false;
                    return true;
                }
                offset += 18;
            }
            if (summaryClosed && cachedHubData.contains("SummaryData")) {
                int ry = btnY + offset;
                if (mX >= btnX && mX < btnX + 14 && mY >= ry && mY < ry + 14) {
                    int freeZoneW = this.leftPos;
                    int summaryH = DraggableWidget.TITLE_BAR_H + VillageSummaryDraggableWidget.VISIBLE_H;
                    int startX = (savedSummaryX >= 0) ? savedSummaryX : centerX(freeZoneW, VillageSummaryDraggableWidget.WIDGET_W);
                    int startY = (savedSummaryY >= 0) ? savedSummaryY : centerY(this.height, summaryH);
                    layer1Widgets.add(0, new VillageSummaryDraggableWidget(cachedHubData.getCompound("MapData"), cachedHubData.getCompound("SummaryData"), startX, startY, freeZoneW, this.height));
                    savedSummaryOpen = true;
                    summaryClosed = false;
                    return true;
                }
                offset += 18;
            }
            if (eraClosed) {
                int ry = btnY + offset;
                if (mX >= btnX && mX < btnX + 14 && mY >= ry && mY < ry + 14) {
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
            if (button == 1 && !hasShiftDown() && hoveredCatalogSlot >= 0 && hoveredCatalogSlot < buildingCatalog.size()) {
                BuildingEntry entry = buildingCatalog.get(hoveredCatalogSlot);
                if (!entry.productionRows().isEmpty()) {
                    if (productionPopup != null) {
                        lastProductionPopupX = productionPopup.getX();
                        lastProductionPopupY = productionPopup.getY();
                        layer1Widgets.remove(productionPopup);
                    }
                    int popupH = BuildingProductionDraggableWidget.computeHeight(entry.productionRows());
                    int popupX = (lastProductionPopupX >= 0) ? lastProductionPopupX : centerX(leftPos, BuildingProductionDraggableWidget.WIDGET_W);
                    int popupY = (lastProductionPopupY >= 0) ? lastProductionPopupY : centerY(this.height, popupH);
                    productionPopup = new BuildingProductionDraggableWidget(
                        formatId(entry.id()), entry.productionRows(),
                        popupX, popupY, leftPos, this.height
                    );
                    layer1Widgets.add(productionPopup);
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
        if (activeTab == 1 || activeTab == 2) return true;
        return super.mouseDragged(mX, mY, button, dX, dY);
    }

    @Override
    public boolean mouseReleased(double mX, double mY, int button) {
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
        if (activeTab == 1 || activeTab == 2) return true;
        return super.mouseReleased(mX, mY, button);
    }

    @Override
    public boolean mouseScrolled(double mX, double mY, double delta) {
        // Layer 3 (quests) has scroll priority, then layer 1
        for (int i = layer3Widgets.size() - 1; i >= 0; i--) {
            if (layer3Widgets.get(i).mouseScrolled(mX, mY, delta)) return true;
        }
        for (int i = layer1Widgets.size() - 1; i >= 0; i--) {
            if (layer1Widgets.get(i).mouseScrolled(mX, mY, delta)) return true;
        }
        if (activeTab == 1) {
            int totalRows = (buildingCatalog.size() + AVAIL_COLS - 1) / AVAIL_COLS;
            int maxOffset = Math.max(0, totalRows - CATALOG_ROWS);
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

    private static List<EraProgressDraggableWidget.EraPathOption> parseEraTransitions(CompoundTag hub) {
        List<EraProgressDraggableWidget.EraPathOption> result = new ArrayList<>();
        int cw = hub.getInt("CurrentWeight");
        int mw = hub.getInt("MaxWeight");
        hub.getList("EraTransitions", Tag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag tt = (CompoundTag) raw;
            String id = tt.getString("Id");
            String displayName = tt.getString("DisplayName");
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
                id, displayName, iconItem, prereqsMet, reqWeight, cw, mw, weightMet,
                costRows, reqRes, activeRes, resMet, reqBuilds, unlocked));
        });
        return result;
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
            case "natural"     -> COLOR_NATURAL;
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
        int barY = topPos + QUEUE_GRID_Y + (CELL - barH) / 2;
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
        int btnY = topPos + 10;
        int offset = 0;
        if (mapClosed) {
            int ry = btnY + offset;
            boolean hover = mx >= btnX && mx < btnX + 14 && my >= ry && my < ry + 14;
            g.fill(btnX, ry, btnX + 14, ry + 14, hover ? 0xFF555555 : 0xFF333333);
            drawHouseIcon(g, btnX + 2, ry + 3);
            offset += 18;
        }
        if (summaryClosed && cachedHubData.contains("SummaryData")) {
            int ry = btnY + offset;
            boolean hover = mx >= btnX && mx < btnX + 14 && my >= ry && my < ry + 14;
            g.fill(btnX, ry, btnX + 14, ry + 14, hover ? 0xFF555555 : 0xFF333333);
            drawInfoIcon(g, btnX + 2, ry + 2);
            offset += 18;
        }
        if (eraClosed) {
            int ry = btnY + offset;
            boolean hover = mx >= btnX && mx < btnX + 14 && my >= ry && my < ry + 14;
            g.fill(btnX, ry, btnX + 14, ry + 14, hover ? 0xFF555555 : 0xFF333333);
            drawEraIcon(g, btnX + 2, ry + 2);
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
