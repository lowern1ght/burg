package org.dawnoftime.onceuponatown.town;

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
        if (tag.contains("InstanceProductionMultiplier"))
            b.instanceProductionMultiplier = tag.getDouble("InstanceProductionMultiplier");
        CompoundTag stockTag = tag.getCompound("Stock");
        for (String key : stockTag.getAllKeys()) {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(key));
            b.stock.put(item, stockTag.getInt(key));
        }
        return b;
    }
}
