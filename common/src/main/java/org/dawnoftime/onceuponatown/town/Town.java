package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.datapack.BuildingListDataHandler;
import org.dawnoftime.onceuponatown.datapack.EraDef;
import org.dawnoftime.onceuponatown.datapack.EraTransitionDataHandler;
import org.dawnoftime.onceuponatown.datapack.EraTransitionDef;
import org.dawnoftime.onceuponatown.datapack.QuestDataHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Town {

    private static final Logger LOGGER = LoggerFactory.getLogger(Town.class);

    public record UnderConstructionEntry(String defId, BlockPos worldPos, BoundingBox bb, Rotation rotation) {}

    private final List<PlacedBuilding> buildings = new ArrayList<>();
    private final List<ConnectionPoint> freeConnections = new ArrayList<>();
    private final Map<Item, Integer> reserveStock = new HashMap<>();
    private final List<UnderConstructionEntry> underConstruction = new ArrayList<>();
    // Bounding boxes reserved for pieces that have no building def (starter, vanilla pieces).
    // Must be serialized - the mixin that populates these only fires during world gen, not on reload.
    private final List<BoundingBox> blockedZones = new ArrayList<>();
    // World positions of buildings currently being upgraded by an NPC (runtime-only, not persisted).
    private final Set<BlockPos> underUpgrade = new HashSet<>();
    // Ordered list of builder NPC UUIDs. Slot 0 is the primary builder (handles street expansion).
    private final List<UUID> builderNpcIds = new ArrayList<>();
    // How many builders should be active. Starts at 1, incremented by era transitions with unlock_new_builder.
    private int targetBuilderCount = 1;
    // Runtime-only claim map: queue index -> builder UUID. Prevents two builders from picking the same entry.
    // Not persisted: claims are re-established on the next idle tick after a server restart.
    private final Map<Integer, UUID> queueIndexClaims = new HashMap<>();
    private String name = "Unknown Town";
    // Active quests for this town. Max 3 at a time (see QuestManager.MAX_ACTIVE_QUESTS).
    private final List<Quest> activeQuests = new ArrayList<>();
    // NOTE quest def IDs that have already been shown. Once a NOTE is dismissed it never spawns again.
    private final Set<String> dismissedNoteIds = new HashSet<>();

    // Player-ordered queue of construction tasks (new builds and upgrades).
    // Resources are pre-reserved in queueReservedStock when an entry is added.
    private final List<QueueEntry> constructionQueue = new ArrayList<>();
    private int constructionQueueCursor = 0;
    // Resources deducted from town stock when buildings are added to the queue.
    // Released back to reserveStock on removal, or cleared when NPC places the building.
    private final Map<Item, Integer> queueReservedStock = new HashMap<>();

    // Max entries the queue can hold; matches TownHubMenu.CHEST_SIZE (6x9 grid).
    public static final int QUEUE_CAPACITY = 54;

    // Current era (0 = Settlement, 1 = Village, ...). Persisted to NBT.
    private int currentEra = 0;
    // The era transition id chosen at the last fork (e.g. "agricultural_rural"). Persisted to NBT.
    private String currentEraPath = "";
    // Orientation tag for the current era branch (e.g. "agricultural", "agricultural_rural"). Persisted to NBT.
    private String currentOrientation = "";
    // Building defIds explicitly unlocked via era transition rewards. Grows over time. Persisted to NBT.
    private final Set<String> unlockedBuildingIds = new HashSet<>();
    // Fed portion of total residents, updated each dawn tick. Persisted to NBT.
    private int activeResidents = 0;
    // Weight cost per building category, sourced from the era 0 data file. Persisted to NBT.
    private final Map<String, Integer> categoryWeights = new HashMap<>();
    // Current weight cap. Starts at initial_max_weight (era 0 file) and grows with each transition. Persisted to NBT.
    private int currentMaxWeight = 20;
    // Active build states keyed by builder slot index. Persisted so builds survive server restarts.
    private final Map<Integer, ActiveBuildState> activeBuilds = new HashMap<>();

    public void registerBuilding(BlockPos worldPos, String defId, List<ConnectionPoint> connections, BoundingBox bb, Rotation rotation) {
        buildings.add(new PlacedBuilding(defId, worldPos, bb, rotation));
        freeConnections.addAll(connections);
    }

    public void addBlockedZone(BoundingBox bb) {
        blockedZones.add(bb);
    }

    public void addUnderConstruction(String defId, BlockPos pos, BoundingBox bb, Rotation rotation) {
        underConstruction.add(new UnderConstructionEntry(defId, pos, bb, rotation));
    }

    public void removeUnderConstruction(BlockPos pos) {
        underConstruction.removeIf(e -> e.worldPos().equals(pos));
    }

    public List<UnderConstructionEntry> getUnderConstructionBuildings() {
        return Collections.unmodifiableList(underConstruction);
    }

    public void addUnderUpgrade(BlockPos pos) {
        underUpgrade.add(pos);
    }

    public void removeUnderUpgrade(BlockPos pos) {
        underUpgrade.remove(pos);
    }

    // Returns the world bounding boxes of all placed buildings plus blocked zones plus in-progress builds.
    // Buildings from saves predating BB tracking have null bb - they are skipped.
    public List<BoundingBox> getOccupiedBoxes() {
        List<BoundingBox> all = new ArrayList<>();
        buildings.stream().map(b -> b.bb).filter(Objects::nonNull).forEach(all::add);
        all.addAll(blockedZones);
        underConstruction.stream().map(UnderConstructionEntry::bb).filter(Objects::nonNull).forEach(all::add);
        return all;
    }

    public void addFreeConnection(ConnectionPoint point) {
        freeConnections.add(point);
    }

    public void useConnection(ConnectionPoint point) {
        freeConnections.remove(point);
    }

    public List<ConnectionPoint> getAvailableConnectionPoints() {
        return Collections.unmodifiableList(freeConnections);
    }

    // Returns building defs available to build: all buildings whose construction cost is met by the town inventory.
    public List<BuildingDef> getBuildableBuildings() {
        TownInventory inv = getTownInventory();
        return BuildingDataHandler.getAll().stream()
            .filter(def -> inv.hasStock(def.constructionCost))
            .toList();
    }

    // Computes committed weight: placed buildings + NewBuild entries in the player queue.
    // Queued-but-unplaced buildings are included so tryAddToConstructionQueue enforces the
    // cap against the full committed load, not just what is already physically in the world.
    public int getCurrentWeight() {
        int total = 0;
        for (PlacedBuilding b : buildings) {
            BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
            if (def == null) continue;
            total += weightForCategory(def.category);
        }
        for (QueueEntry entry : constructionQueue) {
            if (entry instanceof QueueEntry.NewBuild nb) {
                BuildingDef def = BuildingDataHandler.get(nb.defId()).orElse(null);
                if (def != null) total += weightForCategory(def.category);
            }
        }
        return total;
    }

    private int weightForCategory(String category) {
        if (categoryWeights.isEmpty()) {
            LOGGER.error("[OUAT] categoryWeights is empty - era def may not have loaded correctly");
            return 0;
        }
        return categoryWeights.getOrDefault(category, 0);
    }

    public int getCurrentEra() { return currentEra; }
    public String getCurrentEraPath() { return currentEraPath; }
    public Set<String> getUnlockedBuildingIds() { return Collections.unmodifiableSet(unlockedBuildingIds); }

    public int getCurrentMaxWeight() { return currentMaxWeight; }

    // Seeds categoryWeights and currentMaxWeight from the matched era 0 data file.
    // Called once at world gen after all starter buildings are registered.
    public void initFromEraDef() {
        String orientation = getOrDeriveOrientation();
        if (orientation.isEmpty()) return;
        EraDef def = EraTransitionDataHandler.getEraDefByOrientation(orientation);
        if (def == null) return;
        if (categoryWeights.isEmpty() && !def.categoryWeights.isEmpty()) {
            categoryWeights.putAll(def.categoryWeights);
        }
        if (def.initialMaxWeight > 0) {
            currentMaxWeight = def.initialMaxWeight;
        }
    }

    // Returns the structure label (e.g. "Settlement", "Village") for the current era and orientation.
    public String resolveStructureLabel() {
        if (currentEra == 0) {
            EraDef def = EraTransitionDataHandler.getEraDefByOrientation(getOrDeriveOrientation());
            return def != null ? def.structureLabel : "";
        }
        for (EraTransitionDef t : EraTransitionDataHandler.getAll()) {
            if (t.fromEra != currentEra - 1) continue;
            String resultOrientation = t.nextOrientation.isEmpty() ? t.fromOrientation : t.nextOrientation;
            if (resultOrientation.equals(getOrDeriveOrientation())) return t.structureLabel;
        }
        return "";
    }

    // Returns era transitions currently available given era and orientation.
    public List<EraTransitionDef> getAvailableTransitions() {
        return EraTransitionDataHandler.getAvailableTransitions(currentEra, getOrDeriveOrientation());
    }

    // Returns the current orientation, deriving it from the placed starter building on first call for old saves.
    public String getOrDeriveOrientation() {
        if (!currentOrientation.isEmpty()) return currentOrientation;
        for (PlacedBuilding b : buildings) {
            EraDef eraDef = EraTransitionDataHandler.getEraDefForStarter(b.defId);
            if (eraDef != null) {
                currentOrientation = eraDef.orientation;
                return currentOrientation;
            }
        }
        return "";
    }

    // Computes the effective minimum weight for a transition, respecting min_weight_percent if set.
    public int effectiveMinWeight(EraTransitionDef t) {
        if (t.minWeightPercent > 0) {
            return (int) Math.round(t.minWeightPercent / 100.0 * currentMaxWeight);
        }
        return 0;
    }

    // Returns true if all prereqs for the given era transition are satisfied.
    public boolean meetsEraTransitionPrereqs(EraTransitionDef t) {
        int w = getCurrentWeight();
        if (w < effectiveMinWeight(t) || w > getCurrentMaxWeight()) return false;
        if (t.requiredResidents > 0 && activeResidents < t.requiredResidents) return false;
        for (BuildingDef.BuildingRequirement req : t.requiredBuildings) {
            long count = buildings.stream().filter(b -> b.defId.equals(req.defId())).count();
            if (count < req.count()) return false;
        }
        if (!getTownInventory().hasStock(t.resourceCost)) return false;
        return true;
    }

    // Performs the era transition identified by pathId. Returns true if successful.
    public boolean advanceEra(String pathId) {
        EraTransitionDef t = EraTransitionDataHandler.get(pathId).orElse(null);
        if (t == null) return false;
        List<EraTransitionDef> available = getAvailableTransitions();
        if (available.stream().noneMatch(a -> a.id.equals(pathId))) return false;
        if (!meetsEraTransitionPrereqs(t)) return false;
        getTownInventory().removeStock(t.resourceCost);
        currentEra++;
        currentMaxWeight += t.weightCapIncrease;
        currentEraPath = t.id;
        if (!t.nextOrientation.isEmpty()) currentOrientation = t.nextOrientation;
        unlockedBuildingIds.addAll(t.unlockedBuildingIds);
        if (t.unlockNewBuilder) targetBuilderCount++;
        for (PlacedBuilding b : buildings) {
            if (b.defId.startsWith("settlement")) {
                tryQueueUpgrade(b.worldPos);
                break;
            }
        }
        return true;
    }

    // Returns all building defs whose entry pool matches the connection point, regardless of cost.
    // Used to distinguish PENDING_RESOURCES (compatible building exists but unaffordable) from DEAD (no compatible building).
    public List<BuildingDef> getPotentialBuildings(ConnectionPoint point) {
        return BuildingDataHandler.getAll().stream()
            .filter(def -> point.targetName().isEmpty() || def.entryPool.equals(point.targetName()))
            .toList();
    }

    // Returns the building defIds that receive the orientation bonus.
    // Uses currentOrientation (persisted) so the result stays correct after path branching.
    public List<String> getBoostedBuildingIds() {
        String orientation = getOrDeriveOrientation();
        if (orientation.isEmpty()) return List.of();
        EraDef eraDef = EraTransitionDataHandler.getEraDefByOrientation(orientation);
        if (eraDef == null) return List.of();
        return eraDef.boostedBuildings;
    }

    // Called by the NPC after a NewBuild placement succeeds.
    // Stamps the orientation bonus multiplier onto the building if its defId is boosted.
    public void onBuildingPlaced(String defId) {
        if (buildings.isEmpty()) return;
        String orientation = getOrDeriveOrientation();
        if (orientation.isEmpty()) return;
        EraDef era = EraTransitionDataHandler.getEraDefByOrientation(orientation);
        if (era == null || !era.boostedBuildings.contains(defId)) return;
        buildings.get(buildings.size() - 1).setInstanceProductionMultiplier(era.boostMultiplier);
    }

    // Replaces a connection point with an incremented failCount.
    public void incrementConnectionFailCount(ConnectionPoint point) {
        int idx = freeConnections.indexOf(point);
        if (idx >= 0) {
            freeConnections.set(idx, new ConnectionPoint(
                point.pos(), point.direction(), point.targetName(), point.failCount() + 1));
        }
    }

    // Computed aggregate view: buildings + floating reserve
    public TownInventory getTownInventory() {
        return new TownInventory(buildings, reserveStock);
    }

    // Player command injection - into first building if available, otherwise into reserve
    public void addStock(Item item, int quantity) {
        if (!buildings.isEmpty()) {
            buildings.get(0).forceAdd(item, quantity);
        } else {
            reserveStock.merge(item, quantity, Integer::sum);
        }
    }

    // Returns {NWCorner, SECorner} as BlockPos array derived from all occupied boxes.
    // Y is set to 0 - the map is purely 2D (XZ plane). Returns null if no boxes exist.
    public BlockPos[] getMapBounds() {
        List<BoundingBox> boxes = getOccupiedBoxes();
        if (boxes.isEmpty()) return null;
        int minX = boxes.stream().mapToInt(BoundingBox::minX).min().getAsInt();
        int minZ = boxes.stream().mapToInt(BoundingBox::minZ).min().getAsInt();
        int maxX = boxes.stream().mapToInt(BoundingBox::maxX).max().getAsInt();
        int maxZ = boxes.stream().mapToInt(BoundingBox::maxZ).max().getAsInt();
        return new BlockPos[]{ new BlockPos(minX, 0, minZ), new BlockPos(maxX, 0, maxZ) };
    }

    public CompoundTag getTownMapData() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        BlockPos[] bounds = getMapBounds();
        if (bounds != null) {
            tag.put("NWCorner", NbtUtils.writeBlockPos(bounds[0]));
            tag.put("SECorner", NbtUtils.writeBlockPos(bounds[1]));
        } else {
            tag.put("NWCorner", NbtUtils.writeBlockPos(BlockPos.ZERO));
            tag.put("SECorner", NbtUtils.writeBlockPos(BlockPos.ZERO));
        }
        ListTag elements = new ListTag();
        for (PlacedBuilding b : getBuildings()) {
            elements.add(buildingToMapElement(b));
        }
        for (BoundingBox bz : blockedZones) {
            elements.add(blockedZoneToMapElement(bz));
        }
        for (UnderConstructionEntry uc : underConstruction) {
            elements.add(underConstructionToMapElement(uc));
        }
        tag.put("Elements", elements);
        return tag;
    }

    private CompoundTag buildingToMapElement(PlacedBuilding b) {
        CompoundTag el = new CompoundTag();
        BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
        boolean isStreet = def != null && def.terrainMatching;
        el.putByte("Category", isStreet ? MapCategory.ROAD : MapCategory.BUILDING);
        el.putString("BuildType", b.defId);
        el.putLong("WorldPos", b.worldPos.asLong());
        // Use bb NW corner as origin so the map element is aligned with the actual placed footprint.
        BlockPos origin = b.bb != null
            ? new BlockPos(b.bb.minX(), b.bb.minY(), b.bb.minZ())
            : b.worldPos;
        el.put("OriginPos", NbtUtils.writeBlockPos(origin));
        if (b.bb != null) {
            el.putInt("SizeX", b.bb.maxX() - b.bb.minX());
            el.putInt("SizeZ", b.bb.maxZ() - b.bb.minZ());
        } else {
            el.putInt("SizeX", 5);
            el.putInt("SizeZ", 5);
        }
        el.putInt("Level", b.getUpgradeLevel() + 1);
        if (def != null) {
            el.putString("IconItem", def.iconItem);
        }
        if (!isStreet && def != null) {
            el.putString("BuildingCategory", def.category);

            // --- Production entries: use resolved stats so upgrades (speed + capacity) are reflected ---
            BuildingDef.ResolvedBuildingStats stats = def.resolveAtLevel(b.getUpgradeLevel());
            ListTag prodTag = new ListTag();
            for (ProductionEntry pe : stats.production()) {
                int effectiveTicks = stats.totalCadenceMultiplier() > 0
                    ? (int) Math.max(1, Math.round(pe.everyTicks() / (1.0 + stats.totalCadenceMultiplier())))
                    : pe.everyTicks();
                CompoundTag pt = new CompoundTag();
                pt.putString("Item", BuiltInRegistries.ITEM.getKey(pe.item()).toString());
                pt.putInt("Amount", pe.amount());
                pt.putInt("EveryTicks", effectiveTicks);
                prodTag.add(pt);
            }
            el.put("Production", prodTag);

            // --- Transformation recipes ---
            if (!def.transformations.isEmpty()) {
                ListTag transformsTag = new ListTag();
                for (TransformationRecipe tr : def.transformations) {
                    CompoundTag tt = new CompoundTag();
                    ListTag inputsTag = new ListTag();
                    for (ItemCost ic : tr.inputs()) {
                        CompoundTag it = new CompoundTag();
                        it.putString("Item", BuiltInRegistries.ITEM.getKey(ic.item()).toString());
                        it.putInt("Amount", ic.amount());
                        inputsTag.add(it);
                    }
                    tt.put("Inputs", inputsTag);
                    tt.putString("OutputItem", BuiltInRegistries.ITEM.getKey(tr.outputItem()).toString());
                    tt.putInt("OutputAmount", tr.outputAmount());
                    tt.putInt("EveryTicks", def.transformEveryTicks);
                    transformsTag.add(tt);
                }
                el.put("Transforms", transformsTag);
            }

            // --- Production bonus: town-wide perk (from BuildingDef) ---
            if (def.productionBonus > 0) {
                el.putDouble("ProductionBonus", def.productionBonus);
            }
            if (def.residents > 0) {
                el.putInt("Residents", def.residents);
            }
        }
        // --- Per-instance orientation bonus (set during bootstrap placement) ---
        if (b.getInstanceProductionMultiplier() > 1.0) {
            el.putDouble("InstanceBonus", b.getInstanceProductionMultiplier() - 1.0);
        }
        if (underUpgrade.contains(b.worldPos)) {
            el.putBoolean("IsUpgrading", true);
        }
        if (isStreet && def != null && def.footprint != null && !def.footprint.isEmpty()) {
            List<String> rotatedFootprint = rotateFootprint(def.footprint, b.rotation);
            ListTag footprintTag = new ListTag();
            for (String row : rotatedFootprint) {
                footprintTag.add(StringTag.valueOf(row));
            }
            el.put("Footprint", footprintTag);
        }
        return el;
    }

    // Rotates a footprint grid (rows of '0'/'1' chars) by the given Rotation enum value.
    // The footprint is defined in the structure's default (NONE) orientation.
    private static List<String> rotateFootprint(List<String> footprint, Rotation rotation) {
        int times = switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
        List<String> result = footprint;
        for (int t = 0; t < times; t++) {
            result = rotateCW90(result);
        }
        return result;
    }

    // Rotates a 2D char grid 90 degrees clockwise.
    // new[col][rows-1-row] = old[row][col]
    private static List<String> rotateCW90(List<String> grid) {
        int rows = grid.size();
        int cols = rows == 0 ? 0 : grid.get(0).length();
        char[][] rotated = new char[cols][rows];
        for (int i = 0; i < rows; i++) {
            String row = grid.get(i);
            for (int j = 0; j < cols && j < row.length(); j++) {
                rotated[j][rows - 1 - i] = row.charAt(j);
            }
        }
        List<String> result = new ArrayList<>();
        for (char[] row : rotated) {
            result.add(new String(row));
        }
        return result;
    }

    private CompoundTag underConstructionToMapElement(UnderConstructionEntry uc) {
        CompoundTag el = new CompoundTag();
        el.putByte("Category", MapCategory.UNDER_CONSTRUCTION);
        el.putString("BuildType", uc.defId());
        el.put("OriginPos", NbtUtils.writeBlockPos(new BlockPos(uc.bb().minX(), uc.bb().minY(), uc.bb().minZ())));
        el.putInt("SizeX", uc.bb().maxX() - uc.bb().minX());
        el.putInt("SizeZ", uc.bb().maxZ() - uc.bb().minZ());
        return el;
    }

    private CompoundTag blockedZoneToMapElement(BoundingBox bz) {
        CompoundTag el = new CompoundTag();
        el.putByte("Category", MapCategory.BUILDING);
        el.putString("BuildType", "starter");
        el.put("OriginPos", NbtUtils.writeBlockPos(new BlockPos(bz.minX(), bz.minY(), bz.minZ())));
        el.putInt("SizeX", bz.maxX() - bz.minX());
        el.putInt("SizeZ", bz.maxZ() - bz.minZ());
        el.putInt("Level", 1);
        el.putString("IconItem", "minecraft:bell");
        el.putString("BuildingCategory", "town_center");
        el.put("Production", new ListTag());
        return el;
    }

    // -------------------------------------------------------------------------
    // Player construction queue
    // -------------------------------------------------------------------------

    public List<QueueEntry> getConstructionQueue() {
        return Collections.unmodifiableList(constructionQueue);
    }

    public int getConstructionQueueCursor() { return constructionQueueCursor; }

    // Checks affordability (available stock minus already-reserved amounts), reserves resources,
    // and appends a NewBuild entry to the queue. Returns false if unaffordable or queue is full.
    public boolean tryAddToConstructionQueue(String defId) {
        BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
        if (def == null || constructionQueue.size() >= QUEUE_CAPACITY) return false;
        // Weight cap: block new builds (not upgrades) that would exceed the era limit.
        if (getCurrentWeight() + weightForCategory(def.category) > getCurrentMaxWeight()) return false;
        TownInventory inv = getTownInventory();
        for (ItemCost cost : def.constructionCost) {
            if (inv.getStock(cost.item()) < cost.amount()) return false;
        }
        inv.removeStock(def.constructionCost);
        for (ItemCost cost : def.constructionCost) {
            queueReservedStock.merge(cost.item(), cost.amount(), Integer::sum);
        }
        constructionQueue.add(new QueueEntry.NewBuild(defId));
        return true;
    }

    // Checks affordability and appends an Upgrade entry to the queue.
    // Returns false if: building not found, already at max level (including pending upgrades),
    // queue full, or insufficient stock.
    // Multiple upgrades for the same building are allowed: each gets a fromLevel incremented
    // by the number of pending upgrades already queued for that building.
    public boolean tryQueueUpgrade(BlockPos worldPos) {
        PlacedBuilding building = null;
        for (PlacedBuilding b : buildings) {
            if (b.worldPos.equals(worldPos)) { building = b; break; }
        }
        if (building == null) return false;

        BuildingDef def = BuildingDataHandler.get(building.defId).orElse(null);
        if (def == null || (def.upgrades.isEmpty() && def.nbtLevels.isEmpty())) return false;

        // Compute effective level after all upgrades already pending in the queue.
        int effectiveLevel = building.getUpgradeLevel();
        for (QueueEntry entry : constructionQueue) {
            if (entry instanceof QueueEntry.Upgrade u && u.buildingWorldPos().equals(worldPos)) {
                effectiveLevel++;
            }
        }

        int maxLevel = Math.max(def.upgrades.size(), def.nbtLevels.size());
        if (effectiveLevel >= maxLevel) return false;
        if (constructionQueue.size() >= QUEUE_CAPACITY) return false;

        // Visual-only upgrades (nbt_levels only, no stat upgrades) are free -- cost already paid by era advance.
        List<ItemCost> cost = effectiveLevel < def.upgrades.size()
            ? def.upgrades.get(effectiveLevel).upgradeCost()
            : List.of();
        TownInventory inv = getTownInventory();
        if (!inv.hasStock(cost)) return false;

        inv.removeStock(cost);
        for (ItemCost c : cost) {
            queueReservedStock.merge(c.item(), c.amount(), Integer::sum);
        }
        constructionQueue.add(new QueueEntry.Upgrade(building.defId, worldPos, effectiveLevel));
        return true;
    }

    // Removes entry at index, restoring its reserved resources to the floating reserve.
    public boolean removeFromConstructionQueue(int index) {
        if (index < 0 || index >= constructionQueue.size()) return false;
        QueueEntry entry = constructionQueue.get(index);
        List<ItemCost> costToRefund = getEntryCost(entry);
        for (ItemCost cost : costToRefund) {
            int reserved = queueReservedStock.getOrDefault(cost.item(), 0);
            int toRestore = Math.min(reserved, cost.amount());
            if (toRestore > 0) {
                queueReservedStock.put(cost.item(), reserved - toRestore);
                reserveStock.merge(cost.item(), toRestore, Integer::sum);
            }
        }
        constructionQueue.remove(index);
        if (constructionQueueCursor >= constructionQueue.size() && constructionQueueCursor > 0) {
            constructionQueueCursor = 0;
        }
        return true;
    }

    // Called by NPC after successfully processing a queued entry.
    // Removes the entry from the queue and clears its resource reservation.
    public void consumeQueueEntry(QueueEntry entry) {
        int idx = constructionQueue.indexOf(entry);
        if (idx >= 0) {
            constructionQueue.remove(idx);
            if (constructionQueueCursor >= constructionQueue.size() && constructionQueueCursor > 0) {
                constructionQueueCursor = 0;
            }
        }
        // Always drain the reservation even if entry was already removed by the player.
        List<ItemCost> cost = getEntryCost(entry);
        for (ItemCost c : cost) {
            int reserved = queueReservedStock.getOrDefault(c.item(), 0);
            queueReservedStock.put(c.item(), Math.max(0, reserved - c.amount()));
        }
    }

    // Returns the resource cost associated with a queue entry (construction cost or upgrade cost).
    private List<ItemCost> getEntryCost(QueueEntry entry) {
        if (entry instanceof QueueEntry.NewBuild nb) {
            BuildingDef def = BuildingDataHandler.get(nb.defId()).orElse(null);
            return def != null ? def.constructionCost : List.of();
        } else if (entry instanceof QueueEntry.Upgrade u) {
            BuildingDef def = BuildingDataHandler.get(u.defId()).orElse(null);
            if (def != null && u.fromLevel() < def.upgrades.size()) {
                return def.upgrades.get(u.fromLevel()).upgradeCost();
            }
        }
        return List.of();
    }

    // Sums resolved residents (including upgrade bonuses) for all placed buildings.
    public int getTotalResidents() {
        int total = 0;
        for (PlacedBuilding b : buildings) {
            BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
            if (def == null) continue;
            total += def.resolveAtLevel(b.getUpgradeLevel()).resolvedResidents();
        }
        return total;
    }

    // Computes total food units demanded per day across all residential buildings (unrounded float).
    public float computeTotalFoodDemandFloat() {
        float total = 0f;
        for (PlacedBuilding b : buildings) {
            BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
            if (def == null) continue;
            BuildingDef.ResolvedBuildingStats stats = def.resolveAtLevel(b.getUpgradeLevel());
            if (stats.resolvedResidents() > 0) {
                total += stats.resolvedResidents() * stats.resolvedConsumptionPerResident();
            }
        }
        return total;
    }

    public int getActiveResidents() { return activeResidents; }
    public void setActiveResidents(int value) { this.activeResidents = value; }

    // Returns true if all prerequisites of the given def are currently satisfied.
    // Uses activeResidents (fed population) instead of total residents.
    public boolean meetsPrerequisites(BuildingDef def) {
        if (def.requiredResidents > 0 && activeResidents < def.requiredResidents) return false;
        for (BuildingDef.BuildingRequirement req : def.requiredBuildings) {
            long count = buildings.stream().filter(b -> b.defId.equals(req.defId())).count();
            if (count < req.count()) return false;
        }
        return true;
    }

    public void advanceQueueCursor() {
        if (constructionQueue.isEmpty()) { constructionQueueCursor = 0; return; }
        constructionQueueCursor = (constructionQueueCursor + 1) % constructionQueue.size();
    }

    public void resetQueueCursor() { constructionQueueCursor = 0; }

    // Returns hub data: map data + construction queue + building catalog + stock snapshot.
    // Sent to client when player opens anchor or after queue changes.
    public CompoundTag getHubData(BlockPos anchorPos) {
        CompoundTag hub = new CompoundTag();
        hub.put("AnchorPos", NbtUtils.writeBlockPos(anchorPos));
        hub.put("MapData", getTownMapData());
        hub.putInt("CurrentEra", currentEra);
        hub.putString("CurrentOrientation", getOrDeriveOrientation());
        hub.putInt("CurrentWeight", getCurrentWeight());
        hub.putInt("MaxWeight", getCurrentMaxWeight());

        List<EraTransitionDef> availableTransitions = getAvailableTransitions();
        ListTag eraTransitionsTag = new ListTag();
        TownInventory invForEra = getTownInventory();
        for (EraTransitionDef t : availableTransitions) {
            CompoundTag tt = new CompoundTag();
            tt.putString("Id", t.id);
            tt.putString("OrientationLabel", t.orientationLabel);
            tt.putString("IconItem", t.iconItem);
            int effMinWeight = effectiveMinWeight(t);
            tt.putBoolean("PrereqsMet", meetsEraTransitionPrereqs(t));
            tt.putInt("RequiredWeight", effMinWeight);
            tt.putInt("CurrentWeight", getCurrentWeight());
            tt.putInt("MaxWeight", getCurrentMaxWeight());
            tt.putBoolean("WeightMet", getCurrentWeight() >= effMinWeight && getCurrentWeight() <= getCurrentMaxWeight());
            ListTag costTag = new ListTag();
            for (ItemCost ic : t.resourceCost) {
                CompoundTag ct = new CompoundTag();
                ct.putString("Item", BuiltInRegistries.ITEM.getKey(ic.item()).toString());
                ct.putInt("Amount", ic.amount());
                ct.putInt("Have", invForEra.getStock(ic.item()));
                costTag.add(ct);
            }
            tt.put("ResourceCost", costTag);
            tt.putInt("RequiredResidents", t.requiredResidents);
            tt.putInt("ActiveResidents", activeResidents);
            tt.putBoolean("ResidentsMet", t.requiredResidents <= 0 || activeResidents >= t.requiredResidents);
            ListTag reqBuildTag = new ListTag();
            for (BuildingDef.BuildingRequirement req : t.requiredBuildings) {
                CompoundTag rt = new CompoundTag();
                rt.putString("DefId", req.defId());
                rt.putInt("Count", req.count());
                long have = buildings.stream().filter(b -> b.defId.equals(req.defId())).count();
                rt.putInt("Have", (int) have);
                reqBuildTag.add(rt);
            }
            tt.put("RequiredBuildings", reqBuildTag);
            ListTag unlockedTag = new ListTag();
            for (String bId : t.unlockedBuildingIds) {
                BuildingDataHandler.get(bId).ifPresent(def -> {
                    CompoundTag ut = new CompoundTag();
                    ut.putString("DefId", bId);
                    ut.putString("IconItem", def.iconItem);
                    ut.putString("Category", def.category);
                    ListTag unlockCostTag = new ListTag();
                    for (ItemCost c : def.constructionCost) {
                        CompoundTag ct = new CompoundTag();
                        ct.putString("Item", BuiltInRegistries.ITEM.getKey(c.item()).toString());
                        ct.putInt("Amount", c.amount());
                        unlockCostTag.add(ct);
                    }
                    ut.put("Cost", unlockCostTag);
                    ut.putInt("RequiredResidents", def.requiredResidents);
                    ListTag unlockReqBuildTag = new ListTag();
                    for (BuildingDef.BuildingRequirement r : def.requiredBuildings) {
                        CompoundTag rt = new CompoundTag();
                        rt.putString("DefId", r.defId());
                        rt.putInt("Count", r.count());
                        unlockReqBuildTag.add(rt);
                    }
                    ut.put("RequiredBuildings", unlockReqBuildTag);
                    ut.putBoolean("HasProduction", !def.production.isEmpty());
                    unlockedTag.add(ut);
                });
            }
            tt.put("UnlockedBuildings", unlockedTag);
            eraTransitionsTag.add(tt);
        }
        hub.put("EraTransitions", eraTransitionsTag);
        ListTag boostedTag = new ListTag();
        for (String id : getBoostedBuildingIds()) boostedTag.add(StringTag.valueOf(id));
        hub.put("BoostedBuildings", boostedTag);

        ListTag cqTag = new ListTag();
        constructionQueue.forEach(e -> cqTag.add(QueueEntry.serialize(e)));
        hub.put("ConstructionQueue", cqTag);

        TownInventory inv = getTownInventory();
        String orientation = getOrDeriveOrientation();
        Set<String> gatedIds = EraTransitionDataHandler.getAllGatedBuildingIds();
        // Collect buildings that unlock on the immediately next era transition (shown locked in catalog)
        Set<String> nextEraIds = new HashSet<>();
        for (EraTransitionDef t : EraTransitionDataHandler.getAvailableTransitions(currentEra, orientation)) {
            nextEraIds.addAll(t.unlockedBuildingIds);
        }
        nextEraIds.removeAll(unlockedBuildingIds);
        List<BuildingDef> sortedDefs = BuildingDataHandler.getAll().stream()
            .filter(def -> !def.terrainMatching && !def.id.startsWith("settlement"))
            .filter(def -> !gatedIds.contains(def.id) || unlockedBuildingIds.contains(def.id) || nextEraIds.contains(def.id))
            .sorted(Comparator.<BuildingDef, Boolean>comparing(def -> nextEraIds.contains(def.id))
                .thenComparingInt(def -> BuildingListDataHandler.getIndex(def.id)))
            .toList();
        ListTag catalogTag = new ListTag();
        for (BuildingDef def : sortedDefs) {
            CompoundTag dt = new CompoundTag();
            dt.putString("Id", def.id);
            dt.putString("Category", def.category);
            dt.putString("IconItem", def.iconItem);
            dt.putString("Nbt", def.nbt.toString());
            dt.putBoolean("HasBuilt", buildings.stream().anyMatch(b -> b.defId.equals(def.id)));
            if (!def.nbtLevels.isEmpty()) {
                ListTag nbtLevelsTag = new ListTag();
                for (BuildingDef.NbtLevel nl : def.nbtLevels) {
                    nbtLevelsTag.add(StringTag.valueOf(nl.nbt().toString()));
                }
                dt.put("NbtLevels", nbtLevelsTag);
            }
            if (nextEraIds.contains(def.id)) dt.putBoolean("NextEra", true);
            ListTag costTag = new ListTag();
            for (ItemCost ic : def.constructionCost) {
                CompoundTag ct = new CompoundTag();
                ct.putString("Item", BuiltInRegistries.ITEM.getKey(ic.item()).toString());
                ct.putInt("Amount", ic.amount());
                costTag.add(ct);
            }
            dt.put("ConstructionCost", costTag);

            ListTag prodTag = new ListTag();
            for (ProductionEntry pe : def.production) {
                CompoundTag pt = new CompoundTag();
                pt.putString("Item", BuiltInRegistries.ITEM.getKey(pe.item()).toString());
                pt.putInt("Amount", pe.amount());
                pt.putInt("EveryTicks", pe.everyTicks());
                prodTag.add(pt);
            }
            dt.put("Production", prodTag);

            ListTag transTag = new ListTag();
            for (TransformationRecipe tr : def.transformations) {
                CompoundTag tt = new CompoundTag();
                ListTag inputsTag = new ListTag();
                for (ItemCost ic : tr.inputs()) {
                    CompoundTag inp = new CompoundTag();
                    inp.putString("Item", BuiltInRegistries.ITEM.getKey(ic.item()).toString());
                    inp.putInt("Amount", ic.amount());
                    inputsTag.add(inp);
                }
                tt.put("Inputs", inputsTag);
                tt.putString("OutputItem", BuiltInRegistries.ITEM.getKey(tr.outputItem()).toString());
                tt.putInt("OutputAmount", tr.outputAmount());
                tt.putInt("EveryTicks", def.transformEveryTicks);
                transTag.add(tt);
            }
            dt.put("Transformations", transTag);
            dt.putDouble("ProductionBonus", def.productionBonus);
            if (def.residents > 0) {
                dt.putInt("Residents", def.residents);
                dt.putFloat("BaseConsumptionPerResident", def.consumptionPerResident);
                int maxLevel = def.upgrades.size();
                if (maxLevel > 0) {
                    BuildingDef.ResolvedBuildingStats maxStats = def.resolveAtLevel(maxLevel);
                    dt.putFloat("MaxConsumptionPerResident", maxStats.resolvedConsumptionPerResident());
                    dt.putInt("MaxResidents", maxStats.resolvedResidents());
                }
            }
            if (def.requiredResidents > 0) {
                dt.putInt("RequiredResidents", def.requiredResidents);
            }
            if (!def.requiredBuildings.isEmpty()) {
                ListTag reqBuildingsTag = new ListTag();
                for (BuildingDef.BuildingRequirement req : def.requiredBuildings) {
                    CompoundTag rt = new CompoundTag();
                    rt.putString("DefId", req.defId());
                    rt.putInt("Count", req.count());
                    long have = buildings.stream().filter(b -> b.defId.equals(req.defId())).count();
                    rt.putInt("Have", (int) have);
                    reqBuildingsTag.add(rt);
                }
                dt.put("RequiredBuildings", reqBuildingsTag);
            }

            catalogTag.add(dt);
        }
        hub.put("BuildingCatalog", catalogTag);

        // Stock snapshot: resources are pre-deducted at queue time via removeStock,
        // so inv.getStock already reflects what is freely available.
        CompoundTag stockTag = new CompoundTag();
        Stream.concat(
            BuildingDataHandler.getAll().stream()
                .flatMap(def -> Stream.concat(
                    def.constructionCost.stream().map(ItemCost::item),
                    def.upgrades.stream().flatMap(u -> u.upgradeCost().stream().map(ItemCost::item))
                )),
            EraTransitionDataHandler.getAll().stream()
                .flatMap(t -> t.resourceCost.stream().map(ItemCost::item))
        ).distinct()
            .forEach(item -> stockTag.putInt(
                BuiltInRegistries.ITEM.getKey(item).toString(), inv.getStock(item)));
        hub.put("StockSnapshot", stockTag);

        // Placed buildings list for the upgrade tab (non-road, non-settlement buildings).
        List<PlacedBuilding> upgradeBuildings = buildings.stream()
            .filter(b -> {
                BuildingDef bDef = BuildingDataHandler.get(b.defId).orElse(null);
                return bDef != null && !bDef.terrainMatching && !b.defId.startsWith("settlement") && !bDef.upgrades.isEmpty();
            })
            .sorted((a, b2) -> {
                BuildingDef da = BuildingDataHandler.get(a.defId).orElse(null);
                BuildingDef db = BuildingDataHandler.get(b2.defId).orElse(null);
                int maxA = da != null ? Math.max(da.upgrades.size(), da.nbtLevels.size()) : 0;
                int maxB = db != null ? Math.max(db.upgrades.size(), db.nbtLevels.size()) : 0;
                boolean atMaxA = maxA > 0 && a.getUpgradeLevel() >= maxA;
                boolean atMaxB = maxB > 0 && b2.getUpgradeLevel() >= maxB;
                if (atMaxA != atMaxB) return atMaxA ? 1 : -1;
                return Integer.compare(BuildingListDataHandler.getIndex(a.defId), BuildingListDataHandler.getIndex(b2.defId));
            })
            .toList();
        ListTag upgradeBuildingsTag = new ListTag();
        for (PlacedBuilding b : upgradeBuildings) {
            BuildingDef bDef = BuildingDataHandler.get(b.defId).orElse(null);
            if (bDef == null) continue;
            CompoundTag ubt = new CompoundTag();
            ubt.putString("DefId", b.defId);
            ubt.putLong("WorldPos", b.worldPos.asLong());
            ubt.putInt("UpgradeLevel", b.getUpgradeLevel());
            ubt.putString("Category", bDef.category);
            ubt.putString("IconItem", bDef.iconItem);
            upgradeBuildingsTag.add(ubt);
        }
        hub.put("UpgradeBuildings", upgradeBuildingsTag);

        // Summary data for the floating summary widget
        CompoundTag summaryData = new CompoundTag();
        summaryData.putString("Orientation", getOrDeriveOrientation());
        int freeSlotsBuildings = 0, freeSlotsJobs = 0, freeSlotsGardens = 0;
        for (ConnectionPoint cp : freeConnections) {
            String pool = cp.targetName();
            if (pool.contains("houses"))       freeSlotsBuildings++;
            else if (pool.contains("jobs"))    freeSlotsJobs++;
            else if (pool.contains("gardens")) freeSlotsGardens++;
        }
        summaryData.putInt("FreeSlotsBuildings", freeSlotsBuildings);
        summaryData.putInt("FreeSlotsJobs", freeSlotsJobs);
        summaryData.putInt("FreeSlotsGardens", freeSlotsGardens);
        int totalRes = getTotalResidents();
        int foodDemand = (int) Math.ceil(computeTotalFoodDemandFloat());
        summaryData.putInt("TotalResidents", totalRes);
        summaryData.putInt("ActiveResidents", activeResidents);
        summaryData.putInt("TotalFoodDemand", foodDemand);
        hub.put("SummaryData", summaryData);
        hub.putInt("TotalResidents", totalRes);
        hub.putInt("ActiveResidents", activeResidents);
        hub.putInt("TotalFoodDemand", foodDemand);

        // Active quests: embed def info (title/desc keys, icon) so client can render cards
        ListTag questsTag = new ListTag();
        for (Quest q : activeQuests) {
            CompoundTag qt = new CompoundTag();
            qt.putString("QuestId", q.questId);
            qt.putString("DefId", q.defId);
            qt.putString("QuestType", q.questType != null ? q.questType : "TASK");
            qt.putLong("ExpiryTime", q.expiryTime);
            QuestDataHandler.get(q.defId).ifPresent(def -> {
                qt.putString("TitleKey", def.titleKey());
                qt.putString("DescKey", def.descKey());
                qt.putString("Icon", def.icon());
            });
            ListTag condsTag = new ListTag();
            for (Quest.Condition c : q.conditions) {
                CompoundTag ct = new CompoundTag();
                ct.putString("Type", c.type);
                if (c.item != null) ct.putString("Item", BuiltInRegistries.ITEM.getKey(c.item).toString());
                ct.putInt("Required", c.required);
                ct.putInt("Received", c.received);
                condsTag.add(ct);
            }
            qt.put("Conditions", condsTag);
            if (q.reward != null) {
                CompoundTag rt = new CompoundTag();
                rt.putString("Type", q.reward.type);
                if (q.reward.item != null) rt.putString("Item", BuiltInRegistries.ITEM.getKey(q.reward.item).toString());
                rt.putInt("Amount", q.reward.amount);
                qt.put("Reward", rt);
            }
            questsTag.add(qt);
        }
        hub.put("Quests", questsTag);

        return hub;
    }

    // -------------------------------------------------------------------------
    // Targeted serialization helpers (used by targeted S2C packets)
    // -------------------------------------------------------------------------

    public CompoundTag getStockUpdateData(BlockPos anchorPos) {
        CompoundTag tag = new CompoundTag();
        tag.put("AnchorPos", NbtUtils.writeBlockPos(anchorPos));
        TownInventory inv = getTownInventory();
        CompoundTag stockTag = new CompoundTag();
        // Items produced by placed buildings (for chest display)
        Set<Item> produced = new java.util.LinkedHashSet<>();
        for (PlacedBuilding b : buildings) {
            BuildingDataHandler.get(b.defId).ifPresent(def -> {
                def.production.forEach(p -> produced.add(p.item()));
                def.transformations.forEach(t -> produced.add(t.outputItem()));
            });
        }
        produced.forEach(item ->
            stockTag.putInt(BuiltInRegistries.ITEM.getKey(item).toString(), inv.getStock(item)));
        // Items needed for costs (for affordability checks in construction/upgrade/era tabs)
        Stream.concat(
            BuildingDataHandler.getAll().stream()
                .flatMap(def -> Stream.concat(
                    def.constructionCost.stream().map(ItemCost::item),
                    def.upgrades.stream().flatMap(u -> u.upgradeCost().stream().map(ItemCost::item))
                )),
            EraTransitionDataHandler.getAll().stream()
                .flatMap(t -> t.resourceCost.stream().map(ItemCost::item))
        ).distinct()
            .filter(item -> !produced.contains(item))
            .forEach(item -> stockTag.putInt(
                BuiltInRegistries.ITEM.getKey(item).toString(), inv.getStock(item)));
        tag.put("StockSnapshot", stockTag);
        return tag;
    }

    public CompoundTag getBuildingListData(BlockPos anchorPos) {
        CompoundTag tag = new CompoundTag();
        tag.put("AnchorPos", NbtUtils.writeBlockPos(anchorPos));
        tag.put("MapData", getTownMapData());
        ListTag cqTag = new ListTag();
        constructionQueue.forEach(e -> cqTag.add(QueueEntry.serialize(e)));
        tag.put("ConstructionQueue", cqTag);
        List<PlacedBuilding> upgradeBuildings2 = buildings.stream()
            .filter(b -> {
                BuildingDef bDef = BuildingDataHandler.get(b.defId).orElse(null);
                return bDef != null && !bDef.terrainMatching && !b.defId.startsWith("settlement") && !bDef.upgrades.isEmpty();
            })
            .sorted((a, b2) -> {
                BuildingDef da = BuildingDataHandler.get(a.defId).orElse(null);
                BuildingDef db = BuildingDataHandler.get(b2.defId).orElse(null);
                int maxA = da != null ? Math.max(da.upgrades.size(), da.nbtLevels.size()) : 0;
                int maxB = db != null ? Math.max(db.upgrades.size(), db.nbtLevels.size()) : 0;
                boolean atMaxA = maxA > 0 && a.getUpgradeLevel() >= maxA;
                boolean atMaxB = maxB > 0 && b2.getUpgradeLevel() >= maxB;
                if (atMaxA != atMaxB) return atMaxA ? 1 : -1;
                return Integer.compare(BuildingListDataHandler.getIndex(a.defId), BuildingListDataHandler.getIndex(b2.defId));
            })
            .toList();
        ListTag upgradeBuildingsTag = new ListTag();
        for (PlacedBuilding b : upgradeBuildings2) {
            BuildingDef bDef = BuildingDataHandler.get(b.defId).orElse(null);
            if (bDef == null) continue;
            CompoundTag ubt = new CompoundTag();
            ubt.putString("DefId", b.defId);
            ubt.putLong("WorldPos", b.worldPos.asLong());
            ubt.putInt("UpgradeLevel", b.getUpgradeLevel());
            ubt.putString("Category", bDef.category);
            ubt.putString("IconItem", bDef.iconItem);
            upgradeBuildingsTag.add(ubt);
        }
        tag.put("UpgradeBuildings", upgradeBuildingsTag);
        return tag;
    }

    public CompoundTag getQuestUpdateData(BlockPos anchorPos) {
        CompoundTag tag = new CompoundTag();
        tag.put("AnchorPos", NbtUtils.writeBlockPos(anchorPos));
        ListTag questsTag = new ListTag();
        for (Quest q : activeQuests) {
            CompoundTag qt = new CompoundTag();
            qt.putString("QuestId", q.questId);
            qt.putString("DefId", q.defId);
            qt.putString("QuestType", q.questType != null ? q.questType : "TASK");
            qt.putLong("ExpiryTime", q.expiryTime);
            QuestDataHandler.get(q.defId).ifPresent(def -> {
                qt.putString("TitleKey", def.titleKey());
                qt.putString("DescKey", def.descKey());
                qt.putString("Icon", def.icon());
            });
            ListTag condsTag = new ListTag();
            for (Quest.Condition c : q.conditions) {
                CompoundTag ct = new CompoundTag();
                ct.putString("Type", c.type);
                if (c.item != null) ct.putString("Item", BuiltInRegistries.ITEM.getKey(c.item).toString());
                ct.putInt("Required", c.required);
                ct.putInt("Received", c.received);
                condsTag.add(ct);
            }
            qt.put("Conditions", condsTag);
            if (q.reward != null) {
                CompoundTag rt = new CompoundTag();
                rt.putString("Type", q.reward.type);
                if (q.reward.item != null) rt.putString("Item", BuiltInRegistries.ITEM.getKey(q.reward.item).toString());
                rt.putInt("Amount", q.reward.amount);
                qt.put("Reward", rt);
            }
            questsTag.add(qt);
        }
        tag.put("Quests", questsTag);
        return tag;
    }

    public CompoundTag getEraUpdateData(BlockPos anchorPos) {
        CompoundTag tag = new CompoundTag();
        tag.put("AnchorPos", NbtUtils.writeBlockPos(anchorPos));
        tag.putInt("CurrentEra", currentEra);
        tag.putString("CurrentOrientation", getOrDeriveOrientation());
        tag.putInt("CurrentWeight", getCurrentWeight());
        tag.putInt("MaxWeight", getCurrentMaxWeight());
        List<EraTransitionDef> availableTransitions = getAvailableTransitions();
        ListTag eraTransitionsTag = new ListTag();
        TownInventory invForEra = getTownInventory();
        for (EraTransitionDef t : availableTransitions) {
            CompoundTag tt = new CompoundTag();
            tt.putString("Id", t.id);
            tt.putString("OrientationLabel", t.orientationLabel);
            tt.putString("IconItem", t.iconItem);
            int effMinWeight = effectiveMinWeight(t);
            tt.putBoolean("PrereqsMet", meetsEraTransitionPrereqs(t));
            tt.putInt("RequiredWeight", effMinWeight);
            tt.putInt("CurrentWeight", getCurrentWeight());
            tt.putInt("MaxWeight", getCurrentMaxWeight());
            tt.putBoolean("WeightMet", getCurrentWeight() >= effMinWeight && getCurrentWeight() <= getCurrentMaxWeight());
            ListTag costTag = new ListTag();
            for (ItemCost ic : t.resourceCost) {
                CompoundTag ct = new CompoundTag();
                ct.putString("Item", BuiltInRegistries.ITEM.getKey(ic.item()).toString());
                ct.putInt("Amount", ic.amount());
                ct.putInt("Have", invForEra.getStock(ic.item()));
                costTag.add(ct);
            }
            tt.put("ResourceCost", costTag);
            tt.putInt("RequiredResidents", t.requiredResidents);
            tt.putInt("ActiveResidents", activeResidents);
            tt.putBoolean("ResidentsMet", t.requiredResidents <= 0 || activeResidents >= t.requiredResidents);
            ListTag reqBuildTag = new ListTag();
            for (BuildingDef.BuildingRequirement req : t.requiredBuildings) {
                CompoundTag rt = new CompoundTag();
                rt.putString("DefId", req.defId());
                rt.putInt("Count", req.count());
                long have = buildings.stream().filter(b -> b.defId.equals(req.defId())).count();
                rt.putInt("Have", (int) have);
                reqBuildTag.add(rt);
            }
            tt.put("RequiredBuildings", reqBuildTag);
            ListTag unlockedTag = new ListTag();
            for (String bId : t.unlockedBuildingIds) {
                BuildingDataHandler.get(bId).ifPresent(def -> {
                    CompoundTag ut = new CompoundTag();
                    ut.putString("DefId", bId);
                    ut.putString("IconItem", def.iconItem);
                    ut.putString("Category", def.category);
                    ListTag unlockCostTag = new ListTag();
                    for (ItemCost c : def.constructionCost) {
                        CompoundTag ct = new CompoundTag();
                        ct.putString("Item", BuiltInRegistries.ITEM.getKey(c.item()).toString());
                        ct.putInt("Amount", c.amount());
                        unlockCostTag.add(ct);
                    }
                    ut.put("Cost", unlockCostTag);
                    ut.putInt("RequiredResidents", def.requiredResidents);
                    ListTag unlockReqBuildTag = new ListTag();
                    for (BuildingDef.BuildingRequirement r : def.requiredBuildings) {
                        CompoundTag rt = new CompoundTag();
                        rt.putString("DefId", r.defId());
                        rt.putInt("Count", r.count());
                        unlockReqBuildTag.add(rt);
                    }
                    ut.put("RequiredBuildings", unlockReqBuildTag);
                    ut.putBoolean("HasProduction", !def.production.isEmpty());
                    unlockedTag.add(ut);
                });
            }
            tt.put("UnlockedBuildings", unlockedTag);
            eraTransitionsTag.add(tt);
        }
        tag.put("EraTransitions", eraTransitionsTag);
        ListTag boostedTag = new ListTag();
        for (String id : getBoostedBuildingIds()) boostedTag.add(StringTag.valueOf(id));
        tag.put("BoostedBuildings", boostedTag);
        return tag;
    }

    public CompoundTag getCitizenUpdateData(BlockPos anchorPos) {
        CompoundTag tag = new CompoundTag();
        tag.put("AnchorPos", NbtUtils.writeBlockPos(anchorPos));
        tag.putInt("TotalResidents", getTotalResidents());
        tag.putInt("ActiveResidents", activeResidents);
        tag.putInt("TotalFoodDemand", (int) Math.ceil(computeTotalFoodDemandFloat()));
        return tag;
    }

    // -------------------------------------------------------------------------
    // Quest management
    // -------------------------------------------------------------------------

    public List<Quest> getActiveQuests() { return Collections.unmodifiableList(activeQuests); }

    public void addQuest(Quest q) { activeQuests.add(q); }

    public void removeQuest(String questId) { activeQuests.removeIf(q -> q.questId.equals(questId)); }

    public Set<String> getDismissedNoteIds() { return Collections.unmodifiableSet(dismissedNoteIds); }

    public void addDismissedNote(String defId) { dismissedNoteIds.add(defId); }

    // Extended accepted set: production items + items needed by active DELIVERY quests.
    public Set<Item> buildAcceptedItemSetWithQuests() {
        Set<Item> accepted = buildAcceptedItemSet();
        for (Quest q : activeQuests) {
            for (Quest.Condition c : q.conditions) {
                if ("DELIVERY".equals(c.type) && c.item != null && c.received < c.required) {
                    accepted.add(c.item);
                }
            }
        }
        return accepted;
    }

    // Applies deposited units to matching DELIVERY quest conditions.
    // Returns total units consumed across all matching conditions.
    public int applyToDeliveryQuests(Item item, int amount) {
        int totalConsumed = 0;
        for (Quest q : activeQuests) {
            for (Quest.Condition c : q.conditions) {
                if ("DELIVERY".equals(c.type) && c.item == item) {
                    int needed  = c.required - c.received;
                    int consume = Math.min(needed, amount - totalConsumed);
                    if (consume > 0) {
                        c.received  += consume;
                        totalConsumed += consume;
                    }
                }
            }
        }
        return totalConsumed;
    }

    // Tries to add item to town stock without checking the accepted set.
    // Used after quest consumption to route any remainder into stock.
    public int tryAddToStockUnchecked(Item item, int amount) {
        TownInventory inv = getTownInventory();
        int maxStock = inv.getMaxStock(item);
        if (maxStock == 0) maxStock = 999;
        int room = maxStock - inv.getStock(item);
        if (room <= 0) return 0;
        int toAdd = Math.min(amount, room);
        inv.addStock(List.of(new ItemCost(item, toAdd)));
        return toAdd;
    }

    // Collects all items produced or transformed by currently placed buildings in this town.
    // Used server-side to validate deposit requests.
    public Set<Item> buildAcceptedItemSet() {
        Set<Item> accepted = new HashSet<>();
        for (PlacedBuilding b : buildings) {
            BuildingDataHandler.get(b.defId).ifPresent(def -> {
                def.production.forEach(p -> accepted.add(p.item()));
                def.transformations.forEach(t -> accepted.add(t.outputItem()));
            });
        }
        return accepted;
    }

    // Tries to add `amount` of `item` to the town stock.
    // Returns how many units were actually accepted (may be less if near cap, 0 if rejected).
    // Fallback cap of 999 applies for items whose getMaxStock() returns 0 (transformation outputs).
    public int tryDepositItem(Item item, int amount, Set<Item> acceptedItems) {
        if (!acceptedItems.contains(item)) return 0;
        TownInventory inv = getTownInventory();
        int maxStock = inv.getMaxStock(item);
        if (maxStock == 0) maxStock = 999;
        int room = maxStock - inv.getStock(item);
        if (room <= 0) return 0;
        int toAdd = Math.min(amount, room);
        inv.addStock(List.of(new ItemCost(item, toAdd)));
        return toAdd;
    }

    public Map<Item, Integer> getReserveStock() { return reserveStock; }

    public List<PlacedBuilding> getBuildings() { return buildings; }
    public List<UUID> getBuilderNpcIds() { return builderNpcIds; }
    public int getTargetBuilderCount() { return targetBuilderCount; }

    // Appends a builder UUID in the next available slot. No-op if already registered.
    public void addBuilderNpcId(UUID id) {
        if (!builderNpcIds.contains(id)) builderNpcIds.add(id);
    }

    // Replaces the UUID at the given slot index (used by TickScheduler on respawn).
    public void setBuilderNpcIdAtSlot(int slot, UUID id) {
        while (builderNpcIds.size() <= slot) builderNpcIds.add(null);
        builderNpcIds.set(slot, id);
    }

    // Returns the slot index for a given builder UUID, or -1 if not found.
    public int getBuilderSlot(UUID id) { return builderNpcIds.indexOf(id); }

    public void setActiveBuild(int slot, ActiveBuildState state) {
        activeBuilds.put(slot, state);
    }

    public void clearActiveBuild(int slot) {
        activeBuilds.remove(slot);
    }

    public ActiveBuildState getActiveBuild(int slot) {
        return activeBuilds.get(slot);
    }

    // Claim-lock helpers for multi-builder queue coordination.
    public boolean claimQueueEntry(int index, UUID builderId) {
        UUID existing = queueIndexClaims.get(index);
        if (existing != null && !existing.equals(builderId)) return false;
        queueIndexClaims.put(index, builderId);
        return true;
    }

    public void releaseQueueClaim(int index, UUID builderId) {
        queueIndexClaims.remove(index, builderId);
    }

    public boolean isQueueEntryClaimedByOther(int index, UUID builderId) {
        UUID existing = queueIndexClaims.get(index);
        return existing != null && !existing.equals(builderId);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        ListTag builderIdsTag = new ListTag();
        for (UUID id : builderNpcIds) {
            CompoundTag idTag = new CompoundTag();
            if (id != null) idTag.putUUID("Id", id);
            builderIdsTag.add(idTag);
        }
        tag.put("BuilderNpcIds", builderIdsTag);
        tag.putInt("TargetBuilderCount", targetBuilderCount);
        ListTag buildingsTag = new ListTag();
        buildings.forEach(b -> buildingsTag.add(b.toNbt()));
        tag.put("Buildings", buildingsTag);
        ListTag connTag = new ListTag();
        freeConnections.forEach(c -> connTag.add(connectionToNbt(c)));
        tag.put("FreeConnections", connTag);
        CompoundTag reserveTag = new CompoundTag();
        reserveStock.forEach((item, qty) ->
            reserveTag.putInt(BuiltInRegistries.ITEM.getKey(item).toString(), qty));
        tag.put("ReserveStock", reserveTag);
        ListTag zonesTag = new ListTag();
        for (BoundingBox bb : blockedZones) {
            CompoundTag zTag = new CompoundTag();
            zTag.putInt("MinX", bb.minX()); zTag.putInt("MinY", bb.minY()); zTag.putInt("MinZ", bb.minZ());
            zTag.putInt("MaxX", bb.maxX()); zTag.putInt("MaxY", bb.maxY()); zTag.putInt("MaxZ", bb.maxZ());
            zonesTag.add(zTag);
        }
        tag.put("BlockedZones", zonesTag);
        ListTag cqTag = new ListTag();
        constructionQueue.forEach(e -> cqTag.add(QueueEntry.serialize(e)));
        tag.put("ConstructionQueue", cqTag);
        tag.putInt("ConstructionQueueCursor", constructionQueueCursor);
        CompoundTag qrTag = new CompoundTag();
        queueReservedStock.forEach((item, qty) ->
            qrTag.putInt(BuiltInRegistries.ITEM.getKey(item).toString(), qty));
        tag.put("QueueReservedStock", qrTag);
        tag.putInt("CurrentEra", currentEra);
        tag.putString("CurrentEraPath", currentEraPath);
        tag.putString("CurrentOrientation", currentOrientation);
        ListTag unlockedTag = new ListTag();
        unlockedBuildingIds.forEach(id -> unlockedTag.add(StringTag.valueOf(id)));
        tag.put("UnlockedBuildingIds", unlockedTag);
        tag.putInt("ActiveResidents", activeResidents);
        tag.putInt("CurrentMaxWeight", currentMaxWeight);
        CompoundTag cwTag = new CompoundTag();
        categoryWeights.forEach(cwTag::putInt);
        tag.put("CategoryWeights", cwTag);
        ListTag activeQuestsTag = new ListTag();
        activeQuests.forEach(q -> activeQuestsTag.add(q.toNbt()));
        tag.put("ActiveQuests", activeQuestsTag);
        ListTag dismissedTag = new ListTag();
        dismissedNoteIds.forEach(id -> dismissedTag.add(StringTag.valueOf(id)));
        tag.put("DismissedNotes", dismissedTag);
        if (!activeBuilds.isEmpty()) {
            CompoundTag activeBuildsTag = new CompoundTag();
            activeBuilds.forEach((slot, state) -> activeBuildsTag.put(String.valueOf(slot), activeBuildStateToNbt(state)));
            tag.put("ActiveBuilds", activeBuildsTag);
        }
        return tag;
    }

    public static Town fromNbt(CompoundTag tag) {
        Town town = new Town();
        town.name = tag.contains("Name") ? tag.getString("Name") : "Unknown Town";
        // Backward compat: old saves stored a single BuilderNpcId UUID.
        if (tag.hasUUID("BuilderNpcId")) {
            town.builderNpcIds.add(tag.getUUID("BuilderNpcId"));
        }
        if (tag.contains("BuilderNpcIds")) {
            tag.getList("BuilderNpcIds", Tag.TAG_COMPOUND).forEach(t -> {
                CompoundTag idTag = (CompoundTag) t;
                town.builderNpcIds.add(idTag.hasUUID("Id") ? idTag.getUUID("Id") : null);
            });
        }
        town.targetBuilderCount = tag.contains("TargetBuilderCount") ? tag.getInt("TargetBuilderCount") : 1;
        tag.getList("Buildings", Tag.TAG_COMPOUND)
            .forEach(t -> town.buildings.add(PlacedBuilding.fromNbt((CompoundTag) t)));
        tag.getList("FreeConnections", Tag.TAG_COMPOUND)
            .forEach(t -> town.freeConnections.add(connectionFromNbt((CompoundTag) t)));
        if (tag.contains("ReserveStock")) {
            CompoundTag reserveTag = tag.getCompound("ReserveStock");
            for (String key : reserveTag.getAllKeys()) {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(key));
                town.reserveStock.put(item, reserveTag.getInt(key));
            }
        }
        tag.getList("BlockedZones", Tag.TAG_COMPOUND).forEach(t -> {
            CompoundTag zTag = (CompoundTag) t;
            town.blockedZones.add(new BoundingBox(
                zTag.getInt("MinX"), zTag.getInt("MinY"), zTag.getInt("MinZ"),
                zTag.getInt("MaxX"), zTag.getInt("MaxY"), zTag.getInt("MaxZ")
            ));
        });
        // BootstrapQueue: silently ignored - bootstrap system removed.
        if (tag.contains("ConstructionQueue")) {
            ListTag cqTagCompound = tag.getList("ConstructionQueue", Tag.TAG_COMPOUND);
            if (!cqTagCompound.isEmpty()) {
                // New format: compound tags with Type/DefId/etc fields
                cqTagCompound.forEach(t -> town.constructionQueue.add(QueueEntry.deserialize((CompoundTag) t)));
            } else {
                // Backward compat: old saves stored plain string defIds
                tag.getList("ConstructionQueue", Tag.TAG_STRING)
                    .forEach(t -> town.constructionQueue.add(new QueueEntry.NewBuild(t.getAsString())));
            }
        }
        town.constructionQueueCursor = tag.contains("ConstructionQueueCursor")
            ? tag.getInt("ConstructionQueueCursor") : 0;
        if (tag.contains("QueueReservedStock")) {
            CompoundTag qrTag = tag.getCompound("QueueReservedStock");
            for (String key : qrTag.getAllKeys()) {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(key));
                town.queueReservedStock.put(item, qrTag.getInt(key));
            }
        }
        town.currentEra = tag.contains("CurrentEra") ? tag.getInt("CurrentEra") : 0;
        town.currentEraPath = tag.contains("CurrentEraPath") ? tag.getString("CurrentEraPath") : "";
        town.currentOrientation = tag.contains("CurrentOrientation") ? tag.getString("CurrentOrientation") : "";
        if (tag.contains("UnlockedBuildingIds")) {
            tag.getList("UnlockedBuildingIds", Tag.TAG_STRING)
                .forEach(t -> town.unlockedBuildingIds.add(t.getAsString()));
        }
        town.activeResidents = tag.contains("ActiveResidents") ? tag.getInt("ActiveResidents") : 0;
        // Backward compat: old saves use the formula 20 + era * 10; new saves store the explicit value.
        town.currentMaxWeight = tag.contains("CurrentMaxWeight")
            ? tag.getInt("CurrentMaxWeight")
            : 20 + town.currentEra * 10;
        if (tag.contains("CategoryWeights")) {
            CompoundTag cwTag = tag.getCompound("CategoryWeights");
            for (String key : cwTag.getAllKeys()) {
                town.categoryWeights.put(key, cwTag.getInt(key));
            }
        }
        if (tag.contains("ActiveQuests")) {
            tag.getList("ActiveQuests", Tag.TAG_COMPOUND)
                .forEach(t -> town.activeQuests.add(Quest.fromNbt((CompoundTag) t)));
        }
        if (tag.contains("DismissedNotes")) {
            tag.getList("DismissedNotes", Tag.TAG_STRING)
                .forEach(t -> town.dismissedNoteIds.add(t.getAsString()));
        }
        if (tag.contains("ActiveBuilds")) {
            CompoundTag activeBuildsTag = tag.getCompound("ActiveBuilds");
            for (String key : activeBuildsTag.getAllKeys()) {
                try {
                    int slot = Integer.parseInt(key);
                    ActiveBuildState state = activeBuildStateFromNbt(activeBuildsTag.getCompound(key));
                    if (state != null) town.activeBuilds.put(slot, state);
                } catch (NumberFormatException ignored) {}
            }
        }
        return town;
    }

    private static CompoundTag connectionToNbt(ConnectionPoint c) {
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", NbtUtils.writeBlockPos(c.pos()));
        tag.putString("Dir", c.direction().getName());
        tag.putString("Pool", c.targetName());
        if (c.failCount() > 0) tag.putInt("FailCount", c.failCount());
        return tag;
    }

    private static ConnectionPoint connectionFromNbt(CompoundTag tag) {
        BlockPos pos = NbtUtils.readBlockPos(tag.getCompound("Pos"));
        net.minecraft.core.Direction dir = net.minecraft.core.Direction.byName(tag.getString("Dir"));
        String pool = tag.getString("Pool");
        int failCount = tag.contains("FailCount") ? tag.getInt("FailCount") : 0;
        return new ConnectionPoint(pos, dir != null ? dir : net.minecraft.core.Direction.NORTH, pool, failCount);
    }

    private static CompoundTag activeBuildStateToNbt(ActiveBuildState s) {
        CompoundTag tag = new CompoundTag();
        tag.putString("DefId", s.defId());
        tag.put("PlacementPos", NbtUtils.writeBlockPos(s.placementPos()));
        tag.putString("Rotation", s.rotation().name());
        tag.put("ConnectionPos", NbtUtils.writeBlockPos(s.connectionPos()));
        tag.putString("ConnectionDir", s.connectionDir().getName());
        tag.putString("ConnectionTarget", s.connectionTarget());
        tag.put("EntryConnectorPos", NbtUtils.writeBlockPos(s.entryConnectorPos()));
        ListTag costTag = new ListTag();
        for (ItemCost c : s.cost()) {
            CompoundTag ct = new CompoundTag();
            ct.putString("Item", BuiltInRegistries.ITEM.getKey(c.item()).toString());
            ct.putInt("Amount", c.amount());
            costTag.add(ct);
        }
        tag.put("Cost", costTag);
        if (s.queueDefId() != null) tag.putString("QueueDefId", s.queueDefId());
        return tag;
    }

    private static ActiveBuildState activeBuildStateFromNbt(CompoundTag tag) {
        String defId = tag.getString("DefId");
        if (defId.isEmpty()) return null;
        BlockPos placementPos = NbtUtils.readBlockPos(tag.getCompound("PlacementPos"));
        Rotation rotation;
        try { rotation = Rotation.valueOf(tag.getString("Rotation")); }
        catch (IllegalArgumentException e) { rotation = Rotation.NONE; }
        BlockPos connectionPos = NbtUtils.readBlockPos(tag.getCompound("ConnectionPos"));
        Direction connectionDir = Direction.byName(tag.getString("ConnectionDir"));
        if (connectionDir == null) connectionDir = Direction.NORTH;
        String connectionTarget = tag.getString("ConnectionTarget");
        BlockPos entryConnectorPos = NbtUtils.readBlockPos(tag.getCompound("EntryConnectorPos"));
        ListTag costList = tag.getList("Cost", Tag.TAG_COMPOUND);
        java.util.List<ItemCost> cost = new ArrayList<>();
        for (int i = 0; i < costList.size(); i++) {
            CompoundTag ct = costList.getCompound(i);
            ResourceLocation rl = ResourceLocation.tryParse(ct.getString("Item"));
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                cost.add(new ItemCost(BuiltInRegistries.ITEM.get(rl), ct.getInt("Amount")));
            }
        }
        String queueDefId = tag.contains("QueueDefId") ? tag.getString("QueueDefId") : null;
        return new ActiveBuildState(defId, placementPos, rotation, connectionPos, connectionDir,
            connectionTarget, entryConnectorPos, cost, queueDefId);
    }
}
