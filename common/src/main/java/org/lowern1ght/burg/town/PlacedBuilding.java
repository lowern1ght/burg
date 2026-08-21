package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.lowern1ght.burg.domain.settlement.StockLedger;
import org.lowern1ght.burg.domain.shared.ItemId;
import org.lowern1ght.burg.infrastructure.config.BuildingOutputCap;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class PlacedBuilding {
    public final String defId;
    public final BlockPos worldPos;
    // World bounding box of this building. Null for buildings loaded from saves that predate this field.
    public final BoundingBox bb;
    // Rotation applied when this building was placed. NONE for saves that predate this field.
    public final Rotation rotation;
    // Per-instance production multiplier. 1.0 = normal. Set to 1.15 for orientation bootstrap buildings.
    private double instanceProductionMultiplier = 1.0;
    private int upgradeLevel = 0;
    // Default true: safe on first load, updated each dawn by FoodManager.
    private boolean herdFed = true;
    private final Map<Item, Integer> stock = new HashMap<>();
    // Per-instance output ledger (act-4 follow-up-1 carve). The new source of
    // truth for what this building has produced; the MC `stock` map is the
    // legacy write-through mirror for the visual side (TownInventory, HUD,
    // town-hub stock list). Mutators below update both — ledger first,
    // then the MC map — so any divergence is detectable as a
    // ledger-vs-map mismatch. Same discipline `Town.stockLedger` uses for
    // its reserve mirror.
    private StockLedger outputLedger = StockLedger.EMPTY;

    public PlacedBuilding(String defId, BlockPos worldPos, BoundingBox bb, Rotation rotation) {
        this.defId = defId;
        this.worldPos = worldPos;
        this.bb = bb;
        this.rotation = rotation;
    }

    // Called by TickScheduler - the per-rule cap was retired (PR #46 carve)
    // but the per-instance output cap is back as a Cloth knob (ADR-0027).
    // Each production tick adds to the SoT ledger first (subject to the
    // FIFO cap), then mirrors to the MC `stock` map (also draining any
    // FIFO-dropped ItemIds). The legacy write-through shape
    // (ledger-first, then the MC map) matches {@link #forceAdd}.
    public void produce(ProductionEntry entry) {
        ItemId id = ItemId.parseOrEmpty(BuiltInRegistries.ITEM.getKey(entry.item()).toString());
        int current = outputLedger.get(id);
        int add = Math.min(entry.amount(), Integer.MAX_VALUE - current);
        if (add > 0) {
            StockLedger capped = applyOutputCap(outputLedger.add(id, add), this::mirrorFifoDropToStock);
            outputLedger = capped;
            stock.put(entry.item(), current + add);
        }
    }

    // Called by TownInventory.removeStock() - drains up to requested amount
    public int drain(Item item, int requested) {
        ItemId id = ItemId.parseOrEmpty(BuiltInRegistries.ITEM.getKey(item).toString());
        int available = outputLedger.get(id);
        int taken = Math.min(available, requested);
        if (taken > 0) {
            outputLedger = outputLedger.take(id, taken);
            stock.put(item, available - taken);
        }
        return taken;
    }

    // Called by Town.addStock() (player command) and by
    // ProductionManager.tickTransformer's write-through to the
    // legacy MC stock map. The per-rule cap is gone (PR #46 carve)
    // but the per-instance output cap is back (ADR-0027, driven by
    // {@link org.lowern1ght.burg.infrastructure.config.BuildingOutputCap}).
    // Both paths accumulate into {@link #outputLedger} first (the
    // source of truth, FIFO-capped at the edge) and then mirror into
    // the legacy {@code Map<Item, Integer>} stock map (the visual
    // side: TownInventory, HUD, town-hub stock list). Any entries
    // the FIFO drained leave both sides. Ledger first, MC
    // write-through — the discipline Town.stockLedger reserves for
    // its mirror.
    public void forceAdd(Item item, int quantity) {
        if (quantity <= 0) return;
        ItemId id = ItemId.parseOrEmpty(BuiltInRegistries.ITEM.getKey(item).toString());
        StockLedger capped = applyOutputCap(outputLedger.add(id, quantity), this::mirrorFifoDropToStock);
        outputLedger = capped;
        stock.merge(item, quantity, Integer::sum);
    }

    /**
     * Wire-side helper for the {@link StockLedger} → {@code Map<Item, Integer>}
     * apply path. The instance method ({@link #outputLedger()}) returns the
     * SoT directly; this static helper is the bridge a serialiser / NBT
     * writer / test fixture uses to (re)build an MC-typed target map from
     * a wire-format ledger without constructing a {@code PlacedBuilding}
     * (which would require a Minecraft world) and without paying for the
     * ledger-write side.
     *
     * <p>Same edge discipline as the mirror {@link Town#applyStockToReserve}:
     * the target map is cleared before the merge, zero and negative
     * quantities drop silently at the wire (no entry survives on the
     * target), unparseable {@link ItemId}s and unregistered {@link Item}s
     * bump the skipped counter, and duplicate wire entries sum onto the
     * existing quantity via {@link Map#merge}.
     *
     * <p>Static, no {@code this}-state — the carve that exposes the
     * helper also wants callers that batch across multiple buildings
     * (e.g. a future sync-from-server pass) to reach the same clear-and-merge
     * body without instantiating one {@code PlacedBuilding} per building.
     *
     * @param wire   the source-of-truth ledger (read-only; not mutated)
     * @param target the MC-typed view the helper populates (cleared first)
     * @return the number of dropped entries: unparseable ItemIds + unregistered
     *         Items + zero/negative quantities. A caller that wires a brand-new
     *         target from a possibly-dirty wire can surface the partial-apply
     *         count to a log line or chat warning.
     */
    public static int applyOutputToLedger(StockLedger wire, Map<Item, Integer> target) {
        Objects.requireNonNull(wire, "wire");
        Objects.requireNonNull(target, "target");
        target.clear();
        int skipped = 0;
        for (Map.Entry<ItemId, Integer> e : wire.entries().entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                // drop zero/negative quantities silently — same edge discipline as StockLedger.of
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
            target.merge(item, e.getValue(), Integer::sum);
        }
        return skipped;
    }

    public int getStock(Item item) { return stock.getOrDefault(item, 0); }
    public java.util.Set<Item> getStockedItems() { return stock.keySet(); }
    public String getDefId() { return defId; }

    /**
     * FIFO-cap the candidate ledger at the per-instance cap
     * {@link BuildingOutputCap#current()} exposes. Drains oldest
     * {@link ItemId} entries (insertion order — see
     * {@link StockLedger#entries()}) one at a time until the ledger is at
     * most {@code cap} entries; each drain is a full
     * {@link StockLedger#take} of the current quantity so zero-quantity
     * drops do not survive the edge.
     *
     * <p>Static, no {@code this}-state — the bare-JVM FIFO test exercises
     * the SoT-level discipline directly without instantiating a
     * {@code PlacedBuilding} or loading the MC registry. The
     * {@code onDrop} hook lets the instance mutators mirror each
     * FIFO-dropped {@link ItemId} onto the legacy {@code stock} map so
     * the SoT and the visual side stay in lockstep.
     *
     * <p>The {@link Integer#MAX_VALUE} sentinel on
     * {@link BuildingOutputCap#items()} is the documented "no cap" value;
     * the helper is a pass-through in that case. The spec clamps the
     * value to {@code [16, 4096]} so the sentinel only fires for a
     * caller that explicitly bypasses the spec (e.g. a future admin
     * command), which is the right place to opt out.
     *
     * @param candidate the post-add but pre-cap ledger (already
     *                  accumulated, not yet committed to {@link #outputLedger})
     * @param onDrop    invoked once per FIFO-dropped ItemId, oldest first;
     *                  may be {@code null} when the caller only needs the
     *                  SoT-level result (the bare-JVM test path)
     * @return the FIFO-capped ledger
     */
    static StockLedger applyOutputCap(StockLedger candidate, java.util.function.Consumer<ItemId> onDrop) {
        int cap = BuildingOutputCap.current().items();
        if (cap == Integer.MAX_VALUE) return candidate;
        StockLedger next = candidate;
        while (next.size() > cap) {
            ItemId oldest = next.entries().keySet().iterator().next();
            int qty = next.get(oldest);
            next = next.take(oldest, qty);
            if (onDrop != null) onDrop.accept(oldest);
        }
        return next;
    }

    /**
     * Mirror a FIFO-dropped {@link ItemId} onto the legacy {@code stock}
     * map. Best-effort: the {@link BuiltInRegistries#ITEM} lookup may
     * return {@code null} (e.g. an unparseable {@link ResourceLocation}
     * or an Item not registered in this world), in which case the drop
     * is ledger-only — the SoT-level FIFO discipline is preserved even
     * when the MC mirror cannot resolve the dropped key.
     */
    private void mirrorFifoDropToStock(ItemId droppedId) {
        ResourceLocation rl = ResourceLocation.tryParse(droppedId.value());
        if (rl == null) return;
        Item droppedItem = BuiltInRegistries.ITEM.get(rl);
        if (droppedItem == null) return;
        stock.remove(droppedItem);
    }

    // Per-instance output ledger (the new source of truth for what this
    // building has produced). Read-only view; the ledger itself is mutated
    // through produce/drain/forceAdd above.
    public StockLedger outputLedger() {
        return outputLedger;
    }

    // Rebuilds the output ledger from the MC stock map. Called by fromNbt
    // after the additive load of the StockTag; the legacy map and the
    // ledger are byte-for-byte equivalent on disk, so the rebuild mirrors
    // the NBT exactly. Idempotent — safe to call from any sync point.
    //
    // The cap is enforced on the rebuilt ledger too: a save that pre-dates
    // the cap, or a save where the user has since lowered the cap, comes
    // into compliance at load time. We do NOT mirror the FIFO drops back
    // to the `stock` map here — the rebuilt ledger is the authoritative
    // side after fromNbt, and the next write through forceAdd / produce
    // resyncs the mirror.
    private void syncOutputLedgerFromStock() {
        if (stock.isEmpty()) {
            outputLedger = StockLedger.EMPTY;
            return;
        }
        Map<ItemId, Integer> entries = new LinkedHashMap<>(stock.size());
        stock.forEach((item, qty) -> {
            if (item == null || qty == null || qty <= 0) return;
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null) return;
            entries.put(ItemId.parseOrEmpty(key.toString()), qty);
        });
        if (entries.isEmpty()) {
            outputLedger = StockLedger.EMPTY;
            return;
        }
        outputLedger = applyOutputCap(StockLedger.of(entries), null);
    }

    public double getInstanceProductionMultiplier() { return instanceProductionMultiplier; }
    public void setInstanceProductionMultiplier(double value) { this.instanceProductionMultiplier = value; }

    /**
     * Game time of the last completed shift here, and the skill of whoever worked it.
     *
     * <p>Two numbers on the building rather than a lookup from the building to its worker, and
     * that is the point: {@code ProductionManager} runs over buildings on a timer and must not
     * have to find, load or wait for an entity to decide what a workshop produced. A stamp also
     * survives the worker walking off, dying or having its chunk unloaded mid-shift.
     */
    private long lastWorkedTick = Long.MIN_VALUE;
    private int lastWorkerSkill = 0;

    public void recordWork(long gameTime, int workerSkill) {
        this.lastWorkedTick = gameTime;
        this.lastWorkerSkill = workerSkill;
    }

    public long getLastWorkedTick() { return lastWorkedTick; }
    public int getLastWorkerSkill() { return lastWorkerSkill; }

    /** Whether somebody has worked here recently enough for the place to count as manned. */
    public boolean isManned(long gameTime, long window) {
        return lastWorkedTick != Long.MIN_VALUE && gameTime - lastWorkedTick <= window;
    }

    public int getUpgradeLevel() { return upgradeLevel; }
    public void setUpgradeLevel(int level) { this.upgradeLevel = level; }

    public boolean isHerdFed() { return herdFed; }
    public void setHerdFed(boolean fed) { this.herdFed = fed; }

    // Returns the effective village-wide production bonus contributed by this building at its current upgrade level.
    public double resolvedProductionBonus(org.lowern1ght.burg.town.BuildingDef def) {
        double bonus = def.productionBonus;
        int capped = Math.min(upgradeLevel, def.upgrades.size());
        for (int i = 0; i < capped; i++) {
            bonus += def.upgrades.get(i).productionBonusAdd();
        }
        return bonus;
    }

    // Returns the extra capacity stacks this building adds to every productive building in the village.
    public int resolvedStockBonus(org.lowern1ght.burg.town.BuildingDef def) {
        int bonus = def.stockBonus;
        int capped = Math.min(upgradeLevel, def.upgrades.size());
        for (int i = 0; i < capped; i++) {
            bonus += def.upgrades.get(i).stockBonusAdd();
        }
        return bonus;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("DefId", defId);
        tag.putLong("WorldPos", worldPos.asLong());
        CompoundTag stockTag = new CompoundTag();
        stock.forEach((item, qty) -> {
            String key = BuiltInRegistries.ITEM.getKey(item).toString();
            stockTag.putInt(key, qty);
        });
        tag.put("Stock", stockTag);
        if (lastWorkedTick != Long.MIN_VALUE) {
            tag.putLong("LastWorkedTick", lastWorkedTick);
            tag.putInt("LastWorkerSkill", lastWorkerSkill);
        }
        if (bb != null) {
            CompoundTag bbTag = new CompoundTag();
            bbTag.putInt("MinX", bb.minX());
            bbTag.putInt("MinY", bb.minY());
            bbTag.putInt("MinZ", bb.minZ());
            bbTag.putInt("MaxX", bb.maxX());
            bbTag.putInt("MaxY", bb.maxY());
            bbTag.putInt("MaxZ", bb.maxZ());
            tag.put("BoundingBox", bbTag);
        }
        tag.putInt("Rotation", rotation.ordinal());
        if (instanceProductionMultiplier != 1.0)
            tag.putDouble("InstanceProductionMultiplier", instanceProductionMultiplier);
        if (upgradeLevel != 0)
            tag.putInt("UpgradeLevel", upgradeLevel);
        if (!herdFed)
            tag.putBoolean("HerdFed", false);
        return tag;
    }

    public static PlacedBuilding fromNbt(CompoundTag tag) {
        String defId = tag.getString("DefId");
        BlockPos pos = BlockPos.of(tag.getLong("WorldPos"));
        BoundingBox bb = null;
        if (tag.contains("BoundingBox")) {
            CompoundTag bbTag = tag.getCompound("BoundingBox");
            bb = new BoundingBox(
                bbTag.getInt("MinX"), bbTag.getInt("MinY"), bbTag.getInt("MinZ"),
                bbTag.getInt("MaxX"), bbTag.getInt("MaxY"), bbTag.getInt("MaxZ")
            );
        }
        Rotation rotation = tag.contains("Rotation")
            ? Rotation.values()[tag.getInt("Rotation")]
            : Rotation.NONE;
        PlacedBuilding b = new PlacedBuilding(defId, pos, bb, rotation);
        if (tag.contains("LastWorkedTick")) {
            b.lastWorkedTick = tag.getLong("LastWorkedTick");
            b.lastWorkerSkill = tag.getInt("LastWorkerSkill");
        }
        if (tag.contains("InstanceProductionMultiplier"))
            b.instanceProductionMultiplier = tag.getDouble("InstanceProductionMultiplier");
        if (tag.contains("UpgradeLevel"))
            b.upgradeLevel = tag.getInt("UpgradeLevel");
        b.herdFed = !tag.contains("HerdFed") || tag.getBoolean("HerdFed");
        CompoundTag stockTag = tag.getCompound("Stock");
        for (String key : stockTag.getAllKeys()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(key));
            b.stock.put(item, stockTag.getInt(key));
        }
        b.syncOutputLedgerFromStock();
        return b;
    }
}
