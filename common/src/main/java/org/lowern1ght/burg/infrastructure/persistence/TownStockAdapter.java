package org.lowern1ght.burg.infrastructure.persistence;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.lowern1ght.burg.application.settlement.ports.TownStockPort;
import org.lowern1ght.burg.domain.settlement.StockLedger;
import org.lowern1ght.burg.domain.shared.ItemId;
import org.lowern1ght.burg.town.ItemCost;
import org.lowern1ght.burg.town.Town;
import org.lowern1ght.burg.town.TownInventory;

import java.util.List;
import java.util.Objects;

/**
 * Infrastructure adapter: answers {@link TownStockPort} by delegating to the
 * existing public {@link Town} facade (ADR-0014). Deliberately thin — every
 * method is a delegation over an existing public path; the only added work is
 * the {@link ItemId} → Minecraft {@code Item} resolution at the edge.
 *
 * <p>Read/write asymmetry inherited from the strangler (ADR-0010) and
 * documented on the port: {@link #stock()} is the reserve-only ledger view;
 * {@link #add} follows the existing player-deposit path
 * ({@code Town.addStock} — first building's storage if any, else reserve);
 * {@link #tryTake} checks and drains the aggregate inventory
 * ({@link TownInventory#getStock} / {@code removeStock} — buildings first,
 * then reserve, the same drain order the construction queue uses).
 *
 * <p>This is the only class in the stock flow that may import Minecraft
 * types ({@code BuiltInRegistries}, {@code Item}). Mutation callers remain
 * responsible for marking the owning {@code LevelTowns} SavedData dirty,
 * exactly as with direct {@code Town} calls.
 */
public final class TownStockAdapter implements TownStockPort {

    private final Town town;

    public TownStockAdapter(Town town) {
        this.town = town;
    }

    @Override
    public StockLedger stock() {
        return town.stockLedger();
    }

    @Override
    public boolean tryTake(ItemId item, int quantity) {
        Objects.requireNonNull(item, "item");
        if (quantity <= 0) return false;
        Item mcItem = resolveItem(item);
        TownInventory inventory = town.getTownInventory();
        if (inventory.getStock(mcItem) < quantity) return false;
        inventory.removeStock(List.of(new ItemCost(mcItem, quantity)));
        return true;
    }

    @Override
    public void add(ItemId item, int quantity) {
        town.addStock(resolveItem(item), quantity);
    }

    /**
     * Resolves the canonical {@code namespace:path} form back to the
     * registered Minecraft item. An unregistered id (mod absent, id typo)
     * throws — a supply of nothing is a caller bug, not a silent no-op.
     */
    private static Item resolveItem(ItemId item) {
        Objects.requireNonNull(item, "item");
        ResourceLocation key = ResourceLocation.parse(item.value());
        Item resolved = BuiltInRegistries.ITEM.get(key);
        if (resolved == null) {
            throw new IllegalStateException(
                "ItemId '" + item.value() + "' is not a registered item");
        }
        return resolved;
    }
}
