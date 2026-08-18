package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashMap;
import java.util.Map;

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

    public PlacedBuilding(String defId, BlockPos worldPos, BoundingBox bb, Rotation rotation) {
        this.defId = defId;
        this.worldPos = worldPos;
        this.bb = bb;
        this.rotation = rotation;
    }

    // Called by TickScheduler - respects the per-item capacity cap
    public void produce(ProductionEntry entry) {
        int current = stock.getOrDefault(entry.item(), 0);
        if (current < entry.capacityItems()) {
            stock.put(entry.item(), Math.min(current + entry.amount(), entry.capacityItems()));
        }
    }

    // Called by TownInventory.removeStock() - drains up to requested amount
    public int drain(Item item, int requested) {
        int available = stock.getOrDefault(item, 0);
        int taken = Math.min(available, requested);
        stock.put(item, available - taken);
        return taken;
    }

    // Called by Town.addStock() (player command) - bypasses capacity cap intentionally
    public void forceAdd(Item item, int quantity) {
        stock.merge(item, quantity, Integer::sum);
    }

    public int getStock(Item item) { return stock.getOrDefault(item, 0); }
    public java.util.Set<Item> getStockedItems() { return stock.keySet(); }
    public String getDefId() { return defId; }

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
        return b;
    }
}
