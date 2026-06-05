package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.BlockPos;
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
    private UUID builderNpcId;
    private String name = "Unknown Town";
    // null until first NPC tick; empty list once bootstrap is complete (open phase).
    private List<String> bootstrapQueue = null;

    // Player-ordered queue of construction tasks (new builds and upgrades).
    // Resources are pre-reserved in queueReservedStock when an entry is added.
    private final List<QueueEntry> constructionQueue = new ArrayList<>();
    private int constructionQueueCursor = 0;
    // Resources deducted from town stock when buildings are added to the queue.
    // Released back to reserveStock on removal, or cleared when NPC places the building.
    private final Map<Item, Integer> queueReservedStock = new HashMap<>();

    // Max entries the queue can hold; matches VillageChestMenu.CHEST_SIZE (6x9 grid).
    public static final int QUEUE_CAPACITY = 54;

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

    // Returns all building defs whose entry pool matches the connection point, regardless of cost.
    // Used to distinguish PENDING_RESOURCES (compatible building exists but unaffordable) from DEAD (no compatible building).
    public List<BuildingDef> getPotentialBuildings(ConnectionPoint point) {
        return BuildingDataHandler.getAll().stream()
            .filter(def -> point.targetName().isEmpty() || def.entryPool.equals(point.targetName()))
            .toList();
    }

    // Bootstrap state queries
    public boolean isBootstrapInitialized() { return bootstrapQueue != null; }
    public boolean isBootstrapping() { return bootstrapQueue != null && !bootstrapQueue.isEmpty(); }
    public List<String> getBootstrapQueue() { return bootstrapQueue != null ? bootstrapQueue : List.of(); }

    // Lazy bootstrap initialization: called on the first NPC tick after village creation.
    // Reads orientation from the placed starter building and copies its full bootstrapBuildings
    // list into the queue. All listed buildings are placed; no random selection occurs.
    // Returns true if state changed (needs markDirty).
    public boolean initBootstrap() {
        if (bootstrapQueue != null) return false;

        for (PlacedBuilding b : buildings) {
            BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
            if (def == null || def.orientation.isEmpty()) continue;

            bootstrapQueue = new ArrayList<>(def.bootstrapBuildings);
            return true;
        }

        // No orientation building found (manual town, edge case): skip bootstrap.
        bootstrapQueue = new ArrayList<>();
        return false;
    }

    // Removes the first occurrence of the given building ID from the bootstrap queue.
    public void consumeBootstrapItem(String buildingId) {
        if (bootstrapQueue != null) bootstrapQueue.remove(buildingId);
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

            // --- Production bonus: village-wide perk (from BuildingDef) ---
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
        if (def == null || def.upgrades.isEmpty()) return false;

        // Compute effective level after all upgrades already pending in the queue.
        int effectiveLevel = building.getUpgradeLevel();
        for (QueueEntry entry : constructionQueue) {
            if (entry instanceof QueueEntry.Upgrade u && u.buildingWorldPos().equals(worldPos)) {
                effectiveLevel++;
            }
        }

        if (effectiveLevel >= def.upgrades.size()) return false;
        if (constructionQueue.size() >= QUEUE_CAPACITY) return false;

        List<ItemCost> cost = def.upgrades.get(effectiveLevel).upgradeCost();
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

        ListTag cqTag = new ListTag();
        constructionQueue.forEach(e -> cqTag.add(QueueEntry.serialize(e)));
        hub.put("ConstructionQueue", cqTag);

        TownInventory inv = getTownInventory();
        List<BuildingDef> sortedDefs = BuildingDataHandler.getAll().stream()
            .filter(def -> !def.terrainMatching && !def.id.startsWith("settlement"))
            .sorted(Comparator.<BuildingDef, Integer>comparing(def -> catalogCategoryOrder(def.category))
                .thenComparing(def -> def.id))
            .toList();
        ListTag catalogTag = new ListTag();
        for (BuildingDef def : sortedDefs) {
            CompoundTag dt = new CompoundTag();
            dt.putString("Id", def.id);
            dt.putString("Category", def.category);
            dt.putString("IconItem", def.iconItem);
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
            }

            catalogTag.add(dt);
        }
        hub.put("BuildingCatalog", catalogTag);

        // Stock snapshot: resources are pre-deducted at queue time via removeStock,
        // so inv.getStock already reflects what is freely available.
        CompoundTag stockTag = new CompoundTag();
        BuildingDataHandler.getAll().stream()
            .flatMap(def -> Stream.concat(
                def.constructionCost.stream().map(ItemCost::item),
                def.upgrades.stream().flatMap(u -> u.upgradeCost().stream().map(ItemCost::item))
            ))
            .distinct()
            .forEach(item -> stockTag.putInt(
                BuiltInRegistries.ITEM.getKey(item).toString(), inv.getStock(item)));
        hub.put("StockSnapshot", stockTag);

        // Placed buildings list for the upgrade tab (non-road, non-settlement buildings).
        ListTag upgradeBuildingsTag = new ListTag();
        for (PlacedBuilding b : buildings) {
            BuildingDef bDef = BuildingDataHandler.get(b.defId).orElse(null);
            if (bDef == null || bDef.terrainMatching || b.defId.startsWith("settlement")) continue;
            if ("buildings".equals(bDef.category) && bDef.upgrades.isEmpty()) continue;
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
        String orientation = "";
        for (PlacedBuilding b : buildings) {
            BuildingDef bDef = BuildingDataHandler.get(b.defId).orElse(null);
            if (bDef != null && !bDef.orientation.isEmpty()) {
                orientation = bDef.orientation;
                break;
            }
        }
        summaryData.putString("Orientation", orientation);
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
        hub.put("SummaryData", summaryData);

        return hub;
    }

    private static int catalogCategoryOrder(String category) {
        return switch (category) {
            case "jobs"        -> 0;
            case "buildings"   -> 1;
            case "gardens"     -> 2;
            case "town_center" -> 3;
            default            -> 4;
        };
    }

    public Map<Item, Integer> getReserveStock() { return reserveStock; }

    public List<PlacedBuilding> getBuildings() { return buildings; }
    public UUID getBuilderNpcId() { return builderNpcId; }
    public void setBuilderNpcId(UUID id) { this.builderNpcId = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        if (builderNpcId != null) tag.putUUID("BuilderNpcId", builderNpcId);
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
        if (bootstrapQueue != null) {
            ListTag bqTag = new ListTag();
            bootstrapQueue.forEach(s -> bqTag.add(StringTag.valueOf(s)));
            tag.put("BootstrapQueue", bqTag);
        }
        ListTag cqTag = new ListTag();
        constructionQueue.forEach(e -> cqTag.add(QueueEntry.serialize(e)));
        tag.put("ConstructionQueue", cqTag);
        tag.putInt("ConstructionQueueCursor", constructionQueueCursor);
        CompoundTag qrTag = new CompoundTag();
        queueReservedStock.forEach((item, qty) ->
            qrTag.putInt(BuiltInRegistries.ITEM.getKey(item).toString(), qty));
        tag.put("QueueReservedStock", qrTag);
        return tag;
    }

    public static Town fromNbt(CompoundTag tag) {
        Town town = new Town();
        town.name = tag.contains("Name") ? tag.getString("Name") : "Unknown Town";
        if (tag.hasUUID("BuilderNpcId")) town.builderNpcId = tag.getUUID("BuilderNpcId");
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
        if (tag.contains("BootstrapQueue")) {
            town.bootstrapQueue = new ArrayList<>();
            tag.getList("BootstrapQueue", Tag.TAG_STRING)
                .forEach(t -> town.bootstrapQueue.add(t.getAsString()));
        }
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
}
