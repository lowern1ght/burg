package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.behavior.executor.BuildExecutor;
import org.lowern1ght.burg.behavior.road.RoadSegment;
import org.lowern1ght.burg.behavior.role.CitizenRole;
import org.lowern1ght.burg.building.schematic.BuildSchematic;
import org.lowern1ght.burg.datapack.BuildingDataHandler;
import org.lowern1ght.burg.datapack.EraDef;
import org.lowern1ght.burg.datapack.EraTransitionDataHandler;
import org.lowern1ght.burg.datapack.EraTransitionDef;
import org.lowern1ght.burg.domain.settlement.Acquisition;
import org.lowern1ght.burg.domain.settlement.ConstructionIntent;
import org.lowern1ght.burg.domain.settlement.ConstructionQueue;
import org.lowern1ght.burg.domain.settlement.HubMode;
import org.lowern1ght.burg.domain.settlement.HubView;
import org.lowern1ght.burg.domain.settlement.QuestLog;
import org.lowern1ght.burg.domain.settlement.QuestRef;
import org.lowern1ght.burg.domain.settlement.Standing;
import org.lowern1ght.burg.domain.settlement.StructuralFlags;
import org.lowern1ght.burg.domain.settlement.StandingBook;
import org.lowern1ght.burg.domain.settlement.StockLedger;
import org.lowern1ght.burg.domain.settlement.vanilla.VanillaBindingDecision;
import org.lowern1ght.burg.domain.settlement.vanilla.VanillaBindingDecider;
import org.lowern1ght.burg.domain.settlement.vanilla.VanillaHouseFootprint;
import org.lowern1ght.burg.domain.shared.CitizenId;
import org.lowern1ght.burg.domain.shared.ItemId;
import org.lowern1ght.burg.entity.citizen.Citizens;
import org.lowern1ght.burg.infrastructure.config.BurgConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Town implements BuildExecutor {

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
    // Who lives here. Unordered; see getResidentNpcIds for why this is a roll and not a count.
    //
    // SUPERSEDED by `people` below, and kept only while the body window is being proved in game.
    // It was a list of loaded ENTITIES, so anybody in an unloaded chunk was invisible to every
    // rule that needed to look at them, and the population could never exceed what a server can
    // pathfind. Retire it once a settler has been seen embodied and re-embodied in the world.
    private final List<UUID> residentNpcIds = new ArrayList<>();
    // The town's people as records. Two thousand of these cost a few hundred kilobytes and tick
    // whether or not anybody is watching; a body is a puppet lent to one of them while a player
    // is near. See org.lowern1ght.burg.people.Person.
    private org.lowern1ght.burg.people.Population people =
        new org.lowern1ght.burg.people.Population();
    // Game time of the last arrival, so word of the town spreads at a pace rather than in a
    // burst the moment a house goes up. Persisted: without it, a reload readmits immediately.
    private long lastSettlerArrival = 0L;
    // Game time of the last raid fire, the per-town anchor the TickScheduler's raid tick
    // reads and stamps. The gate itself lives on RaidManager.tick(previousFire, gameTime)
    // and reads RaidConfig.current().cooldownTicks() for the cooldown; this field is the
    // town's slot in the wire site. Persisted: without it, a reload resets every town's
    // cooldown to the additive default (0L) and the first raid after restart fires at
    // the cooldown boundary from world load, not from when the town last fired — wrong
    // for towns that have already earned their first reprieve.
    private long lastRaidFireTick = 0L;
    // Which settler holds the trade at which building. Persisted, unlike the builder's queue
    // claims: a settler must keep the same trade across a reload or it is not a trade, it is a
    // thing it happens to be doing. Deriving "is this taken" by scanning for other settlers
    // instead would fail exactly while their chunks are unloaded, which is most of the time.
    private final Map<BlockPos, UUID> jobClaims = new HashMap<>();
    // How many builders should be active. Starts at 1, incremented by era transitions with unlock_new_builder.
    private int targetBuilderCount = 1;
    // Runtime-only claim map: queue index -> builder UUID. Prevents two builders from picking the same entry.
    // Not persisted: claims are re-established on the next idle tick after a server restart.
    private final Map<Integer, UUID> queueIndexClaims = new HashMap<>();
    private long nextEntryId = 0L;
    private String name = "Unknown Town";
    // -------------------------------------------------------------------------
    // ADR-0028 — quest log flip (the SoT promotion).
    //
    // Before this carve the MC `List<Quest> activeQuests` + `Map<String, Long>
    // questDefLastCompleted` pair was the SoT; `QuestLog` (the immutable
    // domain value object) was a cached projection rebuilt at every mutation
    // site (ADR-0012 + ADR-0016). The flip: `questLog` is the primary state
    // on `Town` and the MC legacy fields are now a derived view materialised
    // on demand — `getActiveQuests()` reads from `activeQuestMap` (the
    // engine-tick's cache of rich `Quest` data) and `getQuestDefLastCompleted()`
    // reads from `questLog.lastCompleted()`. NBT keys `ActiveQuests` and
    // `QuestDefLastCompleted` stay byte-identical: `toNbt` materialises the
    // legacy compound shapes from the SoT, `fromNbt` reads them back into a
    // fresh `QuestLog`. Worlds saved before this carve load unchanged.
    //
    // Why keep `activeQuestMap` at all (it is the engine tick's view, not the
    // SoT): the MC `Quest` carries conditions, rewards and the `questId`
    // the contribute packet addresses — `QuestRef` cannot carry any of that.
    // So `QuestLog` holds the SoT's roll + completion map (Minecraft-free,
    // bare-JVM testable), and `activeQuestMap` holds the rich `Quest` data
    // the engine tick needs (`TickScheduler.tickQuests`,
    // `QuestManager.isAlreadyActive`, `TownHubDataBuilder.buildQuestsTag`,
    // `C2SContributeQuestPacket.handle`). The two are kept consistent by
    // every mutator on `Town` — `addQuest`, `removeQuest` and
    // `cleanupOrphanedQuestData` mutate both; the SoT (`questLog`) is the
    // persisted field and `activeQuestMap` is the derived view the engine
    // tick reads.
    //
    // LinkedHashMap so the `ActiveQuests` NBT list preserves legacy insertion
    // order — `TickScheduler.tickQuests` adds in `QuestDataHandler.getAll()`
    // order and the NBT contract is byte-identical.
    // -------------------------------------------------------------------------
    private QuestLog questLog = QuestLog.EMPTY;
    private final Map<String, Quest> activeQuestMap = new LinkedHashMap<>();

    // Sliding window of the last 20 activity events; persisted to NBT.
    private final ArrayDeque<TownLogEntry> activityLog = new ArrayDeque<>();
    private static final int LOG_MAX = 20;

    // Players who opted in to receiving village log entries as chat messages. Persisted to NBT.
    private final Set<UUID> chatSubscribers = new HashSet<>();

    // -------------------------------------------------------------------------
    // ADR-0009 — standing + acquisition (strangler facade; no behavior change).
    //
    // Acquisition and StandingBook live in the domain layer and are stored
    // here only because Town is the save-format owner. NBT keys are additive:
    // missing keys read as FREE / empty book, so worlds saved before this
    // carve load unchanged. See openspec/changes/settlement-standing-acquisition
    // for the scenario set.
    // -------------------------------------------------------------------------

    /** Town's relation to outside authority (FREE by default). Persisted to NBT. */
    private Acquisition acquisition = Acquisition.FREE;

    /** Per-citizen standing roll. Empty by default. Persisted to NBT (sparse). */
    private StandingBook standingBook = StandingBook.EMPTY;

    // Player-ordered queue of construction tasks (new builds and upgrades).
    // Resources are pre-reserved in queueReservedStock when an entry is added.
    // ADR-0027 — domain type is the SoT. The MC `List<QueueEntry>` view that
    // older call sites used to read is now derived on demand via
    // {@link #getConstructionQueue()}; mutations go through the immutable
    // `ConstructionQueue` value object so the cache and the source are the
    // same thing. NBT keys `ConstructionQueue` and `QueueReservedStock` stay
    // byte-identical: `toNbt` materializes a list of `QueueEntry` from the
    // domain via `QueueEntry.fromIntent`, `fromNbt` reads the same NBT into
    // a fresh domain queue via `QueueEntry.toIntent`.
    private ConstructionQueue constructionQueue = ConstructionQueue.EMPTY;
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
    // Current weight cap. Starts at initial_max_weight (era 0 file) and grows with each transition. Persisted to NBT.
    private int currentMaxWeight = 0;
    // Active build states keyed by builder slot index. Persisted so builds survive server restarts.
    private final Map<Integer, ActiveBuildState> activeBuilds = new HashMap<>();
    // Monotonically increasing counter stamped onto each ConnectionPoint when added to freeConnections.
    // Allows sorting by insertion age: lower = older = closer to the village center.
    private long cpInsertionCounter = 0;

    public void registerBuilding(BlockPos worldPos, String defId, List<ConnectionPoint> connections, BoundingBox bb, Rotation rotation) {
        buildings.add(new PlacedBuilding(defId, worldPos, bb, rotation));
        for (ConnectionPoint cp : connections) {
            freeConnections.add(new ConnectionPoint(cp.pos(), cp.direction(), cp.targetName(), cpInsertionCounter++));
        }
        // Remove phantom CPs whose expansion block falls inside the new building's footprint.
        // These are open connectors that point into already-occupied space and can never be used.
        if (bb != null) {
            freeConnections.removeIf(cp -> {
                net.minecraft.core.BlockPos expansion = cp.pos().relative(cp.direction());
                return bb.isInside(expansion);
            });
        }
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

    // Tracks whether the "village full" chat message has fired for the current empty state.
    // Reset when a new CP is added so the message can fire again if the village hits zero a second time.
    private boolean villageFullNotified = false;

    public void addFreeConnection(ConnectionPoint point) {
        freeConnections.add(new ConnectionPoint(point.pos(), point.direction(), point.targetName(), cpInsertionCounter++));
        villageFullNotified = false;
    }

    public void useConnection(ConnectionPoint point) {
        freeConnections.remove(point);
    }

    // Returns true exactly once when freeConnections transitions from non-empty to empty.
    public boolean checkVillageFullTransition() {
        if (!villageFullNotified && freeConnections.isEmpty()) {
            villageFullNotified = true;
            return true;
        }
        return false;
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
            total += def.weight;
        }
        for (ConstructionIntent intent : constructionQueue.entries()) {
            if (intent instanceof ConstructionIntent.NewBuild nb) {
                BuildingDef def = BuildingDataHandler.get(nb.buildingDefId()).orElse(null);
                if (def != null) total += def.weight;
            }
        }
        return total;
    }

    public int getCurrentEra() { return currentEra; }
    public String getCurrentEraPath() { return currentEraPath; }
    public Set<String> getUnlockedBuildingIds() { return Collections.unmodifiableSet(unlockedBuildingIds); }

    public int getCurrentMaxWeight() { return currentMaxWeight; }
    public List<BoundingBox> getBlockedZones() { return Collections.unmodifiableList(blockedZones); }
    public boolean isUnderUpgrade(BlockPos pos) { return underUpgrade.contains(pos); }

    // -------------------------------------------------------------------------
    // DIST-1 Zoning
    //
    // Position-based heuristic that classifies world positions into one of
    // four zones based on distance from the town's centroid. The heuristic
    // is intentionally simple for the first slice: a future phase will also
    // look at planned road segments (for the ROAD zone) and the next era
    // weight cap (for MILITARY threshold changes).
    //
    // Anchor (centroid) is derived from the placed buildings; if there are
    // none yet, the anchor falls back to BlockPos.ZERO. Building zone
    // enforcement (Town.tryAddToConstructionQueue respecting requiredZone)
    // is deferred to a future commit — this slice only exposes the zone
    // taxonomy + the per-position lookup.
    // -------------------------------------------------------------------------

    /**
     * Position-based zone taxonomy. CORE is the inner ring around the town's
     * centroid (people, stores, public buildings); INDUSTRY is the outer
     * ring (farms, workshops, dirty work); ROAD is reserved for positions
     * on a planned road segment; MILITARY is beyond INDUSTRY (walls, towers).
     */
    public enum Zone {
        /** Within 32 blocks of anchor — houses, public buildings, the well. */
        CORE,
        /** 32-64 blocks — farms, workshops, kilns, byres. */
        INDUSTRY,
        /** On a planned road segment (detected from {@link org.lowern1ght.burg.behavior.road.RoadGraph}). */
        ROAD,
        /** Beyond 64 blocks — walls, towers, lookout posts. Reserved for the future. */
        MILITARY
    }

    /** Centroid of placed buildings; falls back to BlockPos.ZERO for an empty town. */
    public BlockPos getAnchorPos() {
        if (buildings.isEmpty()) return BlockPos.ZERO;
        long sumX = 0;
        long sumZ = 0;
        int y = buildings.get(0).worldPos.getY();
        for (PlacedBuilding b : buildings) {
            sumX += b.worldPos.getX();
            sumZ += b.worldPos.getZ();
        }
        int n = buildings.size();
        return new BlockPos((int) (sumX / n), y, (int) (sumZ / n));
    }

    /**
     * Returns the zone for a given position, based on distance from the
     * town anchor. ROAD zone detection (intersection with the road graph)
     * is not yet implemented — that lands with the full zoning enforcement
     * in a future commit.
     */
    public Zone zoneOf(BlockPos pos) {
        BlockPos anchor = getAnchorPos();
        double dx = pos.getX() - anchor.getX();
        double dz = pos.getZ() - anchor.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= 32.0) return Zone.CORE;
        if (dist <= 64.0) return Zone.INDUSTRY;
        return Zone.MILITARY;
    }

    // Seeds currentMaxWeight from the matched era 0 data file.
    // Called once at world gen after all starter buildings are registered.
    public void initFromEraDef() {
        String orientation = getOrDeriveOrientation();
        if (orientation.isEmpty()) return;
        EraDef def = EraTransitionDataHandler.getEraDefByOrientation(orientation);
        if (def == null) return;
        if (def.initialMaxWeight > 0) {
            currentMaxWeight = def.initialMaxWeight;
        }
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
        if (!t.autoUpgradeIds.isEmpty()) {
            for (PlacedBuilding b : buildings) {
                if (t.autoUpgradeIds.contains(b.defId)) {
                    forceQueueUpgrade(b.worldPos);
                }
            }
        }
        return true;
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

    // Computed aggregate view: buildings + floating reserve
    public TownInventory getTownInventory() {
        // The callback keeps stockLedger in sync whenever the inventory view
        // mutates reserveStock (removeStock / addStock); see TownInventory for
        // the wiring. The view holds the same Map reference so a mutation in
        // either place is visible to the other.
        return new TownInventory(buildings, reserveStock, this::syncStockLedgerFromReserve);
    }

    // Player command injection - into first building if available, otherwise into reserve
    public void addStock(Item item, int quantity) {
        if (!buildings.isEmpty()) {
            buildings.get(0).forceAdd(item, quantity);
        } else {
            reserveStock.merge(item, quantity, Integer::sum);
            syncStockLedgerFromReserve();
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

    // -------------------------------------------------------------------------
    // Player construction queue
    //
    // ADR-0027 — the immutable domain type {@link ConstructionQueue} is the
    // SoT; this block mutates it via {@code enqueue} / {@code without} and
    // surfaces a derived MC-typed view through {@link #getConstructionQueue()}
    // for callers that still need a {@link QueueEntry} (the
    // {@code TownHubDataBuilder} S2C packet, the {@code SimpleStateMachine}
    // builder NPC, the GameTest). The derived list is rebuilt on every
    // read — the queue is bounded at {@link #QUEUE_CAPACITY} entries so
    // the O(N) materialization cost is negligible, and the per-mutation
    // savings (no cache rebuild, no consistency check) outweigh the
    // per-read cost by a lot.
    // -------------------------------------------------------------------------

    public List<QueueEntry> getConstructionQueue() {
        List<ConstructionIntent> intents = constructionQueue.entries();
        List<QueueEntry> derived = new ArrayList<>(intents.size());
        for (ConstructionIntent intent : intents) {
            derived.add(QueueEntry.fromIntent(intent));
        }
        return Collections.unmodifiableList(derived);
    }

    // Checks affordability (available stock minus already-reserved amounts), reserves resources,
    // and appends a NewBuild entry to the queue. Returns false if unaffordable or queue is full.
    public boolean tryAddToConstructionQueue(String defId) {
        BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
        if (def == null || !constructionQueue.hasCapacity()) return false;
        // Weight cap: block new builds (not upgrades) that would exceed the era limit.
        if (getCurrentWeight() + def.weight > getCurrentMaxWeight()) return false;
        TownInventory inv = getTownInventory();
        for (ItemCost cost : def.constructionCost) {
            if (inv.getStock(cost.item()) < cost.amount()) return false;
        }
        inv.removeStock(def.constructionCost);
        for (ItemCost cost : def.constructionCost) {
            queueReservedStock.merge(cost.item(), cost.amount(), Integer::sum);
        }
        constructionQueue = constructionQueue.enqueue(
            new ConstructionIntent.NewBuild(nextEntryId++, defId));
        return true;
    }

    // Checks affordability and appends an Upgrade entry to the queue.
    // Returns false if: building not found, already at max level, upgrade already pending,
    // queue full, or insufficient stock. Only one upgrade per building can be queued at a time.
    public boolean tryQueueUpgrade(BlockPos worldPos) {
        PlacedBuilding building = null;
        for (PlacedBuilding b : buildings) {
            if (b.worldPos.equals(worldPos)) { building = b; break; }
        }
        if (building == null) return false;

        BuildingDef def = BuildingDataHandler.get(building.defId).orElse(null);
        if (def == null || (def.upgrades.isEmpty() && def.nbtLevels.isEmpty())) return false;

        // Block if an upgrade for this building is already pending in the queue.
        for (ConstructionIntent intent : constructionQueue.entries()) {
            if (intent instanceof ConstructionIntent.Upgrade u
                && BlockPos.of(Long.parseLong(u.worldPosKey())).equals(worldPos)) {
                return false;
            }
        }

        int effectiveLevel = building.getUpgradeLevel();
        int maxLevel = Math.max(def.upgrades.size(), def.nbtLevels.size());
        if (effectiveLevel >= maxLevel) return false;
        if (!constructionQueue.hasCapacity()) return false;

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
        constructionQueue = constructionQueue.enqueue(
            new ConstructionIntent.Upgrade(
                nextEntryId++,
                building.defId,
                Long.toString(worldPos.asLong()),
                effectiveLevel));
        return true;
    }

    // Free upgrade bypassing resource check -- cost absorbed by era transition.
    // Still verifies: building exists, not at max level, queue not full.
    public boolean forceQueueUpgrade(BlockPos worldPos) {
        PlacedBuilding building = null;
        for (PlacedBuilding b : buildings) {
            if (b.worldPos.equals(worldPos)) { building = b; break; }
        }
        if (building == null) return false;

        BuildingDef def = BuildingDataHandler.get(building.defId).orElse(null);
        if (def == null || (def.upgrades.isEmpty() && def.nbtLevels.isEmpty())) return false;

        int effectiveLevel = building.getUpgradeLevel();
        for (ConstructionIntent intent : constructionQueue.entries()) {
            if (intent instanceof ConstructionIntent.Upgrade u
                && BlockPos.of(Long.parseLong(u.worldPosKey())).equals(worldPos)) {
                effectiveLevel++;
            }
        }

        int maxLevel = Math.max(def.upgrades.size(), def.nbtLevels.size());
        if (effectiveLevel >= maxLevel) return false;
        if (!constructionQueue.hasCapacity()) return false;

        constructionQueue = constructionQueue.enqueue(
            new ConstructionIntent.Upgrade(
                nextEntryId++,
                building.defId,
                Long.toString(worldPos.asLong()),
                effectiveLevel));
        return true;
    }

    // -------------------------------------------------------------------------
    // BuildExecutor (behavior engine seam)
    //
    // These three methods adapt the engine's ResourceLocation-keyed interface to the
    // legacy bare-string-keyed queue. The placerNpcUuid is accepted but currently ignored:
    // Town assigns the builder internally when SimpleStateMachine picks the entry up.
    // -------------------------------------------------------------------------

    @Override
    public boolean tryQueueNewBuild(Town town, ResourceLocation buildingDefId, String placerNpcUuid) {
        // placer identity is recorded by the engine; Town assigns internally.
        return tryAddToConstructionQueue(buildingDefId.getPath());
    }

    @Override
    public boolean tryQueueUpgrade(Town town, BlockPos buildingPos, String placerNpcUuid) {
        return tryQueueUpgrade(buildingPos);
    }

    @Override
    public boolean isPlaced(Town town, ResourceLocation buildingDefId) {
        String path = buildingDefId.getPath();
        for (PlacedBuilding b : buildings) {
            if (path.equals(b.defId)) return true;
        }
        return false;
    }

    // Removes entry at index, restoring its reserved resources to the floating reserve.
    public boolean removeFromConstructionQueue(int index) {
        if (index < 0 || index >= constructionQueue.size()) return false;
        QueueEntry entry = QueueEntry.fromIntent(constructionQueue.entries().get(index));
        List<ItemCost> costToRefund = getEntryCost(entry);
        for (ItemCost cost : costToRefund) {
            int reserved = queueReservedStock.getOrDefault(cost.item(), 0);
            int toRestore = Math.min(reserved, cost.amount());
            if (toRestore > 0) {
                queueReservedStock.put(cost.item(), reserved - toRestore);
                reserveStock.merge(cost.item(), toRestore, Integer::sum);
            }
        }
        constructionQueue = constructionQueue.without(index);
        shiftClaimsAfter(index);
        syncStockLedgerFromReserve();
        return true;
    }

    // Called by NPC after successfully processing a queued entry.
    // Removes the entry from the queue and clears its resource reservation.
    public int findQueueIndex(long entryId) {
        List<ConstructionIntent> intents = constructionQueue.entries();
        for (int i = 0; i < intents.size(); i++) {
            if (intents.get(i).entryId() == entryId) return i;
        }
        return -1;
    }

    public void consumeQueueEntry(QueueEntry entry) {
        int idx = findQueueIndex(entry.entryId());
        if (idx >= 0) {
            constructionQueue = constructionQueue.without(idx);
            shiftClaimsAfter(idx);
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

    // Computes total food units demanded per day across all residential and herd buildings (unrounded float).
    public float computeTotalFoodDemandFloat() {
        float total = 0f;
        for (PlacedBuilding b : buildings) {
            BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
            if (def == null) continue;
            BuildingDef.ResolvedBuildingStats stats = def.resolveAtLevel(b.getUpgradeLevel());
            if (stats.resolvedResidents() > 0) {
                total += stats.resolvedResidents() * stats.resolvedConsumptionPerResident();
            }
            if (stats.resolvedHerd() > 0) {
                total += stats.resolvedHerd() * stats.resolvedConsumptionPerHerd();
            }
        }
        return total;
    }

    // Sums resolved herd count across all placed buildings.
    public int getTotalHerd() {
        int total = 0;
        for (PlacedBuilding b : buildings) {
            BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
            if (def == null) continue;
            total += def.resolveAtLevel(b.getUpgradeLevel()).resolvedHerd();
        }
        return total;
    }

    // Sums resolved herd count for buildings whose herd was fed at last dawn.
    public int getActiveHerd() {
        int total = 0;
        for (PlacedBuilding b : buildings) {
            BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
            if (def == null) continue;
            int h = def.resolveAtLevel(b.getUpgradeLevel()).resolvedHerd();
            if (h > 0 && b.isHerdFed()) total += h;
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

    // Returns hub data: map + era + catalog + stock + queue + summary + quests.
    public CompoundTag getHubData(BlockPos anchorPos) {
        return new TownHubDataBuilder(this).buildHubData(anchorPos);
    }

    // -------------------------------------------------------------------------
    // Targeted serialization helpers (used by targeted S2C packets)
    // -------------------------------------------------------------------------

    public CompoundTag getStockUpdateData(BlockPos anchorPos) {
        return new TownHubDataBuilder(this).buildStockUpdateData(anchorPos);
    }

    public CompoundTag getBuildingListData(BlockPos anchorPos) {
        return new TownHubDataBuilder(this).buildBuildingListData(anchorPos);
    }

    public CompoundTag getQuestUpdateData(BlockPos anchorPos) {
        return new TownHubDataBuilder(this).buildQuestUpdateData(anchorPos);
    }

    public CompoundTag getEraUpdateData(BlockPos anchorPos) {
        return new TownHubDataBuilder(this).buildEraUpdateData(anchorPos);
    }

    public CompoundTag getCitizenUpdateData(BlockPos anchorPos) {
        return new TownHubDataBuilder(this).buildCitizenUpdateData(anchorPos);
    }

    // -------------------------------------------------------------------------
    // Quest management
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Activity log
    // -------------------------------------------------------------------------

    public void addLogEntry(TownLogEntry entry) {
        if (activityLog.size() >= LOG_MAX) activityLog.pollFirst();
        activityLog.addLast(entry);
    }

    public List<TownLogEntry> getActivityLog() {
        return List.copyOf(activityLog);
    }

    public void addChatSubscriber(UUID playerId)    { chatSubscribers.add(playerId); }
    public void removeChatSubscriber(UUID playerId) { chatSubscribers.remove(playerId); }
    public boolean isChatSubscriber(UUID playerId)  { return chatSubscribers.contains(playerId); }
    public Set<UUID> getChatSubscribers()           { return Collections.unmodifiableSet(chatSubscribers); }

    // -------------------------------------------------------------------------
    // ADR-0009 — strangler facade for standing + acquisition.
    //
    // These accessors are the only edge through which standing and acquisition
    // mutate today; the domain objects themselves are immutable and the
    // Town-level view is replaced wholesale on every change.
    // -------------------------------------------------------------------------

    public Acquisition getAcquisition() { return acquisition; }
    public void setAcquisition(Acquisition value) { this.acquisition = value; }

    /** Returns the standing for a citizen; not-on-roll reads as zero. */
    public Standing standingFor(UUID citizenId) {
        return standingBook.standingFor(CitizenId.of(citizenId));
    }

    /** Adjusts the score for a citizen by {@code delta}; new score replaces the old. */
    public void adjustStanding(UUID citizenId, int delta) {
        standingBook = standingBook.adjust(CitizenId.of(citizenId), delta);
    }

    /** Read-only view of the full standing roll. */
    public StandingBook getStandingBook() { return standingBook; }

    // -------------------------------------------------------------------------
    // ADR-0010 + ADR-0013 — strangler facade for stock (ItemId + StockLedger).
    //
    // reserveStock remains the source of truth and the NBT-roundtrip owner;
    // the NBT shape is byte-for-byte unchanged. StockLedger is now a cached
    // Minecraft-free view rebuilt at every known mutation site, with a
    // fallback rebuild when the cache disagrees with reserveStock. ADR-0013
    // adds the symmetric domain→MC write path ({@link #applyStockLedger})
    // so application code can drive the reserve from the ledger without
    // rewriting the production tick. reserveStock stays in
    // {@link TownInventory} and on disk; StockLedger stays in the domain.
    // -------------------------------------------------------------------------

    /**
     * Cached Minecraft-free view of {@code reserveStock}. Kept in sync at
     * every known mutation site ({@link #addStock}, the queue's refund
     * cycle, the additive NBT load in {@link #fromNbt}, every
     * {@link TownInventory} mutation via its callback, and
     * {@link #applyStockLedger}). {@link #stockLedger()} falls back to a
     * full rebuild when the cache and reserveStock disagree — see
     * {@link #stockLedgerCacheIsConsistent()}.
     */
    private StockLedger stockLedger = StockLedger.EMPTY;

    /**
     * Rebuilds {@link #stockLedger} from {@code reserveStock}. Cheap
     * (reserve is small, typically a few dozen) and idempotent. Called at
     * every known reserveStock mutation site so {@link #stockLedger()} can
     * return the cache on the fast path. Unknown / null entries are
     * dropped at the edge so the ledger stays sparse — the same discipline
     * StockLedger applies to its own constructor.
     */
    private void syncStockLedgerFromReserve() {
        if (reserveStock.isEmpty()) {
            stockLedger = StockLedger.EMPTY;
            return;
        }
        LinkedHashMap<ItemId, Integer> view = new LinkedHashMap<>(reserveStock.size());
        reserveStock.forEach((item, qty) -> {
            if (item == null || qty == null || qty <= 0) return;
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null) return;  // unregistered item — drop at edge
            view.put(ItemId.of(key.toString()), qty);
        });
        stockLedger = view.isEmpty() ? StockLedger.EMPTY : StockLedger.of(view);
    }

    /**
     * Cheap consistency check between {@link #stockLedger} and
     * {@code reserveStock}: same emptiness on both sides and, when non-
     * empty, the same number of positive entries. Sufficient to detect the
     * "cache is empty because someone forgot to sync" case; a fuller
     * content-equality check would be O(reserve.size) per call and is not
     * worth the cost on the hot read path.
     */
    private boolean stockLedgerCacheIsConsistent() {
        if (reserveStock.isEmpty()) return stockLedger.isEmpty();
        if (stockLedger.isEmpty()) return false;
        int reserveCount = 0;
        for (Map.Entry<Item, Integer> e : reserveStock.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) {
                reserveCount++;
            }
        }
        return reserveCount == stockLedger.size();
    }

    /**
     * Returns the town's reserve stock as a Minecraft-free
     * {@link StockLedger}. Returns the cached {@link #stockLedger} field on
     * the fast path; falls back to a full rebuild via
     * {@link #syncStockLedgerFromReserve()} when the cache disagrees with
     * {@code reserveStock} (the "missed a sync" safety net). ADR-0010
     * left the accessor rebuilding on every call; ADR-0013 keeps that
     * correctness but caches the result so the rebuild only fires when the
     * cache is provably stale.
     */
    public StockLedger stockLedger() {
        if (stockLedgerCacheIsConsistent()) return stockLedger;
        syncStockLedgerFromReserve();
        return stockLedger;
    }

    /**
     * Replaces {@code reserveStock} from the contents of the given domain
     * {@link StockLedger}. Each {@link ItemId} is resolved against the
     * Minecraft item registry; entries whose ItemId is absent from the
     * registry, malformed, or {@code minecraft:air} are skipped — this
     * lets a domain ledger built from arbitrary sources (datapacks,
     * generated configs, tests) apply safely to a real town without
     * polluting it with phantom items. Returns the number of skipped
     * entries so callers can log / surface a partial-apply warning.
     *
     * <p>This is the domain→MC write counterpart to {@link #stockLedger()}
     * (the MC→domain read path). It enables the application layer to drive
     * the reserve from the StockLedger without rewriting the production
     * tick; reserveStock remains the persistence owner.
     */
    public int applyStockLedger(StockLedger ledger) {
        Objects.requireNonNull(ledger, "ledger");
        reserveStock.clear();
        int skipped = 0;
        for (Map.Entry<ItemId, Integer> e : ledger.entries().entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                // drop zero-quantity entries silently — same discipline as StockLedger.of
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(e.getKey().value());
            if (rl == null) {
                skipped++;
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null) {
                skipped++;
                continue;
            }
            reserveStock.merge(item, e.getValue(), Integer::sum);
        }
        syncStockLedgerFromReserve();
        return skipped;
    }

    /**
     * Pure-domain wire→reserve helper: clears {@code reserve}, then merges every
     * {@code wire} entry whose quantity is positive, whose {@link ItemId} parses as
     * a {@link ResourceLocation}, and whose {@link Item} is registered in the live
     * item registry. Returns the number of dropped entries (unparseable ItemIds +
     * unregistered Items + zero/negative quantities).
     *
     * <p><b>Static on purpose.</b> No {@code this} state is read or mutated — the
     * helper is a pure function over its arguments, so the bare-JVM test exercises
     * it without constructing a {@code Town} (which would require a Minecraft world).
     * The instance method {@link #applyStockLedger(StockLedger)} delegates to the
     * same body on {@code this.reserveStock} and then runs the cache sync; this
     * carve splits the wire-side body out so callers that don't want the cache
     * update (e.g. one-off test fixtures, batch apply across multiple towns) can
     * reach the same logic without paying for {@code syncStockLedgerFromReserve}.
     *
     * <p>The {@code wire} entries are iterated in insertion order so the merge
     * is deterministic — {@link StockLedger} already preserves insertion order on
     * the read path. Same edge discipline as the instance method: zero and
     * negative quantities drop silently at the wire (no entry survives on the
     * reserve), unparseable and unregistered ItemIds bump the skipped counter,
     * duplicate wire entries sum onto the existing quantity via
     * {@link Map#merge}.
     */
    public static int applyStockToReserve(StockLedger wire, Map<Item, Integer> reserve) {
        Objects.requireNonNull(wire, "wire");
        Objects.requireNonNull(reserve, "reserve");
        reserve.clear();
        int skipped = 0;
        for (Map.Entry<ItemId, Integer> e : wire.entries().entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                // drop zero-quantity entries silently — same discipline as StockLedger.of
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(e.getKey().value());
            if (rl == null) {
                skipped++;
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null) {
                skipped++;
                continue;
            }
            reserve.merge(item, e.getValue(), Integer::sum);
        }
        return skipped;
    }

    // -------------------------------------------------------------------------
    // ADR-0027 — promote ConstructionQueue to the SoT (flip the dual-write).
    //
    // ADR-0011 + ADR-0016 had the MC `List<QueueEntry> constructionQueue`
    // as the SoT and a cached `ConstructionQueue` derived from it at every
    // mutation site, with a rebuild fallback when the two disagreed. The
    // flip: the immutable `ConstructionQueue` is now the SoT and the
    // primary state on `Town`. The MC list that older call sites read via
    // {@link #getConstructionQueue()} is now a derived view materialized
    // on demand — the queue is bounded at `QUEUE_CAPACITY` (54), so the
    // O(N) per-read cost is negligible, and the per-mutation savings
    // (no cache rebuild, no consistency check, no sync helper) outweigh
    // the per-read cost by a lot.
    //
    // `constructionQueueView()` returns the SoT directly — the "missed a
    // sync" safety net is gone because the SoT and the cache are the
    // same field now. Burg's act-5 SUPPLY-mode loop reads
    // `constructionQueueView()` on every idle tick per builder per town;
    // the flip makes the read a plain field read and the write a
    // single immutable replacement.
    //
    // NBT keys `ConstructionQueue` and `QueueReservedStock` are unchanged:
    // `toNbt` materializes a list of `QueueEntry` from the domain via
    // {@link QueueEntry#fromIntent}; `fromNbt` reads the same NBT list
    // into a fresh domain queue via {@link QueueEntry#toIntent}. Format
    // is byte-identical, so worlds saved before the flip load unchanged.
    // -------------------------------------------------------------------------

    /**
     * Returns the town's construction queue as a Minecraft-free
     * {@link ConstructionQueue}. This is the SoT (ADR-0027): the field
     * itself, not a derived view. The legacy MC-typed read path is
     * {@link #getConstructionQueue()}.
     */
    public ConstructionQueue constructionQueueView() {
        return constructionQueue;
    }

    // -------------------------------------------------------------------------
    // ADR-0019 — hub-mode read-only strangler facade (hub-becomes-window).
    //
    // The hub now answers two questions: which mode is this town in
    // (CONSTRUCTION for acts 0–3, SUPPLY for act 4+), and what view of the
    // town does that mode show. The mode is *derived* from acquisition +
    // structural-flag state + standing-at-read-time at read time — there
    // is no persisted field, no migration, and no NBT key. Worlds saved
    // before this change load unchanged: a town with FREE / CAPTURED
    // acquisition, an empty structural flag-set, or a highest standing
    // below {@code BurgConfig.ACT_THRESHOLD} reads as HubView.EMPTY
    // (mode = CONSTRUCTION), which is exactly what today's command-console
    // hub already renders.
    //
    // The act-4 gate is now a real three-leg predicate:
    //   (1) acquisition: ELEVATED or FOUNDED ⇒ standing is established;
    //   (2) structural triple (core_populated | industry_zoned | road_laid);
    //   (3) standing-at-read-time: at least one citizen's standing has
    //       crossed {@code BurgConfig.ACT_THRESHOLD} (default 50).
    // Today {@link #structuralFlags()} reads the stub fields
    // ({@link #zoningCount} and {@link #plannedRoads}); both start empty
    // so the strict derivation returns NONE on every fresh save, gating
    // the hub to CONSTRUCTION regardless of acquisition. The mutators
    // ({@link #addZoning(Zone, int)} / {@link #addRoadSegment(RoadSegment)})
    // land the first increment; the act-5 zoning / road-planner carves
    // wire the production call sites and the gate gets its teeth.
    //
    // `TownAnchorBlock.useWithoutItem` is the only consumer right now: it
    // logs the mode at right-click. The TownHubScreen widget set is
    // unchanged in this carve — it still renders the command-console shape
    // for both modes — and the supply-mode widgets land in the act-4
    // follow-up PR. The mode is therefore observable from a unit round
    // trip and from the engine log, not yet from the screen.
    // -------------------------------------------------------------------------

    /**
     * Returns the hub's current mode for this town. Derived per call from
     * three legs (acquisition + structural-flag state + standing threshold)
     * all AND-ed together — see the ADR-0019 comment block above for the
     * full rationale. The act-4 trigger from
     * {@code openspec/changes/hub-becomes-window/specs/construction-mode-supply-mode
     * §"Requirement: structural predicate is three conditions AND-ed"}.
     *
     * <p>Additive default for any town that doesn't satisfy the SUPPLY
     * precondition is {@link HubMode#CONSTRUCTION}, which is what the
     * legacy {@code TownHubScreen} already renders.
     *
     * <p>The third leg ({@code highestStanding() >= ACT_THRESHOLD}) is the
     * {@link BurgConfig#ACT_THRESHOLD} Cloth Config knob's only reader.
     * Read live at call time so a slider edit takes effect on the next
     * right-click without a world reload.
     */
    public HubMode hubMode() {
        Acquisition a = getAcquisition();
        if (a != Acquisition.FREE
            && structuralFlags().isAnySet()
            && StandingBook.meetsActThreshold(highestStanding(), BurgConfig.ACT_THRESHOLD.get())) {
            return HubMode.SUPPLY;
        }
        return HubMode.CONSTRUCTION;
    }

    /**
     * The third leg of the {@link #hubMode()} predicate: returns the
     * highest standing score any citizen on this town's roll has earned
     * (delegates to {@link StandingBook#highestStanding()}), or
     * {@link Standing#DEFAULT} (zero) when the book is empty. Public so
     * the {@code TownCommand} and the {@code TownHubDataBuilder} S2C
     * packet can read it without going through the full mode predicate.
     */
    public int highestStanding() {
        return standingBook.highestStanding();
    }

    /**
     * Returns the hub's current view for this town. Thin wrapper around
     * {@link #hubMode()} so callers that want the {@link HubView} (and the
     * EMPTY sentinel) don't have to rebuild the record themselves. Same
     * derivation discipline as {@link #hubMode()}; no caching.
     */
    public HubView hubView() {
        return HubView.of(hubMode());
    }

    /**
     * Returns the structural act-4 trigger for this town as a
     * {@link StructuralFlags} flag-set. Derived per call (no cache, no
     * persisted field) from the three conditions the
     * {@code hub-becomes-window} spec names: {@code core_populated},
     * {@code industry_zoned}, {@code road_laid}.
     *
     * <p><b>Per-flag status (act-4 follow-up-2 + config-and-structural + structural-fields carves):</b>
     * <ul>
     *   <li><b>core_populated</b> — REAL derivation. Walks every XZ
     *       cell inside the 32-block core radius (mirroring
     *       {@link Zone#CORE} from {@link #zoneOf(BlockPos)}) and
     *       verifies each is covered by at least one placed
     *       building's bounding box. The 32-block radius is the
     *       existing zoneOf constant — no new field is fabricated
     *       on {@code Town} for this purpose. Returns false for an
     *       empty town (no anchor / no buildings) and for any town
     *       whose core is not yet fully built out.</li>
     *   <li><b>industry_zoned</b> — REAL derivation. Reads from
     *       {@link #zoningCount} — a per-town count by zone — and
     *       returns {@code true} iff the map is non-empty (the spec
     *       asks "has the zoning layer touched this town", regardless
     *       of which zone). The field starts empty on a fresh save, so
     *       this returns {@code false} until the zoning layer calls
     *       {@link #addZoning(Zone, int)} at least once. The mutator
     *       is the seam — wiring it into the production zoning tick
     *       is the act-5 zoning carve's only remaining work.</li>
     *   <li><b>road_laid</b> — REAL derivation. Reads from
     *       {@link #plannedRoads} — a per-town roll of
     *       {@link RoadSegment} — and returns {@code true} iff the
     *       list is non-empty. The list starts empty on a fresh save, so
     *       this returns {@code false} until the road planner calls
     *       {@link #addRoadSegment(RoadSegment)} at least once. The
     *       mutator is the seam — wiring it into the road planner's
     *       commit path is the act-5 road carve's only remaining
     *       work.</li>
     * </ul>
     *
     * <p><b>Net behaviour on a fresh save.</b> All three fields are
     * empty by default, so the strict derivation
     * ({@link StructuralFlags#isAnySet()}) returns {@code false} for
     * every fresh save. That collapses the structural triple to
     * {@code of(false, false, false)} = {@link StructuralFlags#NONE},
     * which gates the hub to {@link HubMode#CONSTRUCTION} regardless of
     * acquisition — exactly the strict form the act-4 follow-up was
     * working toward. The mutators land the moment the zoning layer /
     * road planner commit their first call; the read site does not
     * change.
     *
     * <p>The immutable-value-object discipline {@link #constructionQueue}
     * uses (ADR-0027) is reserved for the future carve where this method
     * stops being free (today the {@code core_populated} walk is
     * user-initiated via {@link #hubMode()} on anchor right-click, so
     * the O(R²) cost is bounded).
     */
    public StructuralFlags structuralFlags() {
        return StructuralFlags.of(corePopulated(), industryZoned(), roadLaid());
    }

    /**
     * Real derivation for {@link StructuralFlags#corePopulated()}: walks
     * every XZ cell inside the 32-block core radius (the same constant
     * {@link #zoneOf(BlockPos)} uses for {@link Zone#CORE}) and verifies
     * each is covered by at least one placed building's bounding box.
     *
     * <p>Returns false for an empty town (no buildings, or buildings
     * whose {@link PlacedBuilding#bb} is {@code null} — pre-BB saves).
     * Returns true only when every cell of the core radius is
     * covered by at least one building with a non-null bb.
     *
     * <p>The 32-block radius is hard-coded to match {@link Zone#CORE};
     * we deliberately do not add a per-town radius field — that would
     * fabricate state the spec specifically asked us not to. If a
     * per-town radius ever lands, the constant moves to the new
     * field.
     */
    private static final int CORE_RADIUS_BLOCKS = 32;

    // -------------------------------------------------------------------------
    // ADR-0026 — structural flags' source of truth. Today the zoning layer
    // and the road graph are keyed by Town externally (zoning is per-
    // position via {@link #zoneOf}; road planning runs against a per-
    // server {@code RoadGraph} keyed by Town). Adding the fields here
    // means {@link #structuralFlags()} consults them right now and flips
    // the gate to its teeth the moment the zoning layer / road planner
    // calls {@link #addZoning(Zone, int)} / {@link #addRoadSegment(RoadSegment)}
    // for the first time; no future refactor of the read site is needed.
    //
    // Strict derivation is wired: both maps/lists start empty, so
    // {@code industryZoned()} and {@code roadLaid()} return {@code false}
    // on every fresh save, and {@code structuralFlags()} collapses to
    // {@link StructuralFlags#NONE} regardless of acquisition. The mutators
    // are the seam: a production zoning tick calls {@code addZoning} as it
    // places cells, and the road planner's commit path calls
    // {@code addRoadSegment}; both populate the SoT and the gate gets its
    // teeth automatically.
    // -------------------------------------------------------------------------

    /**
     * Per-town zoning count by zone. Empty until the zoning layer's
     * {@link #addZoning(Zone, int)} mutator lands the first increment;
     * the field stays empty on a fresh save so {@link #industryZoned()}
     * collapses to {@code false} regardless of acquisition.
     */
    private final Map<Zone, Integer> zoningCount = new EnumMap<>(Zone.class);

    /**
     * Per-town planned roads. Empty until the road planner's
     * {@link #addRoadSegment(RoadSegment)} mutator appends the first
     * segment; the field stays empty on a fresh save so
     * {@link #roadLaid()} collapses to {@code false} regardless of
     * acquisition.
     */
    private final List<RoadSegment> plannedRoads = new ArrayList<>();

    /**
     * Read-only view of the per-zone zoning count. The map is the SoT;
     * the zoning layer mutates it via {@link #addZoning(Zone, int)}.
     */
    public Map<Zone, Integer> getZoningCount() {
        return Collections.unmodifiableMap(zoningCount);
    }

    /**
     * Records a zoning decision for this town. The layer responsible
     * for placing cells into a {@link Zone} calls this once per
     * committed decision (one placement = one call), passing the zone
     * and the number of cells the decision covers. Multiple calls for
     * the same zone merge via {@link Map#merge}, so a layer that emits
     * decisions incrementally accumulates naturally.
     *
     * <p>Negative {@code cells} and {@code null} {@code zone} are
     * dropped silently at the edge: the structural flag-set's job is
     * to record "has the zoning layer touched this town", and a
     * negative count is never a meaningful answer to that question.
     *
     * <p>Once this returns the field has at least one entry, so
     * {@link #industryZoned()} flips to {@code true} on the next call
     * to {@link #structuralFlags()}. The hub-mode gate's structural
     * leg goes from "always false" to "true once the zoning layer
     * commits a decision".
     */
    public void addZoning(Zone zone, int cells) {
        if (zone == null || cells <= 0) return;
        zoningCount.merge(zone, cells, Integer::sum);
    }

    /**
     * Read-only view of the per-town planned-road roll. The list is the
     * SoT; the road planner mutates it via {@link #addRoadSegment(RoadSegment)}.
     */
    public List<RoadSegment> getPlannedRoads() {
        return Collections.unmodifiableList(plannedRoads);
    }

    /**
     * Records a committed road segment for this town. The road planner
     * (or any caller that has produced a {@link RoadSegment} the engine
     * has accepted) calls this once per committed segment; the segment
     * is appended to the per-town roll in emission order.
     *
     * <p>{@code null} segments are dropped silently at the edge — the
     * structural flag-set's job is to record "has a road segment been
     * committed for this town", and a null segment is never a
     * meaningful answer to that question.
     *
     * <p>Once this returns the list has at least one entry, so
     * {@link #roadLaid()} flips to {@code true} on the next call to
     * {@link #structuralFlags()}. The hub-mode gate's structural leg
     * goes from "always false" to "true once the road planner commits
     * a segment".
     */
    public void addRoadSegment(RoadSegment segment) {
        if (segment == null) return;
        plannedRoads.add(segment);
    }

    /**
     * Strict derivation for {@link StructuralFlags#industryZoned()}: true
     * iff the per-zone count has any entry. Returns {@code false} for an
     * empty map — true on a fresh save, until the zoning layer calls
     * {@link #addZoning(Zone, int)} at least once.
     *
     * <p>Map emptiness, not the {@code INDUSTRY}-specific entry: the spec
     * asks whether any zoning decision has been made, regardless of zone,
     * because the structural flag-set's job is to record "has the zoning
     * layer touched this town". The zone the layer increments first is
     * its call; the gate only cares that something landed.
     */
    boolean industryZoned() {
        return !zoningCount.isEmpty();
    }

    /**
     * Strict derivation for {@link StructuralFlags#roadLaid()}: true iff
     * the town has at least one planned road segment. Returns
     * {@code false} for an empty list — true on a fresh save, until the
     * road planner calls {@link #addRoadSegment(RoadSegment)} at least
     * once.
     */
    boolean roadLaid() {
        return !plannedRoads.isEmpty();
    }

    boolean corePopulated() {
        if (buildings.isEmpty()) return false;
        BlockPos anchor = getAnchorPos();
        long rSq = (long) CORE_RADIUS_BLOCKS * CORE_RADIUS_BLOCKS;
        for (int dx = -CORE_RADIUS_BLOCKS; dx <= CORE_RADIUS_BLOCKS; dx++) {
            for (int dz = -CORE_RADIUS_BLOCKS; dz <= CORE_RADIUS_BLOCKS; dz++) {
                if ((long) dx * dx + (long) dz * dz > rSq) continue;   // outside the circle
                int wx = anchor.getX() + dx;
                int wz = anchor.getZ() + dz;
                if (!cellCoveredByBuilding(wx, wz)) return false;
            }
        }
        return true;
    }

    /**
     * True iff at least one placed building's bounding box covers the
     * (worldX, worldZ) cell. Buildings without a bounding box (saves
     * predating {@link PlacedBuilding#bb} tracking) contribute nothing
     * — they neither help nor hinder coverage. The walk's correctness
     * depends on at least one building having a non-null bb; if none do,
     * the walk fails closed (returns false for every cell).
     */
    private boolean cellCoveredByBuilding(int worldX, int worldZ) {
        for (PlacedBuilding b : buildings) {
            BoundingBox bb = b.bb;
            if (bb == null) continue;
            if (bb.minX() <= worldX && worldX <= bb.maxX()
                && bb.minZ() <= worldZ && worldZ <= bb.maxZ()) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // ADR-0028 — promote `QuestLog` to the source of truth (flip the
    // dual-write). ADR-0012 + ADR-0016 had `activeQuests` +
    // `questDefLastCompleted` as the SoT and `questLog()` as a cached
    // rebuild synced at every mutation site. The flip: `questLog` is
    // now the SoT and the primary state on `Town`; the MC legacy
    // fields (`activeQuests` + `questDefLastCompleted`) are gone, and
    // the read paths (`getActiveQuests`, `getQuestDefLastCompleted`,
    // `questLog`) read straight from `questLog` and the derived
    // `activeQuestMap`. NBT keys `ActiveQuests` and
    // `QuestDefLastCompleted` are unchanged: `toNbt` materialises the
    // legacy compound shapes from the SoT, `fromNbt` reads them back
    // into a fresh `QuestLog` plus the derived map. Format is
    // byte-identical, so worlds saved before this carve load unchanged.
    //
    // The sync helper `syncQuestLogFromLegacy` and the cache
    // consistency check `questLogCacheIsConsistent` are gone — there is
    // no second copy to fall out of sync, the SoT and the projection
    // are the same field. (`syncConstructionQueueFromLegacy` and
    // `constructionQueueCacheIsConsistent` were removed by ADR-0027
    // for the same reason.)
    //
    // No `applyQuestLog` write path this PR: `QuestRef` carries only
    // `(defId, type, status)`, while the MC `Quest` carries conditions,
    // rewards and the full TaskDef binding. A domain→legacy apply would
    // silently drop the rich per-quest data the engine needs to run the
    // quest tick. The `activeQuestMap` derived field carries that
    // rich data; the SoT on `questLog` carries the Minecraft-free
    // shape that the application layer can reason about without
    // `net.minecraft` on the classpath.
    // -------------------------------------------------------------------------

    /**
     * Returns the town's quest log as a Minecraft-free {@link QuestLog}.
     * This is the SoT (ADR-0028): the field itself, not a derived view.
     * The legacy `getActiveQuests()` / `getQuestDefLastCompleted()`
     * accessors return MC-typed projections for callers that still need
     * them (`TickScheduler.tickQuests`, `QuestManager.isAlreadyActive`,
     * `Settlers.tick`).
     */
    public QuestLog questLog() {
        return questLog;
    }

    /**
     * Read-only view of the rich per-quest state the engine tick needs —
     * `Quest` objects with conditions, rewards, and the {@code questId}
     * the contribute packet addresses. Backed by the derived
     * {@link #activeQuestMap} (LinkedHashMap preserves insertion order
     * so the NBT `ActiveQuests` list round-trips byte-for-byte). The
     * SoT is {@link #questLog}; this accessor stays MC-typed for the
     * `TickScheduler.tickQuests` /
     * `QuestManager.isAlreadyActive(def, List<Quest>)` /
     * `TownHubDataBuilder.buildQuestsTag` /
     * `C2SContributeQuestPacket.handle` consumers.
     */
    public List<Quest> getActiveQuests() {
        return List.copyOf(activeQuestMap.values());
    }

    /**
     * Appends a quest to the town's log. Mutates both the SoT
     * ({@link #questLog} gains a STATUS_ACTIVE ref for the def) and
     * the derived {@link #activeQuestMap} (the rich `Quest` keyed by
     * {@code questId}). Replacing a defId already on the roll is a
     * no-op at the SoT — `QuestLog.withAdded` replaces by defId, so a
     * second {@code addQuest} with the same defId swaps the existing
     * ref for the new one (the engine treats defId as the primary key).
     */
    public void addQuest(Quest q) {
        Objects.requireNonNull(q, "q");
        if (q.defId == null || q.defId.isEmpty()) return;
        if (q.questId == null || q.questId.isEmpty()) return;
        String type = q.questType != null ? q.questType : QuestRef.TYPE_TASK;
        activeQuestMap.put(q.questId, q);
        questLog = questLog.withAdded(QuestRef.of(q.defId, type, QuestRef.STATUS_ACTIVE));
    }

    /**
     * Drops the active quest with the given {@code questId} from both
     * the SoT (its defId ref is removed) and the derived map. No-op
     * when the questId is unknown — the SoT stays untouched.
     */
    public void removeQuest(String questId) {
        if (questId == null) return;
        Quest removed = activeQuestMap.remove(questId);
        if (removed == null) return;
        questLog = questLog.withRemoved(removed.defId);
    }

    /**
     * Read-only view of the {@code defId → tick} completion map.
     * Derived from {@link #questLog#lastCompleted()} — the SoT. Callers
     * MUST NOT mutate the returned map; use
     * {@link #stampQuestCompletion(String, long)} to record a completion
     * tick.
     */
    public Map<String, Long> getQuestDefLastCompleted() {
        return questLog.lastCompleted();
    }

    /**
     * Records the completion tick for a quest def. ADR-0028 — this is
     * the only sanctioned write path into the completion map. It
     * mutates the SoT {@link #questLog} so {@link #questLog()} stays
     * on the fast path. Negative ticks and empty defIds are dropped
     * silently (the engine never goes back in time and a defId is
     * always set).
     *
     * <p>If no ref with this defId is currently on the roll (the
     * engine already removed the active quest via
     * {@link #removeQuest(String)}), a STATUS_COMPLETED ref is appended
     * so the SoT matches the legacy semantic — `questLog.findById`
     * returns the completed ref, and {@link #getActiveQuests()} (which
     * filters by `activeQuestMap`) stays empty for this defId. This is
     * what {@code C2SContributeQuestPacket.handle} drives: the player
     * completes a quest → {@code removeQuest} drops the active ref →
     * {@code stampQuestCompletion} appends the completed ref + tick.
     */
    public void stampQuestCompletion(String defId, long gameTime) {
        if (defId == null || defId.isEmpty()) return;
        if (gameTime < 0L) return;
        questLog = questLog.withCompleted(defId, gameTime);
        if (questLog.findById(defId) == null) {
            questLog = questLog.withAdded(
                QuestRef.of(defId, QuestRef.TYPE_TASK, QuestRef.STATUS_COMPLETED));
        }
    }

    /**
     * Removes {@link #activeQuestMap} and {@link #questLog} entries
     * whose {@code defId} no longer appears in {@code validDefIds}.
     * Called at world load after datapacks have been read. Returns
     * {@code true} if either map shrank.
     *
     * <p>Mutation paths: the derived map loses every quest whose defId
     * is stale (logged at WARN — the same level the legacy code
     * used); the SoT's roll and completion map are both filtered by
     * the same defId set, then collapsed back into a fresh
     * {@link QuestLog} via {@link QuestLog#of(List, Map)} (which
     * defensively drops malformed entries).
     */
    public boolean cleanupOrphanedQuestData(Set<String> validDefIds) {
        boolean changedActive = activeQuestMap.entrySet().removeIf(e -> {
            if (validDefIds.contains(e.getValue().defId)) return false;
            LOGGER.warn("[OUAT] Removing orphaned quest {}", e.getValue().defId);
            return true;
        });
        List<QuestRef> filteredRefs = new ArrayList<>(questLog.entries().size());
        boolean refsChanged = false;
        for (QuestRef ref : questLog.entries()) {
            if (validDefIds.contains(ref.defId())) {
                filteredRefs.add(ref);
            } else {
                refsChanged = true;
            }
        }
        Map<String, Long> filteredCompleted = new LinkedHashMap<>();
        boolean completedChanged = false;
        for (Map.Entry<String, Long> e : questLog.lastCompleted().entrySet()) {
            if (validDefIds.contains(e.getKey())) {
                filteredCompleted.put(e.getKey(), e.getValue());
            } else {
                completedChanged = true;
            }
        }
        if (refsChanged || completedChanged) {
            questLog = QuestLog.of(filteredRefs, filteredCompleted);
        }
        return changedActive || refsChanged || completedChanged;
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

    public Map<Item, Integer> getReserveStock() { return reserveStock; }

    public List<PlacedBuilding> getBuildings() { return buildings; }
    public List<UUID> getBuilderNpcIds() { return builderNpcIds; }
    public int getTargetBuilderCount() { return targetBuilderCount; }

    /**
     * Who actually lives here, by UUID. The roll a settler validates itself against on load.
     *
     * <p>A list and not a count, and the distinction is the whole point. {@link
     * #getTotalResidents} is the town's <b>capacity</b> — beds summed over its buildings — and
     * before this the population WAS that number and nothing else, so nobody could be born,
     * nobody could leave and nobody could die. This is the roll of real people; capacity is only
     * the ceiling it may grow to.
     *
     * <p>Unordered, unlike {@link #builderNpcIds}, where the index is the builder's slot and a
     * null hole must be kept. Nobody has a resident slot.
     */
    public List<UUID> getResidentNpcIds() { return residentNpcIds; }

    /**
     * Returns the citizen's role for this town.
     *
     * <p>Backward-compatible shim: a citizen whose UUID is in the legacy {@code builderNpcIds}
     * list reads as {@link CitizenRole#BUILDER}; everyone else reads as {@link CitizenRole#IDLE}.
     * The full role system lives in {@link org.lowern1ght.burg.behavior.role.RoleAssigner}
     * and the engine will start honouring its assignments in the next slice. This method exists
     * so call sites that need a "what does this town think of this citizen" answer have one
     * place to look and the engine can later swap the implementation without rippling the call
     * sites.
     */
    public CitizenRole roleOf(UUID citizenId) {
        if (citizenId != null && builderNpcIds.contains(citizenId)) return CitizenRole.BUILDER;
        return CitizenRole.IDLE;
    }

    /** The town's people, records and all, living and dead. Never null. */
    public org.lowern1ght.burg.people.Population people() { return people; }

    public long getLastSettlerArrival() { return lastSettlerArrival; }

    public void setLastSettlerArrival(long gameTime) { this.lastSettlerArrival = gameTime; }

    /**
     * The gameTime of the town's last raid fire — the per-town anchor the
     * {@link org.lowern1ght.burg.tick.TickScheduler#tickRaids} gate reads on
     * every server tick. Returns {@code 0L} for a town that has never fired
     * (the additive default); the gate treats {@code 0L} as
     * "first-ever-fire-from-zero" and the cooldown counts from there
     * (see {@link org.lowern1ght.burg.behavior.war.RaidManager#tick}).
     */
    public long getLastRaidFireTick() { return lastRaidFireTick; }

    /**
     * Stamps the raid-fire tick. Called by {@code TickScheduler.tickRaids}
     * after a successful gate fire, so the next call's
     * {@code previousFire} is the firing gameTime.
     */
    public void setLastRaidFireTick(long gameTime) { this.lastRaidFireTick = gameTime; }

    /** Who works at this building, or null. */
    public UUID getJobHolder(BlockPos buildingPos) { return jobClaims.get(buildingPos); }

    /** @return false if somebody already holds it. One trade, one worker. */
    public boolean claimJob(BlockPos buildingPos, UUID settler) {
        if (jobClaims.containsKey(buildingPos)) return false;
        jobClaims.put(buildingPos, settler);
        return true;
    }

    /** Give up whatever this settler held. Safe to call for somebody who held nothing. */
    public void releaseJob(UUID settler) {
        jobClaims.values().removeIf(id -> id.equals(settler));
    }

    /** @return true if this person was not already on the roll. */
    public boolean addResident(UUID id) {
        if (id == null || residentNpcIds.contains(id)) return false;
        return residentNpcIds.add(id);
    }

    /** Struck off — dead, or moved away. Nothing refills the place; that is what capacity is for. */
    public boolean removeResident(UUID id) { return residentNpcIds.remove(id); }

    /**
     * Beds standing empty: how many more people this town could hold.
     *
     * <p>What immigration is gated on, together with food. Can go negative if a house is
     * destroyed while its occupants live, and is clamped so that a lost roof does not turn into
     * an eviction notice.
     */
    public int getVacancies() {
        return Math.max(0, getTotalResidents() - residentNpcIds.size());
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

    public void releaseAllClaimsForBuilder(UUID builderId) {
        queueIndexClaims.values().removeIf(id -> id.equals(builderId));
    }

    // After removing an entry at removedIdx, shift all claims at higher indices down by one
    // so they remain aligned with the new positions of their entries in the queue.
    private void shiftClaimsAfter(int removedIdx) {
        Map<Integer, UUID> shifted = new HashMap<>();
        queueIndexClaims.entrySet().removeIf(e -> {
            if (e.getKey() > removedIdx) {
                shifted.put(e.getKey() - 1, e.getValue());
                return true;
            }
            return false;
        });
        queueIndexClaims.putAll(shifted);
    }

    public boolean isQueueEntryClaimedByOther(int index, UUID builderId) {
        UUID existing = queueIndexClaims.get(index);
        return existing != null && !existing.equals(builderId);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    // -------------------------------------------------------------------------
    // ADR-0020 — vanilla-village conversion (bridgehead seam).
    //
    // The bind path lives here, not on TownAnchorBlock, because the footprint
    // collection and the Bridgehead placement are town-level concerns: the
    // Town is what keeps the blocked-zones roll, not the Anchor. The Anchor
    // is the *trigger*; the actual conversion is a Town method. The decider
    // itself is in the domain layer ({@link VanillaBindingDecider}); this
    // method is the Minecraft-aware facade that hands it `(int, int)` and a
    // footprint set.
    // -------------------------------------------------------------------------

    /**
     * The bridgehead NBT path. Resolves to {@code data/burg/structure/plains/bridgehead.nbt};
     * missing file is tolerated by {@link #bindToVanillaVillage} (a warning is logged and
     * the rest of the conversion still happens) so the in-game binding does not depend on
     * the street piece being shipped before it is authored.
     */
    public static final ResourceLocation BRIDGEHEAD_NBT =
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "plains/bridgehead");

    /**
     * XZ radius used to scan for vanilla house footprints. Aliased to
     * {@link VanillaBindingDecider#DEFAULT_RADIUS} so the scan and the decision agree;
     * changing one without the other would let the decider say "Skip" while the scan
     * produces a non-empty footprint set (or vice versa).
     */
    public static final int VANILLA_BIND_RADIUS = VanillaBindingDecider.DEFAULT_RADIUS;

    /**
     * The Minecraft-aware half of the vanilla-village conversion.
     *
     * <p>Scans every loaded block inside {@link #VANILLA_BIND_RADIUS} of {@code meetingPoint}
     * for doors and bed heads, hands the resulting set to {@link VanillaBindingDecider#decide},
     * and depending on the decision either enacts the binding (enlist existing villagers,
     * register every footprint as a blocked zone so growth never lands on a vanilla house,
     * place the bridgehead piece) or returns {@code false} without mutating this Town.
     *
     * <p>Unloaded chunks inside the radius are silently skipped, not failed: a vanilla
     * village whose centre is in an unloaded chunk produces an empty footprint set,
     * which the decider turns into {@link VanillaBindingDecision.Skip#noFootprints()}.
     * Same fallback the player gets from placing the anchor in open plains, so the
     * contract is "either we can see it or we skip".
     *
     * <p>Failure mode for a missing bridgehead NBT: log a warning, leave the
     * block alone, finish the rest of the conversion. Growth still works — the
     * town just enters Build mode without an outward seam from the vanilla street.
     *
     * @return {@code true} iff the conversion succeeded (the caller can register this
     *         town in {@link LevelTowns}); {@code false} means "no vanilla village at
     *         this position" and the anchor stays as today's campfire-only fallback.
     */
    public boolean bindToVanillaVillage(BlockPos meetingPoint, ServerLevel level) {
        Set<VanillaHouseFootprint> footprints = scanVanillaFootprints(meetingPoint, level);
        VanillaBindingDecision decision = new VanillaBindingDecider().decide(
            footprints, meetingPoint.getX(), meetingPoint.getZ());
        if (decision instanceof VanillaBindingDecision.Skip skip) {
            LOGGER.info("[OUAT] Town anchor at {} did not bind a vanilla village: {} ({})",
                meetingPoint.toShortString(), skip.reasonCode(), skip.detail());
            return false;
        }
        // We are binding. Enlist the existing villagers first so the people roll is
        // populated before any growth tick reads from it.
        int enlisted = Citizens.enlistAllNear(level, meetingPoint);
        // Reserve every collected footprint as a 1-block BlockedZone. Growth reads
        // getOccupiedBoxes() to decide where it may place, so a door/bed-head under
        // a Burg building is no longer a question.
        for (VanillaHouseFootprint fp : footprints) {
            addBlockedZone(new BoundingBox(fp.x(), fp.y(), fp.z(), fp.x(), fp.y(), fp.z()));
        }
        // Degrade gracefully if the bridgehead NBT is not on disk yet: the rest of the
        // conversion (villagers + blocked zones) still holds, growth just picks its own
        // first outward connector on the next tick instead of attaching to the bridgehead.
        if (level.getStructureManager().get(BRIDGEHEAD_NBT).isPresent()) {
            BuildSchematic.place(level, meetingPoint, BRIDGEHEAD_NBT, Rotation.NONE);
        } else {
            LOGGER.warn("[OUAT] Bridgehead NBT {} missing -- vanilla village at {} bound"
                + " without a bridgehead piece; growth will pick an alternative outward connector.",
                BRIDGEHEAD_NBT, meetingPoint.toShortString());
        }
        LOGGER.info("[OUAT] Town anchor at {} bound to vanilla village: {} footprints reserved,"
            + " {} villager(s) enlisted.", meetingPoint.toShortString(), footprints.size(), enlisted);
        return true;
    }

    /**
     * Scans every loaded block inside {@link #VANILLA_BIND_RADIUS} of {@code meetingPoint}
     * for vanilla house footprints. A "footprint" here is either a {@link DoorBlock} or
     * the {@link BedPart#HEAD} half of a {@link BedBlock}; both shapes mean "a vanilla
     * house lives here, growth must not land on top of it".
     *
     * <p>Y range covers the full vertical build envelope so doors placed inside a
     * hillside or on a plateau are still found; the {@link ServerLevel#isLoaded} gate
     * keeps the scan cheap on worlds with chunks still unloaded around the candidate.
     */
    private Set<VanillaHouseFootprint> scanVanillaFootprints(BlockPos meetingPoint, ServerLevel level) {
        Set<VanillaHouseFootprint> out = new HashSet<>();
        int r = VANILLA_BIND_RADIUS;
        BlockPos from = new BlockPos(
            meetingPoint.getX() - r, level.getMinBuildHeight(), meetingPoint.getZ() - r);
        BlockPos to = new BlockPos(
            meetingPoint.getX() + r, level.getMaxBuildHeight() - 1, meetingPoint.getZ() + r);
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock) {
                out.add(new VanillaHouseFootprint(pos.getX(), pos.getY(), pos.getZ()));
            } else if (state.getBlock() instanceof BedBlock
                    && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                out.add(new VanillaHouseFootprint(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return out;
    }

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
        ListTag residentIdsTag = new ListTag();
        for (UUID id : residentNpcIds) {
            if (id == null) continue;   // no slots here, so a hole carries no meaning
            CompoundTag idTag = new CompoundTag();
            idTag.putUUID("Id", id);
            residentIdsTag.add(idTag);
        }
        tag.put("ResidentNpcIds", residentIdsTag);
        tag.putLong("LastSettlerArrival", lastSettlerArrival);
        tag.putLong("LastRaidFireTick", lastRaidFireTick);
        ListTag jobClaimsTag = new ListTag();
        jobClaims.forEach((pos, id) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", pos.asLong());
            c.putUUID("Id", id);
            jobClaimsTag.add(c);
        });
        tag.put("JobClaims", jobClaimsTag);
        tag.put("People", PopulationNbt.save(people));
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
        // ADR-0027 — the SoT is the domain `ConstructionQueue`; serialize
        // each intent into the legacy `QueueEntry` NBT shape so the wire
        // format is byte-identical to pre-ADR-0027 saves.
        for (ConstructionIntent intent : constructionQueue.entries()) {
            cqTag.add(QueueEntry.serialize(QueueEntry.fromIntent(intent)));
        }
        tag.put("ConstructionQueue", cqTag);
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
        // ADR-0028 — quest log SoT flip. The SoT is `questLog`; the legacy
        // NBT shape is `ActiveQuests` (a list of MC `Quest` compounds) +
        // `QuestDefLastCompleted` (a defId → tick compound). The active list
        // is read from the derived `activeQuestMap` (preserves LinkedHashMap
        // insertion order so the list stays byte-identical to pre-ADR-0028
        // saves); the completion map is read straight from `questLog`.
        ListTag activeQuestsTag = new ListTag();
        for (Quest q : activeQuestMap.values()) {
            activeQuestsTag.add(q.toNbt());
        }
        tag.put("ActiveQuests", activeQuestsTag);
        if (!questLog.lastCompleted().isEmpty()) {
            CompoundTag qdlcTag = new CompoundTag();
            questLog.lastCompleted().forEach(qdlcTag::putLong);
            tag.put("QuestDefLastCompleted", qdlcTag);
        }
        if (!activityLog.isEmpty()) {
            ListTag logTag = new ListTag();
            for (TownLogEntry e : activityLog) {
                CompoundTag lt = new CompoundTag();
                lt.putString("Type", e.type().name());
                lt.putString("Param", e.param());
                lt.putLong("Tick", e.gameTick());
                logTag.add(lt);
            }
            tag.put("ActivityLog", logTag);
        }
        if (!activeBuilds.isEmpty()) {
            CompoundTag activeBuildsTag = new CompoundTag();
            activeBuilds.forEach((slot, state) -> activeBuildsTag.put(String.valueOf(slot), activeBuildStateToNbt(state)));
            tag.put("ActiveBuilds", activeBuildsTag);
        }
        if (!chatSubscribers.isEmpty()) {
            ListTag subsTag = new ListTag();
            chatSubscribers.forEach(id -> subsTag.add(StringTag.valueOf(id.toString())));
            tag.put("ChatSubscribers", subsTag);
        }
        tag.putLong("CpInsertionCounter", cpInsertionCounter);
        tag.putLong("NextEntryId", nextEntryId);
        // ADR-0009 — standing + acquisition. Always write Acquisition (so
        // FREE is recorded as an explicit default, not silently absent);
        // write Standings only when non-empty (sparse save).
        tag.putString("Acquisition", acquisition.toNbt());
        if (!standingBook.isEmpty()) {
            ListTag standTag = new ListTag();
            standingBook.entries().forEach((citizen, standing) -> {
                CompoundTag s = new CompoundTag();
                s.putString("Id", citizen.value());
                s.putInt("Value", standing.value());
                standTag.add(s);
            });
            tag.put("Standings", standTag);
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
        town.lastSettlerArrival = tag.getLong("LastSettlerArrival");
        // Additive: pre-raid-tick saves have no LastRaidFireTick key, default 0L
        // (the "never fired" sentinel the gate reads).
        town.lastRaidFireTick = tag.contains("LastRaidFireTick")
            ? tag.getLong("LastRaidFireTick") : 0L;
        if (tag.contains("People")) town.people = PopulationNbt.load(tag.getCompound("People"));
        if (tag.contains("JobClaims")) {
            tag.getList("JobClaims", Tag.TAG_COMPOUND).forEach(t -> {
                CompoundTag c = (CompoundTag) t;
                if (c.hasUUID("Id")) town.jobClaims.put(BlockPos.of(c.getLong("Pos")), c.getUUID("Id"));
            });
        }
        if (tag.contains("ResidentNpcIds")) {
            tag.getList("ResidentNpcIds", Tag.TAG_COMPOUND).forEach(t -> {
                CompoundTag idTag = (CompoundTag) t;
                if (idTag.hasUUID("Id")) town.residentNpcIds.add(idTag.getUUID("Id"));
            });
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
        town.cpInsertionCounter = tag.contains("CpInsertionCounter") ? tag.getLong("CpInsertionCounter") : (long) town.freeConnections.size();
        if (tag.contains("ReserveStock")) {
            CompoundTag reserveTag = tag.getCompound("ReserveStock");
            for (String key : reserveTag.getAllKeys()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(key));
                town.reserveStock.put(item, reserveTag.getInt(key));
            }
        }
        // ADR-0013 — sync the StockLedger cache after the additive NBT load.
        // A pre-ADR-0010 world has no StockLedger data; this produces the
        // EMPTY sentinel. A post-ADR-0010 world has reserveStock populated
        // by the loop above; this rebuilds the cache to match.
        town.syncStockLedgerFromReserve();
        tag.getList("BlockedZones", Tag.TAG_COMPOUND).forEach(t -> {
            CompoundTag zTag = (CompoundTag) t;
            town.blockedZones.add(new BoundingBox(
                zTag.getInt("MinX"), zTag.getInt("MinY"), zTag.getInt("MinZ"),
                zTag.getInt("MaxX"), zTag.getInt("MaxY"), zTag.getInt("MaxZ")
            ));
        });
        // BootstrapQueue: silently ignored - bootstrap system removed.
        // ADR-0027 — the legacy list was the SoT; after the flip the
        // domain `ConstructionQueue` is. We read the NBT into a local
        // list of `QueueEntry` first (so the NextEntryId restamp path
        // can mutate it freely), then collapse it into a fresh
        // domain queue at the end. The NBT shape is unchanged:
        // `ConstructionQueue` compounds deserialize via
        // `QueueEntry.deserialize`; legacy plain-string entries
        // deserialize as `NewBuild(0L, defId)` for backward compat.
        List<QueueEntry> legacyQueue = new ArrayList<>();
        if (tag.contains("ConstructionQueue")) {
            ListTag cqTagCompound = tag.getList("ConstructionQueue", Tag.TAG_COMPOUND);
            if (!cqTagCompound.isEmpty()) {
                // New format: compound tags with Type/DefId/etc fields
                for (Tag t : cqTagCompound) {
                    legacyQueue.add(QueueEntry.deserialize((CompoundTag) t));
                }
            } else {
                // Backward compat: old saves stored plain string defIds
                for (Tag t : tag.getList("ConstructionQueue", Tag.TAG_STRING)) {
                    legacyQueue.add(new QueueEntry.NewBuild(0L, t.getAsString()));
                }
            }
        }
        // Backward compat: old saves have no NextEntryId -- reassign sequential IDs to all entries.
        if (tag.contains("NextEntryId")) {
            town.nextEntryId = tag.getLong("NextEntryId");
        } else {
            long seq = 0L;
            List<QueueEntry> restamped = new ArrayList<>(legacyQueue.size());
            for (QueueEntry e : legacyQueue) {
                if (e instanceof QueueEntry.NewBuild nb)
                    restamped.add(new QueueEntry.NewBuild(seq++, nb.defId()));
                else if (e instanceof QueueEntry.Upgrade u)
                    restamped.add(new QueueEntry.Upgrade(seq++, u.defId(), u.buildingWorldPos(), u.fromLevel()));
                else restamped.add(e);
            }
            legacyQueue = restamped;
            town.nextEntryId = seq;
        }
        // Collapse the legacy list into a fresh domain queue. Empty
        // legacy yields the EMPTY sentinel; otherwise `ConstructionQueue.of`
        // defensively copies and clips to capacity.
        if (legacyQueue.isEmpty()) {
            town.constructionQueue = ConstructionQueue.EMPTY;
        } else {
            List<ConstructionIntent> intents = new ArrayList<>(legacyQueue.size());
            for (QueueEntry e : legacyQueue) intents.add(QueueEntry.toIntent(e));
            town.constructionQueue = ConstructionQueue.of(intents);
        }
        if (tag.contains("QueueReservedStock")) {
            CompoundTag qrTag = tag.getCompound("QueueReservedStock");
            for (String key : qrTag.getAllKeys()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(key));
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
        // ADR-0028 — quest log SoT flip. Read the legacy NBT into the
        // derived `activeQuestMap` (LinkedHashMap preserves insertion
        // order) and the completion map, then collapse both into the SoT
        // `questLog` so the post-load state is exactly what `toNbt`
        // would write for the same input. A pre-ADR-0012 world
        // (no `ActiveQuests`, no `QuestDefLastCompleted`) produces
        // QuestLog.EMPTY; a post-ADR-0028 world populates `questLog`
        // straight from the loops below — no sync helper, no cache
        // fallback, the SoT is the field the load writes into.
        if (tag.contains("ActiveQuests")) {
            tag.getList("ActiveQuests", Tag.TAG_COMPOUND)
                .forEach(t -> {
                    Quest q = Quest.fromNbt((CompoundTag) t);
                    if (q == null || q.questId == null || q.questId.isEmpty()
                        || q.defId == null || q.defId.isEmpty()) {
                        return;
                    }
                    town.activeQuestMap.put(q.questId, q);
                });
        }
        Map<String, Long> completed = new LinkedHashMap<>();
        if (tag.contains("QuestDefLastCompleted")) {
            CompoundTag qdlcTag = tag.getCompound("QuestDefLastCompleted");
            for (String key : qdlcTag.getAllKeys()) {
                completed.put(key, qdlcTag.getLong(key));
            }
        }
        List<QuestRef> entries = new ArrayList<>(town.activeQuestMap.size() + completed.size());
        Set<String> activeDefIds = new HashSet<>();
        for (Quest q : town.activeQuestMap.values()) {
            String type = q.questType != null ? q.questType : QuestRef.TYPE_TASK;
            entries.add(QuestRef.of(q.defId, type, QuestRef.STATUS_ACTIVE));
            activeDefIds.add(q.defId);
        }
        for (Map.Entry<String, Long> e : completed.entrySet()) {
            if (activeDefIds.contains(e.getKey())) continue;
            entries.add(QuestRef.of(e.getKey(), QuestRef.TYPE_TASK, QuestRef.STATUS_COMPLETED));
        }
        town.questLog = QuestLog.of(entries, completed);
        if (tag.contains("ActivityLog")) {
            tag.getList("ActivityLog", Tag.TAG_COMPOUND).forEach(t -> {
                CompoundTag lt = (CompoundTag) t;
                try {
                    TownLogEntry.TownLogType type = TownLogEntry.TownLogType.valueOf(lt.getString("Type"));
                    town.activityLog.addLast(new TownLogEntry(type, lt.getString("Param"), lt.getLong("Tick")));
                } catch (IllegalArgumentException ignored) {}
            });
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
        if (tag.contains("ChatSubscribers")) {
            tag.getList("ChatSubscribers", Tag.TAG_STRING).forEach(t -> {
                try { town.chatSubscribers.add(UUID.fromString(t.getAsString())); }
                catch (IllegalArgumentException ignored) {}
            });
        }
        // ADR-0009 — standing + acquisition. Missing keys default to FREE /
        // empty book; old saves load unchanged.
        town.acquisition = tag.contains("Acquisition")
            ? Acquisition.fromNbtOrDefault(tag.getString("Acquisition"))
            : Acquisition.FREE;
        if (tag.contains("Standings")) {
            Map<CitizenId, Standing> loaded = new LinkedHashMap<>();
            tag.getList("Standings", Tag.TAG_COMPOUND).forEach(t -> {
                CompoundTag s = (CompoundTag) t;
                CitizenId id = CitizenId.parseOrEmpty(s.getString("Id"));
                if (CitizenId.EMPTY.equals(id)) return;
                int value = s.getInt("Value");
                if (value == Standing.DEFAULT) return;
                loaded.put(id, new Standing(id, value));
            });
            town.standingBook = StandingBook.of(loaded);
        } else {
            town.standingBook = StandingBook.EMPTY;
        }
        return town;
    }

    private static CompoundTag connectionToNbt(ConnectionPoint c) {
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", NbtUtils.writeBlockPos(c.pos()));
        tag.putString("Dir", c.direction().getName());
        tag.putString("Pool", c.targetName());
        tag.putLong("Order", c.insertionOrder());
        return tag;
    }

    private static ConnectionPoint connectionFromNbt(CompoundTag tag) {
        BlockPos pos = Constants.readBlockPos(tag, "Pos");
        net.minecraft.core.Direction dir = net.minecraft.core.Direction.byName(tag.getString("Dir"));
        String pool = tag.getString("Pool");
        long order = tag.getLong("Order");
        return new ConnectionPoint(pos, dir != null ? dir : net.minecraft.core.Direction.NORTH, pool, order);
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
        if (s.queueEntryId() >= 0) tag.putLong("QueueEntryId", s.queueEntryId());
        return tag;
    }

    private static ActiveBuildState activeBuildStateFromNbt(CompoundTag tag) {
        String defId = tag.getString("DefId");
        if (defId.isEmpty()) return null;
        BlockPos placementPos = Constants.readBlockPos(tag, "PlacementPos");
        Rotation rotation;
        try { rotation = Rotation.valueOf(tag.getString("Rotation")); }
        catch (IllegalArgumentException e) { rotation = Rotation.NONE; }
        BlockPos connectionPos = Constants.readBlockPos(tag, "ConnectionPos");
        Direction connectionDir = Direction.byName(tag.getString("ConnectionDir"));
        if (connectionDir == null) connectionDir = Direction.NORTH;
        String connectionTarget = tag.getString("ConnectionTarget");
        BlockPos entryConnectorPos = Constants.readBlockPos(tag, "EntryConnectorPos");
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
        long queueEntryId = tag.contains("QueueEntryId") ? tag.getLong("QueueEntryId") : -1L;
        return new ActiveBuildState(defId, placementPos, rotation, connectionPos, connectionDir,
            connectionTarget, entryConnectorPos, cost, queueDefId, queueEntryId);
    }
}
